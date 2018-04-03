package client;


import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * This class represents the client for the server-client-error simulator system. It
 * has the capability to send read and write requests to a server at a specified IP address,
 * toggle the print-out of details of every packet sent and recieved, and an extensive help 
 * menu to understand acceptable commands.
 */
public class Client {
	/**START: object constants*/
	//port number of intermediate host (ErrorSimulator)
	private static final int INTERMEDIATE_HOST_PORT_NUMBER = 23;
	//max size for data in a DatagramPacket
	private static final int MAX_PACKET_SIZE = 516;
	//max block size as an int
	private static final int MAX_BLOCK_SIZE = 512;
	//mode to send to server (not used in this example, but a part of TFTP)
	private static final String MODE = "octet";
	//boolean indicating if client can timeout while waiting for packet
	private static final boolean TIMEOUTS_ON = true;
	//number of milliseconds until client times out while waiting for packet
	private static final int TIMEOUT_MILLISECONDS = 5000;
	
	/**START: TFTP operation codes*/
	//read request
	private static final byte OP_RRQ = 1;
	//write request
	private static final byte OP_WRQ = 2;
	//data packet
	private static final byte OP_DATA = 3;
	//acknowledge packet
	private static final byte OP_ACK = 4;
	//error packet
	private static final byte OP_ERROR = 5;
	/**END: TFTP operation codes*/

	/**START: TFTP error codes*/
	//TFTP error code 4 - unrecognized operation
	private static final byte ILLEGAL_TFTP_OPERATION = 4;
	//TFTP error code 5 - unrecognized operation
	private static final byte UNRECOGNIZED_TID = 5;
	/**END: TFTP error codes*/
	/**END: object constants*/
	
	/**START: instance variables*/
	//the address of the server to send requests to
	private InetAddress serverAddress = null;
	//the port number of the server to send requests to
	private int serverPort;
	//socket used by client to send and receive datagrams
	private DatagramSocket sendReceiveSocket;
	//the most recent packet received
	private DatagramPacket receivePacket;
	//the most recent (non-ERROR-code-5) packet sent out
	private DatagramPacket lastPacketSent;
	//boolean indicating if details of packets sent and received should be printed to console
	private boolean quietMode;
	/**END: instance variables*/
	
	/**Constructor
	 * @throws SocketException indicates failed to create socket for client
	 * @author Luke Newton
	 * */
	public Client() throws SocketException {
		try {
			receivePacket = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			System.out.println("Failed to initialize TFTP Server IP");
			e.printStackTrace();
			System.exit(1);
		}
		serverPort = INTERMEDIATE_HOST_PORT_NUMBER;
		quietMode = false;
		//attempt to create socket for send and receive
		sendReceiveSocket = new DatagramSocket();
		//turn on timeout if required
		if(TIMEOUTS_ON)
			sendReceiveSocket.setSoTimeout(TIMEOUT_MILLISECONDS);
	}

	/**
	 * Writes data to file
	 *
	 * @param fileName name of the new file to write to
	 * @param fileContents contents byte array of data blocks received to write into file.
	 * @author Joe Frederick Samuel, Luke Newton
	 */
	private void writeFile(String fileName, byte[] fileContents) {
		try {
			FileOutputStream fileWriter = new FileOutputStream(fileName);
			fileWriter.write(fileContents);
			fileWriter.close();
		} catch (IOException e) {
			System.out.println("Failed to write the file.");
			e.printStackTrace();
		} catch (SecurityException se) {
			System.out.println("Access violation while trying to write file from server.");
			se.printStackTrace();
		}
	}

	/**
	 * Acknowledges reception of server packet.
	 *
	 * @param blockID byte array containing the block ID whose reception is acknowledged.
	 * @author Joe Frederick Samuel
	 */
	private void acknowledge(byte[] blockID) {
		byte[] ack = {0, OP_ACK, blockID[2], blockID[3]};

		DatagramPacket ACKDatagram = new DatagramPacket(ack, ack.length, serverAddress, serverPort);

		sendMessage(ACKDatagram);
		if(!quietMode)
			System.out.println("sent acknowledgement to server");
	}


