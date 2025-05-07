package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.BodyValidator;
import app.finwave.backend.utils.params.validators.ValidatorFunc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BodyValidatorTest {

    private BodyValidator<String> validator;

    @BeforeEach
    void setUp() {
        validator = new BodyValidator<>("test");
    }

    @Test
    void testMatches_ValidInput() {
        ValidatorFunc<String, Boolean> isNotEmpty = input -> !input.isEmpty();
        assertDoesNotThrow(() -> validator.matches(isNotEmpty, "input"));
    }

    @Test
    void testMatches_InvalidInput() {
        validator = new BodyValidator<>("");
        ValidatorFunc<String, Boolean> isNotEmpty = input -> !input.isEmpty();
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(isNotEmpty, "input"));
    }

    @Test
    void testMatches_NullInput() {
        validator = new BodyValidator<>(null);
        ValidatorFunc<String, Boolean> isNotNull = input -> input != null;
        assertDoesNotThrow(() -> validator.matches(isNotNull, "input"));
    }

    @Test
    void testMatches_ExceptionInValidator() {
        ValidatorFunc<String, Boolean> throwsException = input -> {
            throw new RuntimeException("Validation error");
        };
        // ← now expect an InvalidParameterException, since any predicate exception is caught & wrapped
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(throwsException, "input"));
    }

    @Test
    void testMatches_WithoutName() {
        ValidatorFunc<String, Boolean> isNotEmpty = input -> !input.isEmpty();
        assertDoesNotThrow(() -> validator.matches(isNotEmpty));
    }

    @Test
    void testMatches_FalseReturn() {
        ValidatorFunc<String, Boolean> alwaysFalse = input -> false;
        assertThrows(InvalidParameterException.class,
                () -> validator.matches(alwaysFalse, "input"));
    }
}
