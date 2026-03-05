package in.indore.whatsappbot.service;

import in.indore.whatsappbot.config.WhatsappConfig;
import in.indore.whatsappbot.dto.OutgoingMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;


@Service
public class WhatsappMessageService {
    private static final Logger log = LoggerFactory.getLogger(WhatsappMessageService.class);
     private final ObjectMapper objectMapper;
     private final WhatsappConfig config;

     public WhatsappMessageService(ObjectMapper objectMapper, WhatsappConfig config) {
         this.objectMapper = objectMapper;
         this.config = config;
         log.debug("WhatsappMessageService initialized (apiBaseUrl={})", config == null ? null : config.getApiBaseUrl());
     }

     // Build an interactive list message payload with 5 dummy grievances localized by lang.
     public OutgoingMessageDto buildListMessage(String to, String lang, List<String> grievances) {
         log.debug("buildListMessage invoked for to={}, lang={}", to, lang);

         boolean isHindi = "hi".equalsIgnoreCase(lang);

         try {
             String bodyText = isHindi
                     ? "कृपया अपनी शिकायत चुनें"
                     : "Please select your grievance";

             String buttonText = isHindi ? "चुनें" : "Select";

             List<Map<String, Object>> rows = new ArrayList<>();

             // ✅ Use list directly (no index/fallback logic)
             for (String grievanceId : grievances) {
                 rows.add(Map.of(
                         "id", grievanceId,
                         "title", grievanceId,
                         "description", ""
                 ));
             }

             Map<String, Object> section = Map.of(
                     "title", isHindi ? "शिकायत सूची" : "Grievances",
                     "rows", rows
             );

             Map<String, Object> interactive = Map.of(
                     "type", "list",
                     "body", Map.of("text", bodyText),
                     "action", Map.of(
                             "button", buttonText,
                             "sections", List.of(section)
                     )
             );

             Map<String, Object> payload = Map.of(
                     "messaging_product", "whatsapp",
                     "to", to,
                     "type", "interactive",
                     "interactive", interactive
             );

             String raw = objectMapper.writeValueAsString(payload);
             log.debug("Built interactive list message for {}: {}", to, raw);

             return new OutgoingMessageDto(to, bodyText, raw);

         } catch (Exception e) {
             log.error("Failed to build interactive list message for {}", to, e);
             throw new RuntimeException(e);
         }
     }


    public OutgoingMessageDto buildTextMessage(String to, String text) {
         try {
             var payload = java.util.Map.of(
                     "messaging_product", "whatsapp",
                     "to", to,
                     "type", "text",
                     "text", java.util.Map.of("body", text)
             );
             String raw = objectMapper.writeValueAsString(payload);
             return new OutgoingMessageDto(to, text, raw);
         } catch (Exception e) {
             log.error("Failed to build text message to={}", to, e);
             throw new RuntimeException(e);
         }
     }

     public HttpHeaders prepareHeaders() {
         log.debug("Preparing HTTP headers for WhatsApp API");
         HttpHeaders headers = new HttpHeaders();
         String token = config.getApiToken();
         if (token != null && !token.isBlank()) {
             headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
             log.trace("Authorization header set (token masked)");
         }
         headers.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
         return headers;
     }

     // Added: send the raw JSON payload to the WhatsApp API using configured base URL and token.
     public void sendMessage(OutgoingMessageDto msg) {
         log.debug("sendMessage invoked for to={}", msg == null ? null : msg.to());
         try {
             String base = config.getApiBaseUrl();
             if (base == null || base.isBlank()) {
                 throw new IllegalStateException("whatsapp.api-base-url is not configured");
             }
//            String endpoint = base.endsWith("/") ? base + "messages" : base + "/messages";
             String endpoint = base;
             HttpClient client = HttpClient.newHttpClient();
             HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create(endpoint))
                     .header(HttpHeaders.AUTHORIZATION, "Bearer " + (config.getApiToken() == null ? "" : config.getApiToken()))
                     .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8")
                     .POST(HttpRequest.BodyPublishers.ofString(msg.rawJson(), StandardCharsets.UTF_8))
                     .build();

             HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
             int sc = response.statusCode();
             log.info("WhatsApp API responded status={} for to={}", sc, msg.to());
             if (sc < 200 || sc >= 300) {
                 log.warn("Non-success response from WhatsApp API: status={}, body={}", sc, response.body());
                 throw new RuntimeException("Non-success response from WhatsApp API: Status " + sc);
             }
             log.trace("WhatsApp response body={}", response.body());
         } catch (Exception e) {
             // best-effort: swallow and return false so caller can continue flow
             log.warn("Failed to send WhatsApp message, returning false", e);
             throw new RuntimeException("Failed to send WhatsApp message: " + e.getMessage(), e);
         }
     }
 }
