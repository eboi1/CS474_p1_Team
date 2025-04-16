package app.finwave.backend.utils.tests;

import app.finwave.backend.utils.params.validators.URLValidator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.MalformedURLException;
import java.net.URL;

class URLValidatorTest {

    @Test
    void testProtocolAnyMatches_ValidProtocol() throws MalformedURLException {
        URL url = new URL("https://google.com");
        URLValidator validator = new URLValidator(url);

        assertDoesNotThrow(() -> validator.protocolAnyMatches("http", "https"));
    }

    @Test
    void testProtocolAnyMatches_InvalidProtocol() throws MalformedURLException {
        URL url = new URL("ftp://google.com");
        URLValidator validator = new URLValidator(url);

        assertThrows(IllegalArgumentException.class, () -> validator.protocolAnyMatches("http", "https"));
    }

    @Test
    void testProtocolAnyMatches_NullURL() {
        URLValidator validator = new URLValidator(null);
        assertDoesNotThrow(() -> validator.protocolAnyMatches("http", "https"));
    }

    @Test
    void testNotLocalAddress_ValidPublicURL() throws MalformedURLException {
        URL url = new URL("https://google.com");
        URLValidator validator = new URLValidator(url);

        assertDoesNotThrow(validator::notLocalAddress);
    }

    @Test
    void testNotLocalAddress_Localhost() throws MalformedURLException {
        URL url = new URL("http://localhost");
        URLValidator validator = new URLValidator(url);

        assertThrows(IllegalArgumentException.class, validator::notLocalAddress);
    }

    @Test
    void testNotLocalAddress_PrivateIP() throws MalformedURLException {
        URL url = new URL("http://192.168.1.1");
        URLValidator validator = new URLValidator(url);

        assertThrows(IllegalArgumentException.class, validator::notLocalAddress);
    }

    @Test
    void testNotLocalAddress_NullURL() {
        URLValidator validator = new URLValidator(null);
        assertDoesNotThrow(validator::notLocalAddress);
    }
}
