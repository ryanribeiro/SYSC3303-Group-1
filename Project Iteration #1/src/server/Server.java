package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * a class representing the server for the server-client-intermediate host system. has
 * Capability to receive requests, process them, and send appropriate responses
 * 
 * @author Luke Newton
 *
 */
public class Server {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//change this to turn on/off timeouts for the server
	private static final boolean TIMEOUTS_ON = false;
	//Milliseconds until server times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 100;

	//socket to receive messages
	private DatagramSocket receiveSocket;
	//port number of client to send response to
	private int clientPort;
	//buffer to contain data to send to client
	private DatagramPacket receivePacket;


	private static Thread serverLogicThread;

	/**
	 * Constructor
	 * 
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public Server() throws SocketException{
		receiveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON)
			receiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);

		//create packet of max size to guarantee it fits a received message
		receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);

	}

	/**
	 * Constructor
	 * 
	 * @param port integer representing the port number to bind intermediate host's socket to
	 * @throws SocketException indicate failed to create socket for the intermediate host
	 */
	public Server(int port) throws SocketException{
		this();
		receiveSocket = new DatagramSocket(port);
	}


	/**
	 *Return the data in the datagram packet received
	 *
	 * @return  the data in the datagram packet received
	 */
	public byte[] getreceivePacketData(){
		return receivePacket.getData();
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
	 * client waits until it receives a message, which is parsed, stored in receivePacket and returned
	 * 
	 * @return the message received as a  DatagramPacket
	 * @throws IOException indicated an I/O error has occurred
	 * @throws InvalidMessageFormatException indicates that the message received was not a valid format
	 */
	public DatagramPacket waitReceiveMessage() throws IOException, InvalidMessageFormatException{

		receiveSocket.receive(receivePacket);
		try {

			serverLogicThread = new ServerSpawnThread(receivePacket);       
			serverLogicThread.start();


		} catch (Exception se) {
			se.printStackTrace();
			System.exit(1);
		}

		return receivePacket;

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
		System.out.println("Contents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}

	/**
	 * main method for server program containing specified server algorithm
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		//attempt to create server
		Server server = null;
		try {
			server = new Server(SERVER_PORT_NUMBER);

		} catch (SocketException e) {
			System.err.println("SocketException: failed to create socket for server");
			e.printStackTrace();
			System.exit(1);
		}

		while(true) {

			/*****************
			 * Receive Packet *
			 *****************/
			DatagramPacket request = null;
			try {
				System.out.println("Server waiting for request...");
				request = server.waitReceiveMessage();

			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while server waiting to receive message");
				e.printStackTrace();
				System.exit(1);
			} catch (InvalidMessageFormatException e) {
				System.err.println("InvalidMessageFormatException: received message is of invalid format");
				e.printStackTrace();
				System.exit(1);
			}	

			//print data received from intermediate host
			System.out.print("Server received message: \nFrom ");
			server.printPacketInfo(request);


		}
	}
}