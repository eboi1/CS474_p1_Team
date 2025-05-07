package app.finwave.backend.api.transaction.manager;

import app.finwave.backend.api.transaction.TransactionDatabase;
import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.api.transaction.manager.*;
import app.finwave.backend.api.transaction.manager.actions.*;
import app.finwave.backend.api.transaction.manager.data.AbstractMetadata;
import app.finwave.backend.api.transaction.manager.data.InternalTransferMetadata;
import app.finwave.backend.api.transaction.manager.data.TransactionEntry;
import app.finwave.backend.api.transaction.manager.records.*;
import app.finwave.backend.api.transaction.metadata.MetadataType;
import app.finwave.backend.database.DatabaseWorker;
import org.jooq.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static app.finwave.backend.jooq.Tables.TRANSACTIONS;
import static app.finwave.backend.jooq.Tables.TRANSACTIONS_METADATA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionsManagerTest {

    static class TestFixture {
        // Core mocks
        final DatabaseWorker databaseWorker = mock(DatabaseWorker.class);
        final DSLContext context = mock(DSLContext.class);
        final TransactionDatabase transactionDB = mock(TransactionDatabase.class);

        // Action workers
        final DefaultActionsWorker defaultWorker = mock(DefaultActionsWorker.class);
        final InternalActionsWorker internalWorker = mock(InternalActionsWorker.class);
        final RecurringActionsWorker recurringWorker = mock(RecurringActionsWorker.class);
        final AccumulationActionsWorker accumulationWorker = mock(AccumulationActionsWorker.class);

        // Test records
        final org.jooq.Record dbRecord = mock(org.jooq.Record.class);
        final BulkTransactionsRecord bulkRecord = mock(BulkTransactionsRecord.class);

        // Manager instance
        TransactionsManager manager;

        TestFixture configure() throws Exception {
            // Database configuration
            when(databaseWorker.getDefaultContext()).thenReturn(context);
            when(databaseWorker.get(TransactionDatabase.class)).thenReturn(transactionDB);

            // Transactional behavior
            when(context.transactionResult(any(TransactionalCallable.class))).thenAnswer(inv ->
                    inv.<TransactionalCallable<Long>>getArgument(0).run(mock(Configuration.class))
            );

            // Initialize real manager with mocked dependencies
            manager = new TransactionsManager(databaseWorker);

            // Inject mock workers via reflection to avoid constructor issues
            setField(manager, "defaultActionsWorker", defaultWorker);
            setField(manager, "internalActionsWorker", internalWorker);
            setField(manager, "recurringActionsWorker", recurringWorker);
            setField(manager, "accumulationActionsWorker", accumulationWorker);

            // Configure default record behavior
            when(dbRecord.get(TRANSACTIONS.ID)).thenReturn(1L);
            when(dbRecord.get(TRANSACTIONS_METADATA.TYPE)).thenReturn(null);
            when(transactionDB.getTransaction(anyLong())).thenReturn(Optional.of(dbRecord));

            when(dbRecord.get(TRANSACTIONS.ACCOUNT_ID)).thenReturn(2L);
            when(dbRecord.get(TRANSACTIONS.CATEGORY_ID)).thenReturn(3L);
            when(dbRecord.get(TRANSACTIONS.DELTA)).thenReturn(BigDecimal.TEN);
            when(dbRecord.get(TRANSACTIONS.CREATED_AT)).thenReturn(OffsetDateTime.now());

            // Configure worker mocks to do nothing
            doNothing().when(defaultWorker).cancel(any(), any());
            doNothing().when(internalWorker).cancel(any(), any());
            doNothing().when(recurringWorker).cancel(any(), any());
            doNothing().when(accumulationWorker).cancel(any(), any());

            // Stub apply methods
            when(defaultWorker.apply(any(), any())).thenReturn(1L);
            when(internalWorker.apply(any(), any())).thenReturn(1L);

            doReturn(1L).when(defaultWorker).apply(any(), any(TransactionNewRecord.class));
            doReturn(1L).when(internalWorker).apply(any(), any(TransactionNewInternalRecord.class));

            // Stub prepareEntry method
            TransactionEntry<?> mockEntry = mock(TransactionEntry.class);
            when(mockEntry.transactionId).thenReturn(1L);

            return this;
        }

        private void setField(Object target, String fieldName, Object value) throws Exception {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private TestFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture = new TestFixture().configure();
    }

    @Test
    void applyTransaction_shouldUseDefaultWorker() {
        // Setup
        TransactionNewRecord record = validTransactionRecord();
        when(fixture.defaultWorker.apply(any(), any())).thenReturn(123L);

        // Execute
        long result = fixture.manager.applyTransaction(record);

        // Verify
        assertEquals(123L, result);
        verify(fixture.defaultWorker).apply(any(), eq(record));
    }

    @Test
    void applyInternalTransfer_shouldUseInternalWorker() {
        // Setup
        TransactionNewInternalRecord record = validInternalRecord();
        when(fixture.internalWorker.apply(any(), any())).thenReturn(456L);

        // Execute
        long result = fixture.manager.applyInternalTransfer(record);

        // Verify
        assertEquals(456L, result);
        verify(fixture.internalWorker).apply(any(), eq(record));
    }

    @Test
    void applyBulkTransactions_shouldHandleMixedRecords() {
        // Create actual records
        TransactionNewRecord transactionRecord = validTransactionRecord();
        TransactionNewInternalRecord internalRecord = validInternalRecord();
        List<Object> records = List.of(transactionRecord, internalRecord);

        // Configure mock
        doReturn(records).when(fixture.bulkRecord).toRecords(anyInt());

        // Execute
        fixture.manager.applyBulkTransactions(fixture.bulkRecord, 1);

        // Verify
        verify(fixture.defaultWorker).apply(any(), eq(transactionRecord));
        verify(fixture.internalWorker).apply(any(), eq(internalRecord));
    }


    @Test
    void cancelTransaction_shouldHandleDifferentMetadataTypes() {
        // Test each metadata type separately
        testCancelForMetadataType(MetadataType.WITHOUT_METADATA);
        testCancelForMetadataType(MetadataType.INTERNAL_TRANSFER);
        testCancelForMetadataType(MetadataType.RECURRING);
        testCancelForMetadataType(MetadataType.HAS_ACCUMULATION);
    }

    private void testCancelForMetadataType(MetadataType type) {
        // Get the appropriate worker
        TransactionActionsWorker<?,?,?> worker = getWorkerForType(type);

        // Setup
        when(fixture.dbRecord.get(TRANSACTIONS_METADATA.TYPE)).thenReturn(
                switch(type) {
                    case WITHOUT_METADATA -> null;
                    case INTERNAL_TRANSFER -> (short) 1;
                    case RECURRING -> (short) 2;
                    case HAS_ACCUMULATION -> (short) 3;
                }
        );

        // Execute
        fixture.manager.cancelTransaction(1L);

        // Verify
        verify(worker).cancel(any(), any());
    }

    @Test
    void getTransactions_shouldUseCorrectWorkerForMetadata() {
        // Configure test data
        when(fixture.transactionDB.getTransactions(anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(List.of(fixture.dbRecord));

        // Test all metadata types
        Arrays.stream(MetadataType.values()).forEach(type -> {
            // Configure metadata type
            when(fixture.dbRecord.get(TRANSACTIONS_METADATA.TYPE)).thenReturn(
                    switch(type) {
                        case WITHOUT_METADATA -> null;
                        case INTERNAL_TRANSFER -> (short) 1;
                        case RECURRING -> (short) 2;
                        case HAS_ACCUMULATION -> (short) 3;
                    }
            );

            // Execute
            List<TransactionEntry<?>> result = fixture.manager.getTransactions(1, 0, 10, TransactionsFilter.EMPTY);

            // Verify
            assertFalse(result.isEmpty());
            TransactionActionsWorker<?,?,?> worker = getWorkerForType(type);
            verify(worker).prepareEntry(any(), any(), any());
        });
    }

    private TransactionActionsWorker<?,?,?> getWorkerForType(MetadataType type) {
        return switch (type) {
            case WITHOUT_METADATA -> fixture.defaultWorker;
            case INTERNAL_TRANSFER -> fixture.internalWorker;
            case RECURRING -> fixture.recurringWorker;
            case HAS_ACCUMULATION -> fixture.accumulationWorker;
        };
    }

    // Helper methods for creating test records
    private TransactionNewRecord validTransactionRecord() {
        return new TransactionNewRecord(
                1, 2L, 3L, OffsetDateTime.now(), BigDecimal.TEN, "Test"
        );
    }

    private TransactionNewInternalRecord validInternalRecord() {
        return new TransactionNewInternalRecord(
                1, 2L, 3L, 4L, OffsetDateTime.now(),
                BigDecimal.valueOf(-10), BigDecimal.valueOf(10), "Transfer"
        );
    }
}