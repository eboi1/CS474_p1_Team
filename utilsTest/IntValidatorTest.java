package app.finwave.backend.utilsTest;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.IntValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntValidatorTest {

    @Test
    public void testRangeValidInput() {
        IntValidator validator = new IntValidator(5);
        assertDoesNotThrow(()->{validator.range(1,10);});
    }

    @Test
    public void testRangeBelow() {
        IntValidator validator = new IntValidator(0);
        assertThrows(InvalidParameterException.class, ()->{validator.range(1,10);});
    }

    @Test
    public void testRangeAbove() {
        IntValidator validator = new IntValidator(11);
        assertThrows(InvalidParameterException.class, ()->{validator.range(1,10);});
    }

    @Test
    public void testRangeNull() {
        IntValidator validator = new IntValidator(null);
        assertDoesNotThrow(()->{validator.range(1,10);});
    }

    @Test
    public void testMatchesValidMatch() {
        IntValidator validator = new IntValidator(10);
        assertDoesNotThrow(()->{validator.matches(val -> val %2 == 0);});
    }

    @Test
    public void testMatchesInvalidMatch() {
        IntValidator validator = new IntValidator(9);
        assertThrows(InvalidParameterException.class, ()->{validator.matches(val -> val %2 == 0);});
    }

    @Test
    public void testMatchesNull() {
        IntValidator validator = new IntValidator(null);
        assertDoesNotThrow(()->{validator.matches(val -> val > 0);});
    }

    @Test
    public void testMatchesThrowsInvalidMatch() {
        IntValidator validator = new IntValidator(10);
        assertThrows(InvalidParameterException.class, ()->{validator.matches(val -> {throw new RuntimeException("Failure");});});
    }
}
