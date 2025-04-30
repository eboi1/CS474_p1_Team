package app.finwave.backend.api;

import app.finwave.backend.api.account.AccountApi;
import app.finwave.backend.api.account.AccountDatabase;
import app.finwave.backend.api.account.folder.AccountFolderDatabase;
import app.finwave.backend.api.accumulation.AccumulationDatabase;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import spark.HaltException;
import spark.Request;
import spark.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountApiTest {

    @Mock DatabaseWorker databaseWorker;
    @Mock Configs configs;
    @Mock WebSocketWorker socketWorker;
    @Mock TransactionsManager transactionsManager;

    @Mock AccountDatabase accountDb;
    @Mock AccountFolderDatabase folderDb;
    @Mock CurrencyDatabase currencyDb;
    @Mock RecurringTransactionDatabase recurringDb;
    @Mock AccumulationDatabase accumulationDb;

    private AccountApi accountApi;

    @Mock Request request;
    @Mock Response response;

    private final int userId = 1;
    private UsersSessionsRecord session;

    @BeforeEach
    void setUp() {
        // session stub
        session = new UsersSessionsRecord();
        session.setUserId(userId);
        when(request.attribute("session")).thenReturn(session);

        // database worker stubs
        when(databaseWorker.get(AccountDatabase.class)).thenReturn(accountDb);
        when(databaseWorker.get(AccountFolderDatabase.class)).thenReturn(folderDb);
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDb);
        when(databaseWorker.get(RecurringTransactionDatabase.class)).thenReturn(recurringDb);
        when(databaseWorker.get(AccumulationDatabase.class)).thenReturn(accumulationDb);

        // config stub
        AccountsConfig cfg = new AccountsConfig();
        cfg.maxAccountsPerUser = 5;
        cfg.maxNameLength = 50;
        cfg.maxDescriptionLength = 100;
        when(configs.getState(any(AccountsConfig.class))).thenReturn(cfg);

        accountApi = new AccountApi(databaseWorker, configs, socketWorker, transactionsManager);
    }

    @Test
    void newAccount_success() {
        // arrange
        when(request.queryParams("folderId")).thenReturn("10");
        when(folderDb.userOwnFolder(userId, 10L)).thenReturn(true);
        when(request.queryParams("currencyId")).thenReturn("20");
        when(currencyDb.userCanReadCurrency(userId, 20L)).thenReturn(true);
        when(request.queryParams("name")).thenReturn("MyAccount");
        when(request.queryParams("description")).thenReturn("Desc");
        when(accountDb.getAccountsCount(userId)).thenReturn(0);
        when(accountDb.newAccount(userId, 10L, 20L, "MyAccount", "Desc"))
                .thenReturn(Optional.of(42L));

        // act
        Object result = accountApi.newAccount(request, response);

        // assert
        assertTrue(result instanceof AccountApi.NewAccountResponse);
        AccountApi.NewAccountResponse resp = (AccountApi.NewAccountResponse) result;
        assertEquals(42L, resp.accountId);
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void newAccount_tooManyAccounts_throws409() {
        when(request.queryParams("folderId")).thenReturn("10");
        when(folderDb.userOwnFolder(userId, 10L)).thenReturn(true);
        when(request.queryParams("currencyId")).thenReturn("20");
        when(currencyDb.userCanReadCurrency(userId, 20L)).thenReturn(true);
        when(request.queryParams("name")).thenReturn("Name");
        when(accountDb.getAccountsCount(userId)).thenReturn(5);

        HaltException ex = assertThrows(HaltException.class, () ->
                accountApi.newAccount(request, response)
        );
        assertEquals(409, ex.statusCode());
    }

    @Test
    void newAccount_dbFailure_throws500() {
        when(request.queryParams("folderId")).thenReturn("10");
        when(folderDb.userOwnFolder(userId, 10L)).thenReturn(true);
        when(request.queryParams("currencyId")).thenReturn("20");
        when(currencyDb.userCanReadCurrency(userId, 20L)).thenReturn(true);
        when(request.queryParams("name")).thenReturn("Name");
        when(accountDb.getAccountsCount(userId)).thenReturn(0);
        when(accountDb.newAccount(userId, 10L, 20L, "Name", null))
                .thenReturn(Optional.empty());

        HaltException ex = assertThrows(HaltException.class, () ->
                accountApi.newAccount(request, response)
        );
        assertEquals(500, ex.statusCode());
    }

    @Test
    void getAccounts_success() {
        // prepare records
        AccountsRecord rec = new AccountsRecord();
        rec.setId(1L);
        rec.setFolderId(2L);
        rec.setCurrencyId(3L);
        rec.setAmount(new BigDecimal("100.00"));
        rec.setHidden(false);
        rec.setName("Acct");
        rec.setDescription("Desc");
        when(accountDb.getAccounts(userId)).thenReturn(List.of(rec));

        // act
        Object result = accountApi.getAccounts(request, response);

        // assert
        assertTrue(result instanceof AccountApi.GetAccountsListResponse);
        AccountApi.GetAccountsListResponse listResp = (AccountApi.GetAccountsListResponse) result;
        assertEquals(1, listResp.accounts.size());
        var entry = listResp.accounts.get(0);
        assertEquals(1L, entry.accountId());
        assertEquals(2L, entry.folderId());
        assertEquals(3L, entry.currencyId());
        assertEquals(new BigDecimal("100.00"), entry.amount());
        assertFalse(entry.hidden());
        assertEquals("Acct", entry.name());
        assertEquals("Desc", entry.description());
        verify(response).status(200);
    }

    @Test
    void hideAccount_success() {
        when(request.queryParams("accountId")).thenReturn("7");
        when(accountDb.userOwnAccount(userId, 7L)).thenReturn(true);

        Object result = accountApi.hideAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account hided", ((ApiMessage) result).message);
        verify(databaseWorker.get(AccountDatabase.class)).hideAccount(7L);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void showAccount_success() {
        when(request.queryParams("accountId")).thenReturn("8");
        when(accountDb.userOwnAccount(userId, 8L)).thenReturn(true);

        Object result = accountApi.showAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account showed", ((ApiMessage) result).message);
        verify(databaseWorker.get(AccountDatabase.class)).showAccount(8L);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void editAccountName_success() {
        when(request.queryParams("accountId")).thenReturn("9");
        when(accountDb.userOwnAccount(userId, 9L)).thenReturn(true);
        when(request.queryParams("name")).thenReturn("NewName");

        Object result = accountApi.editAccountName(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account name edited", ((ApiMessage) result).message);
        verify(accountDb).editAccountName(9L, "NewName");
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void editAccountDescription_success() {
        when(request.queryParams("accountId")).thenReturn("10");
        when(accountDb.userOwnAccount(userId, 10L)).thenReturn(true);
        when(request.queryParams("description")).thenReturn("UpdatedDesc");

        Object result = accountApi.editAccountDescription(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account description edited", ((ApiMessage) result).message);
        verify(accountDb).editAccountDescription(10L, "UpdatedDesc");
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void editAccountFolder_success() {
        when(request.queryParams("accountId")).thenReturn("11");
        when(accountDb.userOwnAccount(userId, 11L)).thenReturn(true);
        when(request.queryParams("folderId")).thenReturn("12");
        when(folderDb.userOwnFolder(userId, 12L)).thenReturn(true);

        Object result = accountApi.editAccountFolder(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account folder edited", ((ApiMessage) result).message);
        verify(accountDb).editAccountFolder(11L, 12L);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void deleteAccount_success() {
        when(request.queryParams("accountId")).thenReturn("13");
        when(accountDb.userOwnAccount(userId, 13L)).thenReturn(true);
        when(recurringDb.accountAffected(13L)).thenReturn(false);
        when(accumulationDb.accountAffected(13L)).thenReturn(false);
        when(transactionsManager.getTransactionsCount(eq(userId), any(TransactionsFilter.class))).thenReturn(0);

        Object result = accountApi.deleteAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Account deleted", ((ApiMessage) result).message);
        verify(accountDb).deleteAccount(13L);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(userId), any(NotifyUpdate.class));
    }

    @Test
    void deleteAccount_recurringExists() {
        when(request.queryParams("accountId")).thenReturn("14");
        when(accountDb.userOwnAccount(userId, 14L)).thenReturn(true);
        when(recurringDb.accountAffected(14L)).thenReturn(true);

        Object result = accountApi.deleteAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Some recurring transaction affects to account", ((ApiMessage) result).message);
        verify(response).status(400);
        verify(accountDb, never()).deleteAccount(anyLong());
        verify(socketWorker, never()).sendToUser(anyInt(), any());
    }

    @Test
    void deleteAccount_accumulationExists() {
        when(request.queryParams("accountId")).thenReturn("15");
        when(accountDb.userOwnAccount(userId, 15L)).thenReturn(true);
        when(recurringDb.accountAffected(15L)).thenReturn(false);
        when(accumulationDb.accountAffected(15L)).thenReturn(true);

        Object result = accountApi.deleteAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Some accumulation settings affects to account", ((ApiMessage) result).message);
        verify(response).status(400);
        verify(accountDb, never()).deleteAccount(anyLong());
        verify(socketWorker, never()).sendToUser(anyInt(), any());
    }

    @Test
    void deleteAccount_transactionsExist() {
        when(request.queryParams("accountId")).thenReturn("16");
        when(accountDb.userOwnAccount(userId, 16L)).thenReturn(true);
        when(recurringDb.accountAffected(16L)).thenReturn(false);
        when(accumulationDb.accountAffected(16L)).thenReturn(false);
        when(transactionsManager.getTransactionsCount(eq(userId), any(TransactionsFilter.class))).thenReturn(5);

        Object result = accountApi.deleteAccount(request, response);

        assertTrue(result instanceof ApiMessage);
        assertEquals("Some transactions affects to account", ((ApiMessage) result).message);
        verify(response).status(400);
        verify(accountDb, never()).deleteAccount(anyLong());
        verify(socketWorker, never()).sendToUser(anyInt(), any());
    }
}
