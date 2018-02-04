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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class ServerSpawnThread implements Runnable{	
	//the message to process and respond to
	private DatagramPacket receivePacket;
	//socket to send a response to message
	private DatagramSocket sendSocket;
	//flags to indicate if received message is a read/write request
	private boolean readRequest, writeRequest;
	//file name acquired from packet
	private String fileName;
	//mode acquired from packet
	private String mode;
	//reference to the server object to use as a lock
	private Server server;
	
	//port number of client to send response to
	private int clientPort;
	
	private static final int MAX_PACKET_SIZE = 516;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;
	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATAPACKET = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;

	/**
	 * Constructor
	 * 
	 * @param server reference to the Server that created this to use as lock
	 * @param packet the message to process and respond to
	 */
	public ServerSpawnThread(Server server, DatagramPacket packet){
		receivePacket = new DatagramPacket(packet.getData(), packet.getLength(),
				packet.getAddress(), packet.getPort());
		clientPort = receivePacket.getPort();
		readRequest = false;
		writeRequest = false;
		this.server = server;
	}

	/**
	 * function to execute when thread created.
	 * handles parsing and responding to message.
	 */
	public void run(){
		/*synchronize on a common object so we only process one message at a time.
		 * primarily so the console prints ll info for a single message at once*/
		synchronized(server){
			//print data received from client
			System.out.print("Server: message from \n");
			printPacketInfo(receivePacket);
			
			server.pause();
			
			/*check if message is proper format*/
			try {
				parseMessage();
				if (readRequest == true) {
					sendData(fileName);
				} else if (writeRequest == true){
					getData(fileName);
				} else {
					System.err.println("Error: Request is neither a write or a read.");
					System.exit(1);
				}
			} catch (InvalidMessageFormatException e) {
				System.out.println("InvalidMessageFormatException: a message received was of an invalid format");
				e.printStackTrace();
				System.out.println("Invalid message Contents:");
				printPacketInfo(receivePacket);
				System.exit(1);
			}         
			
			server.pause();
			/*send response*/
			try {
				sendPacket(receivePacket); 
			} catch (IOException e) {
				//failed to determine the host IP address
				System.err.println("IOException: I/O exception occured while sending message");
				e.printStackTrace();
				System.exit(1);
			}  
			server.pause();
			
			server.messageProcessed();
		}
	}
	
	//Trivial File Transfer Protocol Methods
	/**
	 * Sends the contents of a file to the server.
	 * 
	 * @param filename the name of the file to be sent
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	public void sendData(String filename) {		
		byte[] bytesReadIn = readFile(filename);
		
		sendFile(bytesReadIn);
	}
	
	/**
	 * Reads the contents of the file and stores it as an array of bytes.
	 * 
	 * @param filename the name of the file to be read
	 * @return byte[] contains the contents of the file read in
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	public byte[] readFile(String filename) {
		File file = new File(filename);
		//Contents of the file read in as bytes 
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
	
	/**
	 * Sends the contents of the file, packet by packet, waiting for an acknowledgment to be returned before continuing to the next packet.
	 * 
	 * @param bytesReadIn an array of bytes containing the contents of the file to be sent
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	public void sendFile(byte[] bytesReadIn) {
		//loop control variables
		int i = 0, j = 0;
		//First block starts with an ID of 1
		int blockID = 1;
		
		//Number of blocks that will be needed to transfer file, less the one additional one block for any remaining bytes
		int numBlocks = (bytesReadIn.length / MAX_BLOCK_SIZE);
		//User to determine if there are any additional bytes that need to be written in the final block
		int numRemainder = bytesReadIn.length % MAX_BLOCK_SIZE;
		
		//Looping through the number of blocks that will be sent
		for (i = 0; i < numBlocks; i++) {
			try {
				//Requires acknowledgement before sending the next DATA packet
				DatagramPacket receiveAcknowledgement = server.waitReceiveMessage();
				byte[] ACKData = receiveAcknowledgement.getData();
				//Checks the acknowledgement is for the correct block
				//Bitwise operations are to get the last 2 bytes of the 4 byte long integer
				if (ACKData[2] != (byte) ((blockID - 1) & 0xFF) || ACKData[3] != (((blockID - 1) >> 8) & 0xFF)) {
					System.err.println("ACK message failed.");
					System.exit(1);
				}
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while server waiting to recieve message");
				e.printStackTrace();
				System.exit(1);
			}		
			//ByteArrayOutputStream used to create a stream of bytes that will be sent in 516 byte DATA packets
			ByteArrayOutputStream bytesToSend = new ByteArrayOutputStream();
			bytesToSend.write(0);
			bytesToSend.write(OP_DATAPACKET);
			//Bitwise operations are to get the last 2 bytes of the 4 byte long integer
			bytesToSend.write((byte) (blockID & 0xFF));
			bytesToSend.write((byte) ((blockID >> 8) & 0xFF));
			//Looping through all bytes, one block at a time
			for (j = MAX_BLOCK_SIZE * i; j < bytesReadIn.length; j++) {
				bytesToSend.write(bytesReadIn[j]);
			}
			byte[] data = bytesToSend.toByteArray();
			//Creating the DATA packet to be sent, then sending it
			try {
				DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), server.getClientPort());
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
		
		//Final packet either empty (0) or remaining bytes, so this bit is to deal with sending one final DATA packet
		ByteArrayOutputStream bytesToSend = new ByteArrayOutputStream();
		bytesToSend.write(0);
		bytesToSend.write(OP_DATAPACKET);
		bytesToSend.write((byte) (blockID & 0xFF));
		bytesToSend.write((byte) ((blockID >> 8) & 0xFF));
		//If there was exactly a multiple of 512 bytes, the final DATA packet will contain a 0 byte data section
		if (numRemainder == 0) {
			bytesToSend.write((0));
		} else {
			for (i = MAX_BLOCK_SIZE * numBlocks; i < bytesReadIn.length; i++) {
				bytesToSend.write(bytesReadIn[i]);
			}
		}
		byte[] data = bytesToSend.toByteArray();
		try {
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), server.getClientPort());
			try {
				sendPacket(sendPacket);
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while sending message to client.");
				e.printStackTrace();
				System.exit(1);
			}
		} catch (UnknownHostException e) {
			System.err.println("UnknownHostException: could not determine IP address of host.");
			e.printStackTrace();
		}
	}

	/**
	 * Receives the contents of a file from the server.
	 * 
	 * @param fileName
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	private void getData(String fileName) {
		//Receive file from TFTP server
		 ByteArrayOutputStream receivedByte = receiveFile();
		
		 //Write File
		 writeFile(fileName, receivedByte);
	}
	
	/**
	 * Writes data to file
	 * 
	 * @param filename filename to send with the read request
	 * @param receivedByte byte array of data blocks received to write into file.
	 * @author Joe Frederick
	 */
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

	/**
	 * Parses the received packet into data blocks 
	 * 
	 * @return ByteArrayOutputStream the byte stream of data blocks received
	 * @author Joe Frederick Samuel
	 */
	private ByteArrayOutputStream receiveFile(){
		ByteArrayOutputStream byteBlock = new ByteArrayOutputStream();
		int packetCount = 1;
		
		DatagramPacket receivePacket = null;
		do {
			System.out.println("Number of TFTP packets receieved: "  + packetCount);
			packetCount++;
			byte[] buffer = new byte[MAX_PACKET_SIZE];
			try {
				receivePacket = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), server.getClientPort());
				try {
					server.getReceiveSocket().receive(receivePacket);
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
	
	/**
	 * Acknowledges reception of server packet.
	 * 
	 * @param blockID byte array containing the block ID whose reception is acknowledged.
	 * @author Joe Frederick Samuel
	 */
	private void acknowledge(byte[] blockID) {
		byte[] ack = {0, OP_ACK, blockID[0], blockID[1]};
		DatagramPacket acknowledgePacket;
		try {
			acknowledgePacket = new DatagramPacket(ack, ack.length, InetAddress.getLocalHost(), server.getClientPort());
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
	
	/**
	 * Checks if the packet received is the last packet for transmission
	 * 
	 * @param receivedPacket the packet received from the server
	 * @return boolean	true if last packet, false otherwise.
	 * @author Joe Frederick Samuel
	 */
	private boolean checkLastPacket(DatagramPacket receivedPacket) {
		if(receivedPacket.getLength() < 512)
			return true;
		else
			return false;
	}
	//End of Trivial File Transfer Protocol Methods

	/**
	 * ensures the received message is of proper format. format follows:
	 * 
	 * byte 0: 0 byte
	 * byte 1: 1 byte for read request, 2 byte for write request
	 * byte 2 to n: some text
	 * byte n+1: 0 byte
	 * byte n+2 to m: some text
	 * byte m+1: 0 byte
	 * nothing follows (ie. byte array has m+2 elements)
	 * 
	 * @throws InvalidMessageFormatException indicates that the received message is not a valid read/write command
	 */
	private void parseMessage() throws InvalidMessageFormatException{
		byte[] messageData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());

		//check first byte
		if(messageData[0] != 0)
			throw new InvalidMessageFormatException();

		//check read/write byte
		if(messageData[1] == 1) {
			readRequest = true;
			writeRequest = false;
		} else if(messageData[1] == 2) {
			readRequest = false;
			writeRequest = true;
		} else {
			throw new InvalidMessageFormatException();
		}

		int currentIndex = 2; //start at 2 to account for the first two bytes
		//store names of file and mode in a stream
		ByteArrayOutputStream textStream = new ByteArrayOutputStream();
		try {
			/******************************************
			 * Check for some text followed by a zero *
			 * & add text to byte array               *
			 *****************************************/
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			for(;messageData[currentIndex] != 0; currentIndex++){
				textStream.write(messageData[currentIndex]);
			}
			if (textStream.size() <= 0)
				throw new InvalidMessageFormatException("File Name Empty");

			//Convert file name to byte array
			fileName = textStream.toString();

			/***********************************************
			 * Check for some more text followed by a zero *
			 ***********************************************/
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			textStream.reset();

			for(currentIndex++; messageData[currentIndex] != 0; currentIndex++){
				textStream.write(messageData[currentIndex]);
			}

			if (textStream.size() <= 0)
				throw new InvalidMessageFormatException("Mode Empty");

			mode = textStream.toString();
			mode = mode.toLowerCase();

			//if the mode text is not netascii or octet, packet is invalid
			if (!mode.equals("netascii") && !mode.equals("octet"))
				throw new InvalidMessageFormatException("Invalid Mode");

		} catch (IndexOutOfBoundsException e){
			/*if we go out of bounds while iterating through the message data,
			 * then it does not end in a 0 and thus is incorrect format
			 */
			throw new InvalidMessageFormatException("Reached End Of Packet");
		}
		//check that this is the end of the message
		if(currentIndex + 1 != messageData.length){
			throw new InvalidMessageFormatException("Reached \"End\" Of Packet But There Is More");
		}
	}

	/**
	 * sends a datagram through the servers's sendSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendPacket(DatagramPacket packet) throws UnknownHostException, IOException{
		/***********************
		 * Create & Send Packet *
		 ***********************/
		byte[] responseData = this.createPacketData();

		DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length,
				InetAddress.getLocalHost(), clientPort);

		//print data to send to intermediate host
		System.out.print("Server Response: \nTo ");
		printPacketInfo(sendPacket);

		sendMessage(sendPacket);

		System.out.println("Server response sent");
	}

	/**
	 * formats a message as a response to the appropriate request
	 * 
	 * @return the message converted into a byte array with proper format
	 */
	public byte[] createPacketData(){
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byteStream.write(0);

		if(readRequest)
			byteStream.write(3);
		else if (writeRequest)
			byteStream.write(4);

		byteStream.write(0);

		if(readRequest)
			byteStream.write(1);
		else if (writeRequest)
			byteStream.write(0);

		return byteStream.toByteArray();
	}

	/**
	 * sends a datagram from the server
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendSocket = new DatagramSocket();
		sendSocket.send(message);
		sendSocket.close();
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
}