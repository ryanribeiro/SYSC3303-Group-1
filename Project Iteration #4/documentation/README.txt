----------------------
FILES IN THIS PROJECT
----------------------
Client.java: 
	represents the client in the system.
ErrorSimulator.java: 
	represents the intermediate host in the system.
ErrorSimMenuRunnable.java
	runs the main menu that takes input from the user.
ServerClientConnection.java
	represents a connection between a client and server for data transfer. spawned by error simulator.
InvalidCommandException.java
	menu invalid command exception
PacketDelayRunnable.java
	delays and then sends a packet while error sim thread still runs.
Server.java:
	represents the server in the system.
ServerSpawnThread.java
	thread spawned by server to process and respond to a received message.
ServerQuitRunnable.java
	thread spawned by server to allow user on server end to close server.
InvalidMessageFormatException.java
	represents an exception thrown when a ServerSpawnThread processes an invalid message
	
----------------------
TO RUN THE ASSIGNMENT
----------------------
The assignment is run by executing three programs: Client.java, ErrorSimulator.java, and Server.java.

Open the project in Eclipse and run each in the following order:
1. Server.java (in server package)
2. ErrorSimulator.java (in errorSimulator package)
3. Client.java (in client package)

**please ensure that your project folder has a folder called "SERVERDATA". This represents a seperate memeory space for the server while both the client and server are on the same machines**

You should see a startup message for each program when it runs. The client program will ask you if you would like to make a read request or a write request. To make a request, type <request> <file name> and hit enter. For example, to make a read request from the server, type "read test.txt". Once the transfer is complete, you may save the file under a new name. The client will then prompt you for another command. You may type "quit" to shut down the client and also type "quit" to shut down the server. The drop down next to the option "Display selected Console" in the top right corner of the console window will allow you to switch between the console outputs for the client, server, and ErrorSimulator. Additional consoles can be opened with the "Open Console" option in the top right corner of the  console by selecting "Open Console" -> "3 New Console View" to view multiple
outputs at once.

The error simulator will prompt you to enter a mode to simulate errors. You can choose to enter a mode before executing commands on the client or not entering a mode for normal operation. After a file operation you may change the mode to alter the behaviour of the next transfer. The menu has four options: normal operation, lose packet, delay packet and duplicate packet. To run normally without transfer errors, type "normal" in the error simulator when prompted for a command. To introduce errors, the following format is generally used: <command> <packet type> <packet number> <delay in milliseconds>. Since there is no delay in a lose packet request, if DATA packet 3 is to be lost, type "lose DATA 3" and hit enter. This can be done for WRQ, RRQ, ACK and DATA packet types. Delay and duplicate both have a delay parameter, so to delay the request, type "delay RRQ 2000". You can also type "delay ACK 1 5000" to delay an ACK or DATA packet since request packets don't have a number associated with them. Similarily, you can write "duplicate WRQ 1000" where the WRQ duplicate will be sent after waiting one second after the original WRQ has been sent.
Note: Client and server have a total timeout time >=15 seconds each (3 timeout/retransmits of 5 seconds each), so delaying a packet for longer than this will result in one or both dropping the transfer entirely.
If the last packet is to be destroyed, it will not be resent, but the recipient should timeout gracefully.

----------------------------------
EDITING PARAMETERS OF THE PROGRAMS
----------------------------------
At the top of Client.java, Server.java, and ErrorSimulator.java there are several constants defined which can be changed to alter the program.

1)Client.java
NUMBER_OF_CLIENT_MAIN_ITERATIONS:
	This integer value is the number of times the client will send out a request packet. Requests will always
	alternate between read and write, and the final request will always be invalid format.
INTERMEDIATE_HOST_PORT_NUMBER:
	This integer value is the port number that the ErrorSimulator will be found at.
	NOTE: if this value is changed in the client, it must be changed to the same value in the intermediate host(ErrorSimulator) as well!
MAX_PACKET_SIZE:
	This integer value specifies the maximum size of the data portion of a DatagramPacket.
FILENAME:
	This string value is the file name that is sent in each request.
MODE
	This string value is the mode that is sent in each request.
TIMEOUTS_ON:
	This boolean value specifies whether or not timeouts for receives are active for the client. Originally timeouts
	be on; set this value to false to deactivate them.
