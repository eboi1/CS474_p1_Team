package app.finwave.backend.api.ai;

import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.AiConfig;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.FilesRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import app.finwave.backend.api.files.FilesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiApiTest {
    @Mock
    private AiWorker aiWorker;

    @Mock
    private AiFileWorker aiFileWorker;

    @Mock
    private AiManager aiManager;

    @Mock
    private FilesManager filesManager;

    @Mock
    private Configs configs;

    @Mock
    private Request request;

    @Mock
    private Response response;

    @Mock
    private UsersSessionsRecord sessionRecord;

    private AiApi aiApi;
    private AiConfig aiConfig;
    private final int userId = 1;
    private final long contextId = 123;
    private final String fileId = "file456";
    private final int maxAdditionalPrompt = 1000;
    private final int maxNewMessageSize = 500;

    @BeforeEach
    void setUp() {
        // Create a real AiConfig instance
        aiConfig = new AiConfig();
        aiConfig.enabled = true;
        aiConfig.maxAdditionalPrompt = maxAdditionalPrompt;
        aiConfig.maxNewMessageSize = maxNewMessageSize;

        // Stub configs.getState to return aiConfig
        when(configs.getState(any())).thenReturn(aiConfig);

        // Manually instantiate AiApi with mocked dependencies
        aiApi = new AiApi(aiWorker, aiFileWorker, aiManager, filesManager, configs);
    }

    @Test
    void testNewContext_SuccessWithAdditionalMessage() {
        when(request.attribute("session")).thenReturn(sessionRecord);
        String additionalMessage = "Test system message";
        when(request.queryParams("additionalSystemMessage")).thenReturn(additionalMessage);
        when(aiWorker.initContext(sessionRecord, additionalMessage)).thenReturn(Optional.of(contextId));

        Object result = aiApi.newContext(request, response);

        verify(response).status(200);
        assertTrue(result instanceof AiApi.NewContextResponse);
        assertEquals(contextId, ((AiApi.NewContextResponse) result).contextId);
    }

    @Test
    void testNewContext_SuccessWithoutAdditionalMessage() {
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(aiWorker.initContext(sessionRecord, null)).thenReturn(Optional.of(contextId));

        Object result = aiApi.newContext(request, response);

        verify(response).status(200);
        assertTrue(result instanceof AiApi.NewContextResponse);
        assertEquals(contextId, ((AiApi.NewContextResponse) result).contextId);
    }

    @Test
    void testNewContext_AiDisabled() {
        aiConfig.enabled = false;

        Object result = aiApi.newContext(request, response);

        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
        assertEquals("AI disabled", ((ApiMessage) result).message);
    }

    @Test
    void testNewContext_Failure() {
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(aiWorker.initContext(sessionRecord, null)).thenReturn(Optional.empty());

        assertThrows(spark.HaltException.class, () -> aiApi.newContext(request, response));
        verify(response).status(500);
    }

    @Test
    void testAttachFile_Success() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        FilesRecord fileRecord = mock(FilesRecord.class);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(request.queryParams("fileId")).thenReturn(fileId);
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(filesManager.userOwnFile(userId, fileId)).thenReturn(true);
        when(filesManager.getFileRecord(fileId)).thenReturn(Optional.of(fileRecord));
        when(aiFileWorker.attachFiles(contextId, List.of(fileRecord))).thenReturn(true);

        Object result = aiApi.attachFile(request, response);

        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
        assertEquals("Attached successfully", ((ApiMessage) result).message);
    }

    @Test
    void testAttachFile_AiDisabled() {
        aiConfig.enabled = false;

        Object result = aiApi.attachFile(request, response);

        verify(response).status(400);
        assertTrue(result instanceof ApiMessage);
        assertEquals("AI disabled", ((ApiMessage) result).message);
    }

    @Test
    void testAttachFile_InvalidContextId() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> aiApi.attachFile(request, response));
    }

    @Test
    void testAttachFile_InvalidFileId() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(request.queryParams("fileId")).thenReturn(fileId);
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(filesManager.userOwnFile(userId, fileId)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> aiApi.attachFile(request, response));
    }

    @Test
    void testAttachFile_FileNotFound() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(request.queryParams("fileId")).thenReturn(fileId);
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(filesManager.userOwnFile(userId, fileId)).thenReturn(true);
        when(filesManager.getFileRecord(fileId)).thenReturn(Optional.empty());

        assertThrows(java.util.NoSuchElementException.class, () -> aiApi.attachFile(request, response));
    }

    @Test
    void testAttachFile_AttachFailure() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        FilesRecord fileRecord = mock(FilesRecord.class);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(request.queryParams("fileId")).thenReturn(fileId);
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(filesManager.userOwnFile(userId, fileId)).thenReturn(true);
        when(filesManager.getFileRecord(fileId)).thenReturn(Optional.of(fileRecord));
        when(aiFileWorker.attachFiles(contextId, List.of(fileRecord))).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> aiApi.attachFile(request, response));
    }

    @Test
    void testAsk_SuccessWithMessage() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        String message = "What is the capital of France?";
        String answer = "The capital of France is Paris.";
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(request.queryParams("message")).thenReturn(message);
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(aiWorker.ask(eq(contextId), eq(sessionRecord), anyList())).thenReturn(answer);

        Object result = aiApi.ask(request, response);

        verify(response).status(200);
        assertTrue(result instanceof AiApi.AnswerResponse);
        assertEquals(answer, ((AiApi.AnswerResponse) result).answer);
    }

    @Test
    void testAsk_SuccessWithoutMessage() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        String answer = "Default response";
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(aiWorker.ask(eq(contextId), eq(sessionRecord), anyList())).thenReturn(answer);

        Object result = aiApi.ask(request, response);

        verify(response).status(200);
        assertTrue(result instanceof AiApi.AnswerResponse);
        assertEquals(answer, ((AiApi.AnswerResponse) result).answer);
    }

    @Test
    void testAsk_AiDisabled() {
        aiConfig.enabled = false;

        Object result = aiApi.ask(request, response);

        verify(response).status(400);
        assertTrue(result instanceof ApiMessage);
        assertEquals("AI disabled", ((ApiMessage) result).message);
    }

    @Test
    void testAsk_InvalidContextId() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> aiApi.ask(request, response));
    }

    @Test
    void testAsk_NoAnswer() {
        when(sessionRecord.getUserId()).thenReturn(userId);
        when(request.attribute("session")).thenReturn(sessionRecord);
        when(request.queryParams("contextId")).thenReturn(String.valueOf(contextId));
        when(aiManager.userOwnContext(userId, contextId)).thenReturn(true);
        when(aiWorker.ask(eq(contextId), eq(sessionRecord), anyList())).thenReturn(null);

        assertThrows(spark.HaltException.class, () -> aiApi.ask(request, response));
        verify(response).status(500);
    }
}