/**
 * 
 */
package errorSimulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

/**
 * used to artificailly created a unknown TID error (ERROR code 5) by sending message through a new socket
 * 
 * @author Luke Newton
 *
 */
public class InvalidTIDRunnable implements Runnable {
	DatagramPacket message;
	private DatagramSocket sendSocket;
	private static final int MAX_PACKET_SIZE = 516;
	
	/**
	 * Construcor
	 * @param message the message to send though with altered TID
	 */
	public InvalidTIDRunnable(DatagramPacket message){
		this.message = message;
		try {
			sendSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.print("failed to create new socket for invalid TID");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {	
		//send message through new TID
		try {
			sendSocket.send(message);
		} catch (IOException e) {
			System.out.println("I/O exception occured while sending delayed packet");
			e.printStackTrace();
			System.exit(1);
		}
		//wait to receive response and print information
		printPacketInfo(waitReceiveMessage());
		//close new created socket
		sendSocket.close();
	}
	
	/**
	 * error simulator waits until it receives a message, which is stored in receivePacket and returned
	 * 
	 * @author Luke Newton
	 * @return returns the receive datagram packet
	 */
	private DatagramPacket waitReceiveMessage() {
		message = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
		try {
			sendSocket.receive(message);
		} catch (IOException e) {
			System.err.println("IOException: I/O error occured while error simulator waiting for response");
			e.printStackTrace();
			System.exit(1);
		}
		return message;
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

		//System.out.println("host: " + packet.getAddress() + ":" + packet.getPort());
		//System.out.println("Message length: " + packet.getLength());
		System.out.print("Type: ");
		switch(dataAsByteArray[1]) {
			case 1: System.out.println("RRQ"); break;
			case 2: System.out.println("WRQ"); break;
			case 3: System.out.println("DATA"); break;
			case 4: System.out.println("ACK"); break;
			case 5: System.out.println("ERROR"); break;
		}
		System.out.println("Number " + (int)dataAsByteArray[3]);
		//System.out.println("Containing: " + new String(dataAsByteArray));
		//System.out.println("Contents as raw data: " + Arrays.toString(dataAsByteArray) + "\n");
	}
}
