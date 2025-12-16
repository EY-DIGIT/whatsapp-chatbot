package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.AuditLog;
import in.indore.whatsappbot.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Slf4j
@Service
public class AuditService {
    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public AuditService(AuditLogRepository repo, ObjectMapper objectMapper, SystemConfigService systemConfigService) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.systemConfigService = systemConfigService;
    }

    @Transactional
    public void logIn(String phone, String message, String raw) {
        // If message logging is disabled in system_config, skip persisting IN messages
        if (!systemConfigService.isMessageLogEnabled()) return;
        String normalized = normalizeRaw(raw);
        repo.save(new AuditLog(phone, "IN", message, normalized));
    }

    @Transactional
    public void logOut(String phone, String message, String raw) {
        // If message logging is disabled in system_config, skip persisting OUT messages
        if (!systemConfigService.isMessageLogEnabled()) return;
        String normalized = normalizeRaw(raw);
        repo.save(new AuditLog(phone, "OUT", message, normalized));
    }

    @Transactional
    public void logError(String phone, String message) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("error", message == null ? "" : message));
            repo.save(new AuditLog(phone, "ERR", message == null ? "" : message, payload));
        } catch (Exception e) {
            log.info("Error logging audit error for {}: {}", phone, e.getMessage());
            // fallback to minimal json
            repo.save(new AuditLog(phone, "ERR", message == null ? "" : message, "{}"));
        }
    }

    private String normalizeRaw(String raw) {
        if (raw == null) return "{}";
        String t = raw.trim();
        if (t.isEmpty()) return "{}";
        try {
            // if it's valid JSON, keep as-is
            objectMapper.readTree(t);
            return t;
        } catch (Exception e) {
            try {
                // otherwise encode as JSON string
                return objectMapper.writeValueAsString(raw);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }
}

