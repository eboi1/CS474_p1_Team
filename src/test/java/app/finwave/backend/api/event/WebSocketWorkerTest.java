package app.finwave.backend.api.event;

import app.finwave.backend.api.event.messages.ResponseMessage;
import app.finwave.backend.api.event.messages.response.notifications.NotificationEvent;
import app.finwave.backend.api.notification.data.Notification;
import app.finwave.backend.api.session.SessionManager;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketWorkerTest {
    @Mock
    private SessionManager sessionManager;

    @Mock
    private WebSocketClient webSocketClient;

    @InjectMocks
    private WebSocketWorker webSocketWorker;

    private static final Gson GSON = new Gson();
    private UsersSessionsRecord sessionRecord;
    private final int userId = 1;
    private final String token = "test-token";
    private final UUID notificationUUID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws IOException {
        sessionRecord = mock(UsersSessionsRecord.class);
//        when(sessionRecord.getUserId()).thenReturn(userId);

        // Mock send() to handle IOException and return CompletableFuture
//        when(webSocketClient.send(anyString())).thenAnswer(invocation -> CompletableFuture.completedFuture(null));
//        when(webSocketClient.send(any(NotificationEvent.class))).thenAnswer(invocation -> CompletableFuture.completedFuture(null));
    }

    @Test
    void testRegisterAnonClient() {
        webSocketWorker.registerAnonClient(webSocketClient);
        assertTrue(webSocketWorker.anonClients.contains(webSocketClient));
    }

    @Test
    void testRemoveAnonClient() {
        webSocketWorker.registerAnonClient(webSocketClient);
        webSocketWorker.removeAnonClient(webSocketClient);
        assertFalse(webSocketWorker.anonClients.contains(webSocketClient));
    }

    @Test
    void testAuthClient_Success() {
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));

        Optional<UsersSessionsRecord> result = webSocketWorker.authClient(webSocketClient, token);

        assertTrue(result.isPresent());
        assertEquals(sessionRecord, result.get());
        assertFalse(webSocketWorker.anonClients.contains(webSocketClient));
        assertTrue(webSocketWorker.authedClients.getOrDefault(userId, new java.util.HashSet<>()).contains(webSocketClient));
    }

    @Test
    void testAuthClient_Failure() {
        when(sessionManager.auth(token)).thenReturn(Optional.empty());

        Optional<UsersSessionsRecord> result = webSocketWorker.authClient(webSocketClient, token);

        assertFalse(result.isPresent());
        assertFalse(webSocketWorker.authedClients.containsKey(userId));
    }

    @Test
    void testRemoveAuthedClient() throws IOException {
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        webSocketWorker.authClient(webSocketClient, token);
        webSocketWorker.subscribeNotification(webSocketClient, notificationUUID);

        webSocketWorker.removeAuthedClient(webSocketClient, userId);

        assertFalse(webSocketWorker.authedClients.getOrDefault(userId, new java.util.HashSet<>()).contains(webSocketClient));
        assertFalse(webSocketWorker.notificationSubscribes.containsKey(notificationUUID));
        assertFalse(webSocketWorker.reversedNotificationSubscribes.containsKey(webSocketClient));
    }

    @Test
    void testSubscribeNotification_Success() {
        boolean result = webSocketWorker.subscribeNotification(webSocketClient, notificationUUID);

        assertTrue(result);
        assertEquals(webSocketClient, webSocketWorker.notificationSubscribes.get(notificationUUID));
        assertEquals(notificationUUID, webSocketWorker.reversedNotificationSubscribes.get(webSocketClient));
    }

    @Test
    void testSubscribeNotification_AlreadySubscribed() {
        webSocketWorker.subscribeNotification(webSocketClient, notificationUUID);
        WebSocketClient anotherClient = mock(WebSocketClient.class);

        boolean result = webSocketWorker.subscribeNotification(anotherClient, notificationUUID);

        assertFalse(result);
        assertEquals(webSocketClient, webSocketWorker.notificationSubscribes.get(notificationUUID));
    }

    @Test
    void testSendNotification_Success() throws ExecutionException, InterruptedException, IOException {
        Notification notification = mock(Notification.class);

        CompletableFuture<Boolean> result = webSocketWorker.sendNotification(notificationUUID, notification);

        assertFalse(result.get()); // No subscriber yet
        verify(webSocketClient, never()).send(any(NotificationEvent.class));

        webSocketWorker.subscribeNotification(webSocketClient, notificationUUID);
        result = webSocketWorker.sendNotification(notificationUUID, notification);

        assertTrue(result.get());
        verify(webSocketClient).send(any(NotificationEvent.class));
    }

    @Test
    void testSendNotification_NoSubscriber() throws ExecutionException, InterruptedException, IOException {
        Notification notification = mock(Notification.class);

        CompletableFuture<Boolean> result = webSocketWorker.sendNotification(notificationUUID, notification);

        assertFalse(result.get());
        verify(webSocketClient, never()).send(any(NotificationEvent.class));
    }

    @Test
    void testSendToUser_Success() throws ExecutionException, InterruptedException, IOException {
        when(sessionManager.auth(token)).thenReturn(Optional.of(sessionRecord));
        webSocketWorker.authClient(webSocketClient, token);
        ResponseMessage<?> message = mock(ResponseMessage.class);

        CompletableFuture<Boolean> result = webSocketWorker.sendToUser(userId, message);

        assertTrue(result.get());
        verify(webSocketClient).send(anyString());
    }

    @Test
    void testSendToUser_NoClients() throws ExecutionException, InterruptedException, IOException {
        ResponseMessage<?> message = mock(ResponseMessage.class);

        CompletableFuture<Boolean> result = webSocketWorker.sendToUser(userId, message);

        assertFalse(result.get());
        verify(webSocketClient, never()).send(anyString());
    }
}