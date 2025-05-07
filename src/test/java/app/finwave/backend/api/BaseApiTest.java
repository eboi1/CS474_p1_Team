package app.finwave.backend.api;

import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import spark.Request;
import spark.Response;

import java.io.InputStream;
import java.util.Properties;

import static org.mockito.Mockito.*;

/**
 * Base class for API tests providing common functionality.
 */
public abstract class BaseApiTest {
    protected Request request;
    protected Response response;
    protected static final Gson gson = new Gson();
    protected static Properties testProperties;

    static {
        // Load test properties once for all tests
        testProperties = new Properties();
        try (InputStream in = BaseApiTest.class.getClassLoader()
                .getResourceAsStream("application-test.properties")) {
            if (in != null) {
                testProperties.load(in);
            }
        } catch (Exception e) {
            System.err.println("Failed to load test properties: " + e.getMessage());
        }
    }

    @BeforeEach
    protected void setUp() {
        request = mock(Request.class);
        response = mock(Response.class);
    }

    /**
     * Helper to load a fixture and set up request query parameters based on its contents.
     * @param fixturePath Path to fixture file relative to fixtures/
     */
    protected void setupRequestFromFixture(String fixturePath) {
        JsonObject fixture = TestFixtureLoader.loadJsonFixture(fixturePath);
        fixture.entrySet().forEach(entry -> {
            when(request.queryParams(entry.getKey())).thenReturn(entry.getValue().getAsString());
        });
    }

    /**
     * Helper to load a fixture and set up request body with its contents.
     * @param fixturePath Path to fixture file relative to fixtures/
     */
    protected void setupRequestBodyFromFixture(String fixturePath) {
        JsonObject fixture = TestFixtureLoader.loadJsonFixture(fixturePath);
        when(request.body()).thenReturn(gson.toJson(fixture));
    }

    /**
     * Helper to get a property from the test properties file.
     * @param key Property key
     * @return Property value
     */
    protected String getTestProperty(String key) {
        return testProperties.getProperty(key);
    }

    /**
     * Helper to stub queryParams or bodyParams:
     *   when(request.queryParams("name")).thenReturn("foo");
     *   when(request.body()).thenReturn("{ ... }");
     */
}
