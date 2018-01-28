package client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * a class representing the client for the server-client-intermediate host system. has
 * Capability to send and receive messages to and from the intermediate host, 
 * and format read/write requests
 * 
 * @author lukeN
 */
public class Client {
	//number of times client algorithm repeats in main
	private static final int NUMBER_OF_CLIENT_MAIN_ITERATIONS = 11;
	//port number of intermediate host
	private static final int INTERMEDIATE_HOST_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 100;
	//file name to send to server
	private static final String FILENAME = "test.txt";
	//mode to send to server
	private static final String MODE = "octet";
	//change this to turn on/off timeouts for the client
	private static final boolean TIMEOUTS_ON = true;
	//Milliseconds until client times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;

	//socket used by client to send and receive datagrams
	private DatagramSocket sendRecieveSocket;
	//place to store response from intermediate host
	private DatagramPacket recievePacket;

	/**Constructor
	 * @throws SocketException indicates failed to create socket for client
	 * */
	public Client() throws SocketException {
		//attempt to create socket for send and receive
		sendRecieveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON)
			sendRecieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);

		//create packet of max size to guarantee it fits a received message
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
	}

	/**
	 * closes the socket for the client
	 */
	public void closeClientSocket() {
		sendRecieveSocket.close();
	}

	/**
	 *return the data in the datagram packet received
	 *
	 * @return  the data in the datagram packet received
	 */
	public byte[] getRecievePacketData(){
		return recievePacket.getData();
	}

	/**
	 * formats a message passed to contain the predefined format for a read request
	 * 
	 * @param filename filename to send with the read request
	 * @param mode the mode to send with the read request
	 * @param type a RequestType enum for the type of request to send
	 * @return the message converted into a byte array with proper format
	 */
	public static byte[] createPacketData(String filename, String mode, RequestType type) {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byteStream.write(0);
		if(type == RequestType.read)
			byteStream.write(1);
		else if (type == RequestType.write)
			byteStream.write(2);
		byteStream.write(filename.getBytes(), 0, filename.getBytes().length);
		byteStream.write(0);
		byteStream.write(mode.getBytes(), 0, mode.getBytes().length);
		byteStream.write(0);

		return byteStream.toByteArray();
	}

	/**
	 * sends a datagram through the intermediate host's sendRecieveSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendRecieveSocket.send(message);
	}

	/**
	 * client waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	public DatagramPacket waitRecieveMessage() throws IOException{
		sendRecieveSocket.receive(recievePacket);	
		return recievePacket;
	}
	
	/**
	 * prints packet information
	 * 
	 * @author Luke Newton, Cameron Rushton
	 * @param packet : DatagramPacket
	 */
	public void printPacketInfo(DatagramPacket packet) {
		//get meaningful portion of message
		byte[] dataAsByteArray = Arrays.copyOf(packet.getData(), packet.getLength());		

		System.out.println("host: " + packet.getAddress() + ":" + packet.getPort());
		System.out.println("Message length: " + packet.getLength());
		System.out.println("Containing: " + new String(dataAsByteArray));
		System.out.println("Conents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}

	/**
	 * main program for client program containing specified client algorithm
	 *  
	 * @param args
	 */
	public static void main(String[] args) {
		Client client = null;
		//attempt to create a client, exiting if fail to create
		try {
			client = new Client();
		} catch (SocketException e) {
			//failed to create client due to failure to create DatagramSocket
			System.err.println("SocketException: failed to create socket for client program.");
			e.printStackTrace();
			System.exit(1);
		}

		//repeat for specified number of iterations
		for(int i = 0; i < NUMBER_OF_CLIENT_MAIN_ITERATIONS; i++){
			/*create content for DatagramPacket to send to intermediate host*/
			byte[] requestData;
			
			/*alternate between read and write requests, with final being invalid format*/
			if(i == NUMBER_OF_CLIENT_MAIN_ITERATIONS - 1) {
				//final invalid format message
				String message = "this message is invalid format";
				requestData = message.getBytes();
				
			} else if(i % 2 == 0) {
				//read request
				requestData = createPacketData(FILENAME, MODE, RequestType.read);
				
			} else {
				//write request
				requestData = createPacketData(FILENAME, MODE, RequestType.write);
			}

			//create packet to send to intermediate host on specified port
			DatagramPacket sendPacket = null;
			try {
				sendPacket = new DatagramPacket(requestData, requestData.length,
						InetAddress.getLocalHost(), INTERMEDIATE_HOST_PORT_NUMBER);
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.err.println("UnknownHostException: could not determine IP address of host while creating packet to send to intermediate host.");
				e.printStackTrace();
				System.exit(1);
			}

			//print information to send in packet to intermediate host
			System.out.print("Client sending message: \nTo ");
			client.printPacketInfo(sendPacket);

			//send datagram to intermediate host
			try {
				client.sendMessage(sendPacket);
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while sending message to intermediate host");
				e.printStackTrace();
				System.exit(1);
			}

			System.out.println("Client sent packet to intermediate host");

			//wait to receive response from intermediate host
			DatagramPacket response = null;
			try {
				System.out.println("Client waiting for response...");
				response = client.waitRecieveMessage();
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while client waiting for response");
				e.printStackTrace();
				System.exit(1);
			}

			//print information received from intermediate host
			System.out.print("Client received message: \nFrom ");
			client.printPacketInfo(response);
			
		}
		client.closeClientSocket();
	}
}