package app.finwave.backend.api.transaction;

import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.jooq.tables.records.TransactionsRecord;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static app.finwave.backend.jooq.Tables.TRANSACTIONS;
import static app.finwave.backend.jooq.Tables.TRANSACTIONS_METADATA;
import static org.jooq.SQLDialect.H2;
import static org.jooq.SQLDialect.POSTGRES;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for TransactionDatabase using both Mockito
 * and jOOQ's MockConnection for different testing scenarios.
 */
class TransactionDatabaseTest {

    // Common test constants
    private static final int USER_ID = 1;
    private static final long CATEGORY_ID = 123;
    private static final long ACCOUNT_ID = 456;
    private static final long CURRENCY_ID = 789;
    private static final long TRANSACTION_ID = 1L;
    private static final BigDecimal DELTA = BigDecimal.valueOf(100.50);
    private static final String DESCRIPTION = "Test Transaction";

    @Nested
    class MockitoTests {
        @Mock
        private DSLContext dslContext;

        private TransactionDatabase transactionDatabase;

        @Captor
        private ArgumentCaptor<Condition> conditionCaptor;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
            transactionDatabase = new TransactionDatabase(dslContext);
        }

        @Test
        void testApplyTransactionSuccessful() {
            // Setup parameters
            OffsetDateTime created = OffsetDateTime.now();
            Record1<Long> idRecord = setupRecord1WithId(TRANSACTION_ID);

            // Mock the JOOQ chain
            InsertSetStep<TransactionsRecord> step1 = mock(InsertSetStep.class);
            InsertSetMoreStep<TransactionsRecord> step2 = mock(InsertSetMoreStep.class);
            InsertResultStep<Record1<Long>> resultStep = mock(InsertResultStep.class);

            when(dslContext.insertInto(TRANSACTIONS)).thenReturn(step1);
            when(step1.set(eq(TRANSACTIONS.OWNER_ID), eq(USER_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CATEGORY_ID), eq(CATEGORY_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.ACCOUNT_ID), eq(ACCOUNT_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CURRENCY_ID), eq(CURRENCY_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CREATED_AT), any(OffsetDateTime.class))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.DELTA), eq(DELTA))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.DESCRIPTION), eq(DESCRIPTION))).thenReturn(step2);
            when(step2.returningResult(TRANSACTIONS.ID)).thenReturn(resultStep);
            when(resultStep.fetchOptional()).thenReturn(Optional.of(idRecord));

            // Execute
            Optional<Long> transactionId = transactionDatabase.applyTransaction(
                    USER_ID, CATEGORY_ID, ACCOUNT_ID, CURRENCY_ID, created, DELTA, DESCRIPTION);

            // Verify
            assertTrue(transactionId.isPresent());
            assertEquals(TRANSACTION_ID, transactionId.get());
            verify(dslContext).insertInto(TRANSACTIONS);
        }

        @Test
        void testApplyTransactionFailure() {
            // Setup parameters
            OffsetDateTime created = OffsetDateTime.now();

            // Mock the JOOQ chain
            InsertSetStep<TransactionsRecord> step1 = mock(InsertSetStep.class);
            InsertSetMoreStep<TransactionsRecord> step2 = mock(InsertSetMoreStep.class);
            InsertResultStep<Record1<Long>> resultStep = mock(InsertResultStep.class);

            when(dslContext.insertInto(TRANSACTIONS)).thenReturn(step1);
            when(step1.set(eq(TRANSACTIONS.OWNER_ID), eq(USER_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CATEGORY_ID), eq(CATEGORY_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.ACCOUNT_ID), eq(ACCOUNT_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CURRENCY_ID), eq(CURRENCY_ID))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.CREATED_AT), any(OffsetDateTime.class))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.DELTA), eq(DELTA))).thenReturn(step2);
            when(step2.set(eq(TRANSACTIONS.DESCRIPTION), eq(DESCRIPTION))).thenReturn(step2);
            when(step2.returningResult(TRANSACTIONS.ID)).thenReturn(resultStep);
            when(resultStep.fetchOptional()).thenReturn(Optional.empty());

            // Execute
            Optional<Long> transactionId = transactionDatabase.applyTransaction(
                    USER_ID, CATEGORY_ID, ACCOUNT_ID, CURRENCY_ID, created, DELTA, DESCRIPTION);

            // Verify
            assertFalse(transactionId.isPresent());
            verify(dslContext).insertInto(TRANSACTIONS);
        }

        @Test
        void testDeleteTransaction() {
            // Setup
            DeleteUsingStep<TransactionsRecord> mockDeleteUsing = mock(DeleteUsingStep.class);
            DeleteConditionStep<TransactionsRecord> mockDelete = mock(DeleteConditionStep.class);

            when(dslContext.deleteFrom(TRANSACTIONS)).thenReturn(mockDeleteUsing);
            when(mockDeleteUsing.where(any(Condition.class))).thenReturn(mockDelete);
            when(mockDelete.execute()).thenReturn(1);

            // Execute
            transactionDatabase.deleteTransaction(TRANSACTION_ID);

            // Verify
            verify(dslContext).deleteFrom(TRANSACTIONS);
            verify(mockDeleteUsing).where(any(Condition.class));
            verify(mockDelete).execute();
        }

        @Test
        void testEditTransaction() {
            // Setup
            OffsetDateTime created = OffsetDateTime.now();
            UpdateSetFirstStep<TransactionsRecord> mockUpdateFirst = mock(UpdateSetFirstStep.class);
            UpdateSetMoreStep<TransactionsRecord> mockUpdateSet = mock(UpdateSetMoreStep.class);
            UpdateConditionStep<TransactionsRecord> mockUpdate = mock(UpdateConditionStep.class);

            when(dslContext.update(TRANSACTIONS)).thenReturn(mockUpdateFirst);
            when(mockUpdateFirst.set(eq(TRANSACTIONS.CATEGORY_ID), eq(CATEGORY_ID))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.set(eq(TRANSACTIONS.ACCOUNT_ID), eq(ACCOUNT_ID))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.set(eq(TRANSACTIONS.CURRENCY_ID), eq(CURRENCY_ID))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.set(eq(TRANSACTIONS.CREATED_AT), eq(created))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.set(eq(TRANSACTIONS.DELTA), eq(DELTA))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.set(eq(TRANSACTIONS.DESCRIPTION), eq(DESCRIPTION))).thenReturn(mockUpdateSet);
            when(mockUpdateSet.where(any(Condition.class))).thenReturn(mockUpdate);
            when(mockUpdate.execute()).thenReturn(1);

            // Execute
            transactionDatabase.editTransaction(TRANSACTION_ID, CATEGORY_ID, ACCOUNT_ID, CURRENCY_ID,
                    created, DELTA, DESCRIPTION);

            // Verify
            verify(dslContext).update(TRANSACTIONS);
            verify(mockUpdateSet).where(any(Condition.class));
            verify(mockUpdate).execute();
        }

        @Test
        void testUserOwnTransaction_True() {
            // Setup
            SelectSelectStep<Record1<Long>> mockSelectStep = mock(SelectSelectStep.class);
            SelectJoinStep<Record1<Long>> mockSelectJoin = mock(SelectJoinStep.class);
            SelectConditionStep<Record1<Long>> mockSelectCondition = mock(SelectConditionStep.class);

            when(dslContext.select(TRANSACTIONS.ID)).thenReturn(mockSelectStep);
            when(mockSelectStep.from(TRANSACTIONS)).thenReturn(mockSelectJoin);
            when(mockSelectJoin.where(any(Condition.class))).thenReturn(mockSelectCondition);
            when(mockSelectCondition.fetchOptional()).thenReturn(Optional.of(mock(Record1.class)));

            // Execute
            boolean result = transactionDatabase.userOwnTransaction(USER_ID, TRANSACTION_ID);

            // Verify
            assertTrue(result);
        }

        @Test
        void testUserOwnTransaction_False() {
            // Setup
            SelectSelectStep<Record1<Long>> mockSelectStep = mock(SelectSelectStep.class);
            SelectJoinStep<Record1<Long>> mockSelectJoin = mock(SelectJoinStep.class);
            SelectConditionStep<Record1<Long>> mockSelectCondition = mock(SelectConditionStep.class);

            when(dslContext.select(TRANSACTIONS.ID)).thenReturn(mockSelectStep);
            when(mockSelectStep.from(TRANSACTIONS)).thenReturn(mockSelectJoin);
            when(mockSelectJoin.where(any(Condition.class))).thenReturn(mockSelectCondition);
            when(mockSelectCondition.fetchOptional()).thenReturn(Optional.empty());

            // Execute
            boolean result = transactionDatabase.userOwnTransaction(USER_ID, TRANSACTION_ID);

            // Verify
            assertFalse(result);
        }

        // Helper methods to reduce code duplication
        private Record1<Long> setupRecord1WithId(long id) {
            Record1<Long> idRecord = DSL.using(H2).newRecord(TRANSACTIONS.ID);
            idRecord.set(TRANSACTIONS.ID, id);
            return idRecord;
        }
    }

    @Nested
    class MockConnectionTests {
        private DSLContext context;
        private TransactionDatabase transactionDatabase;
        private MockDataProvider provider;

        @BeforeEach
        void setUp() {
            provider = mock(MockDataProvider.class);
            MockConnection connection = new MockConnection(provider);
            context = DSL.using(connection, POSTGRES);
            transactionDatabase = new TransactionDatabase(context);
        }

        @Test
        void testApplyTransaction_Success() throws SQLException {
            // Setup mock response for successful insert
            Record1<Long> idRecord = context.newRecord(TRANSACTIONS.ID);
            idRecord.set(TRANSACTIONS.ID, 123L);
            Result<Record1<Long>> result = context.newResult(TRANSACTIONS.ID);
            result.add(idRecord);
            
            when(provider.execute(any(MockExecuteContext.class)))
                    .thenReturn(new MockResult[]{new MockResult(1, result)});
            
            // Execute
            Optional<Long> id = transactionDatabase.applyTransaction(
                    USER_ID,
                    CATEGORY_ID,
                    ACCOUNT_ID,
                    CURRENCY_ID,
                    OffsetDateTime.now(),
                    DELTA,
                    DESCRIPTION
            );
            
            // Verify
            assertTrue(id.isPresent());
            assertEquals(123L, id.get());
        }

        @Test
        void testGetTransaction_Found() throws SQLException {
            // Create a result set with the exact query structure
            TableField<?, ?>[] allFields = Stream.of(TRANSACTIONS.fields(), TRANSACTIONS_METADATA.fields())
                                               .flatMap(Arrays::stream)
                                               .toArray(TableField[]::new);
        
            Result<Record> result = context.newResult(allFields);
            
            // Create a record with all required fields
            Record record = context.newRecord(allFields);
            record.set(TRANSACTIONS.ID, 123L);
            record.set(TRANSACTIONS.OWNER_ID, USER_ID);
            record.set(TRANSACTIONS.CATEGORY_ID, CATEGORY_ID);
            record.set(TRANSACTIONS.ACCOUNT_ID, ACCOUNT_ID);
            record.set(TRANSACTIONS.CURRENCY_ID, CURRENCY_ID);
            record.set(TRANSACTIONS.CREATED_AT, OffsetDateTime.now());
            record.set(TRANSACTIONS.DELTA, DELTA);
            record.set(TRANSACTIONS.DESCRIPTION, DESCRIPTION);
            record.set(TRANSACTIONS.METADATA_ID, null);
            
            // Set null values for metadata table fields
            for (Field<?> field : TRANSACTIONS_METADATA.fields()) {
                record.set(field, null);
            }
            
            result.add(record);
            
            when(provider.execute(any(MockExecuteContext.class)))
                .thenReturn(new MockResult[]{new MockResult(1, result)});
        
            Optional<Record> transaction = transactionDatabase.getTransaction(123L);
            assertTrue(transaction.isPresent());
            assertEquals(123L, transaction.get().get(TRANSACTIONS.ID));
        }

        @Test
        void testGetTransactions() throws SQLException {
            // Create a result set with the exact query structure
            TableField<?, ?>[] allFields = Stream.of(TRANSACTIONS.fields(), TRANSACTIONS_METADATA.fields())
                                               .flatMap(Arrays::stream)
                                               .toArray(TableField[]::new);
        
            Result<Record> result = context.newResult(allFields);
            
            // Create two records
            Record record1 = context.newRecord(allFields);
            record1.set(TRANSACTIONS.ID, 123L);
            record1.set(TRANSACTIONS.OWNER_ID, USER_ID);
            record1.set(TRANSACTIONS.CATEGORY_ID, CATEGORY_ID);
            record1.set(TRANSACTIONS.ACCOUNT_ID, ACCOUNT_ID);
            record1.set(TRANSACTIONS.CURRENCY_ID, CURRENCY_ID);
            record1.set(TRANSACTIONS.CREATED_AT, OffsetDateTime.now());
            record1.set(TRANSACTIONS.DELTA, DELTA);
            record1.set(TRANSACTIONS.DESCRIPTION, "desc1");
            record1.set(TRANSACTIONS.METADATA_ID, null);
            
            Record record2 = context.newRecord(allFields);
            record2.set(TRANSACTIONS.ID, 456L);
            record2.set(TRANSACTIONS.OWNER_ID, USER_ID);
            record2.set(TRANSACTIONS.CATEGORY_ID, CATEGORY_ID);
            record2.set(TRANSACTIONS.ACCOUNT_ID, ACCOUNT_ID);
            record2.set(TRANSACTIONS.CURRENCY_ID, CURRENCY_ID);
            record2.set(TRANSACTIONS.CREATED_AT, OffsetDateTime.now());
            record2.set(TRANSACTIONS.DELTA, DELTA);
            record2.set(TRANSACTIONS.DESCRIPTION, "desc2");
            record2.set(TRANSACTIONS.METADATA_ID, null);
            
            // Set null values for metadata table fields
            for (Field<?> field : TRANSACTIONS_METADATA.fields()) {
                record1.set(field, null);
                record2.set(field, null);
            }
            
            result.add(record1);
            result.add(record2);

            when(provider.execute(any(MockExecuteContext.class)))
                .thenReturn(new MockResult[]{new MockResult(2, result)});

            List<Record> transactions = transactionDatabase.getTransactions(USER_ID, 0, 10, TransactionsFilter.EMPTY);
            assertEquals(2, transactions.size());
            assertEquals(123L, transactions.get(0).get(TRANSACTIONS.ID));
            assertEquals(456L, transactions.get(1).get(TRANSACTIONS.ID));
        }

        @Test
        void testEditTransactionWithNullCreated() throws SQLException {
            // Setup mock response for update query
            when(provider.execute(any(MockExecuteContext.class)))
                    .thenReturn(new MockResult[]{new MockResult(1, null)});
            
            // Execute with null created time
            transactionDatabase.editTransaction(
                    123L,
                    CATEGORY_ID,
                    ACCOUNT_ID, 
                    CURRENCY_ID,
                    null,
                    DELTA,
                    DESCRIPTION
            );
            
            // No assert needed, just verifying it runs without errors
        }
    }

    @Nested
    class FilterConditionTests {
        private TransactionDatabase transactionDatabase;

        @BeforeEach
        void setUp() {
            DSLContext dslContext = mock(DSLContext.class);
            transactionDatabase = new TransactionDatabase(dslContext);
        }

        @Test
        void testGenerateFilterCondition_OnlyUserId() {
            // Setup
            TransactionsFilter filter = mock(TransactionsFilter.class);
            when(filter.getCategoriesIds()).thenReturn(null);
            when(filter.getAccountIds()).thenReturn(null);
            when(filter.getCurrenciesIds()).thenReturn(null);
            when(filter.getFromTime()).thenReturn(null);
            when(filter.getToTime()).thenReturn(null);
            when(filter.getDescription()).thenReturn(null);

            // Execute
            var condition = TransactionDatabase.generateFilterCondition(USER_ID, filter);

            // Verify that the condition string contains only the userId condition
            String conditionString = condition.toString();
            assertTrue(conditionString.contains(String.valueOf(USER_ID)));
            assertFalse(conditionString.contains("and"));
        }

        @Test
        void testGenerateFilterCondition_WithDescription() {
            // Setup
            TransactionsFilter filter = mock(TransactionsFilter.class);
            when(filter.getCategoriesIds()).thenReturn(null);
            when(filter.getAccountIds()).thenReturn(null);
            when(filter.getCurrenciesIds()).thenReturn(null);
            when(filter.getFromTime()).thenReturn(null);
            when(filter.getToTime()).thenReturn(null);
            when(filter.getDescription()).thenReturn("test description");

            // Execute
            var condition = TransactionDatabase.generateFilterCondition(USER_ID, filter);

            // Verify
            String conditionString = condition.toString();
            assertTrue(conditionString.contains(String.valueOf(USER_ID)));
            assertTrue(conditionString.contains("test description"));
        }

        @Test
        void testGenerateFilterCondition_WithAllFilters() {
            // Setup
            TransactionsFilter filter = mock(TransactionsFilter.class);
            List<Long> categoryIds = Arrays.asList(1L, 2L);
            List<Long> accountIds = Arrays.asList(3L, 4L);
            List<Long> currencyIds = Arrays.asList(5L, 6L);
            OffsetDateTime fromTime = OffsetDateTime.now().minusDays(7);
            OffsetDateTime toTime = OffsetDateTime.now();

            when(filter.getCategoriesIds()).thenReturn(categoryIds);
            when(filter.getAccountIds()).thenReturn(accountIds);
            when(filter.getCurrenciesIds()).thenReturn(currencyIds);
            when(filter.getFromTime()).thenReturn(fromTime);
            when(filter.getToTime()).thenReturn(toTime);
            when(filter.getDescription()).thenReturn("test description");

            // Execute
            var condition = TransactionDatabase.generateFilterCondition(USER_ID, filter);

            // Verify
            String conditionString = condition.toString();
            assertTrue(conditionString.contains(String.valueOf(USER_ID)));
            assertTrue(conditionString.contains("test description"));
            assertTrue(conditionString.contains("1"));  // category ID
            assertTrue(conditionString.contains("3"));  // account ID
            assertTrue(conditionString.contains("5"));  // currency ID
        }

        @Test
        void testGenerateFilterAnyCondition() {
            List<Long> values = Arrays.asList(1L, 2L, 3L);
            Condition condition = TransactionDatabase.generateFilterAnyCondition(
                TRANSACTIONS.CATEGORY_ID, 
                values
            );

            String sql = condition.toString();
            assertAll(
                () -> assertTrue(sql.contains("\"category_id\" = 1")),
                () -> assertTrue(sql.contains("\"category_id\" = 2")),
                () -> assertTrue(sql.contains("\"category_id\" = 3")),
                () -> assertTrue(sql.contains(" or "))
            );
        }
    }
}