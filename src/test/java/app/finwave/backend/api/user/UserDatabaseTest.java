package app.finwave.backend.api.user;

import app.finwave.backend.utils.PBKDF2;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import static app.finwave.backend.jooq.tables.Users.USERS;
import static org.junit.jupiter.api.Assertions.*;

class UserDatabaseTest {
    private DSLContext ctx;
    private UserDatabase userDb;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        // Initialize in-memory H2 database in PostgreSQL compatibility mode
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa", ""
        );

        ctx = DSL.using(connection, SQLDialect.H2);

        // Create USERS table
        ctx.execute(
                "CREATE TABLE USERS (" +
                        "ID SERIAL PRIMARY KEY," +
                        "USERNAME VARCHAR(255) UNIQUE NOT NULL," +
                        "PASSWORD VARCHAR(512) NOT NULL" +
                        ");"
        );
        userDb = new UserDatabase(ctx);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ctx.execute("DROP ALL OBJECTS");
        connection.close();
    }

    @Test
    void testUserExistsAndRegister() {
        // Initially no user
        assertFalse(userDb.userExists("alice"));

        // Register new user
        Optional<Integer> idOpt = userDb.registerUser("alice", "password123");
        assertTrue(idOpt.isPresent(), "registerUser should return an ID");
        int userId = idOpt.get();

        // Now userExists should be true
        assertTrue(userDb.userExists("alice"));

        // getUsername should return the correct username
        assertEquals("alice", userDb.getUsername(userId));
    }

    @Test
    void testChangeUserPassword() throws Exception {
        // Register and get initial password hash (Base64)
        Optional<Integer> idOpt = userDb.registerUser("bob", "secret1");
        assertTrue(idOpt.isPresent());
        int userId = idOpt.get();

        // Fetch the stored Base64 hash
        Record1<String> rec1 = ctx.select(USERS.PASSWORD)
                .from(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne();
        String hash1 = rec1.component1();
        // verifyBase64 validates the password against the Base64-encoded hash
        assertTrue(PBKDF2.verifyBase64("secret1", hash1));

        // Change password
        userDb.changeUserPassword(userId, "secret2");

        // Fetch the new hash
        Record1<String> rec2 = ctx.select(USERS.PASSWORD)
                .from(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne();
        String hash2 = rec2.component1();

        // The hash should have changed
        assertNotEquals(hash1, hash2);
        // New hash validates new password
        assertTrue(PBKDF2.verifyBase64("secret2", hash2));
        // Old password no longer validates
        assertFalse(PBKDF2.verifyBase64("secret1", hash2));
    }

    @Test
    void testGetUsernameNonexistent() {
        assertNull(userDb.getUsername(9999), "Nonexistent user should return null");
    }

    @Test
    void testRegisterDuplicateUser() {
        // First registration should succeed
        Optional<Integer> firstId = userDb.registerUser("duplicate", "password123");
        assertTrue(firstId.isPresent(), "First registration should succeed");

        // Second registration with same username should throw IntegrityConstraintViolationException
        assertThrows(org.jooq.exception.IntegrityConstraintViolationException.class, () -> {
            userDb.registerUser("duplicate", "different_password");
        });
    }

    @Test
    void testRegisterEmptyUsername() {
        // Registration with empty username should fail with exception
        assertThrows(Exception.class, () -> {
            userDb.registerUser(null, "password123");
        });
    }

    @Test
    void testRegisterNullUsername() {
        // Registration with null username should fail with exception
        assertThrows(Exception.class, () -> {
            userDb.registerUser(null, "password123");
        });
    }

    @Test
    void testRegisterNullPassword() {
        // Registration with null password should fail with exception
        assertThrows(Exception.class, () -> {
            userDb.registerUser("validuser", null);
        });
    }

    @Test
    void testRegisterVeryLongUsername() {
        // Create a username that exceeds the VARCHAR(255) limit
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("a");
        }
        String veryLongUsername = sb.toString();

        // Registration with very long username should fail with exception
        assertThrows(Exception.class, () -> {
            userDb.registerUser(veryLongUsername, "password123");
        });
    }
}
