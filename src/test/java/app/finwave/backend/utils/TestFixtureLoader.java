package app.finwave.backend.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading test fixtures from the resources directory.
 */
public class TestFixtureLoader {

    private static final Gson gson = new Gson();

    /**
     * Load a JSON fixture file from the classpath.
     *
     * @param path The path to the fixture file, relative to resources/fixtures/
     * @return JsonObject representing the fixture data
     * @throws RuntimeException if the fixture cannot be loaded
     */
    public static JsonObject loadJsonFixture(String path) {
        String fullPath = "fixtures/" + path;
        try (InputStream is = TestFixtureLoader.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new RuntimeException("Fixture file not found: " + fullPath);
            }
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + fullPath, e);
        }
    }

    /**
     * Load a fixture file and parse it to a specific class type.
     *
     * @param path The path to the fixture file, relative to resources/fixtures/
     * @param classOfT The class to parse the JSON into
     * @param <T> The type to return
     * @return The parsed object
     * @throws RuntimeException if the fixture cannot be loaded or parsed
     */
    public static <T> T loadFixture(String path, Class<T> classOfT) {
        String fullPath = "fixtures/" + path;
        try (InputStream is = TestFixtureLoader.class.getClassLoader().getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new RuntimeException("Fixture file not found: " + fullPath);
            }
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            return gson.fromJson(reader, classOfT);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + fullPath, e);
        }
    }
} 