	/**
	 * Checks if the packet received is the last packet for transmission
	 *
	 * @param receivedPacket the packet received from the server
	 * @return boolean	true if last packet, false otherwise.
	 * @author Joe Frederick Samuel
	 */
	private boolean isLastPacket(DatagramPacket receivedPacket) {
		return receivedPacket.getLength() < MAX_PACKET_SIZE;
	}

	//End of Trivial File Transfer Protocol Methods

	/**
	 * closes the socket for the client
	 *
	 * @author Luke Newton
	 */
	private void closeClientSocket() {
		sendReceiveSocket.close();
	}

	/**
	 * formats a message passed to contain the predefined format for a request
	 *
	 * @author Luke Newton, Joe Frederick Samuel
	 * @param filename filename to send with the read request
	 * @param mode the mode to send with the read request
	 * @param OP_Code type a OP_Code for the type of request to send
	 * @return the message converted into a byte array with proper format
	 */
	private static byte[] createPacketData(String filename, String mode, byte OP_Code) {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		byteStream.write(0);
		if(OP_Code == OP_RRQ || OP_Code == OP_WRQ)
			byteStream.write(OP_Code);
		byteStream.write(filename.getBytes(), 0, filename.getBytes().length);
		byteStream.write(0);
		byteStream.write(mode.getBytes(), 0, mode.getBytes().length);
		byteStream.write(0);

		return byteStream.toByteArray();
	}

	/**
	 * sends a datagram through the intermediate host's sendReceiveSocket
	 * @author Luke Newton
	 *
	 * @param message the datagram packet to send
	 */
	private void sendMessage(DatagramPacket message){
		try {
			sendReceiveSocket.send(message);
		} catch (IOException e) {
			System.out.println("IOException: I/O error occurred while client sending message");
			e.printStackTrace();
			System.exit(1);
		}
		if(!quietMode){
			System.out.print("Sending packet \nTo: ");
			printPacketInfo(message);
		}
		if(receivePacket.getPort() == serverPort && receivePacket.getAddress() == serverAddress)
			lastPacketSent = message;
	}

	/**
	 * main program for client program
	 *
	 * @author Luke Newton
	 * @param args any arguements passed to Client main are not used
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

		client.printHelpMenu();

		Scanner scanner = new Scanner(System.in);
		String command = "";
		String filename = "";
		String[] input;
		boolean filenameGiven;
		while(true){
			client.serverPort = INTERMEDIATE_HOST_PORT_NUMBER;
			System.out.print("Command: ");

			/*receive user input*/
			input = scanner.nextLine().split(" ");
			command = input[0];
			try{
				filename = input[1];
				filenameGiven = true;
			} catch (ArrayIndexOutOfBoundsException e){
				//no filename specified by user
				filenameGiven = false;
			}

