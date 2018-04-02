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

	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;

	//socket for error simulator to send and receive packets
	private DatagramSocket recieveSocket, sendRecieveSocket;
	//buffer to contain data to send to server/client
	private DatagramPacket recievePacket;
	//port number of client to send response to
	private int clientPort;
	//booleans indicating whether error types occur in a datatransfer
	private volatile boolean packetLostError, packetDuplicateError, createPacketDelay;
	//specifies which blcok number to cause error on (if applicable)
	private volatile int errorBlockNumber;
	//specifies which type of packet to cause error on
	private volatile int errorOpCode, packetDelayTime;
	
	private boolean invalidTIDError;
	private boolean invalidOpcodeError;
	private boolean invalidModeError;
	private boolean invalidFilenameError;



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
	private DatagramPacket waitRecieveClientMessage() throws IOException{
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
	 * alter the behaviour of the error simulator set introduce a lost packet error
	 * or not
	 * 
	 * @param b boolean indicating whether further data transfers will have a error 
	 * packet error to handle
	 * @author Luke Newton
	 */
	public void setPacketLose(boolean b) {
		this.packetLostError = b;
	}

	/**
	 * alter the behaviour of the error simulator set introduce a duplicate packet
	 * error or not
	 * 
	 * @param b boolean indicating whether further data transfers will have a duplicate 
	 * packet error to handle
	 * @param millis the number of milliseconds to delay the duplicate packet for
	 * @author Luke Newton, CRushton
	 */
	public void setPacketDuplicate(boolean b, int millis) {
		this.packetDuplicateError = b;
		this.packetDelayTime = millis;
	}

	/**
	 * @param b boolean indicating whether the data transfer will have to handle an invalid TID error
	 */
	public void setInvalidTID(boolean b) {
		this.invalidTIDError = b;
	}

	/**
	 * @param b boolean indicating whether the data transfer will have to handle an invalid opcode error
	 */
	public void setInvalidOpcode(boolean b) {
		this.invalidOpcodeError = b;
	}

	/**
	 * @param b boolean indicating whether the data transfer will have to handle an invalid mode error
	 */
	public void setInvalidMode(boolean b) {
		this.invalidModeError = b;	
	}

	/**
	 * @param b boolean indicating whether the data transfer will have to handle an invalid filename error
	 */
	public void setInvalidFilename(boolean b) {
		this.invalidFilenameError = b;
	}

	/**
	 * Set the DATA block number to cause error on
	 * 
	 * @param errorBlockNumber the block number to cause error on
	 */
	public void setErrorPacketBlockNumber(int errorBlockNumber) {
		this.errorBlockNumber = errorBlockNumber;
	}

	/**
	 * set the type of packet for error to occur on(RRQ, WRQ, or DATA)
	 * 
	 * @param errorOpCode the type of packet to cause error on
	 */
	public void setErrorPacketType(int errorOpCode) {
		this.errorOpCode = errorOpCode;
	}

	/**
	 * alter the behaviour of the error simulator set introduce a packet delay
	 * 
	 * @param b boolean indicating whether further data transfers will have a packet delay 
	 * @param i integer representing milllisecnds to dealy for
	 * @author Luke Newton
	 */
	public void setPacketDelay(boolean b, int i) {
		this.createPacketDelay = b;
		this.packetDelayTime = i;
	}
	
	/**
	 * main for error simulator program containing specified 
	 * error sim algorithm
	 * 
	 * @author Luke Newton
	 * @param args any arguements passed to ErrorSimulator are not used
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

		//create error simulator menu
		(new Thread(new ErrorSimMenuRunnable(errorSim))).start();

		while(true){
			//wait for message to come in from client
			DatagramPacket request = null;
			
			try {
				System.out.println("Error simulator waiting on request...");
				
				request = errorSim.waitRecieveClientMessage();
				
				int requestType = request.getData()[1];
				
				//create a packet loss for WRQ and RRQ
				if(errorSim.packetLostError && ((errorSim.errorOpCode == OP_WRQ && requestType == OP_WRQ) 
						|| (errorSim.errorOpCode == OP_RRQ && requestType == OP_RRQ))){
					//dont send the first WRQ/RRQ recieved
					request = errorSim.waitRecieveClientMessage();
				} 
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			}

			//inform the file transfer thread if we need to add artificial errors
			if(errorSim.packetLostError || errorSim.packetDuplicateError || errorSim.createPacketDelay || errorSim.invalidFilenameError
					||errorSim.invalidModeError || errorSim.invalidOpcodeError || errorSim.invalidTIDError){
				//create a client server connection with added errors
				(new Thread(new ClientServerConnection(request, errorSim, errorSim.packetDuplicateError, 
						errorSim.packetLostError, errorSim.createPacketDelay, errorSim.invalidModeError, 
						errorSim.invalidFilenameError, errorSim.invalidOpcodeError, errorSim.invalidTIDError,
						errorSim.errorOpCode, errorSim.errorBlockNumber, errorSim.packetDelayTime))).start();
			}else{
				//create a client server connection without added errors
				(new Thread(new ClientServerConnection(request, errorSim))).start();
			}
		}
	}
}