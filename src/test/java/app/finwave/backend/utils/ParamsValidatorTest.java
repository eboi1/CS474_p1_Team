package app.finwave.backend.utils;

import app.finwave.backend.utils.params.ParamsValidator;
import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.*;
import org.junit.jupiter.api.Test;
import spark.Request;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ParamsValidatorTest {

    @Test
    void testStringValidatorFromRaw() {
        StringValidator validator = ParamsValidator.string("some value", "testName");
        assertNotNull(validator);
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testStringValidatorWithoutName() {
        StringValidator validator = ParamsValidator.string("some value");
        assertNotNull(validator);
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testStringValidatorFromRequest() {
        Request request = mock(Request.class);
        when(request.queryParams("nameParam")).thenReturn("hello");

        StringValidator validator = ParamsValidator.string(request, "nameParam");
        assertNotNull(validator);
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testIntegerValidatorFromRawNull() {
        IntValidator validator = ParamsValidator.integer((String)null, "testInt");
        assertNotNull(validator);
    }

    @Test
    void testIntegerValidatorFromRawWithValue() {
        IntValidator validator = ParamsValidator.integer("42", "testInt");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.range(0, 100));
    }

    @Test
    void testIntegerValidatorWithoutName() {
        IntValidator validator = ParamsValidator.integer("42");
        assertNotNull(validator);
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
    void testIntegerValidatorWithInvalidValue() {
        IntValidator validator = ParamsValidator.integer("not-a-number", "testInt");
        assertNotNull(validator);
        assertThrows(InvalidParameterException.class, validator::require);
    }

    @Test
    void testLongValidatorFromRaw() {
        LongValidator validator = ParamsValidator.longV("9999", "testLong");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.range(1L, 10_000L));
    }

    @Test
    void testLongValidatorWithoutName() {
        LongValidator validator = ParamsValidator.longV("9999");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.range(1L, 10_000L));
    }

    @Test
    void testLongValidatorFromRequest() {
        Request request = mock(Request.class);
        when(request.queryParams("longParam")).thenReturn("999");

        LongValidator validator = ParamsValidator.longV(request, "longParam");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.range(1L, 1_000L));
    }

    @Test
    void testLongValidatorWithInvalidValue() {
        LongValidator validator = ParamsValidator.longV("not-a-number", "testLong");
        assertNotNull(validator);
        assertThrows(InvalidParameterException.class, validator::require);
    }

    @Test
    void testURLValidatorFromRaw() {
        URLValidator validator = ParamsValidator.url("https://example.com", "someUrl");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.protocolAnyMatches("http", "https"));
    }

    @Test
    void testURLValidatorWithInvalidURL() {
        URLValidator validator = ParamsValidator.url("not-a-url", "badUrl");
        assertNotNull(validator);
        assertThrows(InvalidParameterException.class, validator::require);
    }

    @Test
    void testURLValidatorFromRequest() {
        Request request = mock(Request.class);
        when(request.queryParams("urlParam")).thenReturn("https://example.org");

        URLValidator validator = ParamsValidator.url(request, "urlParam");
        assertNotNull(validator);
        assertDoesNotThrow(validator::require);
    }

    @Test
    void testBodyValidatorFromRawObject() {
        BodyValidator<String> validator = ParamsValidator.bodyObject("hello");
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.matches(val -> val.equals("hello")));
    }

    @Test
    void testBodyValidatorFromRequest() {
        Request request = mock(Request.class);
        when(request.body()).thenReturn("\"someText\"");

        BodyValidator<String> validator = ParamsValidator.bodyObject(request, String.class);
        assertNotNull(validator);
        assertDoesNotThrow(() -> validator.matches(val -> val.equals("someText")));
    }

    @Test
    void testBodyValidatorThrowsForInvalidJson() {
        Request request = mock(Request.class);
        when(request.body()).thenReturn("{ unclosed: \"invalid\"");

        assertThrows(InvalidParameterException.class,
                () -> ParamsValidator.bodyObject(request, String.class));
    }

    @Test
    void testBodyValidatorWithNullRequest() {
        Request request = mock(Request.class);
        when(request.body()).thenReturn(null);

        // creation should succeed and return a validator wrapping a null raw value
        BodyValidator<String> validator = assertDoesNotThrow(
                () -> ParamsValidator.bodyObject(request, String.class)
        );
        assertNotNull(validator);
    }
}
