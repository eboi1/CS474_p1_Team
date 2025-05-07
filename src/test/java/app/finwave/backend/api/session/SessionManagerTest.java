package app.finwave.backend.api.session;

import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.UserConfig;
import app.finwave.backend.config.general.CachingConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SessionManagerTest {

    private static class Fixture {
        private final SessionDatabase sessionDb = mock(SessionDatabase.class);
        private final DatabaseWorker databaseWorker = mock(DatabaseWorker.class);
        private final Configs configs = mock(Configs.class);
        private final UsersSessionsRecord sessionRecord = mock(UsersSessionsRecord.class);
        private SessionManager sessionManager;
        
        public Fixture setup() {
            // Create and configure UserConfig
            UserConfig userConfig = new UserConfig();
            userConfig.userSessionsLifetimeDays = 7;
            when(configs.getState(any(UserConfig.class))).thenReturn(userConfig);
            
            // Create and configure CachingConfig with Sessions subconfig
            CachingConfig cachingConfig = new CachingConfig();
            cachingConfig.sessions = new CachingConfig.Sessions();
            cachingConfig.sessions.maxTokens = 100;
            cachingConfig.sessions.maxLists = 100;
            when(configs.getState(any(CachingConfig.class))).thenReturn(cachingConfig);
            
            when(databaseWorker.get(SessionDatabase.class)).thenReturn(sessionDb);
            
            sessionManager = new SessionManager(databaseWorker, configs);
            
            return this;
        }
    }
    
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new Fixture().setup();
    }

    @Test
    void testAuth_ValidToken() throws Exception {
        String token = "validToken";
        UsersSessionsRecord record = new UsersSessionsRecord();
        record.setToken(token);
        when(fixture.sessionDb.get(token)).thenReturn(Optional.of(record));

        Optional<UsersSessionsRecord> result = fixture.sessionManager.auth(token);

        assertTrue(result.isPresent(), "Auth should return record for valid token");
        assertEquals(token, result.get().getToken());
    }

    @Test
    void testAuth_InvalidToken() {
        String token = "invalidToken";
        when(fixture.sessionDb.get(token)).thenReturn(Optional.empty());

        Optional<UsersSessionsRecord> result = fixture.sessionManager.auth(token);

        assertTrue(result.isEmpty(), "Auth should return empty for invalid token");
    }

    @Test
    void testNewSession_Success() {
        int userId = 1;
        int lifetime = 7;
        String description = "Test session";
        boolean limited = true;
        UsersSessionsRecord newRec = new UsersSessionsRecord();
        newRec.setUserId(userId);
        newRec.setLimited(limited);
        newRec.setToken("newToken");
        when(fixture.sessionDb.newSession(eq(userId), anyString(), eq(lifetime), eq(description), eq(limited)))
                .thenReturn(Optional.of(newRec));

        Optional<UsersSessionsRecord> result = fixture.sessionManager.newSession(userId, lifetime, description, limited);

        assertTrue(result.isPresent());
        verify(fixture.sessionDb).newSession(eq(userId), anyString(), eq(lifetime), eq(description), eq(limited));
        assertEquals("newToken", result.get().getToken(), "New session token should match");
    }

    @Test
    void testNewSession_Failure() {
        when(fixture.sessionDb.newSession(anyInt(), anyString(), anyInt(), anyString(), anyBoolean()))
                .thenReturn(Optional.empty());

        Optional<UsersSessionsRecord> result = fixture.sessionManager.newSession(1, 7, "desc", false);

        assertTrue(result.isEmpty(), "Should return empty if database fails to create session");
    }

    @Test
    void testGetSessions_CacheHit() {
        int userId = 1;
        List<UsersSessionsRecord> sessions = List.of(new UsersSessionsRecord());
        // Pre-populate cache via cache loader logic
        when(fixture.sessionDb.getUserSessions(userId)).thenReturn(sessions);
        List<UsersSessionsRecord> result1 = fixture.sessionManager.getSessions(userId);
        List<UsersSessionsRecord> result2 = fixture.sessionManager.getSessions(userId);

        verify(fixture.sessionDb, times(1)).getUserSessions(userId);
        assertEquals(1, result1.size());
        assertEquals(result1, result2, "Subsequent calls should hit cache and return same sessions");
    }

    @Test
    void testDeleteSession_ByRecord() {
        UsersSessionsRecord rec = new UsersSessionsRecord();
        rec.setId(100L);
        rec.setUserId(1);
        rec.setToken("tok");
        when(fixture.sessionDb.deleteSession(100L)).thenReturn(rec);

        fixture.sessionManager.deleteSession(100L);

        verify(fixture.sessionDb).deleteSession(100L);
        // Ensure caches invalidated for user and token
        // (No direct verification possible; rely on no exceptions thrown)
    }

    @Test
    void testDeleteAllUserSessions() {
        int userId = 1;
        UsersSessionsRecord rec1 = new UsersSessionsRecord(); rec1.setToken("t1");
        UsersSessionsRecord rec2 = new UsersSessionsRecord(); rec2.setToken("t2");
        List<UsersSessionsRecord> removed = List.of(rec1, rec2);
        when(fixture.sessionDb.deleteAllUserSessions(userId)).thenReturn(removed);

        fixture.sessionManager.deleteAllUserSessions(userId);

        verify(fixture.sessionDb).deleteAllUserSessions(userId);
        // All tokens should be invalidated, and user session list cache invalidated
    }

    @Test
    void testDeleteOverdueSessions() {
        UsersSessionsRecord rec = new UsersSessionsRecord();
        rec.setUserId(1); rec.setToken("tok");
        List<UsersSessionsRecord> removed = List.of(rec);
        when(fixture.sessionDb.deleteOverdueSessions()).thenReturn(removed);

        fixture.sessionManager.deleteOverdueSessions();

        verify(fixture.sessionDb).deleteOverdueSessions();
        // The overdue session's token and user list should be invalidated
    }

    @Test
    void testUpdateSessionLifetime() {
        UsersSessionsRecord updated = new UsersSessionsRecord();
        updated.setId(200L);
        updated.setUserId(2);
        updated.setToken("tok2");
        when(fixture.sessionDb.updateSessionLifetime(eq(200L), anyInt())).thenReturn(updated);

        fixture.sessionManager.updateSessionLifetime(200L, 2);

        verify(fixture.sessionDb).updateSessionLifetime(200L, fixture.sessionManager.config.userSessionsLifetimeDays);
        // Verify tokenCache updated with new record
        // (No direct method to inspect cache, ensure no exceptions)
    }

    @Test
    void testUserOwnSession() throws Exception {
        int userId = 1;
        long sessionId = 10L;
        UsersSessionsRecord rec = new UsersSessionsRecord();
        rec.setId(sessionId);
        when(fixture.sessionDb.getUserSessions(userId)).thenReturn(List.of(rec));

        assertTrue(fixture.sessionManager.userOwnSession(userId, sessionId), "User should own session that is in list");
        assertFalse(fixture.sessionManager.userOwnSession(userId, 99L), "User should not own session not in list");
    }
}

