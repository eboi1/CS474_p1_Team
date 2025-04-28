package app.finwave.backend.utils;

import app.finwave.backend.utils.params.ParamsValidator;
import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.*;
import org.junit.jupiter.api.Test;
import spark.Request;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link ParamsValidator}.
 * Demonstrates creation of validator objects and basic checks on them.
 */
public class ParamsValidatorTest {

    @Test
    void testStringValidatorFromRaw() {
        StringValidator validator = ParamsValidator.string("some value", "testName");
        assertNotNull(validator);
        // Check basic usage
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testStringValidatorFromRequest() {
        // Mock a Spark Request
        Request request = mock(Request.class);
        when(request.queryParams("nameParam")).thenReturn("hello");

        StringValidator validator = ParamsValidator.string(request, "nameParam");
        assertNotNull(validator);
        // Validate
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testIntegerValidatorFromRawNull() {
        IntValidator validator = ParamsValidator.integer((String)null, "testInt");
        // Null is allowed, we just check that it doesn't throw on creation
        assertNotNull(validator);
        // Typically no exception because it returns a valid IntValidator
    }

    @Test
    void testIntegerValidatorFromRawWithValue() {
        IntValidator validator = ParamsValidator.integer("42", "testInt");
        assertNotNull(validator);
        // Check usage
        assertDoesNotThrow(() -> validator.range(0, 100));
    }

    @Test
    void testIntegerValidatorFromRequest() {
        Request request = mock(Request.class);
        when(request.queryParams("intParam")).thenReturn("10");

        IntValidator validator = ParamsValidator.integer(request, "intParam");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.range(1, 20));
    }

    @Test
    void testLongValidatorFromRaw() {
        LongValidator validator = ParamsValidator.longV("9999", "testLong");
        assertNotNull(validator);
        // Check usage
        assertDoesNotThrow(() -> validator.range(1L, 10000L));
    }

    @Test
    void testURLValidatorFromRaw() {
        // Valid URL
        URLValidator validator = ParamsValidator.url("https://example.com", "someUrl");
        assertNotNull(validator);
        // For instance, check that it doesn't throw on creation
        assertDoesNotThrow(() -> validator.protocolAnyMatches("http", "https"));
    }

    @Test
    void testBodyValidatorFromRawObject() {
        // Provide any object; e.g. a simple string
        BodyValidator<String> validator = ParamsValidator.bodyObject("hello");
        assertNotNull(validator);
        // We can do a minimal test to ensure it doesn't throw on creation
        assertDoesNotThrow(() -> validator.matches(val -> val.equals("hello")));
    }

    @Test
    void testBodyValidatorFromRequest() {
        // Mock the Request to return a simple JSON representing a string
        Request request = mock(Request.class);
        when(request.body()).thenReturn("\"someText\"");

        BodyValidator<String> validator = ParamsValidator.bodyObject(request, String.class);
        assertNotNull(validator);
        // Check the contained value
        assertDoesNotThrow(() -> validator.matches(val -> val.equals("someText")));
    }

    @Test
    void testBodyValidatorThrowsForInvalidJson() {
        Request request = mock(Request.class);
        // This is invalid JSON, expecting an exception
        when(request.body()).thenReturn("INVALID_JSON");

        assertThrows(InvalidParameterException.class,
                () -> ParamsValidator.bodyObject(request, String.class));
    }
}