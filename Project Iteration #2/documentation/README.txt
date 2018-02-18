----------------------
FILES IN THIS PROJECT
----------------------
Client.java: 
	represents the client in the system.
ErrorSimulator.java: 
	represents the intermediate host in the system.
ServerClientConnection.java
	represents a connection between a client and server for data transfer. spawned by error simulator.
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
NOTE: By default, each program will timeout after 5 seconds of waiting for a message, so programs must
be opened in rapid succession (or see section below for how to disable timeouts)

**please ensure that your project folder has a folder called "SERVERDATA". This represents a seperate memeory space for the server while
both the client and server are on the same machines**

You should see a startup message for each program when it runs. The client program will ask you if you would like to make a read request or a write request. To make a request, type <request> <file name> and hit enter. For example, to make a read request from the server, type "read test.txt". Once the transfer is complete, you may save the file under a new name. The client will then prompt you for another command. You may type "quit" to shut down the client and also type "quit" to shut down the server. The drop down next to the option "Display selected Console" in the top right corner of the console window will allow you to switch between the console outputs for the client, server, and ErrorSimulator. Additional consoles can be opened with the "Open Console" option in the top right corner of the  console by selecting "Open Console" -> "3 New Console View" to view multiple
outputs at once.

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

--------------------------
Responsibilities breakdown
--------------------------
Cameron:
- Reviewing/merging 
- Disk full error

Kevin:
- File not found error

Luke:
- Fixing errors from iteration 1
- Timing diagrams

Ryan:
- Access violation error

Joe:
- File already exists error
- UML diagrams and javadoc
	
---------------
DESIGN CHOICES
---------------
While all three of the client, server, and ErrorSimulator have many of the same functionality (send, receive, many identical constants), the decision was made to not use a superclass/interface to reduce this repetition. 

This is because these are three separate programs which theoretically should be able to run on different computers with little additional work required. If this assignment were one single program, a superclass or interface would certainly have been used here.

ADDITIONAL DOCUMENTATION CAN BE SEEN IN THE 'doc' FOLDER IN THE JAR FILE
