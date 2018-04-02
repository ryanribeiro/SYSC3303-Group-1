package server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;


/**
 * a class representing the server for the server-client-intermediate host system. has
 * Capability to receive requests, and create threads to process and send appropriate 
 * responses
 * 
 * @author Luke Newton, Kevin Sun, Joe Frederick Samuel, Ryan Ribeiro
 *
 */
public class Server {
	//the port the server is located on
	private static final int SERVER_PORT_NUMBER = 69;
	//change this to turn on/off timeouts for the server
	private static final boolean TIMEOUTS_ON = false;
	//Milliseconds until server times out while waiting for response
	private static final int TIMEOUT_MILLISECONDS = 5000;
	//max size for data in a DatagramPacket

	private static final int MAX_PACKET_SIZE = 516;

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
	
	/**
	 * Sends the receive socket to the thread to be reused.
	 * 
	 * @return receiveSocket
	 */
	public DatagramSocket getReceiveSocket() {
		return receiveSocket;
	}
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
	 * 
	 * @author Luke Newton
	 */
	public synchronized void setQuitTime() {
			this.quitPreperation = true;
			receiveSocket.close();	
	}

	/**
	 * create a new thread to deal with a specified message
	 * 
	 * @author Luke Newton
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
	 * 
	 * @author Luke Newton
	 */
	public void messageProcessed(){
		numberOfMessagesBeingProcessed--;
	}

	/**
	 *  returns the number of messages currently being processed
	 *  
	 *  @author Luke Newton
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
	
	/**
	 * pauses execution breiefly so output can be read as it is created
	 */
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
	 * @param args any arguements passed to Server main are not used
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