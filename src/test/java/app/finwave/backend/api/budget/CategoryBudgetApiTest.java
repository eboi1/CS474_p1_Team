package app.finwave.backend.api.budget;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.category.CategoryDatabase;
import app.finwave.backend.api.currency.CurrencyDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.CategoriesBudgetsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CategoryBudgetApiTest extends BaseApiTest {

    @Mock
    private CategoryBudgetManager budgetManager;
    
    @Mock
    private WebSocketWorker socketWorker;
    
    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private CategoryDatabase categoryDatabase;
    
    @Mock
    private CurrencyDatabase currencyDatabase;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    private CategoryBudgetApi budgetApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup mocks
        when(databaseWorker.get(CategoryDatabase.class)).thenReturn(categoryDatabase);
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDatabase);
        
        // Setup session
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Create the API instance
        budgetApi = new CategoryBudgetApi(budgetManager, socketWorker, databaseWorker);
    }

    @Test
    void testAddBudget_Success() {
        // Setup
        long categoryId = 1L;
        long currencyId = 2L;
        short dateType = 0;
        String amount = "100.00";
        
        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("dateType")).thenReturn(String.valueOf(dateType));
        when(request.queryParams("amount")).thenReturn(amount);
        
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(budgetManager.budgetExists(1, categoryId, currencyId, -1)).thenReturn(false);
        when(budgetManager.add(eq(1), eq(categoryId), eq(currencyId), eq(dateType), any(BigDecimal.class)))
            .thenReturn(Optional.of(3L));
        
        // Execute
        CategoryBudgetApi.NewBudgetResponse result = (CategoryBudgetApi.NewBudgetResponse) budgetApi.addBudget(request, response);
        
        // Verify
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, result.budgetId);
    }
    
    @Test
    void testAddBudget_AlreadyExists() {
        // Setup
        long categoryId = 1L;
        long currencyId = 2L;
        short dateType = 0;
        String amount = "100.00";
        
        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("dateType")).thenReturn(String.valueOf(dateType));
        when(request.queryParams("amount")).thenReturn(amount);
        
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(budgetManager.budgetExists(1, categoryId, currencyId, -1)).thenReturn(true);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> budgetApi.addBudget(request, response));
    }
    
    @Test
    void testAddBudget_ZeroAmount() {
        // Setup
        long categoryId = 1L;
        long currencyId = 2L;
        short dateType = 0;
        String amount = "0.00";
        
        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("dateType")).thenReturn(String.valueOf(dateType));
        when(request.queryParams("amount")).thenReturn(amount);
        
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(budgetManager.budgetExists(1, categoryId, currencyId, -1)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> budgetApi.addBudget(request, response));
    }
    
    @Test
    void testEditBudget_Success() {
        // Setup
        long budgetId = 3L;
        long categoryId = 1L;
        long currencyId = 2L;
        short dateType = 0;
        String amount = "150.00";
        
        when(request.queryParams("budgetId")).thenReturn(String.valueOf(budgetId));
        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("dateType")).thenReturn(String.valueOf(dateType));
        when(request.queryParams("amount")).thenReturn(amount);
        
        when(budgetManager.userOwnBudget(1, budgetId)).thenReturn(true);
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(budgetManager.budgetExists(1, categoryId, currencyId, budgetId)).thenReturn(false);
        
        // Execute
        Object result = budgetApi.editBudget(request, response);
        
        // Verify
        verify(budgetManager).update(eq(1), eq(budgetId), eq(categoryId), eq(currencyId), eq(dateType), any(BigDecimal.class));
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testEditBudget_AlreadyExists() {
        // Setup
        long budgetId = 3L;
        long categoryId = 1L;
        long currencyId = 2L;
        short dateType = 0;
        String amount = "150.00";
        
        when(request.queryParams("budgetId")).thenReturn(String.valueOf(budgetId));
        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("dateType")).thenReturn(String.valueOf(dateType));
        when(request.queryParams("amount")).thenReturn(amount);
        
        when(budgetManager.userOwnBudget(1, budgetId)).thenReturn(true);
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(currencyDatabase.userCanReadCurrency(1, currencyId)).thenReturn(true);
        when(budgetManager.budgetExists(1, categoryId, currencyId, budgetId)).thenReturn(true);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> budgetApi.editBudget(request, response));
        verify(budgetManager, never()).update(anyInt(), anyLong(), anyLong(), anyLong(), anyShort(), any(BigDecimal.class));
    }
    
    @Test
    void testGetSettings_Success() {
        // Setup
        List<CategoriesBudgetsRecord> records = new ArrayList<>();
        CategoriesBudgetsRecord record1 = mock(CategoriesBudgetsRecord.class);
        when(record1.getId()).thenReturn(1L);
        when(record1.getCategoryId()).thenReturn(1L);
        when(record1.getDateType()).thenReturn((short) 0);
        when(record1.getCurrencyId()).thenReturn(1L);
        when(record1.getAmount()).thenReturn(new BigDecimal("100.00"));
        records.add(record1);
        
        CategoriesBudgetsRecord record2 = mock(CategoriesBudgetsRecord.class);
        when(record2.getId()).thenReturn(2L);
        when(record2.getCategoryId()).thenReturn(2L);
        when(record2.getDateType()).thenReturn((short) 1);
        when(record2.getCurrencyId()).thenReturn(1L);
        when(record2.getAmount()).thenReturn(new BigDecimal("200.00"));
        records.add(record2);
        
        when(budgetManager.getSettings(1)).thenReturn(records);
        
        // Execute
        CategoryBudgetApi.GetListResponse result = (CategoryBudgetApi.GetListResponse) budgetApi.getSettings(request, response);
        
        // Verify
        verify(response).status(200);
        assertEquals(2, result.budgets.size());
        assertEquals(1L, result.budgets.get(0).budgetId());
        assertEquals(1L, result.budgets.get(0).categoryId());
        assertEquals(0, result.budgets.get(0).dateType());
        assertEquals(1L, result.budgets.get(0).currencyId());
        assertEquals(new BigDecimal("100.00"), result.budgets.get(0).amount());
        
        assertEquals(2L, result.budgets.get(1).budgetId());
        assertEquals(2L, result.budgets.get(1).categoryId());
        assertEquals(1, result.budgets.get(1).dateType());
        assertEquals(1L, result.budgets.get(1).currencyId());
        assertEquals(new BigDecimal("200.00"), result.budgets.get(1).amount());
    }
    
    @Test
    void testRemove_Success() {
        // Setup
        long budgetId = 1L;
        when(request.queryParams("budgetId")).thenReturn(String.valueOf(budgetId));
        when(budgetManager.userOwnBudget(1, budgetId)).thenReturn(true);
        when(budgetManager.remove(budgetId)).thenReturn(true);
        
        // Execute
        Object result = budgetApi.remove(request, response);
        
        // Verify
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testRemove_UnsuccessfulRemoval() {
        // Setup
        long budgetId = 1L;
        when(request.queryParams("budgetId")).thenReturn(String.valueOf(budgetId));
        when(budgetManager.userOwnBudget(1, budgetId)).thenReturn(true);
        when(budgetManager.remove(budgetId)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> budgetApi.remove(request, response));
    }
} 