TIMEOUT_MILLISECONDS:
	This integer value sets the timeout length in milliseconds.
OP_RRQ: The TFTP OP Code for read request
OP_WRQ: The TFTP OP Code for write request
OP_DATAPACKET: The TFTP OP Code for data packet transmission
OP_ACK: The TFTP OP Code for acknowledging reception of packet.
OP_ERROR: The TFTP OP Code for notifying of failed packet transmission error.
	
2)ErrorSimulator.java
SERVER_PORT_NUMBER:
	This integer value is the port number that the server will be found at.
	NOTE: if this value is changed in the ErrorSimulator, it must be changed to the same value in the server
		  as well!
INTERMEDIATE_HOST_PORT_NUMBER:
	This integer value is the port number that the ErrorSimulator will be found at.
	NOTE: if this value is changed in the ErrorSimulator, it must be changed to the same value in the client
		  as well!
MAX_PACKET_SIZE:
	This integer value specifies the maximum size of the data portion of a DatagramPacket.
TIMEOUTS_ON:
	This boolean value specifies whether or not timeouts for receives are active for the client. Originally timeouts
	be on; set this value to false to deactivate them.
TIMEOUT_MILLISECONDS:
	This integer value sets the timeout length in milliseconds.
	
3)Server.java
SERVER_PORT_NUMBER:
	This integer value is the port number that the server will be found at.
	NOTE: if this value is changed in the server, it must be changed to the same value in the intermediate
		  host as well!
	NOTE: This value was specified to be 69 in the assignment
TIMEOUTS_ON:
	This boolean value specifies whether or not timeouts for receives are active for the client. Originally timeouts
	be on; set this value to false to deactivate them.
TIMEOUT_MILLISECONDS:
	This integer value sets the timeout length in milliseconds.
MAX_PACKET_SIZE:
	This integer value specifies the maximum size of the data portion of a DatagramPacket.

-----------------------
Testing (Normal/Errors)
-----------------------
Testing
1) Read Request
A read request can be tested by typing the command 'read' and then typing in the name of a file that exists in the project's main folder. Some of our used files include 'sons_of_martha.txt', 'test.txt', and 'two_cities.txt'.

2) Write Request
A write request can be tested by typing the command 'write' and then typing in the name of a file that exists in the project's main folder. Some of our used files include 'sons_of_martha.txt', 'test.txt', and 'two_cities.txt', and the files created by the write request can be found in the SERVERDATA folder.

Errors
1) FileNotFound
Simulated by trying to read/write a file that does not exist in the folder.

2) AccessViolation
Simulated by changing the premissions on the SERVERDATA folder, and then attempting to write to it. Probably not possible to recreate at school, since the computers there do not have ADMIN premissions.

3) DiskFull
Simulated using a small USB, filling it up with junk files, and then attempting to save to it.

4) FileAlreadyExists
Simulated by trying to write to the server with a file name that already exists.

5) Handling incoming packets with unexpected formats
Simulated using invalid command in the Error Simulator.

6) Unkown transfer ID
Implemented but not tested thoroughly using the Error Simulator.


--------------------------
Responsibilities breakdown
--------------------------
*It was found that fixing one error also fixed the other errors, so it worked out better to have one person do the writing for errors handling.

Cameron:
- Error handling code, packet unexpected formats.

Kevin:
- Timing diagrams
- Code review/suggestions with focus on duplicate packets

Luke:
- Error simulator rewrite to create errors
- Code review/suggestions

Ryan:
- Code review/Suggestions with focus on duplicate packets, incorrect TIDs, unexpected formats.
- Bug fixing

Joe:
- Code review/suggestions with focus on lost and delayed packets, error sim menu.
- Bug fixing
	
---------------
DESIGN CHOICES
---------------
While all three of the client, server, and ErrorSimulator have many of the same functionality (send, receive, many identical constants), the decision was made to not use a superclass/interface to reduce this repetition. 

This is because these are three separate programs which theoretically should be able to run on different computers with little additional work required. If this assignment were one single program, a superclass or interface would certainly have been used here.

ADDITIONAL DOCUMENTATION CAN BE SEEN IN THE 'doc' FOLDER IN THE JAR FILE
