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
	private DatagramSocket receiveSocket, sendReceiveSocket;
	//buffer to contain data to send to server/client
	private DatagramPacket receivePacket;
	//port number of client to send response to
	private int clientPort;
	//booleans indicating whether error types occur in a data transfer
	private volatile boolean packetLostError, packetDuplicateError, createPacketDelay;
	//specifies which block number to cause error on (if applicable)
	private volatile int errorBlockNumber;
	//specifies which type of packet to cause error on
	private volatile int errorOpCode, packetDelayTime;
	//indicates whether packet format is altered
	private volatile boolean invalidModeError, invalidDataError, invalidBlockError, invalidOpcodeError;



	/**
	 * Constructor
	 * 
	 * @author Luke Newton
	 * @throws SocketException indicate failed to create socket for the error simulator
	 */
	public ErrorSimulator() throws SocketException{
		receiveSocket = new DatagramSocket();
		sendReceiveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON){
			sendReceiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
			receiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		}
		//create packet of max size to guarantee it fits a received message
		receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
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
		receiveSocket = new DatagramSocket(port);
	}

	/**
	 *Return the data in the datagram packet received
	 *
	 *@author Luke Newton
	 * @return  the data in the received packet 
	 */
	public byte[] getReceivePacketData(){
		return receivePacket.getData();
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
	private DatagramPacket waitReceiveClientMessage() throws IOException{
		receiveSocket.receive(receivePacket);
		//get the port number from the sender (client) to send response
		clientPort = receivePacket.getPort();

		return receivePacket;
	}

	/**
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @author Luke Newton
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	public DatagramPacket waitReceiveServerMessage() throws IOException{
		sendReceiveSocket.receive(receivePacket);
		return receivePacket;
	}

	/**
	 * sends a datagram through the error simulator's sendReceiveSocket
	 * 
	 * @author Luke Newton
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendReceiveSocket.send(message);
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
	 * @author Luke Newton, CRushton
	 */
	public void setPacketDuplicate(boolean b, int millis) {
		this.packetDuplicateError = b;
		this.packetDelayTime = millis;
	}

	/**
	 * Turn on modification of the opcode of a packet
	 * @author CRushton
	 * @param b boolean indicating if the opcode is to be invalidated
	 */
	public void setInvalidOpcode(boolean b) {
		invalidOpcodeError = b;
	}

	/**
	 * Turn on modification of the mode of a packet
	 * @author CRushton
	 * @param b boolean indicating if the mode is to be invalidated
	 */
	public void setInvalidMode(boolean b) {
		invalidModeError = b;
	}

	/**
	 * Turn on modification of the block number of a packet
	 * @author CRushton
	 * @param b boolean indicating if the block number is to be invalidated
	 */
	public void setInvalidBlock(boolean b) {
		invalidBlockError = b;
	}

	/**
	 * Turn on modification of the data of a packet
	 * @author CRushton
	 * @param b boolean indicating if the data is to be invalidated
	 */
	public void setInvalidData(boolean b) {
		invalidDataError = b;
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

		//create error simulator menu
		(new Thread(new ErrorSimMenuRunnable(errorSim))).start();

		while(true){
			//wait for message to come in from client
			DatagramPacket request = null;
			
			try {
				System.out.println("Error simulator waiting on request...");
				
				request = errorSim.waitReceiveClientMessage();
				
				int requestType = request.getData()[1];
				
				//create a packet loss for WRQ and RRQ
				if(errorSim.packetLostError && ((errorSim.errorOpCode == OP_WRQ && requestType == OP_WRQ) 
						|| (errorSim.errorOpCode == OP_RRQ && requestType == OP_RRQ))){
					//dont send the first WRQ/RRQ received
					request = errorSim.waitReceiveClientMessage();
				} 
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while error simulator waiting to receive message");
				e.printStackTrace();
				System.exit(1);
			}

			//inform the file transfer thread if we need to add artificial errors
			if(errorSim.packetLostError || errorSim.packetDuplicateError || errorSim.createPacketDelay ||
					errorSim.invalidDataError || errorSim.invalidBlockError || errorSim.invalidModeError ||
					errorSim.invalidOpcodeError){
				//create a client server connection with added errors
				(new Thread(new ClientServerConnection(request, errorSim, errorSim.packetDuplicateError, 
						errorSim.packetLostError, errorSim.createPacketDelay, errorSim.errorOpCode, 
						errorSim.errorBlockNumber, errorSim.packetDelayTime, errorSim.invalidModeError,
						errorSim.invalidDataError, errorSim.invalidOpcodeError, errorSim.invalidBlockError))).start();
			}else{
				//create a client server connection without added errors
				(new Thread(new ClientServerConnection(request, errorSim))).start();
			}
		}
	}
}