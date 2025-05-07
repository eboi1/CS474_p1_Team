package app.finwave.backend.api;

import app.finwave.backend.utils.TestFixtureLoader;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest extends BaseApiTest {

    @Test
    void testJsonSerialization() {
        // Load test data from fixture
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("api-response-test.json");
        String message = fixture.get("message").getAsString();
        int value = fixture.get("value").getAsInt();
        
        TestResponse response = new TestResponse(message, value);
        String json = response.toString();
        assertTrue(json.contains("\"message\":\"" + message + "\""));
        assertTrue(json.contains("\"value\":" + value));
    }

    @Test
    void testLocalDateSerialization() {
        // Load test data from fixture
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("api-date-test.json");
        int year = fixture.get("year").getAsInt();
        int month = fixture.get("month").getAsInt();
        int day = fixture.get("day").getAsInt();
        
        LocalDate date = LocalDate.of(year, month, day);
        DateResponse response = new DateResponse(date);
        String json = response.toString();
        assertTrue(json.contains("\"date\":\"" + year + "-" + 
                    (month < 10 ? "0" + month : month) + "-" + 
                    (day < 10 ? "0" + day : day) + "\""));
    }

    @Test
    void testLocalDateTimeSerialization() {
        // Load test data from fixture
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("api-datetime-test.json");
        int year = fixture.get("year").getAsInt();
        int month = fixture.get("month").getAsInt();
        int day = fixture.get("day").getAsInt();
        int hour = fixture.get("hour").getAsInt();
        int minute = fixture.get("minute").getAsInt();
        int second = fixture.get("second").getAsInt();
        
        LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute, second);
        DateTimeResponse response = new DateTimeResponse(dateTime);
        String json = response.toString();
        String expectedDate = String.format("%d-%02d-%02dT%02d:%02d:%02d", 
                              year, month, day, hour, minute, second);
        assertTrue(json.contains("\"dateTime\":\"" + expectedDate + "\""));
    }

    @Test
    void testOffsetDateTimeSerialization() {
        // Load test data from fixture
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("api-offset-datetime-test.json");
        int year = fixture.get("year").getAsInt();
        int month = fixture.get("month").getAsInt();
        int day = fixture.get("day").getAsInt();
        int hour = fixture.get("hour").getAsInt();
        int minute = fixture.get("minute").getAsInt();
        int second = fixture.get("second").getAsInt();
        
        OffsetDateTime dateTime = OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC);
        OffsetDateTimeResponse response = new OffsetDateTimeResponse(dateTime);
        String json = response.toString();
        String expectedDate = String.format("%d-%02d-%02dT%02d:%02d:%02dZ", 
                              year, month, day, hour, minute, second);
        assertTrue(json.contains("\"dateTime\":\"" + expectedDate + "\""));
    }

    @Test
    void testOptionalPresentSerialization() {
        // Load test data from fixture
        JsonObject fixture = TestFixtureLoader.loadJsonFixture("api-optional-test.json");
        String optionalValue = fixture.get("value").getAsString();
        
        Optional<String> optional = Optional.of(optionalValue);
        OptionalResponse response = new OptionalResponse(optional);
        String json = response.toString();
        assertTrue(json.contains("\"value\":\"" + optionalValue + "\""));
    }

    @Test
    void testOptionalEmptySerialization() {
        Optional<String> optional = Optional.empty();
        OptionalResponse response = new OptionalResponse(optional);
        String json = response.toString();
        assertEquals("{}", json);
    }

    // Test response classes
    static class TestResponse extends ApiResponse {
        public final String message;
        public final int value;

        public TestResponse(String message, int value) {
            this.message = message;
            this.value = value;
        }
    }

    static class DateResponse extends ApiResponse {
        public final LocalDate date;

        public DateResponse(LocalDate date) {
            this.date = date;
        }
    }

    static class DateTimeResponse extends ApiResponse {
        public final LocalDateTime dateTime;

        public DateTimeResponse(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }
    }

    static class OffsetDateTimeResponse extends ApiResponse {
        public final OffsetDateTime dateTime;

        public OffsetDateTimeResponse(OffsetDateTime dateTime) {
            this.dateTime = dateTime;
        }
    }

    static class OptionalResponse extends ApiResponse {
        public final Optional<String> value;

        public OptionalResponse(Optional<String> value) {
            this.value = value;
        }
    }
} 