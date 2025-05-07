package app.finwave.backend.api.session;

import app.finwave.backend.api.ApiResponse;
import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.UserConfig;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SessionApiTest extends BaseApiTest {

    @Mock private SessionManager sessionManager;
    @Mock private UsersSessionsRecord sessionRecord;
    private SessionApi sessionApi;

    // Helper method to get field value using reflection
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configs with UserConfig
        UserConfig userConfig = new UserConfig();
        userConfig.maxSessionDescriptionLength = 100;
        Configs configs = mock(Configs.class);
        when(configs.getState(any(UserConfig.class))).thenReturn(userConfig);
        
        // Create SessionManager mock with proper behavior
        sessionManager = mock(SessionManager.class);
        
        // Initialize the API with mocks
        sessionApi = new SessionApi(sessionManager, configs);

        // Default sessionRecord from BaseApiTest has userId 1 and not limited
        sessionRecord = mock(UsersSessionsRecord.class);
        when(sessionRecord.getUserId()).thenReturn(1);
        when(sessionRecord.getLimited()).thenReturn(false);
        when(request.attribute("session")).thenReturn(sessionRecord);
    }

    @Test
    void testNewSession_Success() {
        when(request.queryParams("lifetimeDays")).thenReturn("5");
        when(request.queryParams("description")).thenReturn("Desc");
        UsersSessionsRecord newRec = new UsersSessionsRecord();
        newRec.setId(42L); newRec.setToken("abc");
        when(sessionManager.newSession(1, 5, "Desc", true)).thenReturn(Optional.of(newRec));

        Object result = sessionApi.newSession(request, response);

        verify(response).status(200);
        assertEquals("abc", getFieldValue(result, "token"));
        assertEquals(Long.valueOf(42L), getFieldValue(result, "sessionId"));
    }

    @Test
    void testNewSession_MissingLifetimeDays() {
        when(request.queryParams("description")).thenReturn("Desc");

        assertThrows(InvalidParameterException.class, () -> sessionApi.newSession(request, response),
                "Should throw exception when lifetimeDays parameter is missing or invalid");
    }

    @Test
    void testNewSession_DescriptionTooLong() {
        String tooLongDescription = "a".repeat(101); // Exceeds the maximum length of 100
        when(request.queryParams("lifetimeDays")).thenReturn("5");
        when(request.queryParams("description")).thenReturn(tooLongDescription);

        assertThrows(InvalidParameterException.class, () -> sessionApi.newSession(request, response),
                "Should throw exception when description exceeds maximum length");
    }

    @Test
    void testNewSession_InternalServerError() {
        when(request.queryParams("lifetimeDays")).thenReturn("5");
        when(request.queryParams("description")).thenReturn("Desc");
        lenient().when(sessionManager.newSession(anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(Optional.empty());

        assertThrows(HaltException.class, () -> sessionApi.newSession(request, response),
                "Should halt with status 500 when database insertion fails");
    }

    @Test
    void testNewSession_OptionalDescription() {
        when(request.queryParams("lifetimeDays")).thenReturn("5");
        when(request.queryParams("description")).thenReturn(null);
        UsersSessionsRecord newRec = new UsersSessionsRecord();
        newRec.setId(42L);
        newRec.setToken("abc");
        when(sessionManager.newSession(1, 5, null, true)).thenReturn(Optional.of(newRec));

        Object result = sessionApi.newSession(request, response);

        verify(response).status(200);
        assertEquals("abc", getFieldValue(result, "token"));
        assertEquals(Long.valueOf(42L), getFieldValue(result, "sessionId"));
    }

    @Test
    void testNewSession_DatabaseFailure() {
        when(request.queryParams("lifetimeDays")).thenReturn("5");
        when(sessionManager.newSession(anyInt(), anyInt(), any(), eq(true))).thenReturn(Optional.empty());

        assertThrows(HaltException.class, () -> sessionApi.newSession(request, response), "Should halt on DB failure");
    }

    @Test
    void testNewSession_LimitedSessionForbidden() {
        when(sessionRecord.getLimited()).thenReturn(true);

        Object result = sessionApi.newSession(request, response);

        verify(response).status(403);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testGetSessions() {
        List<UsersSessionsRecord> sessions = new ArrayList<>();
        UsersSessionsRecord rec = new UsersSessionsRecord();
        rec.setId(10L);
        rec.setLimited(false);
        rec.setCreatedAt(LocalDateTime.now());
        rec.setExpiresAt(LocalDateTime.now().plusDays(1));
        rec.setDescription("foo");
        sessions.add(rec);

        when(sessionManager.getSessions(1)).thenReturn(sessions);

        Object result = sessionApi.getSessions(request, response);

        verify(response).status(200);
        // first reflect out the 'sessions' field (a List<Entry>)
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) getFieldValue(result, "sessions");
        assertEquals(1, entries.size());

        // now grab the first Entry and reflect on its sessionId
        Object firstEntry = entries.get(0);
        assertEquals(Long.valueOf(10L),
                getFieldValue(firstEntry, "sessionId"));

        // and finally the currentId
        assertEquals(sessionRecord.getId(),
                getFieldValue(result, "currentId"));
    }

    @Test
    void testDeleteSession_Success() {
        when(request.queryParams("sessionId")).thenReturn("10");
        when(sessionManager.userOwnSession(1, 10L)).thenReturn(true);

        Object result = sessionApi.deleteSession(request, response);

        verify(sessionManager).deleteSession(10L);
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testDeleteSession_NotOwner() {
        when(request.queryParams("sessionId")).thenReturn("11");
        when(sessionManager.userOwnSession(1, 11L)).thenReturn(false);

        // Expect HaltException due to ParamsValidator.require failing
        assertThrows(InvalidParameterException.class, () -> sessionApi.deleteSession(request, response));
    }

    @Test
    void testDeleteSession_LimitedSessionForbidden() {
        when(sessionRecord.getLimited()).thenReturn(true);
        when(request.queryParams("sessionId")).thenReturn("12");

        Object result = sessionApi.deleteSession(request, response);

        verify(response).status(403);
        assertTrue(result instanceof ApiMessage);
        verify(sessionManager, never()).deleteSession(anyLong());
    }
}

