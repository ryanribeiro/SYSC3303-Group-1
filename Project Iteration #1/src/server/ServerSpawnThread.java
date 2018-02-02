package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;


public class ServerSpawnThread extends Thread{

	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;
	private DatagramSocket sendSocket;
	private int writeRead;
	private Thread response;

	//flags to indicate if received message is a read/write request
	private boolean readRequest, writeRequest;

	//file name acquired from packet
	private String fileName;
	//mode acquired from packet
	private String mode;
	
	//port number of client to send response to
	private int clientPort;
	
	public ServerSpawnThread(DatagramPacket packet){
		// Assign a datagram socket and bind it to port 69 
		// on the local host machine. This socket will be used to
		// receive UDP Datagram packets.
		receivePacket = packet;
		clientPort = receivePacket.getPort();
		readRequest = false;
		writeRequest = false;
	}

	public void close(){
		receiveSocket.close();
	}

	public void run(){
		while(!Thread.interrupted()){

       
				try {
					parseMessage();
				} catch (InvalidMessageFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}           

				//build response message 

				sendPacket(receivePacket); 

			
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
	/**
	 * creates the data to be placed into a  DatagramPacket based on the type of request last received
	 * 
	 * @return a byte array containing the response to the last request sent
	 */
	
public void sendPacket(DatagramPacket packet){
	/***********************
	 * Create & Send Packet *
	 ***********************/
	DatagramPacket sendPacket = null;
	byte[] responseData = this.createPacketData();
	try {
		sendPacket = new DatagramPacket(responseData, responseData.length,
				InetAddress.getLocalHost(), clientPort);

	} catch (UnknownHostException e) {
		//failed to determine the host IP address
		System.err.println("UnknownHostException: could not determine IP address of host while creating server response.");
		e.printStackTrace();
		System.exit(1);
	}
	
	//print data to send to intermediate host
	System.out.print("Server Response: \nTo ");
	this.printPacketInfo(sendPacket);
	
	try {
		this.sendMessage(sendPacket);
	} catch (IOException e) {
		System.err.println("IOException: I/O error occured while server sending message");
		e.printStackTrace();
		System.exit(1);
	}
	
	System.out.println("Server response sent");
}

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
	
	public static void main( String args[] ){
		
	}   

}