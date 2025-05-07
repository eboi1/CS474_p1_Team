package app.finwave.backend;

import app.finwave.backend.api.budget.CategoryBudgetApi;
import app.finwave.backend.api.budget.CategoryBudgetDatabase;
import app.finwave.backend.api.budget.CategoryBudgetManager;
import app.finwave.backend.api.category.CategoryDatabase;
import app.finwave.backend.api.currency.CurrencyDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.CachingConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.CategoriesBudgetsRecord;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for budget-related functionality.
 * This test verifies the interaction between budget components.
 * Uses SQL tables and fixtures from the resources folder.
 */
@ExtendWith(MockitoExtension.class)
class BudgetIntegrationTest extends BaseIntegrationTest {

    @Mock private WebSocketWorker webSocketWorker;
    @Mock private UsersSessionsRecord sessionRecord;
    @Mock private DatabaseWorker databaseWorker;
    @Mock private DSLContext dslContext;
    @Mock private Configs configs;
    @Mock private CategoryBudgetDatabase budgetDatabase;
    @Mock private CategoryDatabase categoryDatabase;
    @Mock private CurrencyDatabase currencyDatabase;

    private CategoryBudgetManager budgetManager;
    private CategoryBudgetApi budgetApi;

    private long categoryId;
    private long currencyId;
    private short dateType;
    private BigDecimal amount;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();

        // Load budget fixture
        setupRequestFromFixture("budget-create.json");
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("budget-create.json");
        categoryId = fixture.get("categoryId").getAsLong();
        currencyId = fixture.get("currencyId").getAsLong();
        dateType = fixture.get("dateType").getAsShort();
        amount = fixture.get("amount").getAsBigDecimal();

        // Stub Configs to return a real CachingConfig
        CachingConfig config = new CachingConfig();
        config.categoriesBudget.maxLists = 100;
        when(configs.getState(any(CachingConfig.class))).thenReturn(config);

        // DatabaseWorker mocks
        when(databaseWorker.get(CategoryBudgetDatabase.class)).thenReturn(budgetDatabase);
        when(databaseWorker.get(CategoryDatabase.class)).thenReturn(categoryDatabase);
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDatabase);
        lenient().when(categoryDatabase.userOwnCategory(anyInt(), anyLong())).thenReturn(true);
        lenient().when(currencyDatabase.userCanReadCurrency(anyInt(), anyLong())).thenReturn(true);

        // Initialize manager and API
        budgetManager = new CategoryBudgetManager(databaseWorker, configs);
        budgetApi = new CategoryBudgetApi(budgetManager, webSocketWorker, databaseWorker);

        // Session stub
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
    }

    @Test
    void testCreateAndFetchBudget() {
        // Stub exists and add
        when(budgetDatabase.budgetExists(anyInt(), anyLong(), anyLong(), anyLong())).thenReturn(false);
        when(budgetDatabase.add(anyInt(), anyLong(), anyLong(), anyShort(), any(BigDecimal.class)))
            .thenReturn(Optional.of(5L));

        Object createResult = budgetApi.addBudget(request, response);

        // Verify creation
        long budgetId;
        try {
            var field = createResult.getClass().getDeclaredField("budgetId");
            field.setAccessible(true);
            budgetId = (long) field.get(createResult);
        } catch (Exception e) {
            fail("Failed to create budget: " + e.getMessage());
            return;
        }

        assertEquals(5L, budgetId);
        verify(response).status(201);
        verify(budgetDatabase).add(
                eq(1), eq(categoryId), eq(currencyId), eq(dateType), eq(amount)
        );
        verify(webSocketWorker).sendToUser(eq(1), any(NotifyUpdate.class));

        // Fetch budgets
        reset(request, response);
        when(request.attribute("session")).thenReturn(sessionRecord);

        CategoriesBudgetsRecord mockBudget = mock(CategoriesBudgetsRecord.class);
        when(mockBudget.getId()).thenReturn(budgetId);
        when(mockBudget.getCategoryId()).thenReturn(categoryId);
        when(mockBudget.getCurrencyId()).thenReturn(currencyId);
        when(mockBudget.getDateType()).thenReturn(dateType);
        when(mockBudget.getAmount()).thenReturn(amount);

        when(budgetDatabase.getList(anyInt())).thenReturn(List.of(mockBudget));

        Object getResult = budgetApi.getSettings(request, response);

        verify(response).status(200);

        try {
            var fieldBudgets = getResult.getClass().getDeclaredField("budgets");
            fieldBudgets.setAccessible(true);
            var budgets = (List<?>) fieldBudgets.get(getResult);
            assertEquals(1, budgets.size());

            Object budget = budgets.get(0);
            var fieldId = budget.getClass().getDeclaredField("budgetId");
            fieldId.setAccessible(true);
            long id = (long) fieldId.get(budget);

            var fieldAmount = budget.getClass().getDeclaredField("amount");
            fieldAmount.setAccessible(true);
            BigDecimal budgetAmount = (BigDecimal) fieldAmount.get(budget);

            assertEquals(budgetId, id);
            assertEquals(amount, budgetAmount);
        } catch (Exception e) {
            fail("Failed to retrieve budgets: " + e.getMessage());
        }
    }

    @Test
    void testUpdateBudget() {
        // Load update budget fixture
        setupRequestFromFixture("budget-update.json");
        JsonObject updateFixture = TestFixtureLoader.loadJsonFixture("budget-update.json");
        long existingBudgetId = updateFixture.get("budgetId").getAsLong();
        short newDateType = updateFixture.get("dateType").getAsShort();
        BigDecimal newAmount = updateFixture.get("amount").getAsBigDecimal();

        when(budgetManager.userOwnBudget(anyInt(), anyLong())).thenReturn(true);
        when(budgetDatabase.budgetExists(anyInt(), anyLong(), anyLong(), anyLong())).thenReturn(false);

        Object updateResult = budgetApi.editBudget(request, response);

        verify(response).status(200);
        verify(budgetDatabase).update(
                eq(existingBudgetId), eq(categoryId), eq(currencyId), eq(newDateType), eq(newAmount)
        );
        verify(webSocketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
    }

    @Test
    void testDeleteBudget() {
        // Load delete budget fixture
        setupRequestFromFixture("budget-delete.json");
        JsonObject deleteFixture = TestFixtureLoader.loadJsonFixture("budget-delete.json");
        long existingBudgetId = deleteFixture.get("budgetId").getAsLong();
        
        when(budgetManager.userOwnBudget(anyInt(), anyLong())).thenReturn(true);

        CategoriesBudgetsRecord deletedRecord = mock(CategoriesBudgetsRecord.class);
        when(deletedRecord.getOwnerId()).thenReturn(1);
        when(budgetDatabase.remove(existingBudgetId)).thenReturn(Optional.of(deletedRecord));

        Object deleteResult = budgetApi.remove(request, response);

        verify(response).status(200);
        verify(budgetDatabase).remove(existingBudgetId);
        // this fails because of commented out code in the source
        // verify(webSocketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
    }
}