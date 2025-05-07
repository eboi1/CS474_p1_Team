package app.finwave.backend.api.config;

import app.finwave.backend.config.Configs;
import app.finwave.backend.config.app.*;
import app.finwave.backend.config.general.AiConfig;
import app.finwave.backend.config.general.ExchangesConfig;
import app.finwave.backend.config.general.UserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Response;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConfigApiTest {

    private Configs mockConfigs;
    private ConfigApi configApi;

    @BeforeEach
    void setUp() {
        mockConfigs = mock(Configs.class);

        // Mock all required config states
        when(mockConfigs.getState(any(UserConfig.class))).thenReturn(new UserConfig());
        when(mockConfigs.getState(any(AccountsConfig.class))).thenReturn(new AccountsConfig());
        when(mockConfigs.getState(any(CurrencyConfig.class))).thenReturn(new CurrencyConfig());
        when(mockConfigs.getState(any(NotesConfig.class))).thenReturn(new NotesConfig());
        when(mockConfigs.getState(any(TransactionConfig.class))).thenReturn(new TransactionConfig());
        when(mockConfigs.getState(any(AnalyticsConfig.class))).thenReturn(new AnalyticsConfig());
        when(mockConfigs.getState(any(NotificationsConfig.class))).thenReturn(new NotificationsConfig());
        when(mockConfigs.getState(any(AccumulationConfig.class))).thenReturn(new AccumulationConfig());
        when(mockConfigs.getState(any(RecurringTransactionConfig.class))).thenReturn(new RecurringTransactionConfig());
        when(mockConfigs.getState(any(ReportConfig.class))).thenReturn(new ReportConfig());

        AiConfig aiConfig = new AiConfig();
        aiConfig.enabled = true;
        when(mockConfigs.getState(any(AiConfig.class))).thenReturn(aiConfig);

        ExchangesConfig exchangesConfig = new ExchangesConfig();
        exchangesConfig.fawazahmed0Exchanges.enabled = true;
        when(mockConfigs.getState(any(ExchangesConfig.class))).thenReturn(exchangesConfig);

        configApi = new ConfigApi(mockConfigs);
    }

    @Test
    void testGetConfigsReturnsJson() {
        Request request = mock(Request.class);
        Response response = mock(Response.class);

        Object result = configApi.getConfigs(request, response);

        assertNotNull(result);
        assertTrue(result.toString().contains("users"));
        assertTrue(result.toString().contains("ai"));
    }

    @Test
    void testHashReturnsValidJson() {
        Request request = mock(Request.class);
        Response response = mock(Response.class);

        Object result = configApi.hash(request, response);

        assertNotNull(result);
        assertTrue(result.toString().contains("hash"));
    }
}
