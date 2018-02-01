package unitTests;
import static org.junit.Assert.*;

import org.junit.Test;

import errorSimulator.ErrorSimulator;

import java.net.SocketException;
import java.util.Random;

/**
 *This class contains test cases for functionality performed only by
 * the error simulator
 * 
 * It tests the creation of error simulators with specific port numbers
 * 
 * @author Luke Newton
 * 
 */
public class ErrorSimulatorTest {
	private ErrorSimulator testErrorSim;
	private int portNumber;
	
	/**
	 * This test checks that when a error simulator is created, it is created with
	 * the correct port number
	 */
	@Test
	public void testServerCreationCorrectPortNumber() {
		Random random = new Random();
		while(true){
			//generate a random port number above 5000 (should not be used)
			portNumber = 5000 + random.nextInt(150);
			
			try {
				//attempt to initialize error simulator
				testErrorSim = new ErrorSimulator(portNumber);
			} catch (SocketException e) {
				//error simulator port number was already in use, try again
				continue;
			}
			//error simulator socket successfully created, exit loop
			break;
		}
		int serverPortNumber = testErrorSim.getPort();
		testErrorSim.closeRecieveSocket();
		
		//check error simulator created has the port number specified
		assertTrue(serverPortNumber == portNumber);
	}
}
