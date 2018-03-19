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

		do {
			System.out.print("Command:");
			input = s.nextLine().split(" ");

			try {
				//check for valid command keyword
				if(input[0].equalsIgnoreCase("normal")){
					errorSim.setPacketDuplicate(false, 0);
					errorSim.setPacketLose(false);
					errorSim.setPacketDelay(false, 0);
					System.out.println("System set to normal operations");
				}else if(input[0].equalsIgnoreCase("duplicate") || input[0].equalsIgnoreCase("lose")
						|| input[0].equalsIgnoreCase("delay")) {

					errorOpCode = checkPacketType(input, 1);

					//send error simulator the type of packet for the error
					errorSim.setErrorPacketType(errorOpCode);

					//check if the specified block number (DATA only) is valid
					if (errorOpCode == OP_DATA || errorOpCode == OP_ACK) {
						if (Integer.parseInt(input[2]) > 0) {
							errorBlockNumber = Integer.parseInt(input[2]);
						} else
							throw new InvalidCommandException();
					} else if (errorOpCode == OP_RRQ)
						errorBlockNumber = 1;
					//send error simulator the block number for the error
					errorSim.setErrorPacketBlockNumber(errorBlockNumber);

					//activate artificial error creation in error simulator
					if (input[0].equalsIgnoreCase("duplicate")) {
						if (errorOpCode == OP_WRQ || errorOpCode == OP_RRQ)
							errorSim.setPacketDuplicate(true, Integer.parseInt(input[2]));
						else
							errorSim.setPacketDuplicate(true, Integer.parseInt(input[3]));
						System.out.println("System set to insert artificial duplicate packet error");
					} else if (input[0].equalsIgnoreCase("lose")) {
						errorSim.setPacketLose(true);
						System.out.println("System set to insert artificial lost packet error");
					} else if (input[0].equalsIgnoreCase("delay")) {
						if (errorOpCode == OP_WRQ || errorOpCode == OP_RRQ)
							errorSim.setPacketDelay(true, Integer.parseInt(input[2]));
						else
							errorSim.setPacketDelay(true, Integer.parseInt(input[3]));

						System.out.println("System set to insert artificial packet delay");
					}
				} else if(input[0].equalsIgnoreCase("invalid")) { //Invalid packet formats
					// Format for RRQ/WRQ: invalid opcode <packet type> or invalid mode <packet type>
					// Format for DATA/ACK: invalid <opcode/block> <packet type> <block number> or invalid data <block number>

					if (input[1].equalsIgnoreCase("opcode")) {
						errorSim.setInvalidOpcode(true);
						setPacketType(input[2]);

						if (input.length == 4) {
							errorSim.setErrorPacketBlockNumber(Integer.parseInt(input[3]));
						} else {
							errorSim.setErrorPacketBlockNumber(1); //default set to 1
						}
						System.out.println("Set to alter opcode");
					} else if (input[1].equalsIgnoreCase("mode") && input.length == 3) {
						errorSim.setInvalidMode(true);
						setPacketType(input[2]);
						System.out.println("Set to alter mode");
					} else if (input[1].equalsIgnoreCase("block") && input.length == 4) { //also need to know which block number to invalidate
						errorSim.setInvalidBlock(true);
						setPacketType(input[2]);
						errorSim.setErrorPacketBlockNumber(Integer.parseInt(input[3]));
						System.out.println("Set to alter block number");
					} else if (input[1].equalsIgnoreCase("data") && input.length == 3) { //examples of invalid data include packet size too large, illegal characters
						errorSim.setInvalidData(true);
						errorSim.setErrorPacketBlockNumber(Integer.parseInt(input[2]));
						System.out.println("Set to alter data");
					} else {
						throw new InvalidCommandException();
					}

				} else if(input[0].equalsIgnoreCase("help")){
					if(input.length == 1)
						printHelpMenu();
					else if(input[1].equalsIgnoreCase("normal")){
						System.out.println("\nFormat: normal\n"
								+ "The command 'normal' sets the error simulator to it's default state, in which no artificial errors are produced.\n");
					}else if(input[1].equalsIgnoreCase("duplicate")){
						System.out.println("\nFormat for RRQ/WRQ: duplicate <packet type> <milliseconds until duplicate sent>\n"
								+ "Format for DATA/ACK: duplicate <packet type> <block number> <milliseconds until duplicate sent>\n"
								+ "The command 'duplicate' will cause the error simulator to send a duplicate packet after a specified number of milliseconds.\n"
								+ "The user specifies what type of packet they want to duplicate (RRQ, WRQ, DATA, or ACK),\n"
								+ "and in the case of DATA or ACK, will specify which block number packet will be duplicated.\n"
								+ "ex. 'duplicate rrq 500' will send a duplicate read request packet to the server after 500 milliseconds (half a second)\n"
								+ "ex2. 'duplicate data 15 1500' will send a duplicate of data block 15 after 1500 milliseconds\n");
					}else if(input[1].equalsIgnoreCase("lose")){
						System.out.println("\nFormat for RRQ/WRQ: lose <packet type>\n"
								+ "Format for DATA/ACK: lose <packet type> <block number>\n"
								+ "The command 'lose' will cause the error simulator to drop the specified packet.\n"
								+ "The user specifies what type of packet they want to drop (RRQ, WRQ, DATA, or ACK),\n"
								+ "and in the case of DATA or ACK, will specify which block number packet will be lost.\n"
								+ "ex. 'lose wrq' will drop the first write request sent by a client\n"
								+ "ex2. 'lose data 10' will drop the first data block 10 sent\n");
					}else if(input[1].equalsIgnoreCase("delay")){
						System.out.println("\nFormat for RRQ/WRQ: delay <packet type> <milliseconds packet delayed for>\n"
								+ "Format for DATA/ACK: delay <packet type> <block number> <milliseconds packet delayed for>\n"
								+ "The command 'delay' will cause the error simulator to delay a packet for a specified number of milliseconds.\n"
								+ "The user specifies what type of packet they want to delay (RRQ, WRQ, DATA, or ACK),\n"
								+ "and in the case of DATA or ACK, will specify which block number packet will be delayed.\n"
								+ "ex. 'delay rrq 1000' will delay a read request packet to the server for 1000 milliseconds (one second)\n"
								+ "ex2. 'delay ack 3 200' will delay the acknowledge for block 3 by 200 milliseconds\n");
					}else if(input[1].equalsIgnoreCase("quit")){
						System.out.println("\nFormat: quit\n"
								+ "The command 'quit' will close the error simulator program.\n"
								+ "Once terminated, files will not be able to be transferred between any running clients and the server until a new error simulator is run.\n"
								+ "A message will be displayed indicating the the error simulator program has been terminated.\n");
					}else if(input[1].equalsIgnoreCase("invalid")) {
						System.out.println("\nFormat for RRQ/WRQ: invalid opcode <packet type> or invalid mode <packet type>\n"
								+ "Format for DATA/ACK: invalid <opcode/block> <packet type> <block number> or invalid data <block number> \n"
								+ "'invalid opcode WRQ' will invalidate the opcode on the WRQ sent through the error simulator\n"
								+ "'invalid mode RRQ' will invalidate the mode of the RRQ packet\n"
								+ "'invalid block ACK 3' will invalidate the block number of ACK packet 3 transferred through the error simulator\n"
								+ "'invalid data 3' will invalidate the data in data block number 3 sent through the error simulator.'");
					}
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

	/**
	 * Check if the specified packet type is valid (RRQ, WRQ, DATA, or ACK)
	 * @author Luke Newton, CRushton
	 * @param input a slit array of strings representing user input
	 * @param index the index of packet type data in the input array
	 * @return opcode of packet type as an integer
	 * @throws InvalidCommandException Invalid command thrown - user must have given a packet type that doesn't exist
	 */
	private int checkPacketType(String[] input, int index) throws InvalidCommandException {

		if (index >= input.length || index < 0) { //invalid index
			return -1;
		}

		if (input[index].equalsIgnoreCase("WRQ"))
			return OP_WRQ;
		else if (input[index].equalsIgnoreCase("RRQ"))
			return OP_RRQ;
		else if (input[index].equalsIgnoreCase("DATA"))
			return OP_DATA;
		else if (input[index].equalsIgnoreCase("ACK"))
			return OP_ACK;
		else
			throw new InvalidCommandException();
	}

	/**
	 * @author CRushton
	 * Sets error packet type in error simulator
	 * @param input the string indicating the packet type input by the user
	 */
	private void setPacketType(String input) {
		switch(input.toUpperCase()){
			case "RRQ": errorSim.setErrorPacketType(OP_RRQ); break;
			case "WRQ": errorSim.setErrorPacketType(OP_WRQ); break;
			case "DATA": errorSim.setErrorPacketType(OP_DATA); break;
			case "ACK": errorSim.setErrorPacketType(OP_ACK); break;
		}
	}
	/**display message to console showing available commands
	 *  @author Luke Newton
	 */
	private void printHelpMenu(){
		System.out.println("\ntype 'normal' to have no artificial errors created (default)");
		System.out.println("type 'duplicate' followed by the type of packet to duplicate, packet number (if applicable) and time in milliseconds between sending duplicate to insert a duplicate packet error");
		System.out.println("type 'lose' followed by the type of packet to lose and packet number (if applicable) to insert a packet loss error");
		System.out.println("type 'delay' followed by the type of packet to delay, pack number (if applicable), and milliseconds to delay for to insert a packet transfer delay");
		System.out.println("type 'invalid' followed by the invalidated packet region (opcode, mode, block, data) and packet type or block number");
		System.out.println("type 'quit' to close the error simulator (will not allow for any further file transfers to take place)");
		System.out.println("type 'help' to display this message again, or 'help' followed by any of the above command words for further description.\n");
	}

}
