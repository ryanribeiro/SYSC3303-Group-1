package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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