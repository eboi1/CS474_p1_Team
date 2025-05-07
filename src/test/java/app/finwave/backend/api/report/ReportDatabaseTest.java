package app.finwave.backend.api.report;

import app.finwave.backend.api.report.data.ReportStatus;
import app.finwave.backend.api.report.data.ReportType;
import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.jooq.tables.records.ReportsRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ReportDatabaseTest {

    private DSLContext ctx;
    private ReportDatabase reportDb;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        ctx = DSL.using(connection, SQLDialect.H2);
        // Create REPORTS table with necessary columns
        ctx.execute("CREATE TABLE REPORTS (ID SERIAL PRIMARY KEY, DESCRIPTION TEXT, STATUS SMALLINT, TYPE SMALLINT, FILTER JSON, LANG JSON, USER_ID INT, FILE_ID VARCHAR(255));");
        reportDb = new ReportDatabase(ctx);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ctx.execute("DROP ALL OBJECTS");
        connection.close();
    }

    @Test
    void testNewAndGetReport() {
        long id = reportDb.newReport("Desc", TransactionsFilter.EMPTY, Map.of(), ReportType.BY_MONTHS, 1, "file1");
        assertTrue(id > 0, "New report should return an ID");
        Optional<ReportsRecord> recOpt = reportDb.getReport(id);
        assertTrue(recOpt.isPresent());
        assertEquals("Desc", recOpt.get().getDescription());
    }

    @Test
    void testUpdateReportStatus() {
        long id = reportDb.newReport("Test", TransactionsFilter.EMPTY, Map.of(), ReportType.BY_MONTHS, 1, "fileX");
        reportDb.updateReport(id, ReportStatus.AVAILABLE);
        Optional<ReportsRecord> recOpt = reportDb.getReport(id);
        assertEquals(ReportStatus.AVAILABLE.getShort(), recOpt.get().getStatus());
    }

    @Test
    void testGetReports_ByUser() {
        reportDb.newReport("R1", TransactionsFilter.EMPTY, Map.of(), ReportType.BY_MONTHS, 2, "f1");
        reportDb.newReport("R2", TransactionsFilter.EMPTY, Map.of(), ReportType.BY_MONTHS, 1, "f2");
        List<ReportsRecord> list = reportDb.getReports(1);
        assertEquals(1, list.size());
        assertEquals("R2", list.get(0).getDescription());
    }

    @Test
    void testRemoveReport() {
        long id = reportDb.newReport("Del", TransactionsFilter.EMPTY, Map.of(), ReportType.BY_MONTHS, 1, "f3");
        reportDb.removeReport(id);
        Optional<ReportsRecord> recOpt = reportDb.getReport(id);
        assertTrue(recOpt.isEmpty(), "Report should be removed");
    }
}
