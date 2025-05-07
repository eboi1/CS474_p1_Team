package app.finwave.backend.api;

import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceLoadTest {

    @Test
    void applicationTestPropertiesShouldBeOnClasspath() throws Exception {
        // Try to load application-test.properties
        Properties props = new Properties();
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("application-test.properties")) {
            assertNotNull(in, "application-test.properties not found on test classpath");
            props.load(in);
        }
        // Assert a property you know is in the file
        assertEquals(
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                props.getProperty("db.url"),
                "db.url did not match expected H2 URL"
        );
    }
    
    @Test
    void applicationIntegrationPropertiesShouldBeOnClasspath() throws Exception {
        // Try to load application-integration.properties
        Properties props = new Properties();
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream("application-integration.properties")) {
            assertNotNull(in, "application-integration.properties not found on test classpath");
            props.load(in);
        }
        // Assert a property you know is in the file
        assertNotNull(props.getProperty("db.url"), "db.url should be defined in integration properties");
    }
    
    @Test
    void fixtureFilesShouldBeLoadable() {
        // Test loading account fixture
        JsonObject accountFixture = TestFixtureLoader.loadJsonFixture("sample-account.json");
        assertNotNull(accountFixture, "sample-account.json fixture could not be loaded");
        assertEquals(1, accountFixture.get("folderId").getAsLong());
        assertEquals("Sample Account", accountFixture.get("name").getAsString());
        
        // Test loading transaction fixture
        JsonObject transactionFixture = TestFixtureLoader.loadJsonFixture("transaction-create.json");
        assertNotNull(transactionFixture, "transaction-create.json fixture could not be loaded");
        assertEquals("Test Transaction", transactionFixture.get("description").getAsString());
        
        // Test loading budget fixture
        JsonObject budgetFixture = TestFixtureLoader.loadJsonFixture("budget-create.json");
        assertNotNull(budgetFixture, "budget-create.json fixture could not be loaded");
        assertEquals(1000.00, budgetFixture.get("amount").getAsDouble());
    }
}
