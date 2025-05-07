package app.finwave.backend.api.currency;

import app.finwave.backend.api.BaseApiTest;
import app.finwave.backend.api.event.WebSocketWorker;
import app.finwave.backend.api.event.messages.response.NotifyUpdate;
import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.CurrencyConfig;
import app.finwave.backend.database.DatabaseWorker;
import app.finwave.backend.http.ApiMessage;
import app.finwave.backend.jooq.tables.records.CurrenciesRecord;
import app.finwave.backend.jooq.tables.records.UsersSessionsRecord;
import app.finwave.backend.utils.params.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import spark.HaltException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CurrencyApiTest extends BaseApiTest {

    @Mock
    private DatabaseWorker databaseWorker;
    
    @Mock
    private Configs configs;
    
    @Mock
    private WebSocketWorker socketWorker;
    
    @Mock
    private CurrencyDatabase currencyDatabase;
    
    @Mock
    private CurrencyConfig currencyConfig;
    
    @Mock
    private UsersSessionsRecord sessionRecord;
    
    private CurrencyApi currencyApi;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        
        // Setup configuration
        currencyConfig.maxCodeLength = 10;
        currencyConfig.maxSymbolLength = 5;
        currencyConfig.maxDecimals = 10;
        currencyConfig.maxDescriptionLength = 250;
        currencyConfig.maxCurrenciesPerUser = 10;
        when(configs.getState(any(CurrencyConfig.class))).thenReturn(currencyConfig);
        
        // Setup database mocks
        when(databaseWorker.get(CurrencyDatabase.class)).thenReturn(currencyDatabase);
        
        // Setup session
        when(sessionRecord.getUserId()).thenReturn(1);
        when(request.attribute("session")).thenReturn(sessionRecord);
        
        // Create the API instance
        currencyApi = new CurrencyApi(databaseWorker, configs, socketWorker);
    }

    @Test
    void testNewCurrency_Success() {
        // Setup
        String code = "USD";
        String symbol = "$";
        int decimals = 2;
        String description = "US Dollar";
        
        when(request.queryParams("code")).thenReturn(code);
        when(request.queryParams("symbol")).thenReturn(symbol);
        when(request.queryParams("decimals")).thenReturn(String.valueOf(decimals));
        when(request.queryParams("description")).thenReturn(description);
        
        when(currencyDatabase.getCurrenciesCount(1)).thenReturn(5);
        when(currencyDatabase.newCurrency(eq(1), eq(code), eq(symbol), eq((short) decimals), eq(description)))
            .thenReturn(Optional.of(3L));
        
        // Execute
        CurrencyApi.NewCurrencyResponse result = (CurrencyApi.NewCurrencyResponse) currencyApi.newCurrency(request, response);
        
        // Verify
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertEquals(3L, result.currencyId);
    }
    
    @Test
    void testNewCurrency_TooManyCurrencies() {
        // Setup
        String code = "USD";
        String symbol = "$";
        int decimals = 2;
        String description = "US Dollar";
        
        when(request.queryParams("code")).thenReturn(code);
        when(request.queryParams("symbol")).thenReturn(symbol);
        when(request.queryParams("decimals")).thenReturn(String.valueOf(decimals));
        when(request.queryParams("description")).thenReturn(description);
        
        when(currencyDatabase.getCurrenciesCount(1)).thenReturn(10); // Max currencies
        
        // Execute & Verify
        assertThrows(HaltException.class, () -> currencyApi.newCurrency(request, response));
    }
    
    @Test
    void testGetCurrencies_Success() {
        // Setup
        List<CurrenciesRecord> currencies = new ArrayList<>();
        CurrenciesRecord currency1 = mock(CurrenciesRecord.class);
        when(currency1.getId()).thenReturn(1L);
        when(currency1.getOwnerId()).thenReturn(1);
        when(currency1.getCode()).thenReturn("USD");
        when(currency1.getSymbol()).thenReturn("$");
        when(currency1.getDecimals()).thenReturn((short) 2);
        when(currency1.getDescription()).thenReturn("US Dollar");
        currencies.add(currency1);
        
        CurrenciesRecord currency2 = mock(CurrenciesRecord.class);
        when(currency2.getId()).thenReturn(2L);
        when(currency2.getOwnerId()).thenReturn(0); // Root currency
        when(currency2.getCode()).thenReturn("EUR");
        when(currency2.getSymbol()).thenReturn("€");
        when(currency2.getDecimals()).thenReturn((short) 2);
        when(currency2.getDescription()).thenReturn("Euro");
        currencies.add(currency2);
        
        when(currencyDatabase.getUserCurrenciesWithRoot(1)).thenReturn(currencies);
        
        // Execute
        CurrencyApi.GetCurrenciesResponse result = (CurrencyApi.GetCurrenciesResponse) currencyApi.getCurrencies(request, response);
        
        // Verify
        assertEquals(2, result.currencies.size());
        
        // First currency (user owned)
        assertEquals(1L, result.currencies.get(0).currencyId());
        assertTrue(result.currencies.get(0).owned());
        assertEquals("USD", result.currencies.get(0).code());
        assertEquals("$", result.currencies.get(0).symbol());
        assertEquals(2, result.currencies.get(0).decimals());
        assertEquals("US Dollar", result.currencies.get(0).description());
        
        // Second currency (system)
        assertEquals(2L, result.currencies.get(1).currencyId());
        assertFalse(result.currencies.get(1).owned());
        assertEquals("EUR", result.currencies.get(1).code());
        assertEquals("€", result.currencies.get(1).symbol());
        assertEquals(2, result.currencies.get(1).decimals());
        assertEquals("Euro", result.currencies.get(1).description());
    }
    
    @Test
    void testEditCurrencyCode_Success() {
        // Setup
        long currencyId = 1L;
        String newCode = "EUR";
        
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("code")).thenReturn(newCode);
        when(currencyDatabase.userCanEditCurrency(1, currencyId)).thenReturn(true);
        
        // Execute
        Object result = currencyApi.editCurrencyCode(request, response);
        
        // Verify
        verify(currencyDatabase).editCurrencyCode(currencyId, newCode);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testEditCurrencyCode_UnauthorizedCurrency() {
        // Setup
        long currencyId = 1L;
        String newCode = "EUR";
        
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("code")).thenReturn(newCode);
        when(currencyDatabase.userCanEditCurrency(1, currencyId)).thenReturn(false);
        
        // Execute & Verify
        assertThrows(InvalidParameterException.class, () -> currencyApi.editCurrencyCode(request, response));
        verify(currencyDatabase, never()).editCurrencyCode(anyLong(), anyString());
    }
    
    @Test
    void testEditCurrencySymbol_Success() {
        // Setup
        long currencyId = 1L;
        String newSymbol = "€";
        
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("symbol")).thenReturn(newSymbol);
        when(currencyDatabase.userCanEditCurrency(1, currencyId)).thenReturn(true);
        
        // Execute
        Object result = currencyApi.editCurrencySymbol(request, response);
        
        // Verify
        verify(currencyDatabase).editCurrencySymbol(currencyId, newSymbol);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testEditCurrencyDecimals_Success() {
        // Setup
        long currencyId = 1L;
        int newDecimals = 3;
        
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("decimals")).thenReturn(String.valueOf(newDecimals));
        when(currencyDatabase.userCanEditCurrency(1, currencyId)).thenReturn(true);
        
        // Execute
        Object result = currencyApi.editCurrencyDecimals(request, response);
        
        // Verify
        verify(currencyDatabase).editCurrencyDecimals(currencyId, (short) newDecimals);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
    
    @Test
    void testEditCurrencyDescription_Success() {
        // Setup
        long currencyId = 1L;
        String newDescription = "European Euro";
        
        when(request.queryParams("currencyId")).thenReturn(String.valueOf(currencyId));
        when(request.queryParams("description")).thenReturn(newDescription);
        when(currencyDatabase.userCanEditCurrency(1, currencyId)).thenReturn(true);
        
        // Execute
        Object result = currencyApi.editCurrencyDescription(request, response);
        
        // Verify
        verify(currencyDatabase).editCurrencyDescription(currencyId, newDescription);
        verify(response).status(200);
        verify(socketWorker).sendToUser(eq(1), any(NotifyUpdate.class));
        assertTrue(result instanceof ApiMessage);
    }
} 