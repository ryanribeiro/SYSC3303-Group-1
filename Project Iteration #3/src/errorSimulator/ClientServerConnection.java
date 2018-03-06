/**
 * This class runs every time the client makes a connection to the server. This should
 * facilitate multiple connestions at once
 * 
 * @author Luke Newton
 */
package errorSimulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * a connection between the client and server to transfer files
 *
 */
public class ClientServerConnection implements Runnable {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 516;

	//socket for error simulator to send and receive packets
	private DatagramSocket sendRecieveSocket;
	//buffer to contain data to send to server/client
	private DatagramPacket recievePacket;
	//port number of client to send response to
	private int clientPort;
	//port number of server to send response to
	private int serverPort;

	private ErrorSimulator errorSim;

	private DatagramPacket request;

	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;

	/**
	 * Constructor
	 * 
	 * @author Luke Newton
	 * @param request the initial request from the client which prompts a connection
	 * @param errorSim reference to main error simulator to use as lock
	 */
	ClientServerConnection(DatagramPacket request, ErrorSimulator errorSim) {
		this.request = new DatagramPacket(request.getData(), request.getLength(),
				request.getAddress(), request.getPort());
		try {
			sendRecieveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		this.errorSim = errorSim;
	}

	/**
	 * prints packet information
	 * 
	 * @author Luke Newton, Cameron Rushton
	 * @param packet : DatagramPacket
	 */
	private void printPacketInfo(DatagramPacket packet) {
		//get meaningful portion of message
		byte[] dataAsByteArray = Arrays.copyOf(packet.getData(), packet.getLength());		

		System.out.println("host: " + packet.getAddress() + ":" + packet.getPort());
		System.out.println("Message length: " + packet.getLength());
		System.out.println("Containing: " + new String(dataAsByteArray));
		System.out.println("Contents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}

	/**
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @author Luke Newton
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	private DatagramPacket waitReceiveServerMessage() throws IOException{
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
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

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		synchronized(errorSim){
			System.out.println("client server connection thread start.");
			//get meaningful portion of initial message
			byte[] clientMessageData = Arrays.copyOf(request.getData(), request.getLength());		
			clientPort = request.getPort();

			//print data received from client
			System.out.print("Error simulator recieved message: \nFrom ");
			printPacketInfo(request);

			//create packet to send request to server on specified port
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
			printPacketInfo(sendPacket);


			//send datagram to server
			try {
				sendMessage(sendPacket);
				System.out.println("Error simulator sent message to server");
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator sending message");
				e.printStackTrace();
				System.exit(1);
			}

			DatagramPacket clientResponse = null;
			DatagramPacket serverResponse = null;
			while(true){
				//wait to receive response from server
				serverResponse = null;
				try {
					System.out.println("Error simulator waiting on response from server...");
					serverResponse = waitReceiveServerMessage();
				} catch (IOException e) {
					System.err.println("IOException: I/O error occured while error simulator waiting for response");
					e.printStackTrace();
					System.exit(1);
				}

				//get meaningful portion of message
				byte[] serverMessageData = Arrays.copyOf(serverResponse.getData(), serverResponse.getLength());
				serverPort = serverResponse.getPort();

				//print response received from server
				System.out.print("Response recieved by error simulator: \nFrom ");
				printPacketInfo(serverResponse);


				//create packet to send to client on client port
				sendPacket = null;
				try {
					sendPacket = new DatagramPacket(serverMessageData, serverMessageData.length,
							InetAddress.getLocalHost(), clientPort);
				} catch (UnknownHostException e) {
					//failed to determine the host IP address
					System.err.println("UnknownHostException: could not determine IP address of host while creating packet to send to client.");
					e.printStackTrace();
					System.exit(1);
				}

				//print data to send to client
				System.out.print("Message to send from error simulator to client: \nTo ");
				printPacketInfo(sendPacket);

				//send datagram to client
				try {
					sendMessage(sendPacket);
				} catch (IOException e) {
					System.err.println("IOException: I/O error occured while error simulator sending message");
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println("Error simulator sent message to client");

				//exit when the final packet is sent from the server
				if((sendPacket.getLength() < MAX_PACKET_SIZE && sendPacket.getData()[1] == OP_DATA) || 
						(clientResponse != null && clientResponse.getLength() < MAX_PACKET_SIZE && clientResponse.getData()[1] == OP_DATA))
					break;

				//wait to receive response from client
				clientResponse = null;
				try {
					System.out.println("Error simulator waiting on response from client...");
					clientResponse = waitReceiveServerMessage();
				} catch (IOException e) {
					System.err.println("IOException: I/O error occured while error simulator waiting for response");
					e.printStackTrace();
					System.exit(1);
				}

				//get meaningful portion of message
				serverMessageData = Arrays.copyOf(clientResponse.getData(), clientResponse.getLength());
				clientPort = clientResponse.getPort();

				//print response received from server
				System.out.print("Response recieved by error simulator: \nFrom ");
				printPacketInfo(clientResponse);

				//create packet to send to client on client port
				sendPacket = null;
				try {
					sendPacket = new DatagramPacket(serverMessageData, serverMessageData.length,
							InetAddress.getLocalHost(), serverPort);
				} catch (UnknownHostException e) {
					//failed to determine the host IP address
					System.err.println("UnknownHostException: could not determine IP address of host while creating packet to send to server.");
					e.printStackTrace();
					System.exit(1);
				}

				//print data to send to client
				System.out.print("Message to send from error simulator to server: \nTo ");
				printPacketInfo(sendPacket);

				//send datagram to client
				try {
					sendMessage(sendPacket);
				} catch (IOException e) {
					System.err.println("IOException: I/O error occured while error simulator sending message");
					e.printStackTrace();
					System.exit(1);
				}
				System.out.println("Error simulator sent message to server");
			}
			System.out.println("client server connection thread finish.");
		}
	}
}
