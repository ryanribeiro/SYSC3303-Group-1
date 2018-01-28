import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The client in the UDP system. Client will send 11 requests to the intermediate host, 5 are read and 5 are write requests. The 11th final request is a purposely invalid request. After
 * each request, client will wait to receive a response back, print out the data, and then send the next request.
 * @author Kevin Sun
 *
 */
class UDPClient
{
	public static void main(String args[]) throws Exception
	{
		DatagramSocket clientSocket = null;
		String filename;
		String mode;

		List<Byte> message = new ArrayList<Byte>();
		byte[] sendData = new byte[100];
		byte[] receiveData = new byte[100];
		byte[] filenameBytes;
		byte[] modeBytes = new byte[1];
		System.out.println("Client running");
		
		//create the datagram socket for receiving and sending requests
		try {
			clientSocket = new DatagramSocket();

			
		}	catch (SocketException se)
		{  
			se.printStackTrace();  
			System.exit(1);
		}

		
		byte byteZero = (byte) 0;
		byte byteOne = (byte) 1;
		byte byteTwo = (byte) 2;
		
		//set the mode to netascii
		mode = "netascii";
		modeBytes = mode.getBytes();
		
		//create 11 requests
		for (int i = 0; i < 11; i++)
		{
			//clear the message List after each request. The data is being formed by adding it to the list
			message.clear();
			filename = "test.txt";
			
			//the final request is an invalid request by making the first character a two byte
			if (i == 10)
			{
				message.add(byteTwo);
			}
			
			filenameBytes = filename.getBytes();
			
			//switching between read and write requests each time
			if (i%2 == 0)
			{
				
				//read requests start with a 0 then a 1 byte
				message.add(byteZero);
				message.add(byteOne);

				//add the filename to the list
				for (byte b: filenameBytes){
					message.add(b);
				}

				message.add(byteZero);

				//add the mode to the list
				for (byte b:modeBytes) {
					message.add(b);
				}

				message.add(byteZero);
			}

			else
			{

				//write requests start with a 0 then a 2 byte
				message.add(byteZero);
				message.add(byteTwo);
				

				for (byte b: filenameBytes){
					message.add(b);
				}

				message.add(byteZero);

				for (byte b:modeBytes) {
					message.add(b);
				}

				message.add(byteZero);

			}
			
			//iterate through the message List and add it to the sendData byte array
			int count = 0;
			for (Byte b: message)
			{
				sendData[count] = b.byteValue();
				count++;
			}
			
			//creating the send packet to be sent to port 23, where the intermediate host will receive it
			InetAddress IPAddress = InetAddress.getLocalHost();
			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 23);

			System.out.println("Client - String in packet being sent: " + sendPacket.getData());
			System.out.println("Client - Bytes in packet being sent: " + sendData);

			int packetNum = i+1;
			
			//print out what packet number is being sent
			System.out.println();
			System.out.println("Sending packet #" + packetNum);
			clientSocket.send(sendPacket);
			
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

			//waiting to receive a packet back from the host, then send the next packet
			try {
				System.out.println("Client waiting...");
				clientSocket.receive(receivePacket);
				
				String data = new String(receivePacket.getData());
				System.out.println("Client - Info received from host:" + data);
				System.out.println("Client - Bytes received from host:" + receivePacket.getData());
				
			

			} catch (IOException e)
			{
				e.printStackTrace();  
				System.exit(1);
			}
		}

		clientSocket.close();
		
	}
}