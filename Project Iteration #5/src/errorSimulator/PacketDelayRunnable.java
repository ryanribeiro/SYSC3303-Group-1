/**
 * 
 */
package errorSimulator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * used to artificailly send a message after a specified time
 * 
 * @author Luke Newton
 *
 */
public class PacketDelayRunnable implements Runnable {
	DatagramPacket message;
	int delayTime;
	private DatagramSocket sendSocket;
	
	public PacketDelayRunnable(DatagramPacket message, DatagramSocket sendRecieveSocket, int milliseconds){
		this.message = message;
		this.delayTime = milliseconds;
		this.sendSocket = sendRecieveSocket;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			Thread.sleep(delayTime);
		} catch (InterruptedException e) {
			System.out.println("packet delay interrupted");
			e.printStackTrace();
		}
		
		try {
			sendSocket.send(message);
		} catch (IOException e) {
			System.out.println("I/O exception occured while sending delayed packet");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
