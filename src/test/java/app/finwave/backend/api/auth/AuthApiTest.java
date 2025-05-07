package app.finwave.backend.api.auth;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.session.SessionManager;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.UserConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.UsersRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private SessionManager sessionManager;
    
    @Mock
    private Configs configs;
    
    @Mock
    private AuthDatabase authDatabase;
    
    @Mock
    private UserConfig userConfig;
    
    @Mock
    private UsersRecord userRecord;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    private AuthApi authApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configuration
        userConfig.minLoginLength = 3;
        userConfig.maxLoginLength = 50;
        userConfig.minPasswordLength = 8;
        userConfig.maxPasswordLength = 100;
        userConfig.maxSessionDescriptionLength = 255;
        userConfig.userSessionsLifetimeDays = 30;
        userConfig.demoMode = false;
        when(configs.getState(any(UserConfig.class))).thenReturn(userConfig);
        
        // Setup database
        when(databaseWorker.get(AuthDatabase.class)).thenReturn(authDatabase);
        
        // Create the API instance
        authApi = new AuthApi(databaseWorker, sessionManager, configs);
    }

    @Test
    void testLogin_Success() {
        // Setup
        String login = "testuser";
        String password = "password123";
        String description = "Test login from browser";
        
        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        when(request.queryParams("description")).thenReturn(description);
        
        when(userRecord.getId()).thenReturn(2);
        when(authDatabase.authUser(login, password)).thenReturn(Optional.of(userRecord));
        when(sessionRecord.getToken()).thenReturn("test-token-123");
        when(sessionManager.newSession(eq(2), eq(30), eq(description), eq(false))).thenReturn(Optional.of(sessionRecord));
        
        // Execute
        AuthApi.LoginResponse result = (AuthApi.LoginResponse) authApi.login(request, response);
        
        // Verify
        assertEquals("test-token-123", result.token);
        assertEquals(30, result.lifetimeDays);
    }
    
    @Test
    void testLogin_InvalidCredentials() {
        // Setup
        String login = "testuser";
        String password = "wrongpassword";
        
        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        
        when(authDatabase.authUser(login, password)).thenReturn(Optional.empty());
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> authApi.login(request, response));
    }
    
    @Test
    void testLogin_DemoAdmin() {
        // Setup
        String login = "admin";
        String password = "adminpassword";
        
        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        
        userConfig.demoMode = true;
        when(userRecord.getId()).thenReturn(1); // Admin user
        when(authDatabase.authUser(login, password)).thenReturn(Optional.of(userRecord));
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> authApi.login(request, response));
    }
    
    @Test
    void testAuth_ValidToken() throws AuthenticationFailException {
        // Setup
        String token = "valid-token-123";
        when(request.headers("Authorization")).thenReturn("Bearer " + token);
        when(request.requestMethod()).thenReturn("GET");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(15);
        when(sessionRecord.getExpiresAt()).thenReturn(expiry);
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        
        // Execute
        authApi.auth(request, response);
        
        // Verify
        verify(request).attribute("session", sessionRecord);
    }
    
    @Test
    void testAuth_ExpiredToken() {
        // Setup
        String token = "expired-token";
        when(request.headers("Authorization")).thenReturn("Bearer " + token);
        when(request.requestMethod()).thenReturn("GET");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.minusDays(1); // Expired
        when(sessionRecord.getExpiresAt()).thenReturn(expiry);
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        
        // Execute & Verify
        assertThrows(AuthenticationFailException.class, () -> authApi.auth(request, response));
        verify(sessionManager).deleteSession(sessionRecord);
    }
    
    @Test
    void testAuth_InvalidToken() {
        // Setup
        String token = "invalid-token";
        when(request.headers("Authorization")).thenReturn("Bearer " + token);
        when(request.requestMethod()).thenReturn("GET");
        
        when(sessionManager.auth(token)).thenReturn(Optional.empty());
        
        // Execute & Verify
        assertThrows(AuthenticationFailException.class, () -> authApi.auth(request, response));
    }
    
    @Test
    void testAuth_NoToken() {
        // Setup
        when(request.headers("Authorization")).thenReturn(null);
        when(request.requestMethod()).thenReturn("GET");
        
        // Execute & Verify
        assertThrows(AuthenticationFailException.class, () -> authApi.auth(request, response));
    }
    
    @Test
    void testAuthAdmin_AdminUser() throws AuthenticationFailException {
        // Setup
        String token = "admin-token";
        when(request.headers("Authorization")).thenReturn("Bearer " + token);
        when(request.requestMethod()).thenReturn("GET");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(15);
        when(sessionRecord.getExpiresAt()).thenReturn(expiry);
        when(sessionRecord.getUserId()).thenReturn(1); // Admin user
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Execute
        authApi.authAdmin(request, response);
        
        // No exception is thrown for admin user
    }
    
    @Test
    void testAuthAdmin_RegularUser() throws AuthenticationFailException {
        // Setup
        String token = "user-token";
        when(request.headers("Authorization")).thenReturn("Bearer " + token);
        when(request.requestMethod()).thenReturn("GET");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusDays(15);
        when(sessionRecord.getExpiresAt()).thenReturn(expiry);
        when(sessionRecord.getUserId()).thenReturn(2); // Regular user
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Execute & Verify
        assertThrows(AuthenticationFailException.class, () -> authApi.authAdmin(request, response));
    }
} 