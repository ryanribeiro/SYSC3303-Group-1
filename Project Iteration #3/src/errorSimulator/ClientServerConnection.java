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
	//specifies whether this connection is for a RRQ or WRQ
	private int connectionOpCode;

	//specifies the op code of packet to create error on
	private int errorOpCode;
	//specifies which DATA/ACK to create error on
	private int errorBlockNumber;
	//specifies whether an error should be artificailly created
	private boolean createDuplicateError, createLostError;

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
		this.createDuplicateError = false;
		this.createLostError = false;
		this.connectionOpCode = request.getData()[1];
	}

	/**
	 * Constructor
	 * 
	 * @author Luke Newton
	 * @param request the initial request from the client which prompts a connection
	 * @param errorSim reference to main error simulator to use as lock
	 * @param errorPacketType specifies that op code to the packet to create an error on
	 * @param errorBlockNumber specifies which ACK/DATA to create error on
	 */
	ClientServerConnection(DatagramPacket request, ErrorSimulator errorSim, boolean createDuplicateError, 
			boolean createLostError, int errorPacketType, int errorBlockNumber) {
		this(request, errorSim);
		this.errorOpCode = errorPacketType;
		this.errorBlockNumber = errorBlockNumber;
		this.createDuplicateError = createDuplicateError;
		this.createLostError = createLostError;
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
	 * @return returns the receive datagram packet
	 */
	private DatagramPacket waitReceiveMessage(){
		recievePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
		try {
			sendRecieveSocket.receive(recievePacket);
		} catch (IOException e) {
			System.err.println("IOException: I/O error occured while error simulator waiting for response");
			e.printStackTrace();
			System.exit(1);
		}
		return recievePacket;
	}

	/**
	 * sends a datagram through the error simulator's sendRecieveSocket
	 * 
	 * @author Luke Newton
	 * @param message	the datagram packet to send
	 */
	public void sendMessage(DatagramPacket message){
		try {
			sendRecieveSocket.send(message);
		} catch (IOException e) {
			System.err.println("IOException: I/O error occured while error simulator sending message");
			e.printStackTrace();
			System.exit(1);
		}	
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		/*perform one transfer between client and server to get port numbers, then
		 * wait for any message and send to the other port number*/
		synchronized(errorSim){
			System.out.println("client server connection thread start.");
			//datagram packets to hold messages to send and messages received
			DatagramPacket sendPacket = null, response = null, previousResponse = null;
			//byte array to store data from recieved datagram packet
			byte[] messageData;

			//get meaningful portion of initial client request
			messageData = Arrays.copyOf(request.getData(), request.getLength());		
			clientPort = request.getPort();

			//print data received from client
			printMessageRecieved(request);

			//create packet to send request to server on specified port
			try {
				sendPacket = new DatagramPacket(messageData, messageData.length,
						InetAddress.getLocalHost(), SERVER_PORT_NUMBER);
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.err.println("UnknownHostException: could not determine IP address of host while creating packet.");
				e.printStackTrace();
				System.exit(1);
			}

			//print data to send to server
			printMessageToSend(sendPacket);

			//send datagram to server
			sendMessage(sendPacket);
			System.out.println("Error simulator sent message to server");

			//wait to receive response from server
			System.out.println("Error simulator waiting on response from server...");
			previousResponse = sendPacket;
			response = waitReceiveMessage();

			//get meaningful portion of message
			messageData = Arrays.copyOf(response.getData(), response.getLength());
			serverPort = response.getPort();

			//print request received by server
			printMessageRecieved(response);

			//create duplicate WRQ and RRQ is necessary
			if(createDuplicateError && ((errorOpCode == OP_WRQ && previousResponse.getData()[1] == OP_WRQ) 
					|| (errorOpCode == OP_RRQ && previousResponse.getData()[1] == OP_RRQ))){
				//send another request to server
				//create packet to send request to server on specified port
				try {
					sendPacket = new DatagramPacket(previousResponse.getData(), previousResponse.getData().length,
							InetAddress.getLocalHost(), serverPort);
				} catch (UnknownHostException e) {
					//failed to determine the host IP address
					System.err.println("UnknownHostException: could not determine IP address of host while creating packet.");
					e.printStackTrace();
					System.exit(1);
				}
				//print data to send to server
				printMessageToSend(sendPacket);
				//send message to server
				sendMessage(sendPacket);
				//wait for server reponse
				response = waitReceiveMessage();

				createLostError = false;
			}

			//create packet to send resposne to client
			try {
				sendPacket = new DatagramPacket(messageData, messageData.length,
						InetAddress.getLocalHost(), clientPort);
			} catch (UnknownHostException e) {
				//failed to determine the host IP address
				System.err.println("UnknownHostException: could not determine IP address of host while creating packet.");
				e.printStackTrace();
				System.exit(1);
			}

			//send datagram to client
			sendMessage(sendPacket);
			System.out.println("Error simulator sent message to client");



			//a DATA/ACK pair represents on  complete packet transfer
			int filetransfers = 1;
			if(connectionOpCode == OP_RRQ)
				filetransfers += 1;
			while(true){
				if(messageData[1] == OP_ERROR)
					break;
				//copy previous message
				previousResponse = response;
				//get next message
				System.out.println("Error simulator waiting on for response...");
				response = waitReceiveMessage();
				
				//get meaningful portion of message
				messageData = Arrays.copyOf(response.getData(), response.getLength());
				//print response received
				printMessageRecieved(response);
				
				//lose DATA and ACK
				if(createLostError &&
						((errorOpCode == OP_DATA && messageData[1] == OP_DATA && (filetransfers/2 + 1) == errorBlockNumber) || 
								(errorOpCode == OP_ACK && messageData[1] == OP_ACK && (filetransfers/2) == errorBlockNumber))){
					createLostError = false;
					continue;
				}

				int portToSendPacket = 0;
				String recipient = "";
				
				//duplicate DATA and ACK packets
				if(createDuplicateError &&
						((errorOpCode == OP_DATA && messageData[1] == OP_ACK && (filetransfers/2) == errorBlockNumber) || 
								(errorOpCode == OP_ACK && messageData[1] == OP_DATA && ((filetransfers)/2) == errorBlockNumber))){
					//resend previous message
					messageData = Arrays.copyOf(previousResponse.getData(), previousResponse.getLength());
					if(response.getPort() == clientPort){
						//send to server
						portToSendPacket = clientPort;
						recipient = "client";
					} else {
						//send to client
						serverPort = response.getPort();
						portToSendPacket = serverPort;
						recipient = "server";
					}
					createDuplicateError = false;
				}
				//normal operations to determine who to send packet to
				else if(response.getPort() == clientPort){
					//send to server
					portToSendPacket = serverPort;
					recipient = "server";
				} else {
					//send to client
					serverPort = response.getPort();
					portToSendPacket = clientPort;
					recipient = "client";
				}

				//create packet to send to recipient
				try {
					sendPacket = new DatagramPacket(messageData, messageData.length,
							InetAddress.getLocalHost(), portToSendPacket);
				} catch (UnknownHostException e) {
					//failed to determine the host IP address
					System.err.println("UnknownHostException: could not determine IP address of host while creating packet.");
					e.printStackTrace();
					System.exit(1);
				}

				//send mesage to recipient
				sendMessage(sendPacket);
				System.out.println("Error simulator sent message to " + recipient);

				//exit when the final packet is sent from the server
				if((previousResponse.getLength() < MAX_PACKET_SIZE && previousResponse.getData()[1] != OP_ACK &&
						((connectionOpCode == OP_RRQ   && portToSendPacket == serverPort) ||
								(connectionOpCode == OP_WRQ && portToSendPacket == clientPort))) ||
						messageData[1] == OP_ERROR){
					break;
				}
				filetransfers++;
			}
			System.out.println("client server connection thread finish.");
		}
	}

	private void printMessageToSend(DatagramPacket sendPacket) {
		System.out.print("Error simulator to send message to server: \nTo ");
		printPacketInfo(sendPacket);
	}

	private void printMessageRecieved(DatagramPacket response) {
		System.out.print("Response recieved by error simulator: \nFrom ");
		printPacketInfo(response);
	}
}
