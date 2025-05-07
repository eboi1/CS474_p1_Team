package app.finwave.backend.api.notification;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.api.notification.data.Notification;
import app.finwave.backend.api.notification.data.point.WebPushPointData;
import app.finwave.backend.api.notification.manager.NotificationManager;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.general.VapidKeysConfig;
import app.finwave.backend.config.app.NotificationsConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.NotificationsPointsRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NotificationApiTest extends BaseApiTest {

    @Mock private NotificationDatabase notificationDb;
    @Mock private NotificationManager notificationManager;
    @Mock private WebSocketWorker socketWorker;
    private NotificationsConfig notificationsConfig;
    private NotificationsConfig.WebPushConfig webPushConfig;
    private NotificationApi notificationApi;
    @Mock private UsersSessionsRecord sessionRecord;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        // Setup config with limits
        notificationsConfig = new NotificationsConfig();
        notificationsConfig.maxPointsPerUser = 2;
        notificationsConfig.maxNotificationLength = 200;
        webPushConfig = new NotificationsConfig.WebPushConfig();
        webPushConfig.maxEndpointLength = 255;
        webPushConfig.maxAuthLength = 100;
        webPushConfig.maxP256dhLength = 100;
        notificationsConfig.webPush = webPushConfig;
        notificationsConfig.maxDescriptionLength = 50;
        VapidKeysConfig vapid = new VapidKeysConfig();
        vapid.publicKey = "PUBLIC_KEY";
        Configs configs = mock(Configs.class);
        when(configs.getState(any(NotificationsConfig.class))).thenReturn(notificationsConfig);
        when(configs.getState(any(VapidKeysConfig.class))).thenReturn(vapid);
        DatabaseWorker dbWorker = mock(DatabaseWorker.class);
        when(dbWorker.get(NotificationDatabase.class)).thenReturn(notificationDb);
        notificationApi = new NotificationApi(dbWorker, notificationManager, configs, socketWorker);
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
    }

    @Test
    void testRegisterNewWebPushPoint_Success() {
        when(request.queryParams("endpoint")).thenReturn("https://example.com/ep");
        when(request.queryParams("auth")).thenReturn("AUTH_TOKEN");
        // Provide a valid P256DH (starts with 0x04 in Base64URL)
        String p256dhValue = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[]{0x04, 0x01});
        when(request.queryParams("p256dh")).thenReturn(p256dhValue);
        when(request.queryParams("description")).thenReturn("My Device");
        when(request.queryParams("primary")).thenReturn("true");
        when(notificationDb.getPointsCount(1)).thenReturn(0);
        when(notificationDb.registerNotificationPoint(eq(1), eq(true), any(WebPushPointData.class), eq("My Device")))
                .thenReturn(Optional.of(5L));

        Object result = notificationApi.registerNewWebPushPoint(request, response);

        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertEquals(Long.valueOf(5L), getFieldValue(result, "pointId"));
    }

    @Test
    void testRegisterNewWebPushPoint_TooManyPoints() {
        when(request.queryParams("endpoint")).thenReturn("x");
        when(request.queryParams("auth")).thenReturn("y");
        when(request.queryParams("p256dh")).thenReturn("BA=="); // Base64URL for 0x04
        when(request.queryParams("description")).thenReturn("d");
        when(notificationDb.getPointsCount(1)).thenReturn(notificationsConfig.maxPointsPerUser);

        // Should halt with 409 when max points reached
        HaltException ex = assertThrows(HaltException.class, () -> notificationApi.registerNewWebPushPoint(request, response));
        assertEquals(409, ex.statusCode());
    }

    @Test
    void testGetPoints() {
        NotificationsPointsRecord rec = new NotificationsPointsRecord();
        rec.setId(10L);
        rec.setDescription("desc");
        rec.setIsPrimary(false);
        rec.setType((short)0);
        rec.setCreatedAt(OffsetDateTime.now());

        List<NotificationsPointsRecord> list = List.of(rec);
        when(notificationDb.getUserNotificationsPoints(1)).thenReturn(list);

        Object result = notificationApi.getPoints(request, response);

        verify(response).status(200);
        List<?> points = (List<?>) getFieldValue(result, "points");
        assertEquals(1, points.size());
        assertEquals("desc", getFieldValue(points.get(0), "description"));
    }

    @Test
    void testEditPointDescription_Success() {
        when(request.queryParams("pointId")).thenReturn("7");
        when(request.queryParams("description")).thenReturn("New Desc");
        when(notificationDb.userOwnPoint(1, 7L)).thenReturn(true);

        Object result = notificationApi.editPointDescription(request, response);

        verify(notificationDb).editNotificationPointDescription(7L, "New Desc");
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testEditPointDescription_NotOwner() {
        when(request.queryParams("pointId")).thenReturn("8");
        when(notificationDb.userOwnPoint(1, 8L)).thenReturn(false);

        assertThrows(InvalidParameterException.class, () -> notificationApi.editPointDescription(request, response));
        verify(notificationDb, never()).editNotificationPointDescription(anyLong(), anyString());
    }

    @Test
    void testEditPointPrimary_Success() {
        when(request.queryParams("pointId")).thenReturn("9");
        when(request.queryParams("primary")).thenReturn("false");
        when(notificationDb.userOwnPoint(1, 9L)).thenReturn(true);

        Object result = notificationApi.editPointPrimary(request, response);

        verify(notificationDb).editNotificationPointPrimary(9L, false);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testDeletePoint_Success() {
        when(request.queryParams("pointId")).thenReturn("3");
        when(notificationDb.userOwnPoint(1, 3L)).thenReturn(true);

        Object result = notificationApi.deletePoint(request, response);

        verify(notificationDb).deleteNotificationPoint(3L);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testPushNotification() {
        when(request.queryParams("pointId")).thenReturn("4");
        when(request.queryParams("text")).thenReturn("Hello");
        when(request.queryParams("silent")).thenReturn("true");
        when(notificationDb.userOwnPoint(1, 4L)).thenReturn(true);

        Object result = notificationApi.pushNotification(request, response);

        verify(notificationManager).push(any(Notification.class));
        verify(response).status(200);
        assertTrue(result instanceof ApiMessage);
    }

    @Test
    void testGetKey() {
        Object result = notificationApi.getKey(request, response);
        verify(response).status(200);
        assertEquals("PUBLIC_KEY", getFieldValue(result, "publicKey"));
    }

    // Helper method to get field value using reflection
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }
}

