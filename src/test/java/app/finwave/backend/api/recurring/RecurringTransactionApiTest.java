package app.finwave.backend.api.recurring;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.account.AccountDatabase;
import app.finwave.backend.api.category.CategoryDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.RecurringTransactionConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.RecurringTransactionsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.api.transaction.manager.TransactionsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecurringTransactionApiTest extends BaseApiTest {

    @Mock private UsersSessionsRecord sessionRecord;
    @Mock private RecurringTransactionDatabase recurringDb;
    @Mock private TransactionsManager transactionsManager;
    @Mock private CategoryDatabase categoryDb;
    @Mock private AccountDatabase accountDb;
    @Mock private WebSocketWorker socketWorker;
    private RecurringTransactionApi recurringApi;
    
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
        RecurringTransactionConfig config = new RecurringTransactionConfig();
        config.maxDescriptionLength = 100;
        Configs configs = mock(Configs.class);
        when(configs.getState(any(RecurringTransactionConfig.class))).thenReturn(config);
        DatabaseWorker dbWorker = mock(DatabaseWorker.class);
        when(dbWorker.get(RecurringTransactionDatabase.class)).thenReturn(recurringDb);
        when(dbWorker.get(CategoryDatabase.class)).thenReturn(categoryDb);
        when(dbWorker.get(AccountDatabase.class)).thenReturn(accountDb);
        recurringApi = new RecurringTransactionApi(dbWorker, transactionsManager, configs, socketWorker);
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
    }

    @Test
    void testNewRecurringTransaction_Success() {
        when(request.queryParams("categoryId")).thenReturn("1");
        when(request.queryParams("accountId")).thenReturn("2");
        when(request.queryParams("nextRepeat")).thenReturn(OffsetDateTime.now().plusDays(1).toString());
        when(request.queryParams("repeatType")).thenReturn("0");
        when(request.queryParams("repeatArg")).thenReturn("1");
        when(request.queryParams("notificationMode")).thenReturn("0");
        when(request.queryParams("delta")).thenReturn("100.00");
        when(request.queryParams("description")).thenReturn("Paycheck");


        when(categoryDb.userOwnCategory(1, 1L)).thenReturn(true);
        when(accountDb.userOwnAccount(1, 2L)).thenReturn(true);
        when(recurringDb.newRecurring(eq(1), eq(1L), eq(2L), any(RepeatType.class), eq((short)1), any(NotificationMode.class), any(OffsetDateTime.class), eq(new BigDecimal("100.00")), eq("Paycheck")))
                .thenReturn(Optional.of(10L));

        Object result = recurringApi.newRecurringTransaction(request, response);

        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(201);
        assertEquals(Long.valueOf(10L), getFieldValue(result, "recurringTransactionId"));
    }

    @Test
    void testNewRecurringTransaction_InvalidOwnership() {
        // Category not owned by user
        when(request.queryParams("categoryId")).thenReturn("1");
        when(request.queryParams("accountId")).thenReturn("2");
        when(categoryDb.userOwnCategory(1, 1L)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> recurringApi.newRecurringTransaction(request, response));

        // Account not owned by user
        when(categoryDb.userOwnCategory(1, 1L)).thenReturn(true);
        when(accountDb.userOwnAccount(1, 2L)).thenReturn(false);
        assertThrows(InvalidParameterException.class, () -> recurringApi.newRecurringTransaction(request, response));
    }

    @Test
    void testEditRecurringTransaction_Success() {
        when(request.queryParams("recurringTransactionId")).thenReturn("5");
        when(request.queryParams("categoryId")).thenReturn("1");
        when(request.queryParams("accountId")).thenReturn("2");
        when(request.queryParams("nextRepeat")).thenReturn(OffsetDateTime.now().plusDays(2).toString());
        when(request.queryParams("repeatType")).thenReturn("1");
        when(request.queryParams("repeatArg")).thenReturn("2");
        when(request.queryParams("notificationMode")).thenReturn("1");
        when(request.queryParams("delta")).thenReturn("50.00");
        when(request.queryParams("description")).thenReturn("Updated");

        when(recurringDb.userOwnRecurringTransaction(1, 5L)).thenReturn(true);
        when(categoryDb.userOwnCategory(1, 1L)).thenReturn(true);
        when(accountDb.userOwnAccount(1, 2L)).thenReturn(true);

        Object result = recurringApi.editRecurringTransaction(request, response);

        verify(recurringDb).editRecurring(eq(5L), eq(1L), eq(2L), any(RepeatType.class), eq((short)2), any(NotificationMode.class), any(OffsetDateTime.class), eq(new BigDecimal("50.00")), eq("Updated"));
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditRecurringTransaction_NotOwner() {
        when(request.queryParams("recurringTransactionId")).thenReturn("6");
        when(recurringDb.userOwnRecurringTransaction(1, 6L)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> recurringApi.editRecurringTransaction(request, response));
        verify(recurringDb, never()).editRecurring(anyLong(), anyLong(), anyLong(), any(), anyShort(), any(), any(), any(), any());
    }

    @Test
    void testDeleteRecurringTransaction_Success() {
        when(request.queryParams("recurringId")).thenReturn("7");
        when(recurringDb.userOwnRecurringTransaction(1, 7L)).thenReturn(true);

        Object result = recurringApi.deleteRecurringTransaction(request, response);

        verify(recurringDb).deleteRecurring(7L);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testDeleteRecurringTransaction_NotOwner() {
        when(request.queryParams("recurringTransactionId")).thenReturn("8");
        when(recurringDb.userOwnRecurringTransaction(1, 8L)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> recurringApi.deleteRecurringTransaction(request, response));
        verify(recurringDb, never()).deleteRecurring(anyLong());
    }

    @Test
    void testGetList_ReturnsList() {
        RecurringTransactionsRecord rec = new RecurringTransactionsRecord();
        rec.setId(11L);
        rec.setOwnerId(1);
        rec.setCategoryId(123L);
        rec.setAccountId(456L);
        rec.setCurrencyId(789L);
        rec.setLastRepeat(OffsetDateTime.now().minusDays(1));
        rec.setNextRepeat(OffsetDateTime.now().plusDays(1));
        rec.setRepeatFunc((short)0);
        rec.setRepeatFuncArg((short)1);
        rec.setNotificationMode((short) 1);
        rec.setDelta(new BigDecimal("42.00"));
        rec.setDescription("Test");

        List<RecurringTransactionsRecord> list = List.of(rec);
        when(recurringDb.getList(1)).thenReturn(list);

        Object result = recurringApi.getList(request, response);

        verify(response).status(200);
        List<?> entries = (List<?>) getFieldValue(result, "recurringList");
        assertEquals(1, entries.size());
        assertEquals(Long.valueOf(11L), getFieldValue(entries.get(0), "recurringTransactionId"));
    }
}

