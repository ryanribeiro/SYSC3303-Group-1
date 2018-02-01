package unitTests;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.net.SocketException;

import org.junit.Before;
import org.junit.Test;

import client.Client;
import client.RequestType;

/**
 * This class contains test cases for functionality perfromed only by
 * the client
 * 
 * It tests the creation of message data for correctness
 * 
 * @author Luke Newton
 *
 */
public class ClientTest {
	Client testClient;
	byte[] packetData;
	
	
	@Before
	public void setUp(){
		try {
			testClient = new Client();
		} catch (SocketException e) {
			System.out.println("failed to create client socket");
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	/**
	 * This tests creates a read request and confirms it contains the correct information
	 */
	@Test
	public void testReadRequestCreation() {
		packetData = Client.createPacketData("filename", "mode", RequestType.read);
		
		//second byte of a read request message should be a 1 byte
		assertTrue(packetData[1] == (byte) 1);
		
	}
	
	/**
	 * This tests creates a write request and confirms it contains the correct information
	 */
	@Test
	public void testWriteRequestCreation() {
		packetData = Client.createPacketData("filename", "mode", RequestType.write);
		
		//second byte of a write request message should be a 2 byte
		assertTrue(packetData[1] == (byte) 2);
	}

	/**
	 * This tests creates a request and confirms it contains the correct filename
	 */
	@Test
	public void testRequestCreationCorrectFilename() {
		String filename = "filename.txt";
		
		packetData = Client.createPacketData(filename, "mode", RequestType.write);
		
		/*get filename back out of message data at expected place*/
		ByteArrayOutputStream textStream = new ByteArrayOutputStream();
		for(int currentIndex = 2; packetData[currentIndex] != 0; currentIndex++){
			textStream.write(packetData[currentIndex]);
		}
		
		//second byte of a write request message should be a 2 byte
		assertTrue(filename.equals(textStream.toString()));
	}
	
	/**
	 * This tests creates a request and confirms it contains the correct filename
	 */
	@Test
	public void testRequestCreationCorrectMode() {
		String mode = "octet";
		
		packetData = Client.createPacketData("filename", mode, RequestType.write);
		
		/*get filename back out of message data at expected place*/
		ByteArrayOutputStream textStream = new ByteArrayOutputStream();
		int currentIndex = 2;
		for(; packetData[currentIndex] != 0; currentIndex++){}
		currentIndex++;
		for(; packetData[currentIndex] != 0; currentIndex++){
			textStream.write(packetData[currentIndex]);
		}
		
		//second byte of a write request message should be a 2 byte
		assertTrue(mode.equals(textStream.toString()));
	}
}
