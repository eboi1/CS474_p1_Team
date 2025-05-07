package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.LongValidator;
import app.finwave.backend.utils.params.validators.ValidatorFunc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LongValidatorTest {

    private LongValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LongValidator(10L);
    }

    @Test
    void testRange_ValidInput() {
        assertDoesNotThrow(() -> validator.range(5, 15));
    }

    @Test
    void testRange_InvalidInput() {
        validator = new LongValidator(20L);
        assertThrows(InvalidParameterException.class,
                () -> validator.range(5, 15));
    }

    @Test
    void testRange_NullInput() {
        validator = new LongValidator(null);
        assertDoesNotThrow(() -> validator.range(5, 15)); // null should be treated as “no-op”
    }

    @Test
    void testMatches_ValidInput() {
        ValidatorFunc<Long, Boolean> isPositive = input -> input > 0;
        assertDoesNotThrow(() -> validator.matches(isPositive));
    }

    @Test
    void testMatches_InvalidInput() {
        validator = new LongValidator(-5L);
        ValidatorFunc<Long, Boolean> isPositive = input -> input > 0;
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(isPositive));
    }

    @Test
    void testMatches_ExceptionInValidator() {
        ValidatorFunc<Long, Boolean> throwsException = input -> {
            throw new RuntimeException("Validation error");
        };
        // since your matches(...) catches and wraps any exception, it should throw
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(throwsException));
    }

    @Test
    void testMatches_FalseReturn() {
        ValidatorFunc<Long, Boolean> alwaysFalse = input -> false;
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(alwaysFalse));
    }

    @Test
    void testMatches_NullRaw() {
        LongValidator validator2 = new LongValidator(null, "null value");
        ValidatorFunc<Long, Boolean> checkNull = input -> input == null;
        assertDoesNotThrow(() -> validator2.matches(checkNull));
    }
}