			/*respond to user input*/
			if(command.equalsIgnoreCase("read") && filenameGiven){
				/*user read request*/
				byte[] file = client.readRequest(filename);

				if(file != null){
					//get filename to save retreived file as
					System.out.print("Enter name to save file as: ");
					String newFilename = scanner.nextLine();

					//write file
					client.writeFile(newFilename, file);
				} else
					System.out.println("File read failed.");
			}else if(command.equalsIgnoreCase("write") && filenameGiven)
				client.sendData(filename);
			else if(command.equalsIgnoreCase("quiet")){
				client.quietMode = true;
				System.out.println("quiet mode activated");
			}else if(command.equalsIgnoreCase("verbose")){
				client.quietMode = false;
				System.out.println("verbose mode activated");
			}else if(command.equalsIgnoreCase("connect")){
				//further requests will be sent to the specified address (default is local address)
				if(input[1].equalsIgnoreCase("local") && input[2].equalsIgnoreCase("host")){
					try {
						client.serverAddress = InetAddress.getLocalHost();
						System.out.println("sending server requests to:" + client.serverAddress.toString());
					} catch (UnknownHostException e) {
						System.err.println("unable to determine local address");
					}
				}else{
					try {
						//note: determining if the specified address is valid seems to take a while, not really much that can be done about this
						client.serverAddress = InetAddress.getByName(input[1]);
						System.out.println("sending server requests to:" + client.serverAddress.toString());
					} catch (UnknownHostException e) {
						System.out.println("Invalid address given:" + input[1]);
					}
				}
			}else if(command.equalsIgnoreCase("help")){
				if(input.length == 1)
					client.printHelpMenu();
				else if(input[1].equalsIgnoreCase("read")){
					System.out.println("\nFormat: read <file name>\n"
							+ "The command 'read' initiates a read request to the server.\n"
							+ "A file name must be supplied corresponding to the file to read from the server\n"
							+ "This file name must be a single word (no whitespaces) and include the file extension.\n"
							+ "Once a file has been read from the server, the user will be prompted for a name to save the file as.\n"
							+ "After the file has been saved to the client space, the user will be prompted for the next command.\n");
				}else if(input[1].equalsIgnoreCase("write")){
					System.out.println("\nFormat: write <file name>\n"
							+ "The command 'write' initiates a write request to the server\n"
							+ "A file name must be supplied corresponding to the file to read from the server\n"
							+ "This file name must be a single word (no whitespaces) and include the file extension.\n"
							+ "After the file has been written to the server, the user will be prompted for the next command.\n");
				}else if(input[1].equalsIgnoreCase("quit")){
					System.out.println("\nFormat: quit\n"
							+ "The command 'quit' will close the current client program.\n"
							+ "A quit command cannot be issued while any file transfer is in progress.\n"
							+ "A message will be displayed indicating the the client program has been terminated.\n");
				}else if(input[1].equalsIgnoreCase("quiet")){
					System.out.println("\nFormat: quiet\n"
							+ "The command 'quiet' will stop the client from displaying information on each packet sent and recieved.\n"
							+ "In quiet mode, only errors will be displayed if they occur.\n");
				}else if(input[1].equalsIgnoreCase("verbose")){
					System.out.println("\nFormat: verbose\n"
							+ "The command 'verbose' will cause the client to display information on each packet sent and recieved.\n");
				}else if(input[1].equalsIgnoreCase("connect")){
					System.out.println("\nGeneral format: connect <IP address>\n"
							+ "Special Case: connect local host\n"
							+ "The command 'connect' will send all further requests to the specified IP address.\n");
				}else{
					System.out.println("Invalid command! Type 'help' to check available commands");
				}
			}else if(command.equalsIgnoreCase("quit"))
				break;
			else if(input.length > 2)
				System.out.println("Invalid command! Too many arguments.");
			else
				System.out.println("Invalid command! Type 'help' to check available commands and remember to provide a file name if required");
		}
		System.out.println("Client shut down due to command given");
		scanner.close();
		client.closeClientSocket();
	}

	/**
	 * performs a file read from the server
	 *
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton
	 * @param filename the name of the file to read from the server
	 * @return the file data read from the server
	 */
	private byte[] readRequest(String filename){
		//create RRQ data
		byte[] RRQData = createPacketData(filename, MODE, OP_RRQ);
		//create RRQ
		DatagramPacket RRQDatagram = new DatagramPacket(RRQData, RRQData.length,
				serverAddress, INTERMEDIATE_HOST_PORT_NUMBER);

		//send RRQ
		sendMessage(RRQDatagram);

		//get server response
		return receiveFile();
	}

	/**
	 * Sends the contents of a file to the server during a write request.
	 *
	 * @param filename the text to send in the file
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton, CRushton
	 */
	private void sendData(String filename){

		//create WRQ data
		byte[] WRQData = createPacketData(filename, MODE, OP_WRQ);
		//create WRQ
		DatagramPacket WRQDatagram = new DatagramPacket(WRQData, WRQData.length,
				serverAddress, serverPort);

		//read in the specified file
		byte[][] fileData = splitByteArray(readFile(filename));

		/*transfer file to server*/
		DatagramPacket response = null;
		int blockNumberInt = 0;
		int blockNumber = 0;
		byte[] serverResponseData, ACKData;
		boolean keepReceiving;
		boolean firstTraversal = true;
		int numTimeouts = 0;
		//get ACK packet

		response = WRQDatagram;
		do {
			//send datagram
			sendMessage(response);

			if (response.getData()[1] == OP_DATA && response.getLength() < MAX_PACKET_SIZE) //Sent last DATA. Dont look for ACK
				break;

			keepReceiving = true;
			do { //received a packet, but packet was found not valid
				do { //did not receive a packet, resend previous packet
					try {
						if(!quietMode)
							System.out.println("Client: waiting for acknowledge");
						sendReceiveSocket.receive(receivePacket);
						if(firstTraversal){
							serverAddress = receivePacket.getAddress();
							serverPort = receivePacket.getPort();
							firstTraversal = false;
						}
						keepReceiving = false;
					} catch (SocketTimeoutException er) { //Timed out, resend previous packet
						if(!quietMode)
							System.err.println("Timed out, resending last packet.");
						numTimeouts++;
						sendMessage(lastPacketSent);
					} catch (IOException e) {
						System.err.println("client error while waiting for acknowledge");
						e.printStackTrace();
						System.exit(1);
					}
				} while (keepReceiving && numTimeouts < 3);

				if (numTimeouts >= 3) {
					if(!quietMode)
						System.err.println("Timed out indefinitely. Time waited: " + (TIMEOUT_MILLISECONDS * 3)/1000 + " seconds");
					break;
				}

				//extract ACK data
				ACKData = receivePacket.getData();
				int receivedBlockNumber = extractBlockNumber(ACKData);

				//print information in message received
				if(!quietMode){
					System.out.println("Client: received packet");
					printPacketInfo(receivePacket);
				}

				if(ACKData[1] == OP_ERROR){
					if(!quietMode){
						System.err.println("Error during file write:");
						printPacketInfo(receivePacket);
						System.err.println("File write failed");
					}
					return;
				}

				//check for illegal operation
				byte opcode = ACKData[1];
				if(!(opcode == OP_RRQ || opcode == OP_WRQ || opcode == OP_ACK 
						|| opcode == OP_DATA || opcode == OP_ERROR)){
					try {
						System.err.println("Error: Illegal TFTP Operation (op code not recognized)");
						createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Op code not recognized", serverPort);
						System.err.println("File write failed");
						return;
					} catch (IOException e) {
						System.err.println("IO error while sending ERROR packet");
						e.printStackTrace();
						System.exit(1);
					}
				}

				if (checkACK(receivePacket) < 0) {
					keepReceiving = true;
				}

				//ensure we got an ACK matching the block number sent
				if (receivedBlockNumber != blockNumber) {
					if(!quietMode)
						System.err.println("Error: ACK block number does not match sent block number.");
					keepReceiving = true;
				}
			}while (keepReceiving);

			/*create response data*/
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

			//ensure packet comes from same TID
			if(receivePacket.getPort() != serverPort || receivePacket.getAddress() != serverAddress){
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
			} else{
				//update block number
				blockNumber++;
				byte[] blockNumberArray = intToByteArray(blockNumber);


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
						serverAddress, serverPort);
			}
		}while(true);
	}

	/**
	 * Creates and sends an error packet on the port that is being serviced.
	 * @author Cameron Rushton
	 * @param errorCode A number indicating the TFTP error code number
	 * @param msg The message that is displayed to the user
	 * @throws IOException
	 */
	private void createAndSendErrorPacket(byte errorCode, String msg, int serverPort) throws IOException {
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
					serverAddress, serverPort); //<- IOException source

			sendMessage(errorPacket);
		}

	}	

	/**
	 * looks for errors in ACK packet
	 * @author Luke Newton, CRushton
	 * @param ACKDatagram An ACK datagram packet
	 * @return int A negative number if there is an error and 1 if everything is expected
	 */
	private int checkACK(DatagramPacket ACKDatagram) {
		//ensure we got an ACK response
		if(ACKDatagram.getData()[1] != OP_ACK) {
			//Checks if it was an error packet
			if (ACKDatagram.getData()[1] == OP_ERROR) {

				ByteArrayOutputStream textStream = new ByteArrayOutputStream();
				//Retrieves the error message sent in the packet
				for (int i = 4; ACKDatagram.getData()[i] != 0; i++) {
					textStream.write(ACKDatagram.getData()[i]);
				}
				if(!quietMode){
					String errorMessage = textStream.toString();
					System.err.println("\n" + errorMessage + "\n");
				}
				return -1;
			} else {
				if(!quietMode)
					System.err.println("Error: expected ACK, received unknown message.");
				return -2;
			}
		}
		return 1;
	}

	/**
	 * segments data into proper sized chunks to send in packets
	 *
	 * @author Luke Newton
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
	 * retrieve a file from the server in multple chunks and put blocks together
	 *
	 * currently assumes that chunks of message appear in correct order!
	 *
	 * @author Joe Frederick Samuel, Luke Newton, CRushton
	 * @return the file retrieved from the server as a byte array
	 */
	private byte[] receiveFile(){
		//store the packets received from the server
		//DatagramPacket response;
		//the size of the message received from the server
		int messageSize = 0;
		//current block number of DATA
		int blockNumber = 0;
		//the data contained in the response datagram
		byte[] serverResponseData = new byte[0];
		//buffer to store what has been received so far
		List<Byte> responseBuffer = new ArrayList<>();

		boolean keepReceiving;
		int numTimeouts = 0;
		boolean firstTraversal = true;

		do {
			keepReceiving = true; //new packet to receive, reset to true
			do { //Received packet, but was incorrect format. Dont send ACK & Try to receive again
				do { //if timed out, send last packet sent and try receiving again

					try {
						sendReceiveSocket.receive(receivePacket);
						if(firstTraversal){
							serverAddress = receivePacket.getAddress();
							serverPort = receivePacket.getPort();
							firstTraversal = false;
						}
						keepReceiving = false;
					} catch (SocketTimeoutException er) {
						if(!quietMode)
							System.err.println("Timed out, waiting on another packet.");
						numTimeouts += 1;
						if (blockNumber == 0) { //if we need to send another RRQ
							sendMessage(lastPacketSent);
						} else { //send last ACK
							acknowledge(intToByteArray(blockNumber));
						}
					} catch (IOException e) {
						System.out.println("IOException: I/O error occurred while client waiting for message");
						e.printStackTrace();
						System.exit(1);
					}
				} while (keepReceiving && numTimeouts < 3);

				if (numTimeouts >= 3) {
					if(!quietMode)
						System.err.println("Timed out indefinitely. Total time waited: " + (TIMEOUT_MILLISECONDS * 3)/1000 + " seconds");
					break;
				}
				//print information in message received
				if(!quietMode){
					System.out.println("Client: received packet");
					printPacketInfo(receivePacket);
				}

				//get size of the message
				messageSize = receivePacket.getLength();
				//get response datagram data
				serverResponseData = receivePacket.getData();

				//check for illegal operation
				byte opcode = serverResponseData[1];
				if(!(opcode == OP_RRQ || opcode == OP_WRQ || opcode == OP_ACK 
						|| opcode == OP_DATA || opcode == OP_ERROR)){
					try {
						System.err.println("Error: Illegal TFTP Operation (op code not recognized)");
						createAndSendErrorPacket(ILLEGAL_TFTP_OPERATION, "Op code not recognized", receivePacket.getPort());
						System.err.println("File write failed");
						return null;
					} catch (IOException e) {
						System.err.println("IO error while sending ERROR packet");
						e.printStackTrace();
						System.exit(1);
					}
				}

				if(serverResponseData[1] == OP_ERROR){
					if(!quietMode){
						System.err.println("Error during file read:");
						printPacketInfo(receivePacket);
					}
					return null;
				}

				//if we did not get a DATA packet, keep receiving
				if (serverResponseData[1] != OP_DATA) {
					if(!quietMode)
						System.err.println("Error during file read: unexpected packet format.");
					keepReceiving = true;
				}
			} while (keepReceiving);
			//get block number
			blockNumber = extractBlockNumber(serverResponseData);

			//ensure packet comes from same TID
			if(receivePacket.getPort() != serverPort || receivePacket.getAddress() != serverAddress){
				System.err.println("unrecognized TID: " + receivePacket.getPort());
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
						outputStream.toByteArray().length, receivePacket.getAddress(), receivePacket.getPort());

				try {
					sendReceiveSocket.send(responseToUnexpectedTID);
					System.out.println("Sent message to:");
					printPacketInfo(responseToUnexpectedTID);
				} catch (IOException e) {
					System.err.println("Server error while sending unknown TID ERROR");
					e.printStackTrace();
					System.exit(1);
				}
			}else{
				//add response data to buffer (index 4 is the start of data in TFTP DATA packets)
				for(int i = 4; i < messageSize; i++)
					responseBuffer.add(serverResponseData[i]);

				//send acknowledgement to server (parameter passed is a conversion of int to byte[])
				acknowledge(intToByteArray(blockNumber));
			}

		} while(!isLastPacket(receivePacket) && numTimeouts < 3);

		if (numTimeouts >= 3 && !quietMode)
			System.err.println("Client timed out");

		/*get final byte array from response buffer*/
		ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
		for(Byte b: responseBuffer)
			byteOutputStream.write(b);

		return byteOutputStream.toByteArray();
	}

	/**
	 * Reads the contents of the file and stores it as an array of bytes.
	 *
	 * @param filename the name of the file to be read
	 * @return contents of the file read in
	 * @author Joe Frederick Samuel, Ryan Ribeiro, Luke Newton
	 */
	private byte[] readFile(String filename) {
		Path path = Paths.get(filename);
		try {
			return Files.readAllBytes(path);
		} catch (IOException e) {
			System.out.println("failed to read file at specified path");
			return null;
		} catch (SecurityException se) {
			System.out.println("Access violation while trying to read file from server.");
			return null;
		}
	}

	/**
	 * prints packet information
	 *
	 * @author Luke Newton, Cameron Rushton
	 * @param packet DatagramPacket
	 */
	private void printPacketInfo(DatagramPacket packet) {
		if(quietMode)
			return;

		//get meaningful portion of message
		byte[] dataAsByteArray = Arrays.copyOf(packet.getData(), packet.getLength());

		System.out.println("host: " + packet.getAddress() + ":" + packet.getPort());
		System.out.println("Message length: " + packet.getLength());
		System.out.print("Type: ");
		switch(dataAsByteArray[1]) {
		case OP_RRQ: System.out.println("RRQ"); break;
		case OP_WRQ: System.out.println("WRQ"); break;
		case OP_DATA: System.out.println("DATA"); break;
		case OP_ACK: System.out.println("ACK"); break;
		case OP_ERROR: System.out.println("ERROR"); break;
		}
		System.out.println("Containing: " + new String(dataAsByteArray));
		System.out.println("Contents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}

	/**
	 * prints a menu containing all valid client commands
	 *
	 * @author Luke Newton
	 */
	private void printHelpMenu() {
		System.out.println("type 'read' followed by a file name to begin a read request.");
		System.out.println("type 'write' followed by a file name to begin a write request.");
		System.out.println("type 'quiet' to set the client to quiet file transfer mode");
		System.out.println("type 'verbose' to set the client to verbose file transfer mode");
		System.out.println("type 'connect' to specify the address to send requests to");
		System.out.println("type 'quit' to shutdown the client.");
		System.out.println("type 'help' to display this message again, or 'help' followed by any of the above command words for further decription.\n");
	}
}
