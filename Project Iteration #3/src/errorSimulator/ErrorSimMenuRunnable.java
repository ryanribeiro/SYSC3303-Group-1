package errorSimulator;

import java.util.Scanner;

/**
 * user menu and functionality to introduce network errors (packet loss/duplication)
 * 
 * @author Luke Newton
 *
 */
public class ErrorSimMenuRunnable implements Runnable{
	//the error simulator this menu is for
	private ErrorSimulator errorSim;

	//TFTP op codes
	private static final byte OP_RRQ = 1;
	private static final byte OP_WRQ = 2;
	private static final byte OP_DATA = 3;
	private static final byte OP_ACK = 4;

	/**Constructor*/
	public ErrorSimMenuRunnable(ErrorSimulator errorSim){
		this.errorSim = errorSim;
	}

	/* main execution for error simulator commands menu
	 * @author Luke Newton
	 * 
	 */
	@Override
	public void run() {
		Scanner s = new Scanner(System.in);
		String[] input;
		int errorOpCode, errorBlockNumber = 0;

		printHelpMenu();

		do{
			System.out.print("command:");
			input = s.nextLine().split(" ");

			try{
				//check for valid command keyword
				if(input[0].equalsIgnoreCase("normal")){
					errorSim.setPacketDuplicate(false);
					errorSim.setPacketLose(false);
					System.out.println("System set to normal operations");
				}else if(input[0].equalsIgnoreCase("duplicate") || input[0].equalsIgnoreCase("lose")
						|| input[0].equalsIgnoreCase("delay")){
					//check if the specified packet type is valid (RRQ, WRQ, DATA, or ACK)
					if(input[1].equalsIgnoreCase("WRQ"))
						errorOpCode = OP_WRQ;
					else if(input[1].equalsIgnoreCase("RRQ"))
						errorOpCode = OP_RRQ;
					else if(input[1].equalsIgnoreCase("DATA"))
						errorOpCode = OP_DATA;
					else if(input[1].equalsIgnoreCase("ACK"))
						errorOpCode = OP_ACK;
					else
						throw new InvalidCommandException();

					//send error simulator the type of packet for the error
					errorSim.setErrorPacketType(errorOpCode);

					//check if the specified block number (DATA only) is valid
					if(errorOpCode == OP_DATA || errorOpCode == OP_ACK){
						if(Integer.parseInt(input[2]) > 0){
							errorBlockNumber = Integer.parseInt(input[2]);
						} else
							throw new InvalidCommandException();
					} else if(errorOpCode == OP_RRQ)
						errorBlockNumber = 1;
					//send error simulator the block number for the error
					errorSim.setErrorPacketBlockNumber(errorBlockNumber);

					//activate artificial error creation in error simulator
					if(input[0].equalsIgnoreCase("duplicate")){
						errorSim.setPacketDuplicate(true);
						System.out.println("System set to insert artificial duplicate packet error");
					} else if(input[0].equalsIgnoreCase("lose")){
						errorSim.setPacketLose(true);
						System.out.println("System set to insert artificial lost packet error");	
					} else if(input[0].equalsIgnoreCase("delay")){
						if(errorOpCode == OP_WRQ || errorOpCode == OP_RRQ)
							errorSim.setPacketDelay(true, Integer.parseInt(input[2]));
						else
							errorSim.setPacketDelay(true, Integer.parseInt(input[3]));
						
						System.out.println("System set to insert artificial packet delay");	
					}
				} else if(input[0].equalsIgnoreCase("help")){
					printHelpMenu();
				}else if(input[0].equalsIgnoreCase("quit")){
					break;
				} else{
					throw new InvalidCommandException();
				}
			} catch (Exception e){
				/*any type of exception that we can get here (IndexOutOfBoundsException,
				 * InvalidNumberFormatException, InvalidMessageFormatException) all 
				 * indicate incorrect command entered */
				System.out.println("invalid command");
			}
		}while(true);
		s.close();
		System.out.println("Error Simulator shutting down due to 'quit' command.");
		System.exit(0);
	}

	/**display message to console showing available commands
	 *  @author Luke Newton
	 */
	private void printHelpMenu(){
		System.out.println("\ntype 'normal' to have no artificial errors created (default)");
		System.out.println("type 'duplicate' followed by the type of packet to duplicate and packet number (if applicable) to insert a duplicate packet error");
		System.out.println("type 'lose' followed by the type of packet to lose and packet number (if applicable) to insert a packet loss error");
		System.out.println("type 'delay' followed by the type of packet to delay, pack number (if applicable), and milliseconds to delay for to insert a packet transfer delay");
		System.out.println("type 'quit' to close the error simulator (will not allow for any further file transfers to take place)");
		System.out.println("type 'help' to display this message again\n");
	}

}
