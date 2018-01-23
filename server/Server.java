package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * a class representing the server for the server-client-intermediate host system. has
 * capabliitty to recieve requests, process them, and send appropriate responses
 * 
 * @author luken
 *
 */
public class Server {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//change this to turn on/off timeouts for the server
	private static final boolean TIMEOUTS_ON = true;
	//miliseconds until server times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 100;

	//socket to recieve messages
	private DatagramSocket recieveSocket, sendSocket;
	//port number of client to send response to
	private int clientPort;
	//buffer to contain data to send to client
	private DatagramPacket recievePacket;
	//flags to indicate if recieved mesage is a read/write request
	private boolean readRequest, writeRequest;

	/**
	 * Constructor
	 * 
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public Server() throws SocketException{
		recieveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON)
			recieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		sendSocket = null;
		//create packet of max size to garunettee it fits a received message
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
		readRequest = false;
		writeRequest = false;
	}

	/**
	 * Constructor
	 * 
	 * @param port integer representing the port number to bind intemediate host's socket to
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public Server(int port) throws SocketException{
		this();
		recieveSocket = new DatagramSocket(port);
	}

	/**
	 *reutrn the data in the datadram packet received
	 *
	 * @return  the data in the datagram packet received
	 */
	public byte[] getRecievePacketData(){
		return recievePacket.getData();
	}

	/**
	 * returns the port number of the latest client to send a message here
	 * 
	 * @return the port number of the latest client to send a message here
	 */
	public int getClientPort(){
		return clientPort;
	}

	/**
	 * client waits until it recieves a message, which is parsed, stored in receivePacket and returned
	 * 
	 * @return the message received as a  DatagramPacket
	 * @throws IOException indicated an I/O error has occured
	 * @throws InvalidMessageFormatException indicates that the mesage received was not a valid format
	 */
	public DatagramPacket waitRecieveMessage() throws IOException, InvalidMessageFormatException{
		recieveSocket.receive(recievePacket);
		//get the port number from the sender (client) to send response
		clientPort = recievePacket.getPort();
		//check mesage for proper format
		parseMessage();
		return recievePacket;
	}

	/**
	 * ensures the received message is of proper format. format follows:
	 * 
	 * byte 0: 0 byte
	 * byte 1: 1 byte for read request, 2 byte for write request
	 * byte 2 to n: some text
	 * byte n+1: 0 byte
	 * byte n+2 to m: some text
	 * byte m+1: 0 byte
	 * nothing follows (ie. byte array has m+2 elements)
	 * 
	 * @throws InvalidMessageFormatException indicates that the recieved message is not a valid read/write command
	 */
	private void parseMessage() throws InvalidMessageFormatException{
		byte[] messageData = Arrays.copyOf(recievePacket.getData(), recievePacket.getLength());

		//check first byte
		if(messageData[0] != 0)
			throw new InvalidMessageFormatException();

		//check read/write byte
		if(messageData[1] == 1){
			readRequest = true;
			writeRequest = false;
		}else if(messageData[1] == 2){
			readRequest = false;
			writeRequest = true;
		} else
			throw new InvalidMessageFormatException();

		int currentIndex = 2;
		try{
			//check for some text followed by a zero
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			for(;messageData[currentIndex] != 0; currentIndex++){}

			//check for some text followed by a zero
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			for(currentIndex++; messageData[currentIndex] != 0; currentIndex++){}
		} catch (IndexOutOfBoundsException e){
			/*if we go out of bounds while iterating through the message data,
			 * then it does not end in a 0 and thus is incorrect format
			 */
			throw new InvalidMessageFormatException();
		}
		//check that this is the end of the message
		if(currentIndex + 1 != messageData.length){
			throw new InvalidMessageFormatException();
		}
	}

	/**
	 * sends a datagram through the servers's sendSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occured while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendSocket = new DatagramSocket();
		sendSocket.send(message);
		sendSocket.close();
	}

	/**
	 * creates the data to be placed into a  DatagramPacket based on the type of request last received
	 * 
	 * @return a byte array containing the response to the last request sent
	 */
	public byte[] createPacketData(){
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byteStream.write(0);

		if(readRequest)
			byteStream.write(3);
		else if (writeRequest)
			byteStream.write(4);

		byteStream.write(0);

		if(readRequest)
			byteStream.write(1);
		else if (writeRequest)
			byteStream.write(0);

		return byteStream.toByteArray();
	}

	/**
	 * main method for server program containing sepecified server algorithm
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		//attempt to create server
		Server server = null;
		try {
			server = new Server(SERVER_PORT_NUMBER);
		} catch (SocketException e) {
			System.out.println("SocketException: failed to create socket for server");
			e.printStackTrace();
			System.exit(1);
		}

		while(true){
			//wait for message from intermediate host
			DatagramPacket request = null;
			try {
				System.out.println("Server waiting for request...");
				request = server.waitRecieveMessage();
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while server waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			} catch (InvalidMessageFormatException e) {
				System.out.println("InvalidMessageFormatException: received message is of invalid format");
				e.printStackTrace();
				System.exit(1);
			}

			//get meaningful portion of message
			byte[] clientMessageData = Arrays.copyOf(request.getData(), request.getLength());		

			//print data received from intermediate host
			System.out.println("Server recieved message: ");
			System.out.println("From host: " + request.getAddress());
			System.out.println("	on port: " + request.getPort());
			System.out.println("Message length: " + request.getLength());
			System.out.println("Containing: " + new String(clientMessageData));
			System.out.println("Conents as raw data: " + Arrays.toString(clientMessageData));

			//create packet to send back to intermediate host
			DatagramPacket sendPacket = null;
			byte[] responseData = server.createPacketData();
			try {
				sendPacket = new DatagramPacket(responseData, responseData.length,
						InetAddress.getLocalHost(), server.getClientPort());
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.out.println("UnknownHostException: could not determine IP address of host while creating server response.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to intermediate host
			System.out.println("Server Response: ");
			System.out.println("To host: " + sendPacket.getAddress());
			System.out.println("	on port: " + sendPacket.getPort());
			System.out.println("Message length: " + sendPacket.getLength());
			System.out.println("Containing: " + Arrays.toString(sendPacket.getData()));

			//send datagram to intermediate host
			try {
				server.sendMessage(sendPacket);
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while server sending message");
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Server response sent");
		}
	}
}
