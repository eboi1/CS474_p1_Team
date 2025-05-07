package app.finwave.backend.api.report;

import app.finwave.backend.api.ApiResponse;
import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.api.files.FilesManager;
import app.finwave.backend.api.report.data.ReportStatus;
import app.finwave.backend.api.report.data.ReportType;
import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.ReportConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.FilesRecord;
import app.finwave.backend.jooq.tables.records.ReportsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.report.ReportBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ReportApiTest extends BaseApiTest {


    @Mock private ReportDatabase reportDb;
    @Mock private ReportBuilder reportBuilder;
    @Mock private FilesManager filesManager;
    @Mock private WebSocketWorker socketWorker;
    @Mock private FilesRecord fileRecord;
    @Mock private UsersSessionsRecord sessionRecord;
    private ReportApi reportApi;
    private ReportConfig reportConfig;

    record ReportRequest(String description, TransactionsFilter filter, int type, Map<String, String> lang) {}

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
        // Setup config values
        reportConfig = new ReportConfig();
        reportConfig.maxDescriptionLength = 255;
        reportConfig.expiresDays = 7;
        Configs configs = mock(Configs.class);
        when(configs.getState(any(ReportConfig.class))).thenReturn(reportConfig);
        // Setup database worker to get ReportDatabase
        DatabaseWorker dbWorker = mock(DatabaseWorker.class);
        when(dbWorker.get(ReportDatabase.class)).thenReturn(reportDb);
        reportApi = new ReportApi(configs, dbWorker, reportBuilder, socketWorker, filesManager);

        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);

        when(reportBuilder.buildAsync(anyLong()))
                .thenReturn(CompletableFuture.completedFuture(ReportStatus.AVAILABLE));

    }

    @Test
    void testNewReport_Success() {
        // Build request JSON body (ReportRequest)
        ReportRequest reqBody = new ReportRequest("TestReport", TransactionsFilter.EMPTY, 0, Map.of());
        when(request.body()).thenReturn(ApiResponse.GSON.toJson(reqBody));
        when(filesManager.registerNewEmptyFile(eq(1), eq(reportConfig.expiresDays), eq(true), eq("reports")))
                .thenReturn(Optional.of(fileRecord));
        when(fileRecord.getId()).thenReturn("file123");
        when(reportDb.newReport(eq("TestReport"), eq(TransactionsFilter.EMPTY), eq(Map.of()), any(ReportType.class), eq(1), eq("file123")))
                .thenReturn(5L);

        Object result = reportApi.newReport(request, response);

        verify(response).status(202);
        verify(reportBuilder).buildAsync(5L);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(Long.valueOf(5L), getFieldValue(result, "reportId"));
        assertEquals("file123", getFieldValue(result, "fileId"));
    }

    @Test
    void testNewReport_FileCreationFailed() {
        ReportRequest reqBody = new ReportRequest("TestReport", TransactionsFilter.EMPTY, 0, Map.of());
        when(request.body()).thenReturn(ApiResponse.GSON.toJson(reqBody));
        when(filesManager.registerNewEmptyFile(eq(1), anyInt(), eq(true), eq("reports")))
                .thenReturn(Optional.empty());

        assertThrows(HaltException.class, () -> reportApi.newReport(request, response), "Should halt if file creation fails");
    }

    @Test
    void testGetList_ReturnsReports() {
        // Prepare some ReportsRecord
        ReportsRecord rep = new ReportsRecord();
        rep.setId(10L);
        rep.setUserId(1);
        rep.setDescription("MyReport");       // non-null description
        rep.setStatus((short) 0);             // some valid status code
        rep.setType((short) 0);               // some valid type code
        rep.setFileId("file123");             // non-null fileId

        List<ReportsRecord> records = List.of(rep);
        when(reportDb.getReports(1)).thenReturn(records);

        Object result = reportApi.getList(request, response);

        verify(response).status(200);
        // Verify GetListResponse contains the records
        List<?> list = (List<?>) getFieldValue(result, "reports");
        assertEquals(1, list.size());
        assertEquals(Long.valueOf(10L), getFieldValue(list.get(0), "reportId"));
        assertEquals("MyReport", getFieldValue(list.get(0), "description"));
        assertEquals(0, (short) getFieldValue(list.get(0), "status"));
        assertEquals(0, (short) getFieldValue(list.get(0), "type"));
        assertEquals("file123", getFieldValue(list.get(0), "fileId"));

    }
}

