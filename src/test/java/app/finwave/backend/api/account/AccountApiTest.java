package app.finwave.backend.api.account;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.account.folder.AccountFolderDatabase;
import app.finwave.backend.api.currency.CurrencyDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.api.recurring.RecurringTransactionDatabase;
import app.finwave.backend.api.transaction.filter.TransactionsFilter;
import app.finwave.backend.api.transaction.manager.TransactionsManager;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.AccountsConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.AccountsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.utils.params.ParamsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private Configs configs;
    
    @Mock
    private WebSocketWorker socketWorker;
    
    @Mock
    private TransactionsManager transactionsManager;
    
    @Mock
    private AccountDatabase accountDatabase;
    
    @Mock
    private AccountFolderDatabase folderDatabase;
    
    @Mock
    private CurrencyDatabase currencyDatabase;
    
    @Mock
    private RecurringTransactionDatabase recurringTransactionDatabase;
    
    @Mock
    private AccountsConfig accountsConfig;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    @Mock
    private app.finwave.backend.api.accumulation.AccumulationDatabase accumulationDatabase;
    
    private AccountApi accountApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configuration
        accountsConfig.maxNameLength = 50;
        accountsConfig.maxDescriptionLength = 250;
        accountsConfig.maxAccountsPerUser = 10;
        when(configs.getState(any(AccountsConfig.class))).thenReturn(accountsConfig);
        
        // Setup database mocks
        when(databaseWorker.get(AccountDatabase.class)).thenReturn(accountDatabase);
        when(databaseWorker.get(AccountFolderDatabase.class)).thenReturn(folderDatabase);
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDatabase);
        when(databaseWorker.get(RecurringTransactionDatabase.class)).thenReturn(recurringTransactionDatabase);
        when(databaseWorker.get(app.finwave.backend.api.accumulation.AccumulationDatabase.class)).thenReturn(accumulationDatabase);
        
        // Setup session
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Create the API instance
        accountApi = new AccountApi(databaseWorker, configs, socketWorker, transactionsManager);
    }

    @Test
    void testNewAccount_Success() {
        // Setup using fixture
        setupRequestFromFixture("account-create.json");
        
        long folderId = 1L;
        long currencyId = 2L;
        String name = "Test Account";
        String description = "Test Description";
        
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(accountDatabase.getAccountsCount(1)).thenReturn(5);
        when(accountDatabase.newAccount(1, folderId, currencyId, name, description)).thenReturn(Optional.of(3L));
        
        // Execute
        AccountApi.NewAccountResponse result = (AccountApi.NewAccountResponse) accountApi.newAccount(request, response);
        
        // Verify
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, result.accountId);
    }
    
    @Test
    void testNewAccount_InvalidFolder() {
        // Setup
        setupRequestFromFixture("account-create.json");
        when(folderDatabase.userOwnFolder(1, 1L)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> accountApi.newAccount(request, response));
    }
    
    @Test
    void testNewAccount_TooManyAccounts() {
        // Setup using fixture
        setupRequestFromFixture("account-create.json");
        
        long folderId = 1L;
        long currencyId = 2L;
        
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(accountDatabase.getAccountsCount(1)).thenReturn(10); // Max accounts
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> accountApi.newAccount(request, response));
    }
    
    @Test
    void testGetAccounts_Success() {
        // Setup
        List<AccountsRecord> accounts = new ArrayList<>();
        AccountsRecord account1 = mock(AccountsRecord.class);
        when(account1.getId()).thenReturn(1L);
        when(account1.getFolderId()).thenReturn(1L);
        when(account1.getCurrencyId()).thenReturn(1L);
        when(account1.getAmount()).thenReturn(BigDecimal.valueOf(100));
        when(account1.getHidden()).thenReturn(false);
        when(account1.getName()).thenReturn("Account 1");
        when(account1.getDescription()).thenReturn("Description 1");
        accounts.add(account1);
        
        when(accountDatabase.getAccounts(1)).thenReturn(accounts);
        
        // Execute
        AccountApi.GetAccountsListResponse result = (AccountApi.GetAccountsListResponse) accountApi.getAccounts(request, response);
        
        // Verify
        verify(response).status(200);
        assertEquals(1, result.accounts.size());
        assertEquals(1L, result.accounts.get(0).accountId());
        assertEquals("Account 1", result.accounts.get(0).name());
    }
    
    @Test
    void testHideAccount_Success() {
        // Setup
        long accountId = 1L;
        when(request.queryParams("accountId")).thenReturn(String.valueOf(accountId));
        when(accountDatabase.userOwnAccount(1, accountId)).thenReturn(true);
        
        // Execute
        Object result = accountApi.hideAccount(request, response);
        
        // Verify
        verify(accountDatabase).hideAccount(accountId);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testShowAccount_Success() {
        // Setup
        long accountId = 1L;
        when(request.queryParams("accountId")).thenReturn(String.valueOf(accountId));
        when(accountDatabase.userOwnAccount(1, accountId)).thenReturn(true);
        
        // Execute
        Object result = accountApi.showAccount(request, response);
        
        // Verify
        verify(accountDatabase).showAccount(accountId);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testDeleteAccount_WithTransactions() {
        // Setup
        long accountId = 1L;
        when(request.queryParams("accountId")).thenReturn(String.valueOf(accountId));
        when(accountDatabase.userOwnAccount(1, accountId)).thenReturn(true);
        when(recurringTransactionDatabase.accountAffected(accountId)).thenReturn(false);
        when(accumulationDatabase.accountAffected(accountId)).thenReturn(false);
        when(transactionsManager.getTransactionsCount(eq(1), any(TransactionsFilter.class))).thenReturn(5);
        
        // Execute
        Object result = accountApi.deleteAccount(request, response);
        
        // Verify
        verify(response).status(400);
        assertTrue(result instanceof ApiMessage);
        verify(accountDatabase, never()).deleteAccount(accountId);
    }
    
    @Test
    void testDeleteAccount_Success() {
        // Setup
        long accountId = 1L;
        when(request.queryParams("accountId")).thenReturn(String.valueOf(accountId));
        when(accountDatabase.userOwnAccount(1, accountId)).thenReturn(true);
        when(recurringTransactionDatabase.accountAffected(accountId)).thenReturn(false);
        when(accumulationDatabase.accountAffected(accountId)).thenReturn(false);
        when(transactionsManager.getTransactionsCount(eq(1), any(TransactionsFilter.class))).thenReturn(0);
        
        // Execute
        Object result = accountApi.deleteAccount(request, response);
        
        // Verify
        verify(accountDatabase).deleteAccount(accountId);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
} 