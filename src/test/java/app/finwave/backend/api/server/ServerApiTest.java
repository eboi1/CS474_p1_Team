package app.finwave.backend.api.server;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.config.Configs;
import app.finwave.backend.utils.VersionCatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.Request;
import spark.Response;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ServerApiTest {

    private Request request;
    private Response response;
    private ServerApi serverApi;

    @Mock
    private Configs configs;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        request = mock(Request.class);
        response = mock(Response.class);
        serverApi = new ServerApi();
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

    @Test
    void testGetVersion_ReturnsCorrectVersion() {
        // The version is stored in VersionCatcher.VERSION (assuming a static value)
        String expectedVersion = VersionCatcher.VERSION;
        Request req = mock(Request.class);
        Response res = mock(Response.class);

        Object result = serverApi.getVersion(req, res);

        assertNotNull(result, "Response should not be null");
        assertEquals(expectedVersion, getFieldValue(result, "version"), "Version should match");
    }
}

