package app.finwave.backend.utils;

import app.finwave.backend.utils.params.validators.StringValidator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StringValidatorTest {

    @Test
    public void test1() {
        // test a valid length
        StringValidator t1 = new StringValidator("raw string");
        assertDoesNotThrow(() ->t1.length(3, 11));
    }

    @Test
    public void test2() {
        // test a length that's too long
        StringValidator t2 = new StringValidator("raw string");
        assertThrows(IllegalArgumentException.class, () ->t2.length(3, 9));
    }

    @Test
    public void test2_1() {
        // test a length that's too short
        StringValidator t2_1 = new StringValidator("raw string");
        assertThrows(IllegalArgumentException.class, () ->t2_1.length(20, 100));
    }

    @Test
    public void test3() {
        // test string that matches raw string
        StringValidator t3 = new StringValidator("abc123");
        assertDoesNotThrow(() -> t3.matches("abc123"));
    }

    @Test
    public void test4() {
        // test string that doesn't match raw string
        StringValidator t4 = new StringValidator("abc123", "test");
        assertThrows(IllegalArgumentException.class, () ->t4.matches("abc1233"));
    }

    @Test
    public void test5() {
        // test string that matches regex
        StringValidator t5 = new StringValidator("abc123");
        assertDoesNotThrow(() -> t5.matches("[a-z]+\\d+"));
    }

    @Test
    public void test6() {
        // test string that doesn't match regex
        StringValidator t6 = new StringValidator("123abc");
        assertThrows(IllegalArgumentException.class, () ->t6.matches("[a-z]+\\d+"));
    }

    @Test
    public void test7() {
        // test string that contains part of string in raw string
        StringValidator t7 = new StringValidator("Hello World!");
        assertDoesNotThrow(() -> t7.contains("Hello"));
    }

    @Test
    public void test8() {
        // test string that doesn't contain a part in raw string
        StringValidator t8 = new StringValidator("Hello World!");
        assertThrows(IllegalArgumentException.class, () ->t8.contains("World!!"));
    }

    @Test
    public void test9() {
        // test null string doesn't throw exception and instead returns "this"
        StringValidator t9 = new StringValidator(null, "null");
        assertDoesNotThrow(() -> t9.length(3, 11));
    }

    @Test
    public void test10() {
        // test null string matching null ""
        StringValidator t10 = new StringValidator(null);
        assertDoesNotThrow(() -> t10.matches(""));
    }

    @Test
    public void test11() {
        // test null string contains null
        StringValidator t11 = new StringValidator(null);
        assertDoesNotThrow(() -> t11.contains(null));
    }
}