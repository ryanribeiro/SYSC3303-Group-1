package server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
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

	//port number of client to send response to
	private int clientPort;

	private static final String TFTP_SERVER_IP = "127.0.0.1";
	private static final int MAX_PACKET_SIZE = 516;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;
	//TFTP OP code
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;
	private static final byte OP_ERROR = 5;
	//Error codes
	private static final byte FILE_NOT_FOUND = 1;
	private static final byte DISK_FULL_CODE = 3;
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
				if (readRequest) {
					readRequest(fileName);
				} else if (writeRequest){
					writeFile(fileName);
				} else {
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
	 * 
	 * 
	 * @param fileName the name of the file to read from the server
	 */
	private void readRequest(String fileName){
		//read in the specified file
		byte[] fileData = readFile(fileName);
		System.out.println("Reading file named " + fileName);
		String fileText = new String(fileData);
		System.out.println("File to send:\n" + fileText);

		//transfer file to client
		sendData(fileText);
	}

	/**
	 * Sends the contents of a file to the client through error sim.
	 * 
	 * @param fileText the file data to send
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton
	 */
	public void sendData(String fileText){
		//split file text into chunks for transfer
		byte[][] fileData = splitByteArray(fileText.getBytes());

		//create socket to transfer file
		DatagramSocket sendReceiveSocket = null;
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Server error while creating socket to transfer data");
			e.printStackTrace();
			System.exit(1);
		}

		/*transfer file to client*/
		DatagramPacket response = null;
		int blockNumberInt = 0;
		int blockNumber = 0;
		byte[] serverResponseData, ACKData;
		DatagramPacket ACKDatagram;
		do{
			//update block number
			blockNumber++;
			byte[] blockNumberArray = intToByteArray(blockNumber);

			/*create response data*/
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(0);
			outputStream.write(OP_DATA);
			outputStream.write(blockNumberArray[2]);
			outputStream.write(blockNumberArray[3]);
			for(int i = 0; i < fileData[blockNumberInt].length; i++){
				outputStream.write(fileData[blockNumberInt][i]);
			}
			blockNumberInt++;	
			serverResponseData = outputStream.toByteArray();

			//create data datagram
			response = new DatagramPacket(serverResponseData, serverResponseData.length, 
					receivePacket.getAddress(), clientPort);

			//print information in message to send
			System.out.println("Server: sending packet");
			printPacketInfo(response);

			//send datagram
			try {
				sendReceiveSocket.send(response);
			} catch (IOException e) {
				System.out.println("Server error while sending data packet to client");
				e.printStackTrace();
				System.exit(1);
			}

			//exit when the final packet is sent from the server
			if(response.getLength() < MAX_PACKET_SIZE)
				break;

			//get ACK packet
			try {
				System.out.println("Server: waiting for acknowledge");
				sendReceiveSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.println("Server error while waiting for acknowledge");
				e.printStackTrace();
				System.exit(1);
			}
			//extract ACK data
			ACKDatagram = receivePacket;
			ACKData = ACKDatagram.getData();
			int recievedBlockNumber = extractBlockNumber(ACKData);

			//print information in message received
			System.out.println("Server: recieved packet");
			printPacketInfo(ACKDatagram);

			//ensure we got an ACK repsponse
			if(ACKDatagram.getData()[1] != OP_ACK){
				System.out.println("Error: expected ACK, received unknown message.");
				return;
			}
			//ensure we got an ACK matching the block number sent
			if(recievedBlockNumber != blockNumber){
				System.out.println("Error: ACK block number does not match sent block number.");
				return;
			}
		} while(true);
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
	 * @param data the data to segement 
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
	 * @param filename name of the new file to write to
	 * @param file contents byte array of data blocks received to write into file.
	 * @author Joe Frederick Samuel, Luke Newton
	 */
	private void writeFile(String fileName) {
		
		byte[] fileContents = receiveFile();
		
		System.out.println("\nFile to write:");
		System.out.println(new String(fileContents) + "\n");
		
		//Check for file already exists
		File file = new File(fileName);
		if(file.exists() && file.isFile()) {
			System.err.println("Error: File Already exists.");
			try {
				createAndSendErrorPacket(FILE_ALREADY_EXISTS, "File Already Exists.");
			} catch (IOException e) {
				System.err.println("Failed creating/sending error packet");
				e.printStackTrace();
			}
		}
		else {
		try {
			FileOutputStream fileWriter = new FileOutputStream(/*"Add path here" +*/fileName); //fileName also includes path. Default path is project folder (may differ from machine to machine)
			fileWriter.write(fileContents);
			fileWriter.close();
		} catch (SyncFailedException err) {
			
			//send error packet
			
			//Thread should terminate on it's own once above is complete
			
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
			//System.exit(1);
		}	
		}
	}
	
	/**
	 * retrieve a file from the server in multple chunks and put blocks together
	 * 
	 * currently assumes that chunks of message appear in correct order!
	 * 
	 * @author Joe Frederick Samuel, Luke Newton
	 * @return the file retrieved from the server as a byte array
	 */
	public byte[] receiveFile(){
		//store the packets received from the server
		DatagramPacket response = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
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
		} catch (SocketException e) {
			System.out.println("failed to create server sokcet");
			e.printStackTrace();
			System.exit(1);
		}
		do {
			
			if(response != null && response.getLength() < MAX_PACKET_SIZE)
				break;
			
			//send acknowledgement to client
			acknowledge(intToByteArray(blockNumber), sendReceiveSocket);
			
			//recieve server message
			try {
				sendReceiveSocket.receive(response);
			} catch (IOException e) {
				System.out.println("I/O EXception while receiving message");
				e.printStackTrace();
				System.exit(1);
			}

			//print information in message received
			System.out.println("server: received packet");
			printPacketInfo(response);

			//get size of the message
			messageSize = response.getLength();
			//get response datagram data
			serverResponseData = response.getData();

			//if we did not get a DATA packet, exit
			if(serverResponseData[1] != OP_DATA){
				System.out.println("Error during file read: unexpected packet format.");
				return null;
			}

			//get block number
			blockNumber = extractBlockNumber(serverResponseData);

			//add response data to buffer (index 4 is the start of data in TFTP DATA packets)
			for(int i = 4; i < messageSize; i++)
				responseBuffer.add(serverResponseData[i]);
		}while(true);

		/*get final byte array from response buffer*/
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		for(Byte b: responseBuffer)
			byteOutputStream.write(b);

		return byteOutputStream.toByteArray();
	}

	/**
	 * Reads the contents of the file and stores it as an array of bytes. If the requested file is not found, 
	 * print error message and send error packet
	 * 
	 * @param filename the name of the file to be read
	 * @return contents of the file read in
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton, Kevin Sun
	 */
	public byte[] readFile(String filename) {
		Path path = Paths.get(filename);
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			//sends error packet to client
			System.out.println("Failed to read file at specified path");
			try {
					createAndSendErrorPacket(FILE_NOT_FOUND, "Failed to read file - File not found.");
				} catch (IOException er) {
					System.err.println("Failed creating/sending error packet");
					er.printStackTrace();
				}
			return null;
		}
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
			bytesToSend.write(OP_DATA);
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
				sendMessage(sendPacket);
			} catch (UnknownHostException e) {
				System.err.println("UnknownHostException: could not determine IP address of host.");
				e.printStackTrace();
			}
			blockID++;
		}	

		//Final packet either empty (0) or remaining bytes, so this bit is to deal with sending one final DATA packet
		ByteArrayOutputStream bytesToSend = new ByteArrayOutputStream();
		bytesToSend.write(0);
		bytesToSend.write(OP_DATA);
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
		//byte[] data = bytesToSend.toByteArray();
		//try {
			//DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getLocalHost(), server.getClientPort());
			try {
				//TODO: Rename to createAndSendPacket(); (not ACK format though)
				sendPacket(); //This used to send a packet but do nothing with it since the packet is created in the method anyway
			} catch (IOException e) {
				System.err.println("IOException: I/O error occured while sending message to client.");
				e.printStackTrace();
				System.exit(1);
			}
		//} catch (UnknownHostException e) {
			//System.err.println("UnknownHostException: could not determine IP address of host.");
			//e.printStackTrace();
		//}
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
		try {
			socket.send(ACKDatagram);
		} catch (IOException e) {
			System.out.println("Server error while sending ACK to client");
			e.printStackTrace();
			System.exit(1);
		}		
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
	private void parseMessage() throws InvalidMessageFormatException{
		byte[] messageData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());

		//check first byte
		if(messageData[0] != 0)
			throw new InvalidMessageFormatException();

		//check read/write byte
		if(messageData[1] == OP_RRQ) {
			readRequest = true;
			writeRequest = false;
		} else if(messageData[1] == OP_WRQ) {
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
	 * Wrapper for sendMessage().
	 * Sends a datagram through the server's sendSocket
	 * 
	 * @param packet the datagram packet to send
	 * @throws IOException indicates and I/O error occurred while sending a message
	 * @throws UnknownHostException thrown if unable to determine the local IP address
	 */
	public void sendPacket() throws UnknownHostException, IOException {
		 /***********************
		 * Create & Send Packet *
		 ***********************/
		byte[] responseData = this.createPacketData();

		DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length,
				InetAddress.getLocalHost(), clientPort);

		System.out.print("Server Response: \nTo ");
		printPacketInfo(sendPacket);

		sendMessage(sendPacket);

		System.out.println("Server response sent");
	}

	/**
	 * formats a message as a response to the appropriate request
	 * Used exclusively for read/write
	 * 
	 * @return the message converted into a byte array with proper format
	 */
	public byte[] createPacketData() {
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
	 * @param message the datagram packet to send
	 */
	public void sendMessage(DatagramPacket message){
		try {
			sendSocket = new DatagramSocket();
			sendSocket.send(message);
		} catch (IOException e) {
			System.out.println("IOException: I/O error occured while server sending message");
			e.printStackTrace();
			System.exit(1);
		}
		sendSocket.close();
	}
	
	
	public void createAndSendErrorPacket(byte errorCode, String msg) throws IOException {
		 /*************************
		 * Check for input errors *
		 *************************/
		boolean doRun = true;
		if (errorCode < 1 || errorCode > 6) {
			System.err.println("Unexpected error code given. Error packet not sent.");
			doRun = false;
		} else if (msg == null) {
			System.err.println("Msg received is null. Error packet not sent.");
			doRun = false;
		}
		 /*********************************
		 * Construct error message & send *
		 *********************************/
		if (doRun) {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			
			byteStream.write(0);
			byteStream.write(OP_ERROR);
			byteStream.write(0);
			byteStream.write(errorCode);
			byteStream.write(msg.getBytes()); //<- IOException source
			byteStream.write(0);
			
			byte[] responseData = byteStream.toByteArray();

			DatagramPacket errorPacket = new DatagramPacket(responseData, responseData.length,
					InetAddress.getLocalHost(), clientPort); //<- IOException source
			
			sendMessage(errorPacket);
		}
		
		
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
