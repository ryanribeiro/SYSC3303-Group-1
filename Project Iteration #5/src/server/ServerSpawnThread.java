package server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * handles the processing of a single client request
 * 
 * @author Kevin Sun, Luke Newton, Joe Frederick Samuel, Ryan Ribeiro
 */
public class ServerSpawnThread implements Runnable {
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
	//Error flag indicating something went wrong and an ACK should not be sent
	private boolean noErrors = true;
	//Last block number received
	private int lastBlockNum;
	//Default path to read/write to/from
	private final String DEFAULT_PATH = "SERVERDATA/";

	//port number of client to send response to
	private int clientPort;
	//address number of client to send response to
	private InetAddress clientAddress;

	//last packet sent
	private DatagramPacket lastPacketSent;

	private static final String TFTP_SERVER_IP = "127.0.0.1";
	private static final int MAX_PACKET_SIZE = 516;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;
	//Socket timeouts
	private static final boolean TIMEOUTS_ON = true;
	private static final int TIMEOUT_MILLISECONDS = 7000;
	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;
	//Error codes
	private static final byte FILE_NOT_FOUND = 1;
	private static final byte ACCESS_VIOLATION_CODE = 2;
	private static final byte DISK_FULL_CODE = 3;
	private static final byte ILLEGAL_TFTP_OPERATION = 4;
	private static final byte UNRECOGNIZED_TID = 5;
	private static final byte FILE_ALREADY_EXISTS = 6;

	private InetAddress serverInetAddress = null;
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
		clientAddress = receivePacket.getAddress();
		readRequest = false;
		writeRequest = false;
		this.server = server;

