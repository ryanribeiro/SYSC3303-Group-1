package server;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import java.net.UnknownHostException;
import java.util.Arrays;


/**
 * a class representing the server for the server-client-intermediate host system. has
 * Capability to receive requests, and create threads to process and send appropriate 
 * responses
 * 
 * @author Luke Newton Kevin Sun
 *
 */
public class Server {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//change this to turn on/off timeouts for the server
	private static final boolean TIMEOUTS_ON = true;
	//Milliseconds until server times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;
	//max size for data in a DatagramPacket

	private static final int MAX_PACKET_SIZE = 516;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;
	
	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATAPACKET = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;


	private static final int MAX_PACKET_SIZE = 100;
	//change this to turn on/off pauses for the server request processing
	private static final boolean PAUSES_ON = true;
	/*number of milliseconds server pauses for each time
	*(note that setting this too high with timeouts on may
	*timeout client before message is processed)*/
	private static final int PAUSE_MILLISECONDS = 1000;

	//socket to receive messages
	private DatagramSocket receiveSocket;
	//port number of client to send response to
	private int clientPort;
	//buffer to contain data to send to client
	private DatagramPacket receivePacket;
	//thread created to handle a client request
	private static Thread serverLogicThread;
	//boolean indicating whther server should be shutting down
	private volatile boolean quitPreperation;
	//integer representing the number of messages currently being processed
	private volatile int numberOfMessagesBeingProcessed;

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
		quitPreperation = false;
		numberOfMessagesBeingProcessed = 0;
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
	
	//TFTP methods
	//RRQ
	public void sendData(String filename) {		
		byte[] bytesReadIn = readFile(filename);
		
		sendFile(bytesReadIn);
	}
	
