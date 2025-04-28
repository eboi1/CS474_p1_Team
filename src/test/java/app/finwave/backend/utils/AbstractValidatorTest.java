package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.AbstractValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteValidator extends AbstractValidator<String> {
    public ConcreteValidator(String raw) {
        super(raw, null);
    }
}

class AbstractValidatorTest {

    private ConcreteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConcreteValidator("test");
    }

    @Test
    void testRequire_ValidInput() {
        assertEquals("test", validator.require());
    }

    @Test
    void testRequire_NullInput() {
        validator = new ConcreteValidator(null);
        assertThrows(InvalidParameterException.class, () -> validator.require());
    }

    @Test
    void testMap_ValidInput() {
        String result = validator.map(String::toUpperCase);
        assertEquals("TEST", result);
    }

    @Test
    void testMap_NullInput() {
        validator = new ConcreteValidator(null);
        String result = validator.map(String::toUpperCase);
        assertNull(result);
    }

    @Test
    void testMapOptional_ValidInput() {
        assertTrue(validator.mapOptional(String::length).isPresent());
    }

    @Test
    void testMapOptional_NullInput() {
        validator = new ConcreteValidator(null);
        assertFalse(validator.mapOptional(String::length).isPresent());
    }

//    @Test
//    void testInvalidMethod() {
//        assertThrows(InvalidParameterException.class, () -> validator.invalid());
//    }
}