		//TFTP
		try {
			serverInetAddress = InetAddress.getByName(TFTP_SERVER_IP);
		} catch (UnknownHostException e) {
			System.out.println("Failed to initalize TFTP Server IP");
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * function to execute when thread created.
	 * handles parsing and responding to message.
	 */
	public void run(){
		System.out.println("server message processing thread start.");
		/*synchronize on a common object so we only process one message at a time.
		 * primarily so the console prints ll info for a single message at once*/
		synchronized(server){
			//print data received from client
			System.out.println("\nServer: message from");
			printPacketInfo(receivePacket);

			server.pause();

			/*check if message is proper format*/
			try {
				parseMessage();
				if (readRequest) 
					sendData(readFile(DEFAULT_PATH + fileName));
				else if (writeRequest) 
					writeFile(DEFAULT_PATH + fileName, receiveFile());
				else {
					System.err.println("Error: Request is neither a write or a read.");
					System.exit(1);
				}
			} catch (InvalidMessageFormatException e) {
				System.out.println("InvalidMessageFormatException: a message received was of an invalid format");
				e.printStackTrace();
				System.out.println("Invalid message Contents:");
				printPacketInfo(receivePacket);

			}
			server.messageProcessed();
		}
		System.out.println("server message processing thread finished.");
	}

	/**
	 * Reads the contents of the file and stores it as an array of bytes. If the requested file is not found,
	 * print error message and send error packet
	 *
	 * @param filename the name of the file to be read
	 * @return contents of the file read in
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton, Kevin Sun
	 */
	private byte[] readFile(String filename) {
		Path path = Paths.get(filename);
		System.out.println("Reading file named " + fileName);

		try {
			byte[] fileData = Files.readAllBytes(path);
			return fileData;
		} catch (IOException e) {
			//sends error packet to client
			System.err.println("Failed to read file at specified path");
			try {
				createAndSendErrorPacket(FILE_NOT_FOUND, "Failed to read file - File not found.");
			} catch (IOException er) {
				System.err.println("Failed creating/sending error packet");
				er.printStackTrace();
			}
			return null;
		} catch (SecurityException se) {
			System.out.println("Access violation while trying to read file from server.");
			try {
				createAndSendErrorPacket(ACCESS_VIOLATION_CODE, "Failed access file - Access Violation.");
			} catch (IOException er) {
				System.err.println("Failed creating/sending error packet");
				er.printStackTrace();
			}
			return null;
		}
	}
	/**
	 * Sends the contents of a file during a RRQ to the client through error sim.
	 * 
	 * @param fileContents the file data to send
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton
	 */
	private void sendData(byte[] fileContents) {
		//split file text into chunks for transfer
		byte[][] fileData = splitByteArray(fileContents);

		//create socket to transfer file
		DatagramSocket sendReceiveSocket = null;
		try {
			sendReceiveSocket = new DatagramSocket();
			if (TIMEOUTS_ON)
				sendReceiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		} catch (SocketException e) {
			System.err.println("Server error while creating socket to transfer data");
			e.printStackTrace();
			System.exit(1);
		}

		/*transfer file to client*/
		DatagramPacket response;
		int blockNumber = 0;
		byte[] serverResponseData, ACKData;
		DatagramPacket ACKDatagram;
		boolean keepReceiving;
		do {
			/*create response data*/
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			//check TID
			if(receivePacket.getPort() != clientPort || receivePacket.getAddress() != clientAddress){
				System.err.println("unrecognized TID: " + receivePacket.getPort());
				//unexpected TID
				outputStream.write(0);
				outputStream.write(OP_ERROR);
				outputStream.write(0);
				outputStream.write(UNRECOGNIZED_TID);
				try {
					outputStream.write("unrecognized TID".getBytes());
				} catch (IOException e) {
					System.err.println("IO exception while sending unexpected TID ERROR");
					e.printStackTrace();
					System.exit(1);
				}
				outputStream.write(0);

				serverResponseData = outputStream.toByteArray();
				response = new DatagramPacket(serverResponseData, serverResponseData.length, 
						receivePacket.getAddress(), receivePacket.getPort());
			}else{
				//update block number
				blockNumber++;
				byte[] blockNumberArray = intToByteArray(blockNumber);

				//normal operation
				outputStream.write(0);
				outputStream.write(OP_DATA);
				outputStream.write(blockNumberArray[2]);
				outputStream.write(blockNumberArray[3]);
				for (int i = 0; i < fileData[blockNumber-1].length; i++){
					outputStream.write(fileData[blockNumber-1][i]);
				}

				serverResponseData = outputStream.toByteArray();
				//create data datagram
				response = new DatagramPacket(serverResponseData, serverResponseData.length, 
						clientAddress, clientPort);
			}
			//print information in message to send
			System.out.println("Server: sending packet");
			printPacketInfo(response);

			//send datagram
			try {
				sendReceiveSocket.send(response);

			} catch (IOException e) {
				System.err.println("Server error while sending data packet to client");
				e.printStackTrace();
				System.exit(1);
			}
			if(receivePacket.getPort() == clientPort && receivePacket.getAddress() == clientAddress)
				lastPacketSent = response;

			int numTimeouts = 0;
			do { //keep receiving if the packet was not the one expected. NOTE: Packet last received is thrown out if unexpected.
				do { //keep receiving if no packet is given and socket times out.
					keepReceiving = true;
					//get ACK packet
					try {
						System.out.println("Server: waiting for acknowledge");

						sendReceiveSocket.receive(receivePacket);
						keepReceiving = false;

					} catch (SocketTimeoutException te) {
						//resend last packet
						numTimeouts += 1;
						System.err.println("Timed out. Resending last packet.");
						printPacketInfo(lastPacketSent);
						try {
							sendReceiveSocket.send(lastPacketSent);
						} catch (IOException e) {
							System.err.println("Server error while sending data packet to client");
							e.printStackTrace();
						}
					} catch (IOException e) {
						System.err.println("Server error while waiting for acknowledge");
						e.printStackTrace();
						System.exit(1);
					}
				} while (keepReceiving && numTimeouts < 3);

				if (numTimeouts >= 3) {//We've given up trying to receive, exit this loop
					System.err.println("Timed out indefinitely. Total time waited: " + (TIMEOUT_MILLISECONDS * 3)/1000 + " seconds");
					break;
				}

				//extract ACK data
				ACKDatagram = receivePacket;
				ACKData = ACKDatagram.getData();
				int receivedBlockNumber = extractBlockNumber(ACKData);

				//print information in message received
				System.out.println("Server: received packet");
				printPacketInfo(ACKDatagram);

				//check for illegal operation
				byte opcode = ACKDatagram.getData()[1];
				if(!(opcode == OP_RRQ || opcode == OP_WRQ || opcode == OP_ACK 
						|| opcode == OP_DATA || opcode == OP_ERROR)){
					try {
						System.err.println("Error: Illegal TFTP Operation (op code not recognized)");
						createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Op code not recognized");
						sendReceiveSocket.close();
						return;
					} catch (IOException e) {
						System.err.println("IO error while sending ERROR packet");
						e.printStackTrace();
						System.exit(1);
					}
				}

				if(opcode == OP_ERROR){
					System.err.println("Error during file read:");
					printPacketInfo(receivePacket);
					System.err.println("File read from server failed");
					return;
				}

				//ensure we got an ACK response
				if (ACKDatagram.getData()[1] != OP_ACK || receivedBlockNumber != blockNumber) {
					System.err.println("Error: packet not expected. Resending last packet sent.");
					printPacketInfo(lastPacketSent);
					try {
						sendReceiveSocket.send(lastPacketSent);
					} catch (IOException e) {
						System.err.println("Server error while sending data packet to client");
						e.printStackTrace();
					}
					keepReceiving = true;
				}
			} while(keepReceiving);

			//Exit when the final ACK is received. If we reach here, the received ACK has been dealt with.
			// Just check that last packet has been sent.
		} while(lastPacketSent.getLength() == MAX_PACKET_SIZE);
		sendReceiveSocket.close();
	}

	/**
	 * extract a block number from an ACK packet
	 * 
	 * @author Luke Newton
	 * @param ACKData the data the ACK packet contains
	 * @return the block number of the ACK packet
	 */
	private int extractBlockNumber(byte[] ACKData) {
		return ByteBuffer.wrap(new byte[]{0, 0, ACKData[2], ACKData[3]}).getInt();
	}

	/**
	 * converts and integer into a byte array
	 * 
	 * @author Luke Newton
	 * @param blockNumber the integer to convert to a byte array
	 * @return the passed integer as a byte array
	 */
	private byte[] intToByteArray(int blockNumber) {
		return ByteBuffer.allocate(Integer.BYTES).putInt(blockNumber).array();
	}

	/**
	 * segments data into proper sized chunks to send in packets
	 * 
	 * @param data the data to segment
	 * @return an array containing byte arrays of a max length 512
	 */
	private byte[][] splitByteArray(byte[] data){
		//the length of the data to segment
		int dataLength = data.length;
		//array to hold the segmented data
		byte[][] result = new byte[(dataLength + MAX_BLOCK_SIZE - 1)/MAX_BLOCK_SIZE + 1][];
		int resultIndex = 0;
		int stopIndex = 0;

		for (int i = 0; i + MAX_BLOCK_SIZE <= dataLength; i += MAX_BLOCK_SIZE){
			stopIndex += MAX_BLOCK_SIZE;
			result[resultIndex] = Arrays.copyOfRange(data, i, stopIndex);
			resultIndex++;
		}

		//add the last segment
		if (stopIndex < dataLength)
			result[resultIndex] = Arrays.copyOfRange(data, stopIndex, dataLength);

		resultIndex++;
		//ensure that we always have a not full segment to send at the end (to stop data transfer)
		result[resultIndex] = new byte[]{0};

		return result;
	}

	/**
	 * Writes data to file
	 * 
	 * @param fileName name of the new file to write to
	 * @author Joe Frederick Samuel, Luke Newton, CRushton
	 */
	private void writeFile(String fileName, byte[] fileContents) {
		if(fileContents == null)
			return;

		//Check for file already exists
		File file = new File(fileName);
		if (file.exists() && file.isFile()) {
			System.err.println("Error: File Already exists.");
			try {
				createAndSendErrorPacket(FILE_ALREADY_EXISTS, "File Already Exists.");
			} catch (IOException e) {
				System.err.println("Failed creating/sending error packet");
				e.printStackTrace();
			}
		} else {
			try {
				FileOutputStream fileWriter = new FileOutputStream(fileName); //fileName also includes path. Default path is project folder (may differ from machine to machine)
				fileWriter.write(fileContents);
				fileWriter.close();

			} catch (IOException e) {
				System.err.println("Failed to write the file.");
				if (e.getMessage().equals("There is not enough space on the disk")) { //create and send error code 3 packet
					try {
						createAndSendErrorPacket(DISK_FULL_CODE, "Failed to write file - disk full.");
					} catch (IOException er) {
						System.err.println("Failed creating/sending error packet");
						er.printStackTrace();
					}
				}
				e.printStackTrace();

			} catch (SecurityException se) {
				System.err.println("Access violation while trying to read file from server.");
				try {
					createAndSendErrorPacket(ACCESS_VIOLATION_CODE, "Failed access file - Access Violation.");
				} catch (IOException er) {
					System.err.println("Failed creating/sending error packet");
					er.printStackTrace();
				}
			}
		}
		//writing file is successful, send ACK
		if (noErrors)
			acknowledge(intToByteArray(lastBlockNum), sendSocket);
	}

	/**
	 * retrieve a file from the server in multple chunks and put blocks together
	 * 
	 * currently assumes that chunks of message appear in correct order!
	 * 
	 * @author Joe Frederick Samuel, Luke Newton
	 * @return the file retrieved from the server as a byte array
	 */
	private byte[] receiveFile(){
		//store the packets received from the server
		DatagramPacket response = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE,
				clientAddress, clientPort);
		//the size of the message received from the server
		int messageSize;
		//current block number of DATA
		int blockNumber = 0;
		//the data contained in the response datagram
		byte[] serverResponseData;
		//buffer to store what has been received so far
		List<Byte> responseBuffer = new ArrayList<>();

		DatagramSocket sendReceiveSocket = null;
		try {
			sendReceiveSocket = new DatagramSocket();
			if (TIMEOUTS_ON)
				sendReceiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
		} catch (SocketException e) {
			System.err.println("Failed to create server socket");
			e.printStackTrace();
			System.exit(1);
		}
		boolean keepReceiving;
		int numTimeouts = 0;
		do {
			//check TID
			if(response.getPort() != clientPort || response.getAddress() != clientAddress){
				System.err.println("unrecognized TID: " + response.getPort());
				//unexpected TID
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				outputStream.write(0);
				outputStream.write(OP_ERROR);
				outputStream.write(0);
				outputStream.write(UNRECOGNIZED_TID);
				try {
					outputStream.write("unrecognized TID".getBytes());
				} catch (IOException e) {
					System.err.println("IO exception while sending unexpected TID ERROR");
					e.printStackTrace();
					System.exit(1);
				}
				outputStream.write(0);

				DatagramPacket responseToUnexpectedTID = new DatagramPacket(outputStream.toByteArray(), 
						outputStream.toByteArray().length, response.getAddress(), response.getPort());

				try {
					sendReceiveSocket.send(responseToUnexpectedTID);
					System.out.println("Sent message to:");
					printPacketInfo(responseToUnexpectedTID);
				} catch (IOException e) {
					System.err.println("Server error while sending unknown TID ERROR");
					e.printStackTrace();
					System.exit(1);
				}
			}  
			//normal operation
			//Send acknowledgement to client. It should be determined after writing the file successfully whether an ACK should be sent or an error
			else if (noErrors && !(response.getLength() < MAX_PACKET_SIZE))
				acknowledge(intToByteArray(blockNumber), sendReceiveSocket);

			if (response.getLength() < MAX_PACKET_SIZE) {
				lastBlockNum = blockNumber;
				break;
			}
			do { //keep receiving if unexpected packet was received. Note: last packet received is thrown out
				do { //send last packet & keep receiving if timeout happens
					keepReceiving = true;
					//receive client message
					try {
						sendReceiveSocket.receive(response);
						keepReceiving = false;
					} catch (SocketTimeoutException te) {
						//resend last message
						numTimeouts += 1;
						try {
							sendReceiveSocket.send(lastPacketSent);
						} catch (IOException e) {
							System.err.println("I/O Exception while resending message");
							e.printStackTrace();
						}
					} catch (IOException e) {
						System.err.println("I/O Exception while receiving message");
						e.printStackTrace();
						System.exit(1);
					}
				} while (keepReceiving && numTimeouts < 3);
				//print information in message received
				System.out.println("Server: received packet");
				printPacketInfo(response);

				//get size of the message
				messageSize = response.getLength();
				//get response datagram data
				serverResponseData = response.getData();

				//check for illegal operation
				byte opcode = response.getData()[1];
				if(!(opcode == OP_RRQ || opcode == OP_WRQ || opcode == OP_ACK 
						|| opcode == OP_DATA || opcode == OP_ERROR)){
					try {
						System.err.println("Error: Illegal TFTP Operation (op code not recognized)");
						createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Op code not recognized");
						sendReceiveSocket.close();
						return null;
					} catch (IOException e) {
						System.err.println("IO error while sending ERROR packet");
						e.printStackTrace();
						System.exit(1);
					}
				}

				//check for error packet
				if(serverResponseData[1] == OP_ERROR){
					System.err.println("Error during file write to server:");
					printPacketInfo(response);
					sendReceiveSocket.close();
					return null;
				}

				//if we did not get a DATA packet, exit
				if (serverResponseData[1] != OP_DATA) {
					System.err.println("Error during file read: unexpected packet format.");
					keepReceiving = true;
				}
			} while(keepReceiving && numTimeouts < 3);
			//get block number
			blockNumber = extractBlockNumber(serverResponseData);


			if(response.getPort() == clientPort && response.getAddress() == clientAddress){
				//add response data to buffer (index 4 is the start of data in TFTP DATA packets)
				for(int i = 4; i < messageSize; i++)
					responseBuffer.add(serverResponseData[i]);
			}

		} while(true);

		/*get final byte array from response buffer*/
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		for(Byte b: responseBuffer)
			byteOutputStream.write(b);

		return byteOutputStream.toByteArray();
	}

