package app.finwave.backend;

import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import spark.Request;
import spark.Response;

import java.io.InputStream;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Base class for integration tests that need database access.
 * Loads application-integration.properties for database configuration.
 */
public abstract class BaseIntegrationTest {
    protected Request request;
    protected Response response;
    protected static final Gson gson = new Gson();
    protected static Properties integrationProperties;

    static {
        // Load integration properties once for all tests
        integrationProperties = new Properties();
        try (InputStream in = BaseIntegrationTest.class.getClassLoader()
                .getResourceAsStream("application-integration.properties")) {
            if (in != null) {
                integrationProperties.load(in);
            }
        } catch (Exception e) {
            System.err.println("Failed to load integration properties: " + e.getMessage());
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
        when(request.queryParams(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return fixture.has(key) ? fixture.get(key).getAsString() : null;
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
     * Helper to get a property from the integration properties file.
     * @param key Property key
     * @return Property value
     */
    protected String getIntegrationProperty(String key) {
        return integrationProperties.getProperty(key);
    }
} 