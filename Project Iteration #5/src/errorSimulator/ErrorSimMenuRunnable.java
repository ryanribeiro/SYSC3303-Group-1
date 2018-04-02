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

	/**Constructor
	 * 
	 * @param errorSim the error simulator that this menu is for
	*/
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
			System.out.print("command:");
			input = s.nextLine().split(" ");

			try {
				//check for valid command keyword
				if(input[0].equalsIgnoreCase("normal")){
					errorSim.setPacketDuplicate(false, 0);
					errorSim.setPacketLose(false);
					errorSim.setPacketDelay(false, 0);
					errorSim.setInvalidTID(false);
					errorSim.setInvalidOpcode(false);
					errorSim.setInvalidMode(false);
					errorSim.setInvalidFilename(false);
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

						if(errorOpCode == OP_WRQ || errorOpCode == OP_RRQ)
							errorSim.setPacketDuplicate(true, Integer.parseInt(input[2]));
						else
							errorSim.setPacketDuplicate(true, Integer.parseInt(input[3]));
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
				} else if(input[0].equalsIgnoreCase("invalid")){
					//create artificial invalid information error
					if(input[1].equalsIgnoreCase("filename") || input[1].equalsIgnoreCase("mode")){
						//invalid filename or mode can only be for RRQ or WRQ
						if(input[2].equalsIgnoreCase("WRQ"))
							errorOpCode = OP_WRQ;
						else if(input[2].equalsIgnoreCase("RRQ"))
							errorOpCode = OP_RRQ;
						else
							throw new InvalidCommandException();

						//set error Sim to create specified command
						if(input[1].equalsIgnoreCase("filename")){
							errorSim.setInvalidFilename(true);
							System.out.println("System set to invalidate filename");
						}else{
							errorSim.setInvalidMode(true);
							System.out.println("System set to invalidate mode");
						}
					} else if(input[1].equalsIgnoreCase("opcode")){
						if(input[2].equalsIgnoreCase("WRQ"))
							errorOpCode = OP_WRQ;
						else if(input[2].equalsIgnoreCase("RRQ"))
							errorOpCode = OP_RRQ;
						else if(input[2].equalsIgnoreCase("DATA"))
							errorOpCode = OP_DATA;
						else if(input[2].equalsIgnoreCase("ACK"))
							errorOpCode = OP_ACK;
						else
							throw new InvalidCommandException();

						//set error Sim to create specified command
						errorSim.setInvalidOpcode(true);
						System.out.println("System set to invalidate opcode");
					}else if(input[1].equalsIgnoreCase("TID")){
						if(input[2].equalsIgnoreCase("DATA"))
							errorOpCode = OP_DATA;
						else if(input[2].equalsIgnoreCase("ACK"))
							errorOpCode = OP_ACK;
						else
							throw new InvalidCommandException();
						
						errorSim.setInvalidTID(true);
						System.out.println("System set to invalidate TID");
					} else 
						throw new InvalidCommandException();

					//check if the specified block number (for DATA or ACK only) is valid
					if(errorOpCode == OP_DATA || errorOpCode == OP_ACK){
						if(Integer.parseInt(input[3]) > 0){
							errorBlockNumber = Integer.parseInt(input[3]);
						} else
							throw new InvalidCommandException();
					} else if(errorOpCode == OP_RRQ)
						errorBlockNumber = 1;
					//send error simulator the block number for the error
					errorSim.setErrorPacketType(errorOpCode);
					errorSim.setErrorPacketBlockNumber(errorBlockNumber);

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
						System.out.println("\nFormat for RRQ/WRQ: invalid <type of invalidation> <packet type> <block number>\n"
								+ "The command 'invalid' will cause the error simulator to invalidate the specified information on a specified packet, resulting in an ERROR code 4 or 5\n"
								+ "Valid types of invalidation are: mode, filename, opcode TID\n"
								+ "'mode' and 'filename' can only be invalidated on WRQ and RRQ packets as they are the only packets containing this information\n"
								+ "'TID' can only be invalidated on DATA and ACK packets as TID cannot be altered on the first packet sent"
								+ "If invalidating the opcode or TID on an ACK or DATA packet, the block number must be specified\n"
								+ "ex. 'invalid mode WRQ' will invalidate the mode on a qrite request, causing the server to send an ERROR 4 packet\n"
								+ "ex2. 'invalid TID DATA 4' will alter the TID of DATA packet 4, resulting in a ERROR 5 packet sent to that new TID\n");
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
				System.out.println("command not recognized, type help for available command list");
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
		System.out.println("type 'duplicate' followed by the type of packet to duplicate, packet number (if applicable) and time in milliseconds between sending duplicate to insert a duplicate packet error");
		System.out.println("type 'lose' followed by the type of packet to lose and packet number (if applicable) to insert a packet loss error");
		System.out.println("type 'delay' followed by the type of packet to delay, packet number (if applicable), and milliseconds to delay for to insert a packet transfer delay");
		System.out.println("type 'invalid' followed by the type of information to invalidate, type of packet to invalidate info on, and packet number (if applicable) to simulate invalid packets sent");
		System.out.println("type 'quit' to close the error simulator (will not allow for any further file transfers to take place)");
		System.out.println("type 'help' to display this message again\n");
	}

}
