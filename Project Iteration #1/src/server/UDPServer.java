import java.io.*;
import java.net.*;
import java.util.ArrayList;

/**
 * The server in the UDP system. Will wait until it receives a packet, then checks if the contents are valid. If it is valid, send a response packet containing some data. If the packet
 * is invalid, throw an exception and shut down the system.
 * @author Kevin Sun
 *
 */
class UDPServer
{
	public static void main(String args[]) throws Exception
	{
		//creates the receive socket in port 69
		System.out.println("Server running");
		DatagramSocket receiveSocket = null;
		try {
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException e) {

			e.printStackTrace();
		}

		//create boolean to determine if the packet is a read, write, or invalid packet
		boolean read = false, write = false, invalid = false;
		byte[] receiveData = new byte[100];
		byte[] sendData = new byte[100];
		byte[] response = new byte[100];

		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

		//forever running this loop until system closes from invalid packet
		while(true)
		{
			try {
				//blocks until receiving a packet, then prints out the data
				receiveSocket.receive(receivePacket);
				String received = new String(receivePacket.getData());
				System.out.println("Server - Received string from host: " + received);
				System.out.println("Server - Received bytes from host" + receivePacket.getData());
			} catch (IOException e)
			{
				e.printStackTrace();  
				System.exit(1);
			}		

			//decode and parse the packet to confirm its contents

			byte[] data = receivePacket.getData();
			Byte[] bytes = new Byte[data.length];

			//copies the data from the packet to a new array of Byte objects
			for (int i = 0; i < data.length; i++)
			{
				bytes[i] = data[i];
			}

			//check if the first character and last character is a 0 byte, if not then packet is invalid
			if(bytes[0] != (byte) 0 || bytes[bytes.length - 1] != (byte) 0) {
				invalid = true;
			}

			//checks if the second character is either a 1 or 2 byte, if both are false then the packet is invalid
			if ((bytes[1] != (byte) 1) && (bytes[1] != (byte) 2))
				invalid = true;
				

			//if the second character is a 1 byte, it is a read request, if it is a 2 byte then it is a write request
			if (bytes[1] == 1)
				read = true;

			else if (bytes[1] == 2)
				write = true;

			/*get the length and index of the filename by iterating through the array. When it finds a 0 byte, it has reached the end of the filename. Starts at index 2 because the first 
			 * two indexes are held by two bytes. Add the filename to a list while iterating
			*/
			int lengthOfFileName = -1;
			ArrayList<Byte> name = new ArrayList<Byte>();
			for(int i = 2; i < bytes.length; i++) {
				if(bytes[i] == (byte)0) {
					lengthOfFileName = i; //9
					break;
				} else {
					name.add(bytes[i]);
				}
			}
			
			//if there is no name in the packet, the packet is invalid
			if (name.size() == 0)
			{
				invalid = true;
			}
			//converts the filename from a list to an array
			byte[] stringFile = new byte[lengthOfFileName - 2]; 
		    for (int i = 0; i < name.size(); i++) {
		    	stringFile[i] = name.get(i);
		    }
			
		    //if the byte after the filename isn't a 0, the packet is invalid
			if (bytes[lengthOfFileName] != 0)
			{
				invalid = true;
			}
					
			int modeLength = 0;
			name = new ArrayList<Byte>();			

			/*get the length and index of the mode by iterating through the array. When it finds a 0 byte, it has reached the end of the mode. Starts at index of filename + 1 because 
			 * everything before is irrelevant. Add the mode name to a list while iterating
			*/
			for(int j = lengthOfFileName + 1; j < bytes.length; j++) {
				if(bytes[j] == (byte) 0) //reached end of mode, encountered the next zero
				{
					modeLength = j;
					break;
				} else {
					name.add(bytes[j]);
				}
			}
		
			stringFile = new byte[modeLength - lengthOfFileName];

			//converts the mode from a list to an array
			for (int k = 0; k < name.size(); k++) {
				stringFile[k] = name.get(k);
			}

			String modeString = new String(stringFile);
			modeString = modeString.toLowerCase();

			//if the mode text is not netascii or octet, packet is invalid
			if (modeString.equals("netascii") || modeString.equals("octet"))
			{
				invalid = true;
			}


			//after checking through the entire packet data, if invalid was set to true then the packet has been deemed invalid
			if (invalid == true)

			{
				receiveSocket.close();
				throw new Exception();
			}
			else
			{
				//if read is true, the packet is a read request
				if (read == true)
				{
					response = new byte[] {0, 3, 0, 1};
				}

				//if write is true, the packet is a write request
				else if (write == true)
				{
					response = new byte[] {0, 4, 0, 0};
				}

				//create the packet to be sent and send it to the port the receive packet came from
				int port = receivePacket.getPort();
				InetAddress IPAddress = receivePacket.getAddress();

				DatagramSocket sendSocket = new DatagramSocket();
				sendData = response;

				System.out.println("Server - Response packet info: " + response);
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
				sendSocket.send(sendPacket);

				//reset read and false booleans
				read = false;
				write = false;

				sendSocket.close();
			}

		}
	}
}
