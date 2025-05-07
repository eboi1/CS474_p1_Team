package app.finwave.backend;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationResourceLoadTest {

    @Test   
    void applicationIntegrationPropertiesShouldBeOnClasspath() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("application-integration.properties")) {
            assertNotNull(in, "application-integration.properties not found");
            props.load(in);
        }
        assertTrue(
            props.getProperty("db.url").startsWith("jdbc:tc:postgresql:"),
            "Expected a Testcontainers URL"
        );
    }
}
