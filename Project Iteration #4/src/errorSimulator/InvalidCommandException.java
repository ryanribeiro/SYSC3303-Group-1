package errorSimulator;

/**
 * A subclass of exception to be thrown when the error simulator
 * recieves a user command with invalid format
 * 
 * @author Luke Newton
 *
 */
public class InvalidCommandException extends Exception{
	private static final long serialVersionUID = 3996163082984878538L;

	/**
	 * Constructor
	 * 
	 * @param message a string to display when the error is thrown
	 */
	public InvalidCommandException(String message){
		super(message);
	}

	/**
	 * Constructor
	 */
	public InvalidCommandException(){
		super();
	}
}
