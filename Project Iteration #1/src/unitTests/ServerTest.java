package unitTests;
import static org.junit.Assert.*;

import org.junit.Test;

import server.Server;

import java.net.SocketException;
import java.util.Random;

/**
 * This class contains test cases for functionality only perfromed by
 * the server
 * 
 * It tests the creation of servers with specific port numbers
 * 
 * (we should have tests for message parsing but its set up weird, 
 * see github issue for details)
 * 
 * @author Luke Newton
 * 
 */
public class ServerTest {
	private Server testServer;
	private int portNumber;
	
	/**
	 * This test checks that when a server is created, it is created with
	 * the correct port number
	 */
	@Test
	public void testServerCreationCorrectPortNumber() {
		Random random = new Random();
		while(true){
			//generate a random port number above 5000 (should not be used)
			portNumber = 5000 + random.nextInt(150);
			
			try {
				//attempt to initialize server
				testServer = new Server(portNumber);
			} catch (SocketException e) {
				//server port number was already in use, try again
				continue;
			}
			//server socket successfully created, exit loop
			break;
		}
		int serverPortNumber = testServer.getPort();
		testServer.closeRecieveSocket();
		
		//check server created has the port number specified
		assertTrue(serverPortNumber == portNumber);
	}
}