	public byte[] readFile(String filename) { 
		File file = new File(filename);
		byte[] buffer = new byte[(int) file.length()];		
		try {
			InputStream input = new FileInputStream(file);
			try {
				input.read(buffer);
			} catch (IOException e) {
				System.out.println("Failed to read the file.");
				e.printStackTrace();
			}
			try {
				input.close();
			} catch (IOException e) {
				System.out.println("Failed to close file InputStream.");
				e.printStackTrace();
			}
		} catch (FileNotFoundException e) {
			System.out.println("Failed to find the file.");
			e.printStackTrace();
		}		
		return buffer; 
	}
	public void sendFile(byte[] bytesReadIn) {
		//loop control variables
		int i = 0, j = 0;
		int blockID = 1;
		
		int numBlocks = (bytesReadIn.length / MAX_BLOCK_SIZE);
		int numRemainder = bytesReadIn.length % MAX_BLOCK_SIZE;
				
		for (i = 0; i < numBlocks; i++) {
			try {
				DatagramPacket receiveAcknowledgement = waitRecieveMessage();
				byte[] ACKData = receiveAcknowledgement.getData();
				if (ACKData[2] != (byte) ((blockID - 1) & 0xFF) || ACKData[3] != (((blockID - 1) >> 8) & 0xFF)) {
					System.err.println("ACK message failed.");
					System.exit(1);
				}
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while server waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			} catch (InvalidMessageFormatException e) {
				System.err.println("InvalidMessageFormatException: received message is of invalid format");
				e.printStackTrace();
				System.exit(1);
			}			
			//This makes the stuff to send
			ByteArrayOutputStream bytesToSend = new ByteArrayOutputStream();
			bytesToSend.write(0);
			bytesToSend.write(OP_DATAPACKET);
			bytesToSend.write((byte) (blockID & 0xFF));
			bytesToSend.write((byte) ((blockID >> 8) & 0xFF));
			for (j = MAX_BLOCK_SIZE * i; j < bytesReadIn.length; j++) {
				bytesToSend.write(bytesReadIn[j]);
			}
			byte[] data = bytesToSend.toByteArray();
			try {
				DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), getClientPort());
				try {
					sendMessage(sendPacket);
				} catch (IOException e) {
					System.err.println("IOException: I/O error occured while sending server was message.");
					e.printStackTrace();
					System.exit(1);
				}
			} catch (UnknownHostException e) {
				System.err.println("UnknownHostException: could not determine IP address of host.");
				e.printStackTrace();
			}
			blockID++;
		}	
		
		//Final packet either empty (0) or remaining bytes
		ByteArrayOutputStream bytesToSend = new ByteArrayOutputStream();
		bytesToSend.write(0);
		bytesToSend.write(OP_DATAPACKET);
		bytesToSend.write((byte) (blockID & 0xFF));
		bytesToSend.write((byte) ((blockID >> 8) & 0xFF));
		if (numRemainder == 0) {
			bytesToSend.write((0));
		} else {
			for (i = MAX_BLOCK_SIZE * numBlocks; i < bytesReadIn.length; i++) {
				bytesToSend.write(bytesReadIn[i]);
			}
		}
		byte[] data = bytesToSend.toByteArray();
		try {
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), getClientPort());
		} catch (UnknownHostException e) {
			System.err.println("UnknownHostException: could not determine IP address of host.");
			e.printStackTrace();
		}
	}
	//END of RRQ
	
	//WRQ
	private void getData(String fileName) {
		//Receive file from TFTP server
		 ByteArrayOutputStream receivedByte = receiveFile();
		
		 //Write File
		 writeFile(fileName, receivedByte);
	}
	
	private void writeFile(String fileName, ByteArrayOutputStream receivedByte) {
		try {
			OutputStream outputStream = new FileOutputStream(fileName);
			receivedByte.writeTo(outputStream);
		} catch (IOException e) {
			System.out.println("Failed to write the file.");
			e.printStackTrace();
			System.exit(1);
		}		
	}

	private ByteArrayOutputStream receiveFile(){
		ByteArrayOutputStream byteBlock = new ByteArrayOutputStream();
		int packetCount = 1;
		
		DatagramPacket receivePacket = null;
		do {
			System.out.println("Number of TFTP packets receieved: "  + packetCount);
			packetCount++;
			byte[] buffer = new byte[MAX_PACKET_SIZE];
			try {
				receivePacket = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), getClientPort());
				try {
					recieveSocket.receive(receivePacket);
				} catch (IOException e) {
					System.out.println("Receive socket has failed to receive packet from server.");
					e.printStackTrace();
					System.exit(1);
				}
			} catch (UnknownHostException e1) {
				System.err.println("UnknownHostException: could not determine IP address of host while creating server response.");
				e1.printStackTrace();
				System.exit(1);
			}
			
			//Analyzing packet data for OP codes
			byte[] opCode = {buffer[0], buffer[1]};
			byte[] blockID = {buffer[2], buffer[3]};
			try {
				byteBlock.write(opCode);
				acknowledge(blockID);
			} catch (IOException e) {
				System.err.println("Failed to write OP code");	
				e.printStackTrace();
				System.exit(1);
			}
		}while(!checkLastPacket(receivePacket));
		return byteBlock;
	}
		
	private void acknowledge(byte[] blockID) {
		byte[] ack = {0, OP_ACK, blockID[0], blockID[1]};
		DatagramPacket acknowledgePacket;
		try {
			acknowledgePacket = new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), getClientPort());
			try {
				sendSocket.send(acknowledgePacket);
			} catch (IOException e) {
				System.out.println("Failed to send acknowledge Packet from Client");
				e.printStackTrace();
				System.exit(1);
			}
		} catch (UnknownHostException e1) {
			System.err.println("UnknownHostException: could not determine IP address of host while creating server response.");
			e1.printStackTrace();
			System.exit(1);
		}	
	}
	
	private boolean checkLastPacket(DatagramPacket receivedPacket) {
		if(receivedPacket.getLength() < 512)
			return true;
		else
			return false;
	}
	//END of WRQ

	/**
	 * informs the caller of whether or not the server is shutting down
	 * 
	 * @return true if server is shutting down, otherwise false
	 */
	public boolean isQuitTime() {
		return quitPreperation;
	}

	/**
	 * inform the server to begin shutting down
	 */
	public void setQuitTime() {
		synchronized(this){
			this.quitPreperation = true;
			receiveSocket.close();
		}
	}

	/**
	 * create a new thread to deal with a specified message
	 * 
	 * @param request the message received to process
	 */
	public void newMessageToProcess(DatagramPacket request){
		System.out.println("Server: received message");
		serverLogicThread = new Thread(new ServerSpawnThread(this, request)); 
		//priorities are set low to make shutdown occur in a timely manner
		serverLogicThread.setPriority(Thread.MIN_PRIORITY);
		serverLogicThread.start();
		numberOfMessagesBeingProcessed++;
	}

	/**
	 * decrememnt the number of messages being processed when done
	 */
	public void messageProcessed(){
		numberOfMessagesBeingProcessed--;
	}

	/**
	 *  returns the number of messages currently being processed
	 *  
	 * @return the number of messages currently being processed
	 */
	private int getNumberOfMessagesBeingProcessed() {
		return numberOfMessagesBeingProcessed;
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
	 */
	public DatagramPacket waitReceiveMessage() throws IOException{
		receiveSocket.receive(receivePacket);
		return receivePacket;
	}
	
	public void pause(){
		if(PAUSES_ON){
			try {
				Thread.sleep(PAUSE_MILLISECONDS);
			} catch (InterruptedException e) {
				System.out.println("Server interrupted while pausing execution");
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * main method for server program containing specified server algorithm
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		/*attempt to create server*/
		Server server = null;
		try {
			server = new Server(SERVER_PORT_NUMBER);

		} catch (SocketException e) {
			System.err.println("SocketException: failed to create socket for server");
			e.printStackTrace();
			System.exit(1);
		}

		//create thread to handle user commands
		Thread serverQuitThread = new Thread(new ServerQuitRunnable(server));
		//priority is set high to make shutdown occur in a timely manner
		serverQuitThread.setPriority(Thread.MAX_PRIORITY);
		serverQuitThread.start();    
		
		System.out.println("Enter 'quit' to begin server shutdown procedures");

		/*Recieve packet and create a thread to handle the request.
		 * Do this while the server is not trying to shut down*/
		while(!server.isQuitTime()) {
			DatagramPacket request = null;
			try {
				request = server.waitReceiveMessage();
			} catch (SocketException e){
				System.out.println("\nSocketException: server receive socket closed");
				break;
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while server waiting to receive message");
				e.printStackTrace();
				System.exit(1);
			}

			server.newMessageToProcess(request);
		}
		//server now shuting down, do not stop until no more messages are being processed
		while(server.getNumberOfMessagesBeingProcessed() != 0){}
		System.out.println("\nServer successfully quit due to user command");
		System.exit(0);
	}
}