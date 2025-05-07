package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.AbstractValidator;
import app.finwave.backend.utils.params.validators.ValidatorFunc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteValidator extends AbstractValidator<String> {
    public ConcreteValidator(String raw, String name) {
        super(raw, name);
    }
    
    public ConcreteValidator(String raw) {
        super(raw, null);
    }
    
    // Expose protected methods for testing
    public void callInvalid() {
        invalid();
    }
    
    public void callInvalidWithName(String name) {
        invalid(name);
    }
}

class AbstractValidatorTest {

    private ConcreteValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConcreteValidator("test", "testParam");
    }

    @Test
    void testRequire_ValidInput() {
        assertEquals("test", validator.require());
    }

    @Test
    void testRequire_NullInput() {
        validator = new ConcreteValidator(null, "testParam");
        assertThrows(InvalidParameterException.class, () -> validator.require());
    }

    @Test
    void testOptional_ValidInput() {
        Optional<String> result = validator.optional();
        assertTrue(result.isPresent());
        assertEquals("test", result.get());
    }

    @Test
    void testOptional_NullInput() {
        validator = new ConcreteValidator(null, "testParam");
        Optional<String> result = validator.optional();
        assertFalse(result.isPresent());
    }

    @Test
    void testMap_ValidInput() {
        String result = validator.map(String::toUpperCase);
        assertEquals("TEST", result);
    }

    @Test
    void testMap_NullInput() {
        validator = new ConcreteValidator(null, "testParam");
        assertThrows(InvalidParameterException.class, () -> validator.map(String::toUpperCase));
    }
    
    @Test
    void testMap_ThrowsException() {
        ValidatorFunc<String, String> throwingFunc = s -> {
            throw new RuntimeException("Test exception");
        };
        
        assertThrows(InvalidParameterException.class, () -> validator.map(throwingFunc));
    }

    @Test
    void testMapOptional_ValidInput() {
        Optional<Integer> result = validator.mapOptional(String::length);
        assertTrue(result.isPresent());
        assertEquals(4, result.get());
    }

    @Test
    void testMapOptional_NullInput() {
        validator = new ConcreteValidator(null, "testParam");
        Optional<Integer> result = validator.mapOptional(String::length);
        assertFalse(result.isPresent());
    }
    
    @Test
    void testMapOptional_ThrowsException() {
        ValidatorFunc<String, Integer> throwingFunc = s -> {
            throw new RuntimeException("Test exception");
        };
        
        Optional<Integer> result = validator.mapOptional(throwingFunc);
        assertFalse(result.isPresent());
    }
    
    @Test
    void testInvalidWithName() {
        validator = new ConcreteValidator("test", "testParam");
        InvalidParameterException exception = assertThrows(InvalidParameterException.class, 
            () -> validator.callInvalidWithName("customParam"));
        assertEquals("'customParam' parameter invalid", exception.getMessage());
    }
    
    @Test
    void testInvalidWithNoName() {
        validator = new ConcreteValidator("test", null);
        assertThrows(InvalidParameterException.class, () -> validator.callInvalid());
    }
    
    @Test
    void testInvalidWithNameNull() {
        validator = new ConcreteValidator("test", "testParam");
        assertThrows(InvalidParameterException.class, () -> validator.callInvalidWithName(null));
    }
}