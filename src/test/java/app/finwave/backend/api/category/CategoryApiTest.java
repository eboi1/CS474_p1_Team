package app.finwave.backend.api.category;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.TransactionConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.CategoriesRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import spark.HaltException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CategoryApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;

    @Mock
    private Configs configs;

    @Mock
    private WebSocketWorker socketWorker;

    @Mock
    private CategoryDatabase categoryDatabase;

    @Mock
    private TransactionConfig transactionConfig;

    @Mock
    private TransactionConfig.CategoryConfig categoriesConfig;

    @Mock
    private UsersSessionsRecord sessionRecord;

    private CategoryApi categoryApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        // common config
        transactionConfig.categories = categoriesConfig;
        categoriesConfig.maxNameLength        = 50;
        categoriesConfig.maxDescriptionLength = 250;
        categoriesConfig.maxCategoriesPerUser = 50;
        when(configs.getState(any(TransactionConfig.class)))
                .thenReturn(transactionConfig);

        // database wiring
        when(databaseWorker.get(CategoryDatabase.class))
                .thenReturn(categoryDatabase);

        // session stub
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(sessionRecord.getUserId()).thenReturn(1);

        categoryApi = new CategoryApi(databaseWorker, configs, socketWorker);
    }

    @Test
    void testNewCategory_Success() {
        // Setup
        int type = 0;
        Long parentId = 2L;
        String name = "Food";
        String description = "Food expenses";

        when(request.queryParams("type")).thenReturn(String.valueOf(type));
        when(request.queryParams("parentId")).thenReturn(String.valueOf(parentId));
        when(request.queryParams("name")).thenReturn(name);
        when(request.queryParams("description")).thenReturn(description);

        when(categoryDatabase.userOwnCategory(1, parentId)).thenReturn(true);
        when(categoryDatabase.getCategoriesCount(1)).thenReturn(10);
        when(categoryDatabase.newCategory(eq(1), eq((short) type), eq(parentId), eq(name), eq(description)))
            .thenReturn(Optional.of(3L));

        // Execute
        CategoryApi.NewCategoryResponse result = (CategoryApi.NewCategoryResponse) categoryApi.newCategory(request, response);

        // Verify
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, result.categoryId);
    }

    @Test
    void testNewCategory_WithoutParent() {
        // Setup
        int type = 0;
        String name = "Food";
        String description = "Food expenses";

        when(request.queryParams("type")).thenReturn(String.valueOf(type));
        when(request.queryParams("name")).thenReturn(name);
        when(request.queryParams("description")).thenReturn(description);

        when(categoryDatabase.getCategoriesCount(1)).thenReturn(10);
        when(categoryDatabase.newCategory(eq(1), eq((short) type), eq(null), eq(name), eq(description)))
            .thenReturn(Optional.of(3L));

        // Execute
        CategoryApi.NewCategoryResponse result = (CategoryApi.NewCategoryResponse) categoryApi.newCategory(request, response);

        // Verify
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, result.categoryId);
    }

    @Test
    void testNewCategory_TooManyCategories() {
        // Setup
        int type = 0;
        String name = "Food";

        when(request.queryParams("type")).thenReturn(String.valueOf(type));
        when(request.queryParams("name")).thenReturn(name);

        when(categoryDatabase.getCategoriesCount(1)).thenReturn(50); // Max categories

        // Execute & Verify
        assertThrows(HaltException.class, () -> categoryApi.newCategory(request, response));
    }

    @Test
    void testGetCategories_Success() {
        // deep‐stub so that c1.getParentsTree() itself is a mock
        CategoriesRecord c1 = mock(CategoriesRecord.class, RETURNS_DEEP_STUBS);
        when(c1.getId()).thenReturn(1L);
        when(c1.getType()).thenReturn((short)0);
        // now stub the chained toString() call directly:
        when(c1.getParentsTree().toString()).thenReturn("[]");
        when(c1.getName()).thenReturn("Food");
        when(c1.getDescription()).thenReturn("Food expenses");

        CategoriesRecord c2 = mock(CategoriesRecord.class, RETURNS_DEEP_STUBS);
        when(c2.getId()).thenReturn(2L);
        when(c2.getType()).thenReturn((short)1);
        when(c2.getParentsTree().toString()).thenReturn("[1]");
        when(c2.getName()).thenReturn("Salary");
        when(c2.getDescription()).thenReturn("Income from work");

        // stub the database call
        when(categoryDatabase.getCategories(eq(1)))
                .thenReturn(Arrays.asList(c1, c2));

        // exercise
        var resp = (CategoryApi.GetCategoriesResponse)
                categoryApi.getCategories(request, response);

        // verify
        verify(response).status(200);
        assertEquals("[]", resp.categories.get(0).parentsTree());
        assertEquals("[1]", resp.categories.get(1).parentsTree());
    }

    @Test
    void testEditCategoryType_Success() {
        // Setup
        long categoryId = 1L;
        int newType = 1;

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("type")).thenReturn(String.valueOf(newType));
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);

        // Execute
        Object result = categoryApi.editCategoryType(request, response);

        // Verify
        verify(categoryDatabase).editCategoryType(categoryId, (short) newType);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditCategoryType_InvalidType() {
        // Setup
        long categoryId = 1L;
        int newType = 2; // Invalid type (out of range -1 to 1)

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("type")).thenReturn(String.valueOf(newType));
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);

        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> categoryApi.editCategoryType(request, response));
        verify(categoryDatabase, never()).editCategoryType(anyLong(), anyShort());
    }

    @Test
    void testEditCategoryParent_Success() {
        // Setup
        long categoryId = 1L;
        long newParentId = 2L;

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("parentId")).thenReturn(String.valueOf(newParentId));
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);
        when(categoryDatabase.userOwnCategory(1, newParentId)).thenReturn(true);
        when(categoryDatabase.newParentIsSafe(categoryId, newParentId)).thenReturn(true);

        // Execute
        Object result = categoryApi.editCategoryParent(request, response);

        // Verify
        verify(categoryDatabase).editCategoryParentId(categoryId, newParentId);
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditCategoryParent_SetToRoot() {
        // Setup
        long categoryId = 1L;

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("setToRoot")).thenReturn("true");
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);

        // Execute
        Object result = categoryApi.editCategoryParent(request, response);

        // Verify
        verify(categoryDatabase).setParentToRoot(categoryId);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditCategoryName_Success() {
        // Setup
        long categoryId = 1L;
        String newName = "Groceries";

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("name")).thenReturn(newName);
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);

        // Execute
        Object result = categoryApi.editCategoryName(request, response);

        // Verify
        verify(categoryDatabase).editCategoryName(categoryId, newName);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditCategoryDescription_Success() {
        // Setup
        long categoryId = 1L;
        String newDescription = "Grocery shopping expenses";

        when(request.queryParams("categoryId")).thenReturn(String.valueOf(categoryId));
        when(request.queryParams("description")).thenReturn(newDescription);
        when(categoryDatabase.userOwnCategory(1, categoryId)).thenReturn(true);

        // Execute
        Object result = categoryApi.editCategoryDescription(request, response);

        // Verify
        verify(categoryDatabase).editCategoryDescription(categoryId, newDescription);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
}