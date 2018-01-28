package errorSimulator;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * A class representing the error simulator for the server-client-error simulator system. 
 * Has capability to receive/send messages from/to both client and server.
 * 
 * @author Luke Newton
 *
 */
public class ErrorSimulator {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//port number of error simulator
	private static final int ERROR_SIM_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 100;
	//change this to turn on/off timeouts for the error simulator
	private static final boolean TIMEOUTS_ON = true;
	//miliseconds until error simulator times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;

	//socket for error simulator to send and receive packets
	private DatagramSocket recieveSocket, sendRecieveSocket;
	//buffer to contain data to send to server/client
	private DatagramPacket recievePacket;
	//port number of client to send response to
	private int clientPort;

	/**
	 * Constructor
	 * 
	 * @throws SocketException indicate failed to create socket for the error simulator
	 */
	public ErrorSimulator() throws SocketException{
		recieveSocket = new DatagramSocket();
		sendRecieveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON){
			sendRecieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
			recieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		}
		//create packet of max size to guarantee it fits a received message
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
	}

	/**
	 * Constructor with defined port number to receive on.
	 * 
	 * @param port integer representing the port number to bind error simulator's socket to
	 * @throws SocketException indicate failed to create socket for the error simulator
	 */
	public ErrorSimulator(int port) throws SocketException{
		this();
		recieveSocket = new DatagramSocket(port);
	}

	/**
	 *Return the data in the datagram packet received
	 *
	 * @return  the data in the received packet 
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
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	public DatagramPacket waitRecieveClientMessage() throws IOException{
		recieveSocket.receive(recievePacket);
		//get the port number from the sender (client) to send response
		clientPort = recievePacket.getPort();

		return recievePacket;
	}

	/**
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	public DatagramPacket waitRecieveServerMessage() throws IOException{
		sendRecieveSocket.receive(recievePacket);
		return recievePacket;
	}

	/**
	 * sends a datagram through the error simulator's sendRecieveSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendRecieveSocket.send(message);
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
	 * main for error simulator program containing specified 
	 * error sim algorithm
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		//attempt to create error simulator
		ErrorSimulator errorSim = null;
		try {
			errorSim = new ErrorSimulator(ERROR_SIM_PORT_NUMBER);
		} catch (SocketException e) {
			System.err.println("SocketException: failed to create socket for error simulator");
			e.printStackTrace();
			System.exit(1);
		}

		while(true){
			//wait for message to come in from client
			DatagramPacket request = null;
			try {
				System.out.println("Error simulator waiting on request...");
				request = errorSim.waitRecieveClientMessage();
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			}

			//get meaningful portion of message
			byte[] clientMessageData = Arrays.copyOf(request.getData(), request.getLength());		

			//print data received from client
			System.out.print("Error simulator recieved message: \nFrom ");
			errorSim.printPacketInfo(request);

			//create packet to send to server on specified port
			DatagramPacket sendPacket = null;
			try {
				sendPacket = new DatagramPacket(clientMessageData, clientMessageData.length,
						InetAddress.getLocalHost(), SERVER_PORT_NUMBER);
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.err.println("UnknownHostException: could not determine IP address of host while creating packet to send to server.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to server
			System.out.print("Error simulator to send message to server: \nTo ");
			errorSim.printPacketInfo(sendPacket);


			//send datagram to server
			try {
				errorSim.sendMessage(sendPacket);
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator sending message");
				e.printStackTrace();
				System.exit(1);
			}

			System.out.println("Error simulator sent message to server");

			//wait to receive response from server
			DatagramPacket response = null;
			try {
				System.out.println("Error simulator waiting on response from server...");
				response = errorSim.waitRecieveServerMessage();
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator waiting for response");
				e.printStackTrace();
				System.exit(1);
			}

			//get meaningful portion of message
			byte[] serverMessageData = Arrays.copyOf(response.getData(), response.getLength());

			//print response received from server
			System.out.print("Response recieved by error simulator: \nFrom ");
			errorSim.printPacketInfo(response);


			//create packet to send to client on client port
			sendPacket = null;
			try {
				sendPacket = new DatagramPacket(serverMessageData, serverMessageData.length,
						InetAddress.getLocalHost(), errorSim.getClientPort());
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.err.println("UnknownHostException: could not determine IP address of host while creating packet to send to client.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to client
			System.out.print("Message to send from error simulator to client: \nTo ");
			errorSim.printPacketInfo(sendPacket);

			//send datagram to client
			try {
				errorSim.sendMessage(sendPacket);
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator sending message");
				e.printStackTrace();
				System.exit(1);
			}
			System.out.println("Error simulator sent message to client");
		}
	}
}