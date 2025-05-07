package app.finwave.backend.api.notification;

import app.finwave.backend.jooq.tables.records.NotificationsPointsRecord;
import app.finwave.backend.api.notification.data.Notification;
import app.finwave.backend.api.notification.data.NotificationOptions;
import app.finwave.backend.api.notification.data.point.WebPushPointData;
import app.finwave.backend.api.notification.data.point.WebSocketPointData;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationDatabaseTest {

    private DSLContext ctx;
    private NotificationDatabase notifDb;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        ctx = DSL.using(connection, SQLDialect.H2);
        ctx.execute("CREATE TABLE NOTIFICATIONS_POINTS (ID SERIAL PRIMARY KEY, USER_ID INT, IS_PRIMARY BOOLEAN, TYPE SMALLINT, CREATED_AT TIMESTAMP, DATA JSON, DESCRIPTION VARCHAR(255));");
        ctx.execute("CREATE TABLE NOTIFICATIONS_PULL (ID SERIAL PRIMARY KEY, TEXT VARCHAR(255), OPTIONS JSON, USER_ID INT, CREATED_AT TIMESTAMP);");
        notifDb = new NotificationDatabase(ctx);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ctx.execute("DROP ALL OBJECTS");
        connection.close();
    }

    @Test
    void testRegisterAndGetNotificationPoint() {
        WebPushPointData data = new WebPushPointData("ep", "auth", "p256");
        Optional<Long> idOpt = notifDb.registerNotificationPoint(1, true, data, "desc");
        assertTrue(idOpt.isPresent());
        long id = idOpt.get();
        assertEquals(1, notifDb.getPointsCount(1));
        List<NotificationsPointsRecord> list = notifDb.getUserNotificationsPoints(1);
        assertTrue(list.stream().anyMatch(r -> r.getId() == id && r.getDescription().equals("desc")));
    }

    @Test
    void testEditPointPrimaryAndDescription() {
        WebSocketPointData data = new WebSocketPointData(UUID.randomUUID());
        long id = notifDb.registerNotificationPoint(2, false, data, "old").get();
        notifDb.editNotificationPointPrimary(id, true);
        notifDb.editNotificationPointDescription(id, "new");
        List<NotificationsPointsRecord> list = notifDb.getUserNotificationsPoints(2);
        NotificationsPointsRecord rec = list.stream().filter(r -> r.getId() == id).findFirst().get();
        assertTrue(rec.getIsPrimary());
        assertEquals("new", rec.getDescription());
    }

    @Test
    void testDeleteNotificationPoint() {
        long id = notifDb.registerNotificationPoint(3, false, new WebPushPointData("ep3", "auth3", "p2563"), "del").get();
        notifDb.deleteNotificationPoint(id);
        assertFalse(notifDb.userOwnPoint(3, id), "Point should be deleted");
    }

    @Test
    void testUserOwnPoint() {
        long id = notifDb.registerNotificationPoint(4, false, new WebPushPointData("ep4", "auth4", "p2564"), "p").get();
        assertTrue(notifDb.userOwnPoint(4, id));
        assertFalse(notifDb.userOwnPoint(4, 999L));
    }

    @Test
    void testSaveAndPullNotifications() {
        Notification notification = Notification.create("Hi", new NotificationOptions(false, -1L, new HashMap<>()), 5);
        notifDb.saveNotification(notification);
        List<Notification> pulled = notifDb.pullNotifications(10);
        assertEquals(1, pulled.size());
        Notification pulledNotif = pulled.get(0);
        assertEquals("Hi", pulledNotif.text());
        assertEquals(5, pulledNotif.userId());
        // After pulling, ensure queue is empty
        assertTrue(notifDb.pullNotifications(10).isEmpty());
    }
}
