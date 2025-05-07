package app.finwave.backend.api.transaction.filter;

import org.junit.jupiter.api.Test;
import spark.Request;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionsFilterTest {

    @Test
    void testEmptyFilter() {
        TransactionsFilter filter = TransactionsFilter.EMPTY;
        
        assertNull(filter.getCategoriesIds());
        assertNull(filter.getAccountIds());
        assertNull(filter.getCurrenciesIds());
        assertNull(filter.getFromTime());
        assertNull(filter.getToTime());
        assertNull(filter.getDescription());
    }

    @Test
    void testFilterWithAllParameters() {
        List<Long> categoryIds = Arrays.asList(1L, 2L);
        List<Long> accountIds = Arrays.asList(3L, 4L);
        List<Long> currencyIds = Arrays.asList(5L, 6L);
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(7);
        OffsetDateTime toTime = OffsetDateTime.now();
        String description = "test";
        
        TransactionsFilter filter = new TransactionsFilter(
                categoryIds,
                accountIds,
                currencyIds,
                fromTime,
                toTime,
                description
        );
        
        assertEquals(categoryIds, filter.getCategoriesIds());
        assertEquals(accountIds, filter.getAccountIds());
        assertEquals(currencyIds, filter.getCurrenciesIds());
        assertEquals(fromTime, filter.getFromTime());
        assertEquals(toTime, filter.getToTime());
        assertEquals(description, filter.getDescription());
    }

    @Test
    void testFilterFromStrings() {
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(7);
        OffsetDateTime toTime = OffsetDateTime.now();
        
        TransactionsFilter filter = new TransactionsFilter(
                "1,2",
                "3,4",
                "5,6",
                fromTime.toString(),
                toTime.toString(),
                "test"
        );
        
        assertEquals(2, filter.getCategoriesIds().size());
        assertTrue(filter.getCategoriesIds().contains(1L));
        assertTrue(filter.getCategoriesIds().contains(2L));
        
        assertEquals(2, filter.getAccountIds().size());
        assertTrue(filter.getAccountIds().contains(3L));
        assertTrue(filter.getAccountIds().contains(4L));
        
        assertEquals(2, filter.getCurrenciesIds().size());
        assertTrue(filter.getCurrenciesIds().contains(5L));
        assertTrue(filter.getCurrenciesIds().contains(6L));
        
        assertNotNull(filter.getFromTime());
        assertNotNull(filter.getToTime());
        assertEquals("test", filter.getDescription());
    }

    @Test
    void testFilterFromRequest() {
        Request request = mock(Request.class);
        when(request.queryParams("categoriesIds")).thenReturn("1,2");
        when(request.queryParams("accountsIds")).thenReturn("3,4");
        when(request.queryParams("currenciesIds")).thenReturn("5,6");
        
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(7);
        OffsetDateTime toTime = OffsetDateTime.now();
        when(request.queryParams("fromTime")).thenReturn(fromTime.toString());
        when(request.queryParams("toTime")).thenReturn(toTime.toString());
        when(request.queryParams("description")).thenReturn("test");
        
        TransactionsFilter filter = new TransactionsFilter(request);
        
        assertEquals(2, filter.getCategoriesIds().size());
        assertTrue(filter.getCategoriesIds().contains(1L));
        assertTrue(filter.getCategoriesIds().contains(2L));
        
        assertEquals(2, filter.getAccountIds().size());
        assertTrue(filter.getAccountIds().contains(3L));
        assertTrue(filter.getAccountIds().contains(4L));
        
        assertEquals(2, filter.getCurrenciesIds().size());
        assertTrue(filter.getCurrenciesIds().contains(5L));
        assertTrue(filter.getCurrenciesIds().contains(6L));
        
        assertNotNull(filter.getFromTime());
        assertNotNull(filter.getToTime());
        assertEquals("test", filter.getDescription());
    }

    @Test
    void testValidateTimeWithValidRange() {
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(5);
        OffsetDateTime toTime = OffsetDateTime.now();
        
        TransactionsFilter filter = new TransactionsFilter(
                (List<Long>)null, 
                (List<Long>)null, 
                (List<Long>)null, 
                fromTime, 
                toTime, 
                null
        );
        
        assertTrue(filter.validateTime(10)); // 5 days < 10 day max
    }

    @Test
    void testValidateTimeWithInvalidRange() {
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(15);
        OffsetDateTime toTime = OffsetDateTime.now();
        
        TransactionsFilter filter = new TransactionsFilter(
                (List<Long>)null, 
                (List<Long>)null, 
                (List<Long>)null, 
                fromTime, 
                toTime, 
                null
        );
        
        assertFalse(filter.validateTime(10)); // 15 days > 10 day max
    }

    @Test
    void testValidateTimeWithNullTimes() {
        // Only from time
        TransactionsFilter filter1 = new TransactionsFilter(
                (List<Long>)null, 
                (List<Long>)null, 
                (List<Long>)null, 
                OffsetDateTime.now(), 
                null, 
                null
        );
        assertFalse(filter1.validateTime(10));
        
        // Only to time
        TransactionsFilter filter2 = new TransactionsFilter(
                (List<Long>)null, 
                (List<Long>)null, 
                (List<Long>)null, 
                null, 
                OffsetDateTime.now(), 
                null
        );
        assertFalse(filter2.validateTime(10));
        
        // Both null
        TransactionsFilter filter3 = new TransactionsFilter(
                (List<Long>)null, 
                (List<Long>)null, 
                (List<Long>)null, 
                null, 
                null, 
                null
        );
        assertFalse(filter3.validateTime(10));
    }

    @Test
    void testParseIds() {
        // Test null input
        assertNull(TransactionsFilter.parseIds(null));
        
        // Test empty string
        List<Long> result = TransactionsFilter.parseIds("");
        assertNotNull(result);
        assertTrue(result.isEmpty());


        // Test single value
        List<Long> result2 = TransactionsFilter.parseIds("123");
        assertEquals(1, result2.size());
        assertEquals(123L, result2.get(0));
        
        // Test multiple values
        List<Long> result3 = TransactionsFilter.parseIds("1,2,3");
        assertEquals(3, result3.size());
        assertEquals(1L, result3.get(0));
        assertEquals(2L, result3.get(1));
        assertEquals(3L, result3.get(2));
    }

    @Test
    void testSetterMethods() {
        // Start with an empty filter
        TransactionsFilter emptyFilter = TransactionsFilter.EMPTY;
        
        // Test each setter individually
        List<Long> categoryIds = Arrays.asList(1L, 2L);
        TransactionsFilter filter1 = emptyFilter.setCategoriesIds(categoryIds);
        assertEquals(categoryIds, filter1.getCategoriesIds());
        assertNull(filter1.getAccountIds());
        
        List<Long> accountIds = Arrays.asList(3L, 4L);
        TransactionsFilter filter2 = emptyFilter.setAccountIds(accountIds);
        assertEquals(accountIds, filter2.getAccountIds());
        assertNull(filter2.getCategoriesIds());
        
        List<Long> currencyIds = Arrays.asList(5L, 6L);
        TransactionsFilter filter3 = emptyFilter.setCurrenciesIds(currencyIds);
        assertEquals(currencyIds, filter3.getCurrenciesIds());
        
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(5);
        TransactionsFilter filter4 = emptyFilter.setFromTime(fromTime);
        assertEquals(fromTime, filter4.getFromTime());
        
        OffsetDateTime toTime = OffsetDateTime.now();
        TransactionsFilter filter5 = emptyFilter.setToTime(toTime);
        assertEquals(toTime, filter5.getToTime());
        
        String description = "test";
        TransactionsFilter filter6 = emptyFilter.setDescription(description);
        assertEquals(description, filter6.getDescription());
        
        // Chain all setters
        TransactionsFilter completeFilter = emptyFilter
                .setCategoriesIds(categoryIds)
                .setAccountIds(accountIds)
                .setCurrenciesIds(currencyIds)
                .setFromTime(fromTime)
                .setToTime(toTime)
                .setDescription(description);
        
        assertEquals(categoryIds, completeFilter.getCategoriesIds());
        assertEquals(accountIds, completeFilter.getAccountIds());
        assertEquals(currencyIds, completeFilter.getCurrenciesIds());
        assertEquals(fromTime, completeFilter.getFromTime());
        assertEquals(toTime, completeFilter.getToTime());
        assertEquals(description, completeFilter.getDescription());
    }

    @Test
    void testEqualsAndHashCode() {
        List<Long> categoryIds = Arrays.asList(1L, 2L);
        List<Long> accountIds = Arrays.asList(3L, 4L);
        List<Long> currencyIds = Arrays.asList(5L, 6L);
        OffsetDateTime fromTime = OffsetDateTime.now().minusDays(5);
        OffsetDateTime toTime = OffsetDateTime.now();
        String description = "test";
        
        TransactionsFilter filter1 = new TransactionsFilter(
                categoryIds, accountIds, currencyIds, fromTime, toTime, description
        );
        
        TransactionsFilter filter2 = new TransactionsFilter(
                categoryIds, accountIds, currencyIds, fromTime, toTime, description
        );
        
        TransactionsFilter filter3 = new TransactionsFilter(
                categoryIds, accountIds, currencyIds, fromTime, toTime, "different"
        );
        
        // Test equals
        assertEquals(filter1, filter1); // Same instance
        assertEquals(filter1, filter2); // Equal content
        assertNotEquals(filter1, filter3); // Different description
        assertNotEquals(filter1, null); // Null comparison
        assertNotEquals(filter1, "not a filter"); // Different class
        
        // Test hashCode
        assertEquals(filter1.hashCode(), filter2.hashCode());
        assertNotEquals(filter1.hashCode(), filter3.hashCode());
    }
} 