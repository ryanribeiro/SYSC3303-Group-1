package errorSimulator;

import java.net.DatagramSocket;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;

/**
 * A class representing the error simulator for the server-client-error simulator system. 
 * Has capability to receive/send messages from/to both client and server.
 */
public class ErrorSimulator {
	//port number of error simulator
	private static final int ERROR_SIM_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 516;
	//change this to turn on/off timeouts for the error simulator
	private static final boolean TIMEOUTS_ON = false;
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
	 * @author Luke Newton
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
	 * @author Luke Newton
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
	 *@author Luke Newton
	 * @return  the data in the received packet 
	 */
	public byte[] getRecievePacketData(){
		return recievePacket.getData();
	}

	/**
	 * returns the port number of the latest client to send a message here
	 * 
	 * @author Luke Newton
	 * @return the port number of the latest client to send a message here
	 */
	public int getClientPort(){
		return clientPort;
	}

	/**
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @author Luke Newton
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
	 * @author Luke Newton
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
	 * @author Luke Newton
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendRecieveSocket.send(message);
	}

	/**
	 * main for error simulator program containing specified 
	 * error sim algorithm
	 * 
	 * @author Luke Newton
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

			(new Thread(new ClientServerConnection(request, errorSim))).start();
		}
	}
}