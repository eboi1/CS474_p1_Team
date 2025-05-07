package app.finwave.backend.auth;

import app.finwave.backend.utils.PBKDF2;
import org.junit.jupiter.api.Test;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHashingUtilTest {

    @Test
    void testHashWithSaltAndVerify() throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Setup
        String password = "SecurePassword123!";
        
        // Execute
        String hashedPassword = PBKDF2.hashWithSaltBase64(password);
        
        // Verify
        assertNotNull(hashedPassword);
        assertFalse(hashedPassword.isEmpty());
        assertTrue(PBKDF2.verifyBase64(password, hashedPassword));
        
        // Verify incorrect password fails
        assertFalse(PBKDF2.verifyBase64("WrongPassword123!", hashedPassword));
    }
    
    @Test
    void testHashWithSaltRawBytesAndVerify() throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Setup
        String password = "AnotherSecurePassword456!";
        
        // Execute
        byte[] hashedPassword = PBKDF2.hashWithSalt(password);
        
        // Verify
        assertNotNull(hashedPassword);
        assertTrue(hashedPassword.length > 0);
        assertTrue(PBKDF2.verify(password, hashedPassword));
        
        // Verify incorrect password fails
        assertFalse(PBKDF2.verify("WrongPassword456!", hashedPassword));
    }
    
    @Test
    void testManualHashAndVerify() throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Setup
        String password = "ThirdSecurePassword789!";
        byte[] salt = PBKDF2.generateSalt();
        
        // Execute
        byte[] hashedPassword = PBKDF2.hash(password, salt);
        
        // Verify
        assertNotNull(hashedPassword);
        assertTrue(hashedPassword.length > 0);
        assertTrue(PBKDF2.verify(password, hashedPassword, salt));
        
        // Verify incorrect password fails
        assertFalse(PBKDF2.verify("WrongPassword789!", hashedPassword, salt));
    }
    
    @Test
    void testGenerateSalt() {
        // Execute
        byte[] salt1 = PBKDF2.generateSalt();
        byte[] salt2 = PBKDF2.generateSalt();
        
        // Verify
        assertNotNull(salt1);
        assertNotNull(salt2);
        assertEquals(32, salt1.length); // Check salt length
        assertEquals(32, salt2.length);
        assertFalse(java.util.Arrays.equals(salt1, salt2)); // Salts should be different
    }
    
    @Test
    void testBase64Encoding() throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Setup
        String password = "FourthSecurePassword!@#";
        byte[] rawHash = PBKDF2.hashWithSalt(password);
        
        // Execute
        String base64Hash = Base64.getEncoder().encodeToString(rawHash);
        byte[] decodedHash = Base64.getDecoder().decode(base64Hash);
        
        // Verify
        assertArrayEquals(rawHash, decodedHash);
        assertTrue(PBKDF2.verify(password, decodedHash));
    }
    
    @Test
    void testConsistency() throws NoSuchAlgorithmException, InvalidKeySpecException {
        // Setup
        String password = "FifthSecurePassword%^&";
        byte[] salt = PBKDF2.generateSalt();
        
        // Execute
        byte[] hash1 = PBKDF2.hash(password, salt);
        byte[] hash2 = PBKDF2.hash(password, salt);
        
        // Verify - same password with same salt should produce same hash
        assertArrayEquals(hash1, hash2);
    }
} 