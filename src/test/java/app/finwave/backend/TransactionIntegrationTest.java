package app.finwave.backend;

import app.finwave.backend.api.account.AccountDatabase;
import app.finwave.backend.api.category.CategoryDatabase;
import app.finwave.backend.api.currency.CurrencyDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.api.transaction.TransactionApi;
import app.finwave.backend.api.transaction.TransactionDatabase;
import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.api.transaction.manager.TransactionsManager;
import app.finwave.backend.api.transaction.manager.data.TransactionEntry;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.TransactionConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.CategoriesRecord;
import app.finwave.backend.jooq.tables.records.TransactionsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.JsonObject;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test for transaction-related functionality.
 * This test verifies the interaction between transaction components.
 * Uses SQL tables and fixtures from the resources folder.
 */
@ExtendWith(MockitoExtension.class)
class TransactionIntegrationTest extends BaseIntegrationTest {

    @Mock
    private WebSocketWorker webSocketWorker;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private DSLContext dslContext;
    
    @Mock
    private Configs configs;
    
    @Mock
    private TransactionsManager transactionsManager;
    
    @Mock
    private AccountDatabase accountDatabase;
    
    @Mock
    private TransactionDatabase transactionDatabase;
    
    @Mock
    private CategoryDatabase categoryDatabase;
    
    @Mock
    private CurrencyDatabase currencyDatabase;
    
    @Mock
    private TransactionConfig transactionConfig;
    
    private TransactionApi transactionApi;
    
    private long accountId;
    private long categoryId;
    
    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // Load transaction fixture data
        setupRequestFromFixture("transaction-create.json");
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("transaction-create.json");
        accountId = fixture.get("accountId").getAsLong();
        categoryId = fixture.get("categoryId").getAsLong();
        BigDecimal delta = fixture.get("delta").getAsBigDecimal();
        String description = fixture.get("description").getAsString();

        // 1) make getState(...) produce a real config
        transactionConfig.maxDescriptionLength = 250;
        transactionConfig.maxTransactionsInListPerRequest = 100;
        when(configs.getState(any(TransactionConfig.class)))
            .thenReturn(transactionConfig);

        // 2) databaseWorker → our mocked DAOs
        when(databaseWorker.get(AccountDatabase.class)).thenReturn(accountDatabase);
        when(databaseWorker.get(CategoryDatabase.class)).thenReturn(categoryDatabase);

        // 3) session → userId == 1L
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);

        // 4) simple ownership/permission checks
        when(accountDatabase.userOwnAccount(anyInt(), anyLong())).thenReturn(true);
        when(categoryDatabase.getCategory(anyLong()))
            .thenReturn(Optional.of(mock(CategoriesRecord.class)));
        when(categoryDatabase.userOwnCategory(anyInt(), anyLong())).thenReturn(true);

        // 5) now construct the API under test
        transactionApi = new TransactionApi(transactionsManager, databaseWorker, configs, webSocketWorker);

        // satisfy the filter's constructor:
        when(request.queryParams("categoriesIds")).thenReturn(null);
        when(request.queryParams("accountsIds")).thenReturn(null);
        when(request.queryParams("currenciesIds")).thenReturn(null);
        when(request.queryParams("fromTime")).thenReturn(null);
        when(request.queryParams("toTime")).thenReturn(null);
        when(request.queryParams("description")).thenReturn(null);

        // pagination params for getTransactions():
        when(request.queryParams("offset")).thenReturn("0");
        when(request.queryParams("count")) .thenReturn("10");
    }
    
    @Test
    void testCreateAndFetchTransaction() {
        // Load the transaction fixture data
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("transaction-create.json");
        BigDecimal delta = fixture.get("delta").getAsBigDecimal();
        String description = fixture.get("description").getAsString();
        
        // Mock the manager's transaction processing
        long newTransactionId = 5;
        when(transactionsManager.applyTransaction(any())).thenReturn(newTransactionId);
        
        // Execute transaction creation
        Object createResult = transactionApi.newTransaction(request, response);
        
        // Extract transaction ID using reflection
        long transactionId;
        try {
            var field = createResult.getClass().getDeclaredField("transactionId");
            field.setAccessible(true);
            transactionId = (long) field.get(createResult);
        } catch (Exception e) {
            fail("Failed to create transaction: " + e.getMessage());
            return;
        }
        
        // Verify transaction was created with expected ID
        assertEquals(newTransactionId, transactionId, "Transaction ID should match the mocked value");
        verify(response).status(201);
        verify(transactionsManager).applyTransaction(any());
        verify(webSocketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        
        // Reset mocks for the next part of the test
        reset(request, response);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // 2. Fetch transactions - mock the transaction retrieval
        // Create a mocked transaction record that will be returned
        TransactionsRecord mockTransaction = mock(TransactionsRecord.class);
        when(mockTransaction.getId()).thenReturn(transactionId);
        when(mockTransaction.getAccountId()).thenReturn(accountId);
        when(mockTransaction.getCategoryId()).thenReturn(categoryId);
        when(mockTransaction.getDelta()).thenReturn(delta);
        when(mockTransaction.getDescription()).thenReturn(description);
        when(mockTransaction.getCreatedAt()).thenReturn(OffsetDateTime.now());
        
        // Mock the getTransactions method to return a transaction entry
        var transactionEntry = new TransactionEntry<>(
                mockTransaction.getId(),
                mockTransaction.getCategoryId(),
                mockTransaction.getAccountId(),
                mockTransaction.getCurrencyId(),
                mockTransaction.getCreatedAt(),
                mockTransaction.getDelta(),
                mockTransaction.getDescription()
        );
        when(transactionsManager.getTransactions(anyInt(), anyInt(), anyInt(), any(TransactionsFilter.class)))
                .thenReturn(Collections.singletonList(transactionEntry));
        
        // Execute transaction retrieval
        Object getResult = transactionApi.getTransactions(request, response);
        
        // Verify response status
        verify(response).status(200);
        
        // Verify transaction exists in the result list
        try {
            var fieldTransactions = getResult.getClass().getDeclaredField("transactions");
            fieldTransactions.setAccessible(true);
            var transactions = (java.util.List<?>) fieldTransactions.get(getResult);
            
            assertEquals(1, transactions.size(), "Should have 1 transaction");
            
            Object transaction = transactions.get(0);
            var fieldId = transaction.getClass().getDeclaredField("transactionId");
            fieldId.setAccessible(true);
            long id = (long) fieldId.get(transaction);
            
            assertEquals(transactionId, id, "Transaction ID should match");
        } catch (Exception e) {
            fail("Failed to retrieve transactions: " + e.getMessage());
        }
    }
} 