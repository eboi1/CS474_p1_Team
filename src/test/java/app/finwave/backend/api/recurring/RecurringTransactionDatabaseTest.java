package app.finwave.backend.api.recurring;

import app.finwave.backend.api.recurring.NotificationMode;
import app.finwave.backend.api.recurring.RepeatType;
import app.finwave.backend.jooq.tables.records.RecurringTransactionsRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

public class RecurringTransactionDatabaseTest {

    private DSLContext ctx;
    private RecurringTransactionDatabase recurringDb;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        ctx = DSL.using(connection, SQLDialect.H2);
        // Create necessary tables (ACCOUNTS, RECURRING_TRANSACTIONS)
        ctx.execute("CREATE TABLE ACCOUNTS (ID INT PRIMARY KEY, CURRENCY_ID INT);");
        ctx.execute("CREATE TABLE RECURRING_TRANSACTIONS (" +
                "ID SERIAL PRIMARY KEY, OWNER_ID INT, CATEGORY_ID INT, ACCOUNT_ID INT, CURRENCY_ID INT, " +
                "REPEAT_FUNC SMALLINT, REPEAT_FUNC_ARG SMALLINT, NOTIFICATION_MODE SMALLINT, " +
                "LAST_REPEAT TIMESTAMP, NEXT_REPEAT TIMESTAMP, DELTA DECIMAL, DESCRIPTION VARCHAR(255));");
        // Insert an account for currency lookup
        ctx.execute("INSERT INTO ACCOUNTS (ID, CURRENCY_ID) VALUES (1, 100);");
        recurringDb = new RecurringTransactionDatabase(ctx);

        // In setup
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ctx.execute("SET TIME ZONE 'UTC';");
    }

    @AfterEach
    void tearDown() throws SQLException {
        ctx.execute("DROP ALL OBJECTS");
        connection.close();
    }

    @Test
    void testNewAndGetRecurring() {
        Optional<Long> idOpt = recurringDb.newRecurring(1, 10L, 1L, RepeatType.IN_DAYS, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now().plusDays(1), new BigDecimal("10.00"), "desc");
        assertTrue(idOpt.isPresent());
        long id = idOpt.get();
        List<RecurringTransactionsRecord> list = recurringDb.getList(1);
        assertTrue(list.stream().anyMatch(r -> r.getId() == id));
    }

    @Test
    void testEditRecurring() {
        long id = recurringDb.newRecurring(1, 10L, 1L, RepeatType.IN_DAYS, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now().plusDays(1), new BigDecimal("5.00"), null).get();
        recurringDb.editRecurring(id, 20L, 1L, RepeatType.WEEKLY, (short)2, NotificationMode.SILENT, OffsetDateTime.now().plusDays(7), new BigDecimal("5.00"), "updated");
        // Verify via getList that fields changed
        RecurringTransactionsRecord rec = recurringDb.getList(1).stream().filter(r -> r.getId() == id).findFirst().get();
        assertEquals(20L, rec.getCategoryId());
        assertEquals("updated", rec.getDescription());
    }

    @Test
    void testDeleteRecurring() {
        long id = recurringDb.newRecurring(1, 10L, 1L, RepeatType.MONTHLY, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now().plusDays(30), new BigDecimal("1.00"), null).get();
        recurringDb.deleteRecurring(id);
        assertFalse(recurringDb.userOwnRecurringTransaction(1, id), "Recurring should be deleted");
    }

    @Test
    void testUserOwnRecurringTransaction() {
        long id = recurringDb.newRecurring(2, 10L, 1L, RepeatType.IN_DAYS, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now().plusDays(1), new BigDecimal("2.00"), null).get();
        assertTrue(recurringDb.userOwnRecurringTransaction(2, id));
        assertFalse(recurringDb.userOwnRecurringTransaction(1, id));
    }

    @Test
    void testAccountAffected() {
        recurringDb.newRecurring(1, 10L, 1L, RepeatType.IN_DAYS, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now().plusDays(1), new BigDecimal("3.00"), null);
        assertTrue(recurringDb.accountAffected(1L));
        assertFalse(recurringDb.accountAffected(999L));
    }

    @Test
    void testUpdateRecurring() {
        long id = recurringDb.newRecurring(1, 10L, 1L, RepeatType.IN_DAYS, (short)1, NotificationMode.WITHOUT, OffsetDateTime.now(), new BigDecimal("4.00"), null).get();
        OffsetDateTime now = OffsetDateTime.now();
        recurringDb.updateRecurring(id, now, now.plusDays(1));
        RecurringTransactionsRecord rec = recurringDb.getList(1).stream().filter(r -> r.getId() == id).findFirst().get();
        assertEquals(now.toInstant().toEpochMilli(), rec.getLastRepeat().toInstant().toEpochMilli(), 1000, "Last repeat should be updated");
    }
}

