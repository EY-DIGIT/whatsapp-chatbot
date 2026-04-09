package in.indore.whatsappbot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.indore.whatsappbot.config.WhatsappConfig;
import in.indore.whatsappbot.service.AuditService;
import in.indore.whatsappbot.service.ChatFlowService;
import in.indore.whatsappbot.service.WhatsappMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappWebhookController {

    private final WhatsappConfig config;
    private final ChatFlowService chatFlow;
    private final WhatsappMessageService waService;
    private final AuditService auditService;

    public WhatsappWebhookController(WhatsappConfig config,
                                     ChatFlowService chatFlow,
                                     WhatsappMessageService waService,
                                     AuditService auditService) {
        this.config = config;
        this.chatFlow = chatFlow;
        this.waService = waService;
        this.auditService = auditService;
    }

    @GetMapping("/webhook")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge,
                                         @RequestParam(name = "hub.verify_token", required = false) String token) {
        if ("subscribe".equals(mode) && token != null && token.equals(config.getWebhookVerifyToken())) {
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader) throws JsonProcessingException {
        if (!isValidMetaSignature(rawBody, signatureHeader)) {
            return ResponseEntity.status(401).body("invalid signature");
        }
        Map<String, Object> payload = new ObjectMapper().readValue(rawBody, Map.class);
        String phone = extractPhone(payload);
        String text = extractText(payload);
        if (phone != null) {
            if (text == null) text = "";
            // call the chat flow which will send messages; controller returns just plain ok
            try {
                chatFlow.handleIncoming(phone, text, serialize(payload));
            } catch (Exception e) {
                // swallow - still return ok to WhatsApp, but could log to auditService if desired
                log.error("Error:"+e.getMessage());
                auditService.logError(phone, e.getMessage());
            }
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("ok");
    }

    private String extractPhone(Map<String, Object> payload) {
        try {
            Object entry = ((java.util.List<?>) payload.get("entry")).get(0);
            Object changes = ((Map<?, ?>) entry).get("changes");
            Object change0 = ((java.util.List<?>) changes).get(0);
            Object value = ((Map<?, ?>) change0).get("value");
            Object messages = ((Map<?, ?>) value).get("messages");
            Object msg0 = ((java.util.List<?>) messages).get(0);
            Object from = ((Map<?, ?>) msg0).get("from");
            return from != null ? from.toString() : null;
        } catch (Exception e) {
            Object f = payload.get("from");
            return f != null ? f.toString() : null;
        }
    }

    private String extractText(Map<String, Object> payload) {
        try {
            Object entry = ((java.util.List<?>) payload.get("entry")).get(0);
            Object changes = ((Map<?, ?>) entry).get("changes");
            Object change0 = ((java.util.List<?>) changes).get(0);
            Object value = ((Map<?, ?>) change0).get("value");
            Object messages = ((Map<?, ?>) value).get("messages");
            Object msg0 = ((java.util.List<?>) messages).get(0);
            // Try to extract common text fields
            // 1) explicit text object (text.body)
            Object text = ((Map<?, ?>) msg0).get("text");
            if (text instanceof Map) {
                Object body = ((Map<?, ?>) text).get("body");
                if (body != null) return body.toString();
            }
            // 2) interactive reply (list_reply or button_reply)
            Object interactive = ((Map<?, ?>) msg0).get("interactive");
            if (interactive instanceof Map) {
                Map<?,?> imap = (Map<?,?>) interactive;
                Object listReply = imap.get("list_reply");
                if (listReply instanceof Map) {
                    Object id = ((Map<?,?>) listReply).get("id");
                    if (id != null) return id.toString();
                    Object title = ((Map<?,?>) listReply).get("title");
                    if (title != null) return title.toString();
                }
                Object buttonReply = imap.get("button_reply");
                if (buttonReply instanceof Map) {
                    Object id = ((Map<?,?>) buttonReply).get("id");
                    if (id != null) return id.toString();
                    Object payload1 = ((Map<?,?>) buttonReply).get("payload");
                    if (payload1 != null) return payload1.toString();
                }
            }
            // 3) fallback to body field
            Object body = ((Map<?, ?>) msg0).get("body");
            return body != null ? body.toString() : null;
        } catch (Exception e) {
            Object body = payload.get("body");
            return body != null ? body.toString() : null;
        }
    }

    private String serialize(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isValidMetaSignature(String payload, String signatureHeader) {

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        String receivedHash = signatureHeader.substring(7);

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(config.getMetaAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);

            byte[] computedHash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedHash = HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
