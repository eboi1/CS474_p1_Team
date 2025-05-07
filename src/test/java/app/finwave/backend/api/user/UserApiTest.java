package app.finwave.backend.api.user;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.session.SessionManager;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.UserConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private SessionManager sessionManager;
    
    @Mock
    private Configs configs;
    
    @Mock
    private UserConfig userConfig;
    
    @Mock
    private UserConfig.RegistrationConfig registrationConfig;
    
    @Mock
    private UserDatabase userDatabase;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    private UserApi userApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configuration
        userConfig.demoMode = true;
        userConfig.minLoginLength = 3;
        userConfig.maxLoginLength = 50;
        userConfig.minPasswordLength = 8;
        userConfig.maxPasswordLength = 100;
        userConfig.registration = registrationConfig;
        registrationConfig.enabled = true;
        registrationConfig.loginRegexFilter = "^[a-zA-Z0-9]+$";
        registrationConfig.passwordRegexFilter = "^.+$";
        
        when(configs.getState(any(UserConfig.class))).thenReturn(userConfig);
        
        // Setup database mocks
        when(databaseWorker.get(UserDatabase.class)).thenReturn(userDatabase);
        
        // Setup session
        when(sessionRecord.getUserId()).thenReturn(1);
        when(sessionRecord.getLimited()).thenReturn(false);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Create the API instance
        userApi = new UserApi(databaseWorker, sessionManager, configs);
    }

    @Test
    void testDemoAccount_Success() {
        // Setup
        when(userDatabase.userExists(anyString())).thenReturn(false);
        when(userDatabase.registerUser(anyString(), anyString())).thenReturn(Optional.of(1));
        
        // Execute
        UserApi.DemoAccountResponse result = (UserApi.DemoAccountResponse) userApi.demoAccount(request, response);
        
        // Verify
        verify(response).status(201);
        assertNotNull(result.username);
        assertNotNull(result.password);
    }
    
    @Test
    void testDemoAccount_DemoModeDisabled() {
        // Setup
        userConfig.demoMode = false;
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> userApi.demoAccount(request, response));
    }
    
    @Test
    void testRegister_Success() {
        // Setup
        String login = "testuser";
        String password = "password123";
        
        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        when(userDatabase.userExists(login)).thenReturn(false);
        when(userDatabase.registerUser(login, password)).thenReturn(Optional.of(1));
        
        // Execute
        Object result = userApi.register(request, response);
        
        // Verify
        verify(response).status(201);
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testRegister_RegistrationDisabled() {
        // Setup
        registrationConfig.enabled = false;
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> userApi.register(request, response));
    }
    
    @Test
    void testRegister_UserExists() {
        // Setup
        String login = "testuser";
        String password = "password123";
        
        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        when(userDatabase.userExists(login)).thenReturn(true);
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> userApi.register(request, response));
    }

    @Test
    void testRegister_RegistrationFails() {
        // Setup - registration throws IntegrityConstraintViolationException to simulate database failure
        String login = "testuser";
        String password = "password123";

        when(request.queryParams("login")).thenReturn(login);
        when(request.queryParams("password")).thenReturn(password);
        when(userDatabase.userExists(login)).thenReturn(false);
        when(userDatabase.registerUser(login, password)).thenReturn(Optional.empty());

        // Execute & Verify - should throw HaltException
        assertThrows(HaltException.class, () -> userApi.register(request, response));
    }
    
    @Test
    void testDemoAccount_RegistrationFails() {
        // Setup - registerUser throws exception to simulate database failure
        when(userDatabase.userExists(anyString())).thenReturn(false);
        when(userDatabase.registerUser(anyString(), anyString())).thenReturn(Optional.empty());
        
        // Execute & Verify - should throw HaltException
        assertThrows(HaltException.class, () -> userApi.demoAccount(request, response));
    }
    
    @Test
    void testDemoAccount_TooManyAttempts() {
        // Setup - userExists always returns true to force multiple login generation attempts
        when(userDatabase.userExists(anyString())).thenReturn(true);
        
        // Execute & Verify - should throw HaltException with code 500 after too many attempts
        HaltException exception = assertThrows(HaltException.class, () -> userApi.demoAccount(request, response));
        assertEquals(500, exception.getStatusCode());
    }
    
    @Test
    void testChangePassword_Success() {
        // Setup
        String newPassword = "newPassword123";
        
        when(request.queryParams("password")).thenReturn(newPassword);
        
        // Execute
        Object result = userApi.changePassword(request, response);
        
        // Verify
        verify(userDatabase).changeUserPassword(1, newPassword);
        verify(sessionManager).deleteAllUserSessions(1);
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testChangePassword_LimitedSession() {
        // Setup
        when(sessionRecord.getLimited()).thenReturn(true);
        
        // Execute
        Object result = userApi.changePassword(request, response);
        
        // Verify
        verify(response).status(403);
        assertTrue(result instanceof ApiMessage);
        verify(userDatabase, never()).changeUserPassword(anyInt(), anyString());
    }
    
    @Test
    void testGetUsername_Success() {
        // Setup
        String username = "testuser";
        when(userDatabase.getUsername(1)).thenReturn(username);
        
        // Execute
        UserApi.GetUsernameResponse result = (UserApi.GetUsernameResponse) userApi.getUsername(request, response);
        
        // Verify
        verify(response).status(200);
        assertEquals(username, result.username);
    }
    
    @Test
    void testLogout_Success() {
        // Execute
        Object result = userApi.logout(request, response);
        
        // Verify
        verify(sessionManager).deleteSession(sessionRecord);
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }
} 