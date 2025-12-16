package in.indore.whatsappbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp")
public class WhatsappConfig {

    private String webhookVerifyToken;
    private String apiToken;
    private String apiBaseUrl;

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }
    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }
    public String getApiToken() {
        return apiToken;
    }
    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }
}
