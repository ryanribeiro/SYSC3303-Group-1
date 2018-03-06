package server;

import java.util.Scanner;

/**
 * This class runs in a seperate thread to check if the user 
 * wants to quit the server
 * (potential to expand to other commands)
 * 
 * @author Luke Newton
 */
public class ServerQuitRunnable implements Runnable {
	//the server running this thread
	Server server;

	public ServerQuitRunnable(Server server){
		this.server = server;
	}

	/**
	 * function to execute when thread created.
	 * waits for user input and checks if they want to quit.
	 * (potential to expand to other commands)
	 */
	@Override
	public void run() {
		Scanner s = new Scanner(System.in);

		while(true){
			String input = s.nextLine();

			//if we want to quit, inform server and stop getting user input
			if(input.equalsIgnoreCase("quit")){
				server.setQuitTime();
				s.close();
				break;
			}
		}
	}
}
