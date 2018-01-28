import java.io.*;
import java.net.*;

/**
 * The intermediate host in the UDP system. The host is the middle-software that receives and sends communication packets between the Server and the client.
 * @author Kevin Sun
 *
 */
public class UDPHost {

	public static void main(String[] args) throws Exception {

		//Create two threads for starting the server and the client. First starts the server, waits 1 second, then starts the client
		Runnable task1 = () -> { try {
			UDPServer.main(args);
		} catch (Exception e) {
			e.printStackTrace();
		} };

		// start the thread
		new Thread(task1).start();

		Thread.sleep(1000);

		Runnable task2 = () -> { try {
			running();
		} catch (Exception e) {
			e.printStackTrace();
		} };

		// start the thread
		new Thread(task2).start();

		Thread.sleep(1000);

		UDPClient.main(args);
	}

	public static void running() {
		System.out.println("Host running");

		int clientPort;
		DatagramSocket receiveSocket = null;

		//creates the receiveSocket in port 23
		try {
			receiveSocket = new DatagramSocket(23);
		} catch (SocketException e) {

			e.printStackTrace();
		}


		while (true)
		{
			try {
				byte[] receiveData = new byte[100];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				System.out.println("Host is waiting...");
				receiveSocket.receive(receivePacket);	


				//Begins interpretting and processing the received data, printing out the data
				String data = new String(receivePacket.getData().toString());
				System.out.println("Host - String received from client: " + data);
				System.out.println("Host - Bytes received client: " + receivePacket.getData());

				DatagramPacket sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), InetAddress.getLocalHost(), 69);
				System.out.println("Host - Info sending to server: " + sendPacket.getData());

				//save the port the client is hosting from
				clientPort = receivePacket.getPort();

				//sends the packet to port 69, where server will receive it
				receiveSocket.send(sendPacket);

				//blocks until receives a new packet
				receiveSocket.receive(receivePacket);
				System.out.println("Host - Bytes received from server:" + receivePacket.getData());

				DatagramSocket sendSocket = new DatagramSocket();

				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), InetAddress.getLocalHost(), clientPort);

				System.out.println("Host - Data being sent to client:" + sendPacket.getData());
				sendSocket.send(sendPacket);

				sendSocket.close();


			} catch (IOException e)
			{
				e.printStackTrace();  
				System.exit(1);
			}

		}
	}
}