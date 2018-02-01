package unitTests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * run this as a JUnit test to run all tests
 * 
 * @author luke newton
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ ClientTest.class, ServerTest.class, ErrorSimulatorTest.class} )
public final class AllTests {} 