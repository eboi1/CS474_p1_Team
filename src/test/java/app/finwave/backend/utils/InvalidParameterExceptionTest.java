package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link InvalidParameterException}.
 * Ensures both constructors are covered.
 */
public class InvalidParameterExceptionTest {

    @Test
    void testDefaultConstructor() {
        InvalidParameterException exception = new InvalidParameterException();
        assertNotNull(exception);
        // Default constructor doesn't set a specific message
        assertNull(exception.getMessage());
    }

    @Test
    void testNamedConstructor() {
        String paramName = "testParam";
        InvalidParameterException exception = new InvalidParameterException(paramName);
        assertNotNull(exception);
        
        // Check that the message contains the parameter name
        String expectedMessage = "'" + paramName + "' parameter invalid";
        assertEquals(expectedMessage, exception.getMessage());
    }
} 