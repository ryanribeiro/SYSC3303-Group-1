package server;

/**
 * A subclass of exception to be thrown when the server reads a message of invalid format
 * 
 * @author Luke Newton
 *
 */
public class InvalidMessageFormatException extends Exception{
	private static final long serialVersionUID = 3996163082984878538L;

	/**
	 * Constructor
	 * 
	 * @param message a string to display when the error is thrown
	 */
	public InvalidMessageFormatException(String message){
		super(message);
	}

	/**
	 * Constructor
	 */
	public InvalidMessageFormatException(){
		super();
	}
}
