package client;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * a class representing the client for the server-client-intermediate host system. has
 * Capability to send and receive messages to and from the intermediate host, 
 * and format read/write requests
 * 
 * @author Luke Newton
 */
public class Client {
	//port number of intermediate host (ErrorSimulator)
	private static final int INTERMEDIATE_HOST_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 516;
	//file name to send to server
	private static final String FILENAME = "test.txt";
	//mode to send to server
	private static final String MODE = "octet";
	//change this to turn on/off timeouts for the client
	private static final boolean TIMEOUTS_ON = true;
	//Milliseconds until client times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;

	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATAPACKET = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;
	
	//TFTP Address
	private static final String TFTP_SERVER_IP = "192.168.1.11";
	private static final int TFTP_DEFAULT_PORT = 69;
	private InetAddress inetAddress = null;
	
	//TFTP data
	private byte[] request;
	private byte[] buffer;
	
	//socket used by client to send and receive datagrams
	private DatagramSocket sendRecieveSocket;
	//place to store response from intermediate host
	private DatagramPacket receivePacket;
	private DatagramPacket sendPacket;

	/**Constructor
	 * @throws SocketException indicates failed to create socket for client
	 * */
	public Client() throws SocketException {
		//TFTP
		try {
			inetAddress = InetAddress.getByName(TFTP_SERVER_IP);
		} catch (UnknownHostException e) {
			System.out.println("Failed to initalize TFTP Server IP");
			e.printStackTrace();
			System.exit(1);
		}
		
		//attempt to create socket for send and receive
		sendRecieveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON)
			sendRecieveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);

		
		//create packet of max size to guarantee it fits a received message
		//receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
	}
	
	//Trivial File Transfer Protocol Methods
	
	/**
	 * Gets the data from file
	 * 
	 * @param filename filename to send with the read request
	 * @return none
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	private void getData(String fileName) {
		//Preparing the send packet
		request = createPacketData(fileName, MODE, OP_RRQ);
		sendPacket = new DatagramPacket(request, request.length, inetAddress, TFTP_DEFAULT_PORT);
		
		//Sending packet request
		try {
			sendMessage(sendPacket);
		} catch (IOException e) {
			System.out.println("Failed to send packet read request");
			e.printStackTrace();
			System.exit(1);
		}
		
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
	 * @return none
	 * @author Joe Frederick Samuel
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
		
		do {
			System.out.println("Number of TFTP packets receieved: "  + packetCount);
			packetCount++;
			buffer = new byte[MAX_PACKET_SIZE];
			receivePacket = new DatagramPacket(buffer, buffer.length, inetAddress, sendRecieveSocket.getLocalPort());
			
			try {
				sendRecieveSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.println("Receive socket has failed to receive packet from server.");
				e.printStackTrace();
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
	 * @return none
	 * @author Joe Frederick Samuel
	 */
	private void acknowledge(byte[] blockID) {
		byte[] ack = {0, OP_ACK, blockID[0], blockID[1]};
		DatagramPacket acknowledgePacket = new DatagramPacket(ack, ack.length, inetAddress, receivePacket.getPort());
		try {
			sendRecieveSocket.send(acknowledgePacket);
		} catch (IOException e) {
			System.out.println("Failed to send acknowledge Packet from Client");
			e.printStackTrace();
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
	
	/**
	 * Sends the contents of a file to the server.
	 * 
	 * @param filename the name of the file to be sent
	 * @author Joe Frederick Samuel, Ryan Ribeiro
	 */
	private void sendData(String filename) {
		//Preparing the send packet
		request = createPacketData(filename, MODE, OP_WRQ);
		sendPacket = new DatagramPacket(request, request.length, inetAddress, TFTP_DEFAULT_PORT);
		DatagramPacket receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
		
		//Sending packet request
		try {
			sendMessage(sendPacket);
		} catch (IOException e) {
			System.out.println("Failed to send packet read request");
			e.printStackTrace();
			System.exit(1);
		}
		
		try {
			sendRecieveSocket.receive(receivePacket);
			byte[] receivedACK = receivePacket.getData();
			if (receivedACK[0] != (byte) 0 && receivedACK[1] != (byte) 0) {
				//invalid ACK packet
				System.out.println("Acknowledgement Packet from server is incorrect.");
				System.exit(1);
			}
		} catch (IOException e) {
			System.out.println("Failed to receive acknowledge Packet from server.");
			e.printStackTrace();
			System.exit(1);
		}
		
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
				DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), INTERMEDIATE_HOST_PORT_NUMBER);
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
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), INTERMEDIATE_HOST_PORT_NUMBER);
			try {
				sendMessage(sendPacket);
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while sending message to server.");
				e.printStackTrace();
				System.exit(1);
			}
		} catch (UnknownHostException e) {
			System.err.println("UnknownHostException: could not determine IP address of host.");
			e.printStackTrace();
		}
	}
	//End of Trivial File Transfer Protocol Methods
	
	/**
	 * closes the socket for the client
	 */
	public void closeClientSocket() {
		sendRecieveSocket.close();
	}

	/**
	 *return the data in the datagram packet received
	 *
	 * @return  the data in the datagram packet received
	 */
	public byte[] getRecievePacketData(){
		return receivePacket.getData();
	}

	/**
	 * formats a message passed to contain the predefined format for a read request
	 * 
	 * @param filename filename to send with the read request
	 * @param mode the mode to send with the read request
	 * @param type a OP_Code for the type of request to send
	 * @return the message converted into a byte array with proper format
	 */
	public static byte[] createPacketData(String filename, String mode, byte OP_Code) {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byteStream.write(0);
		if(OP_Code == 1)
			byteStream.write(1);
		else if (OP_Code == 2)
			byteStream.write(2);
		byteStream.write(filename.getBytes(), 0, filename.getBytes().length);
		byteStream.write(0);
		byteStream.write(mode.getBytes(), 0, mode.getBytes().length);
		byteStream.write(0);

		return byteStream.toByteArray();
	}

	/**
	 * sends a datagram through the intermediate host's sendRecieveSocket
	 * 
	 * @param message	the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 */
	public void sendMessage(DatagramPacket message) throws IOException{
		sendRecieveSocket.send(message);
	}

	/**
	 * client waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @throws IOException indicated an I/O error has occurred
	 * @return returns the receive datagram packet
	 */
	public DatagramPacket waitRecieveMessage() throws IOException{
		sendRecieveSocket.receive(receivePacket);	
		return receivePacket;
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

	/**
	 * main program for client program containing specified client algorithm
	 *  
	 * @param args
	 */
	public static void main(String[] args) {
		Client client = null;
		//attempt to create a client, exiting if fail to create
		try {
			client = new Client();
		} catch (SocketException e) {
			//failed to create client due to failure to create DatagramSocket
			System.err.println("SocketException: failed to create socket for client program.");
			e.printStackTrace();
			System.exit(1);
		}

		client.getData(FILENAME);		
		client.sendData(FILENAME);
		
		client.closeClientSocket();
	}
}
