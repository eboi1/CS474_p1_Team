package app.finwave.backend.utils;

import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.validators.URLValidator;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class URLValidatorTest {

    @Test
    void protocolAnyMatches_ok_whenProtocolMatches() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("https://example.com"));
        assertDoesNotThrow(() -> v.protocolAnyMatches("http", "https"));
    }

    @Test
    void protocolAnyMatches_error_whenProtocolDiffers() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("ftp://example.com"));
        assertThrows(InvalidParameterException.class,
                () -> v.protocolAnyMatches("http", "https"));
    }

    @Test
    void protocolAnyMatches_noOp_whenUrlIsNull() {
        URLValidator v = new URLValidator(null);
        assertDoesNotThrow(() -> v.protocolAnyMatches("http", "https"));
    }

    @Test
    void notLocalAddress_ok_forPublicHost() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("https://google.com"));
        assertDoesNotThrow(v::notLocalAddress);
    }

    @Test
    void notLocalAddress_error_forLocalHostName() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("http://localhost"));
        assertThrows(InvalidParameterException.class, v::notLocalAddress);
    }

    @Test
    void notLocalAddress_error_forLoopbackIp() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("http://127.0.0.1"));
        assertThrows(InvalidParameterException.class, v::notLocalAddress);
    }

    @Test
    void notLocalAddress_error_forPrivateIp() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("http://192.168.1.1"));
        assertThrows(InvalidParameterException.class, v::notLocalAddress);
    }

    @Test
    void notLocalAddress_error_whenHostUnknown() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("http://invalid.invalid.invalid"));
        assertThrows(InvalidParameterException.class, v::notLocalAddress);
    }

    @Test
    void notLocalAddress_noOp_whenUrlIsNull() {
        URLValidator v = new URLValidator(null);
        assertDoesNotThrow(v::notLocalAddress);
    }

    @Test
    void notLocalAddress_error_forLinkLocalIp() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("http://169.254.0.1"));
        assertThrows(InvalidParameterException.class, v::notLocalAddress);
    }

    @Test
    void constructor_withName_setsUpValidator() throws MalformedURLException {
        URLValidator v = new URLValidator(new URL("https://example.com"), "url");
        assertDoesNotThrow(v::notLocalAddress);   // quick sanity call
    }
}
