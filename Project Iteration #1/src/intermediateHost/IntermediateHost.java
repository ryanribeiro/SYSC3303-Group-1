package intermediateHost;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * a class representing the intermediate host for the server-client-intermediate host system. has
 * capabliitty to recieve/send messages from/to both client and server
 * 
 * @author luken
 *
 */
public class IntermediateHost {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//port number of intermediate host
	private static final int INTERMEDIATE_HOST_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 100;
	//change this to turn on/off timeouts for the intermediate host
	private static final boolean TIMEOUTS_ON = true;
	//miliseconds until intermediate host times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;

	//socket for intermediate host to send and recieve packets
	private DatagramSocket recieveSocket, sendRecieveSocket;
	//buffer to contain data to send to server/client
	private DatagramPacket recievePacket;
	//port number of client to send response to
	private int clientPort;

	/**
	 * Constructor
	 * 
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public IntermediateHost() throws SocketException{
		recieveSocket = new DatagramSocket();
		sendRecieveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON){
			sendRecieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
			recieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		}
		//create packet of max size to garunettee it fits a received message
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
	}

	/**
	 * Constructor
	 * 
	 * @param port integer representing the port number to bind intemediate host's socket to
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public IntermediateHost(int port) throws SocketException{
		this();
		recieveSocket = new DatagramSocket(port);
	}

	/**
	 *reutrn the data in the datagram packet received
	 *
	 * @return  the data in the recieved packet 
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
	 * intermediate host waits until it recieves a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occured
	 * @return returns the recieve datagram packet
	 */
	public DatagramPacket waitRecieveClientMessage() throws IOException{
		recieveSocket.receive(recievePacket);
		//get the port number from the sender (client) to send response
		clientPort = recievePacket.getPort();

		return recievePacket;
	}

	/**
	 * intermediate host waits until it recieves a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occured
	 * @return returns the recieve datagram packet
	 */
	public DatagramPacket waitRecieveServerMessage() throws IOException{
		sendRecieveSocket.receive(recievePacket);
		return recievePacket;
	}

	/**
	 * sends a datagram through the intermediate host's sendRecieveSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occured while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendRecieveSocket.send(message);
	}

	/**
	 * main for intermediate host program containg specified 
	 * intermediate host algoriethm
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		//attempt to create intermediate host
		IntermediateHost intermediateHost = null;
		try {
			intermediateHost = new IntermediateHost(INTERMEDIATE_HOST_PORT_NUMBER);
		} catch (SocketException e) {
			System.out.println("SocketException: failed to create socket for intermediate host");
			e.printStackTrace();
			System.exit(1);
		}

		while(true){
			//wait for message to come in from client
			DatagramPacket request = null;
			try {
				System.out.println("Intermediate host waiting on request...");
				request = intermediateHost.waitRecieveClientMessage();
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while intermediate host waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			}

			//get meaningful portion of message
			byte[] clientMessageData = Arrays.copyOf(request.getData(), request.getLength());		

			//print data received from client
			System.out.println("Intermediate host recieved message: ");
			System.out.println("From host: " + request.getAddress());
			System.out.println("	on port: " + request.getPort());
			System.out.println("Message length: " + request.getLength());
			System.out.println("Containing: " + new String(clientMessageData));
			System.out.println("Conents as raw data: " + Arrays.toString(clientMessageData));

			//create packet to send to server on specified port
			DatagramPacket sendPacket = null;
			try {
				sendPacket = new DatagramPacket(clientMessageData, clientMessageData.length,
						InetAddress.getLocalHost(), SERVER_PORT_NUMBER);
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.out.println("UnknownHostException: could not determine IP address of host while creating packet to send to server.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to server
			System.out.println("Intermediate host to send message to server: ");
			System.out.println("To host: " + sendPacket.getAddress());
			System.out.println("	on port: " + sendPacket.getPort());
			System.out.println("Message length: " + sendPacket.getLength());
			System.out.println("Containing: " + new String(sendPacket.getData()));
			System.out.println("Conents as raw data: " + Arrays.toString(sendPacket.getData()));


			//send datagram to server
			try {
				intermediateHost.sendMessage(sendPacket);
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while intermediate host sending message");
				e.printStackTrace();
				System.exit(1);
			}

			System.out.println("Intermediate host sent message to server");

			//wait to receive response from server
			DatagramPacket response = null;
			try {
				System.out.println("Intermediate host waiting on response from server...");
				response = intermediateHost.waitRecieveServerMessage();
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while intermediate host waiting for response");
				e.printStackTrace();
				System.exit(1);
			}

			//get meaningful portion of message
			byte[] serverMessageData = Arrays.copyOf(response.getData(), response.getLength());

			//print response received from server
			System.out.println("response recieved by intermediate host: ");
			System.out.println("To host: " + response.getAddress());
			System.out.println("	on port: " + response.getPort());
			System.out.println("Message length: " + response.getLength());
			System.out.println("Containing: " + Arrays.toString(serverMessageData));


			//create packet to send to client on client port
			sendPacket = null;
			try {
				sendPacket = new DatagramPacket(serverMessageData, serverMessageData.length,
						InetAddress.getLocalHost(), intermediateHost.getClientPort());
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.out.println("UnknownHostException: could not determine IP address of host while creating packet to send to client.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to client
			System.out.println("Message to send from intermediate host to client: ");
			System.out.println("To host: " + sendPacket.getAddress());
			System.out.println("	on port: " + sendPacket.getPort());
			System.out.println("Message length: " + sendPacket.getLength());
			System.out.println("Containing: " + Arrays.toString(sendPacket.getData()));

			//send datagram to client
			try {
				intermediateHost.sendMessage(sendPacket);
			} catch (IOException e) {
				System.out.println("IOException: I/O error occured while intermediate host sending message");
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Intermediate host sent message to host");
		}
	}
}