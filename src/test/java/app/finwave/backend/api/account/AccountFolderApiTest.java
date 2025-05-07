package app.finwave.backend.api.account;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.account.folder.AccountFolderApi;
import app.finwave.backend.api.account.folder.AccountFolderDatabase;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.AccountsConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.AccountsFoldersRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountFolderApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private Configs configs;
    
    @Mock
    private WebSocketWorker socketWorker;
    
    @Mock
    private AccountFolderDatabase folderDatabase;
    
    @Mock
    private AccountsConfig accountsConfig;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    private AccountFolderApi folderApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configuration
        accountsConfig.folders = new AccountsConfig.AccountFoldersConfig();
        accountsConfig.folders.maxNameLength = 50;
        accountsConfig.folders.maxDescriptionLength = 250;
        accountsConfig.folders.maxFoldersPerUser = 5;
        when(configs.getState(any(AccountsConfig.class))).thenReturn(accountsConfig);
        
        // Setup database mocks
        when(databaseWorker.get(AccountFolderDatabase.class)).thenReturn(folderDatabase);
        
        // Setup session
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Create the API instance
        folderApi = new AccountFolderApi(databaseWorker, configs, socketWorker);
    }

    @Test
    void testNewFolder_Success() {
        // Setup
        String name = "Test Folder";
        String description = "Test Description";
        
        when(request.queryParams("name")).thenReturn(name);
        when(request.queryParams("description")).thenReturn(description);
        
        when(folderDatabase.getFolderCount(1)).thenReturn(2); // Below max folders
        when(folderDatabase.newFolder(1, name, description)).thenReturn(Optional.of(3L));
        
        // Execute
        Object result = folderApi.newFolder(request, response);
        
        // Verify
        verify(response).status(201);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, (long) getFieldValue(result, "folderId"));
    }
    
    @Test
    void testNewFolder_TooManyFolders() {
        // Setup
        String name = "Test Folder";
        
        when(request.queryParams("name")).thenReturn(name);
        when(folderDatabase.getFolderCount(1)).thenReturn(5); // Max folders reached
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> folderApi.newFolder(request, response));
    }
    
    @Test
    void testGetFolders_Success() {
        // Setup
        List<AccountsFoldersRecord> folders = new ArrayList<>();
        AccountsFoldersRecord folder1 = mock(AccountsFoldersRecord.class);
        when(folder1.getId()).thenReturn(1L);
        when(folder1.getName()).thenReturn("Folder 1");
        when(folder1.getDescription()).thenReturn("Description 1");
        folders.add(folder1);
        
        when(folderDatabase.getFolders(1)).thenReturn(folders);
        
        // Execute
        Object result = folderApi.getFolders(request, response);
        
        // Verify
        verify(response).status(200);
        
        // Get folders list using reflection since it's package-private
        List<?> folderList = getFieldValue(result, "folders");
        assertEquals(1, folderList.size());
        
        // Test first folder in the list using reflection
        Object folder = folderList.get(0);
        assertEquals(1L, (long) getFieldValue(folder, "folderId"));
        assertEquals("Folder 1", getFieldValue(folder, "name"));
        assertEquals("Description 1", getFieldValue(folder, "description"));
    }
    
    @Test
    void testEditFolderName_Success() {
        // Setup
        long folderId = 1L;
        String newName = "Updated Folder Name";
        
        when(request.queryParams("folderId")).thenReturn(String.valueOf(folderId));
        when(request.queryParams("name")).thenReturn(newName);
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        
        // Execute
        Object result = folderApi.editFolderName(request, response);
        
        // Verify
        verify(folderDatabase).editFolderName(folderId, newName);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testEditFolderName_UnauthorizedAccess() {
        // Setup
        long folderId = 1L;
        String newName = "Updated Folder Name";
        
        when(request.queryParams("folderId")).thenReturn(String.valueOf(folderId));
        when(request.queryParams("name")).thenReturn(newName);
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> folderApi.editFolderName(request, response));
        verify(folderDatabase, never()).editFolderName(anyLong(), anyString());
    }
    
    @Test
    void testDeleteFolder_Success() {
        // Setup
        long folderId = 1L;
        
        when(request.queryParams("folderId")).thenReturn(String.valueOf(folderId));
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        when(folderDatabase.folderSafeToDelete(folderId)).thenReturn(true);
        
        // Execute
        Object result = folderApi.deleteFolder(request, response);
        
        // Verify
        verify(folderDatabase).deleteFolder(folderId);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testDeleteFolder_FolderInUse() {
        // Setup
        long folderId = 1L;
        
        when(request.queryParams("folderId")).thenReturn(String.valueOf(folderId));
        when(folderDatabase.userOwnFolder(1, folderId)).thenReturn(true);
        when(folderDatabase.folderSafeToDelete(folderId)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> folderApi.deleteFolder(request, response));
        verify(folderDatabase, never()).deleteFolder(anyLong());
    }
    
    // Helper method to get field value using reflection
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }
} 