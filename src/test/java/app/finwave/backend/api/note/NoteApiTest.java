package app.finwave.backend.api.note;

import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.NotesConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.jooq.tables.records.NotesRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NoteApiTest {

    private NoteApi noteApi;
    private NoteDatabase mockNoteDatabase;
    private WebSocketWorker mockSocketWorker;
    private Request mockRequest;
    private Response mockResponse;
    private UsersSessionsRecord mockSession;

    @BeforeEach
    void setUp() {
        // Config
        NotesConfig notesConfig = new NotesConfig();
        notesConfig.maxNoteLength = 100;
        notesConfig.maxNotesPerUser = 5;

        Configs mockConfigs = mock(Configs.class);
        when(mockConfigs.getState(any(NotesConfig.class))).thenReturn(notesConfig);

        // Database and socket
        DatabaseWorker mockDbWorker = mock(DatabaseWorker.class);
        mockNoteDatabase = mock(NoteDatabase.class);
        when(mockDbWorker.get(NoteDatabase.class)).thenReturn(mockNoteDatabase);

        mockSocketWorker = mock(WebSocketWorker.class);

        noteApi = new NoteApi(mockConfigs, mockDbWorker, mockSocketWorker);

        // Common mocks
        mockRequest = mock(Request.class);
        mockResponse = mock(Response.class);

        mockSession = mock(UsersSessionsRecord.class);
        when(mockSession.getUserId()).thenReturn(1);
        when(mockRequest.attribute("session")).thenReturn(mockSession);
    }

    @Test
    void testGetNotesListReturnsNotes() {
        NotesRecord record = mock(NotesRecord.class);
        when(mockNoteDatabase.getNotes(1)).thenReturn(List.of(record));

        Object result = noteApi.getNotesList(mockRequest, mockResponse);
        assertNotNull(result);
        verify(mockResponse).status(200);
    }

    @Test
    void testGetNoteReturnsNote() {
        long noteId = 123L;
        NotesRecord record = mock(NotesRecord.class);

        when(mockNoteDatabase.userOwnNote(1, noteId)).thenReturn(true);
        when(mockNoteDatabase.getNote(noteId)).thenReturn(Optional.of(record));
        when(mockRequest.attribute("session")).thenReturn(mockSession);
        when(mockRequest.queryParams("noteId")).thenReturn(String.valueOf(noteId));

        Object result = noteApi.getNote(mockRequest, mockResponse);
        assertNotNull(result);
        verify(mockResponse).status(200);
    }

    @Test
    void testDeleteNote() {
        long noteId = 456L;
        when(mockNoteDatabase.userOwnNote(1, noteId)).thenReturn(true);
        when(mockRequest.queryParams("noteId")).thenReturn(String.valueOf(noteId));

        Object result = noteApi.deleteNote(mockRequest, mockResponse);
        assertNotNull(result);
        verify(mockNoteDatabase).deleteNote(noteId);
        verify(mockSocketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(mockResponse).status(200);
    }
}
