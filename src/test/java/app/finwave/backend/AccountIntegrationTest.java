package app.finwave.backend;

import app.finwave.backend.api.account.AccountApi;
import app.finwave.backend.api.account.AccountDatabase;
import app.finwave.backend.api.account.folder.AccountFolderApi;
import app.finwave.backend.api.account.folder.AccountFolderDatabase;
import app.finwave.backend.api.currency.CurrencyDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.transaction.manager.TransactionsManager;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.AccountsConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.JsonObject;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for account-related functionality.
 * This test assumes a database setup with a test user.
 */
@ExtendWith(MockitoExtension.class)
class AccountIntegrationTest extends BaseIntegrationTest {
    @Mock
    private Configs configs;
    
    @Mock
    private WebSocketWorker webSocketWorker;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private DSLContext dslContext;
    
    @Mock
    private TransactionsManager transactionsManager;
    
    @Mock
    private AccountDatabase accountDatabase;
    
    @Mock
    private AccountFolderDatabase folderDatabase;
    
    @Mock
    private CurrencyDatabase currencyDatabase;
    
    private AccountApi accountApi;
    private AccountFolderApi accountFolderApi;


    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        setupRequestFromFixture("sample-account.json");
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("sample-account.json");
        long folderId = fixture.get("folderId").getAsLong();
        long currencyId = fixture.get("currencyId").getAsLong();
        String accountName = fixture.get("name").getAsString();
        String accountDescription = fixture.get("description").getAsString();

        // Mock the request and response objects
        when(request.queryParams("folderId")).thenReturn(String.valueOf(folderId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("name")).thenReturn(fixture.get("name").getAsString());
        when(request.queryParams("description")).thenReturn(fixture.get("description").getAsString());

        // 1) stub Configs to return a real AccountsConfig
        AccountsConfig cfg = new AccountsConfig();
        when(configs.getState(any(AccountsConfig.class))).thenReturn(cfg);

        // 2) DatabaseWorker → returns your mocked databases
        when(databaseWorker.get(AccountDatabase.class)).thenReturn(accountDatabase);
        when(databaseWorker.get(AccountFolderDatabase.class)).thenReturn(folderDatabase);
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDatabase);
        // if you ever hit deleteAccount, you'll also need:
        // when(databaseWorker.get(RecurringTransactionDatabase.class)).thenReturn(recurringTransactionDatabase);
        // when(databaseWorker.get(AccumulationDatabase.class)).thenReturn(accumulationDatabase);

        // 3) session record and ownership checks
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(sessionRecord.getUserId()).thenReturn(1);

        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        when(accountDatabase.getAccountsCount(1)).thenReturn(0);

        // 4) finally, construct your API under test
        accountApi = new AccountApi(databaseWorker, configs, webSocketWorker, transactionsManager);
        accountFolderApi = new AccountFolderApi(databaseWorker, configs, webSocketWorker);
    }
    
    @Test
    void testCreateAndFetchAccount() {
        // 1. Create a new account - setup mock for account creation using fixture
        setupRequestFromFixture("account-create.json");
        
        // Extract values from the fixture to use in our test
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("account-create.json");
        String accountName = fixture.get("name").getAsString();
        String accountDescription = fixture.get("description").getAsString();
        Long currencyId = fixture.get("currencyId").getAsLong();
        Long folderId = fixture.get("folderId").getAsLong();
        
        // Add mock to allow access to currency ID 2
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        
        // Mock the database response for creating an account
        long newAccountId = 5;
        when(accountDatabase.newAccount(
                eq(1),
                eq(folderId),
                eq(currencyId),
                eq(accountName),
                eq(accountDescription)
        )).thenReturn(Optional.of(newAccountId));
        
        // Execute account creation
        Object createResult = accountApi.newAccount(request, response);
        
        // Extract account ID using reflection
        long accountId;
        try {
            var field = createResult.getClass().getDeclaredField("accountId");
            field.setAccessible(true);
            accountId = (long) field.get(createResult);
        } catch (Exception e) {
            fail("Failed to create account: " + e.getMessage());
            return;
        }
        
        // Verify account was created with expected ID
        assertEquals(newAccountId, accountId, "Account ID should match the mocked value");
        verify(response).status(201);
        verify(accountDatabase).newAccount(
                eq(1),
                eq(folderId),
                eq(currencyId),
                eq(accountName),
                eq(accountDescription)
        );
        verify(webSocketWorker).sendToUser(eq(1), any());
        
        // Reset mocks for the next part of the test
        reset(request, response);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // 2. Fetch accounts - mock the account retrieval
        // Create a mocked account record that will be returned
        var mockAccount = mock(app.finwave.backend.jooq.tables.records.AccountsRecord.class);
        when(mockAccount.getId()).thenReturn(accountId);
        when(mockAccount.getFolderId()).thenReturn(folderId);
        when(mockAccount.getCurrencyId()).thenReturn(currencyId);
        when(mockAccount.getName()).thenReturn(accountName);
        when(mockAccount.getDescription()).thenReturn(accountDescription);
        when(mockAccount.getAmount()).thenReturn(java.math.BigDecimal.ZERO);
        when(mockAccount.getHidden()).thenReturn(false);
        
        when(accountDatabase.getAccounts(1)).thenReturn(java.util.Collections.singletonList(mockAccount));
        
        // Execute account retrieval
        Object getResult = accountApi.getAccounts(request, response);
        
        // Verify response status
        verify(response).status(200);
        
        // Verify account exists in the result list
        try {
            var fieldAccounts = getResult.getClass().getDeclaredField("accounts");
            fieldAccounts.setAccessible(true);
            var accounts = (java.util.List<?>) fieldAccounts.get(getResult);
            
            assertEquals(1, accounts.size(), "Should have 1 account");
            
            Object account = accounts.get(0);
            var fieldId = account.getClass().getDeclaredField("accountId");
            fieldId.setAccessible(true);
            long id = (long) fieldId.get(account);
            
            var fieldName = account.getClass().getDeclaredField("name");
            fieldName.setAccessible(true);
            String name = (String) fieldName.get(account);
            
            assertEquals(accountId, id, "Account ID should match");
            assertEquals(accountName, name, "Account name should match");
        } catch (Exception e) {
            fail("Failed to retrieve accounts: " + e.getMessage());
        }
    }
} 