	/**
	 * Acknowledges reception of server packet.
	 * 
	 * @param blockID byte array containing the block ID whose reception is acknowledged.
	 * @author Joe Frederick Samuel, Luke Newton
	 */
	private void acknowledge(byte[] blockID, DatagramSocket socket) {
		byte[] ack = {0, OP_ACK, blockID[2], blockID[3]};
		DatagramPacket ACKDatagram = new DatagramPacket(ack, ack.length, serverInetAddress, receivePacket.getPort());

		//create socket if needed
		if (socket == null) {
			try {
				socket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		try {
			socket.send(ACKDatagram);
		} catch (IOException e) {
			System.err.println("Server error while sending ACK to client");
			e.printStackTrace();
			System.exit(1);
		}
		lastPacketSent = ACKDatagram;
		System.out.println("sent acknowledgement to client");
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
	private void parseMessage() throws InvalidMessageFormatException {
		byte[] messageData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());

		//check first byte
		if (messageData[0] != 0)
			throw new InvalidMessageFormatException();

		//check read/write byte
		if (messageData[1] == OP_RRQ) {
			readRequest = true;
			writeRequest = false;
		} else if (messageData[1] == OP_WRQ) {
			readRequest = false;
			writeRequest = true;
		} else {
			try {
				createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Op code not recognized");
			} catch (IOException e) {
				System.err.println("IO error occured while sending ERROR packet");
				e.printStackTrace();
				System.exit(1);
			}
			throw new InvalidMessageFormatException();
		}

		int currentIndex = 2; //start at 2 to account for the first two bytes
		//store names of file and mode in a stream
		ByteArrayOutputStream textStream = new ByteArrayOutputStream();
		try {
			/*
			 * Check for some text followed by a zero
			 * & add text to byte array
			 */
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			for (; messageData[currentIndex] != 0; currentIndex++) {
				textStream.write(messageData[currentIndex]);
			}


			//Convert file name to byte array
			fileName = textStream.toString();

			//if our filename does not have a '.' in it or is empty, it does not indicate file type, and so is invalid
			if (textStream.size() <= 0 || !fileName.contains(".")){
				try {
					createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Invalid Filename");
				} catch (IOException e) {
					System.err.println("IO error occured while sending ERROR packet");
					e.printStackTrace();
					System.exit(1);
				}
				throw new InvalidMessageFormatException("Invalid Filename");
			}


			/*
			 * Check for some more text followed by a zero
			 */
			//NOTE: this does not allow for spaces (space represented by a zero byte)
			textStream.reset();

			for (currentIndex++; messageData[currentIndex] != 0; currentIndex++) {
				textStream.write(messageData[currentIndex]);
			}

			mode = textStream.toString();
			mode = mode.toLowerCase();

			//if the mode text is not netascii or octet, or is empty, packet is invalid
			if (textStream.size() <= 0 || 
					(!mode.equals("netascii") && !mode.equals("octet"))){
				try {
					createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Invalid Mode");
				} catch (IOException e) {
					System.err.println("IO error occured while sending ERROR packet");
					e.printStackTrace();
					System.exit(1);
				}
				throw new InvalidMessageFormatException("Invalid Mode");
			}

		} catch (IndexOutOfBoundsException e) {
			/*if we go out of bounds while iterating through the message data,
			 * then it does not end in a 0 and thus is incorrect format
			 */
			throw new InvalidMessageFormatException("Reached End Of Packet");
		}
		//check that this is the end of the message
		if (currentIndex + 1 != messageData.length) {
			throw new InvalidMessageFormatException("Reached \"End\" Of Packet But There Is More");
		}
	}

	/**
	 * sends a datagram from the server
	 * 
	 * @param message the datagram packet to send
	 */
	private void sendMessage(DatagramPacket message){
		try {
			sendSocket = new DatagramSocket();
			sendSocket.send(message);
		} catch (IOException e) {
			System.out.println("IOException: I/O error occurred while server sending message");
			e.printStackTrace();
			System.exit(1);
		}
		lastPacketSent = message;
		sendSocket.close();
	}

	/**
	 * Creates and sends an error packet on the port that is being serviced.
	 * @author Cameron Rushton
	 * @param errorCode A number indicating the TFTP error code number
	 * @param msg The message that is displayed to the user
	 * @throws IOException
	 */
	private void createAndSendErrorPacket(byte errorCode, String msg) throws IOException {
		noErrors = false;
		/*
		 * Check for input errors
		 */
		if (errorCode < 0 || errorCode > 7) {
			System.err.println("Unexpected error code given. Error packet not sent.");

		} else if (msg == null) {
			System.err.println("Error message is null. Error packet not sent.");

		} else {
			/*
			 * Construct error message & send
			 */
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

			byteStream.write(0);
			byteStream.write(OP_ERROR);
			byteStream.write(0);
			byteStream.write(errorCode);
			byteStream.write(msg.getBytes()); //<- IOException source
			byteStream.write(0);

			byte[] responseData = byteStream.toByteArray();

			DatagramPacket errorPacket = new DatagramPacket(responseData, responseData.length,
					clientAddress, clientPort); //<- IOException source

			sendMessage(errorPacket);
		}

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
		System.out.print("Type: ");
		switch(dataAsByteArray[1]) {
		case 1: System.out.println("RRQ"); break;
		case 2: System.out.println("WRQ"); break;
		case 3: System.out.println("DATA"); break;
		case 4: System.out.println("ACK"); break;
		case 5: System.out.println("ERROR"); break;
		}
		System.out.println("Containing: " + new String(dataAsByteArray));
		System.out.println("Contents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}
}
