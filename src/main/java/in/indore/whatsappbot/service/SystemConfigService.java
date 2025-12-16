package in.indore.whatsappbot.service;

import in.indore.whatsappbot.model.SystemConfig;
import in.indore.whatsappbot.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SystemConfigService {

    public static final String MESSAGE_LOG_KEY = "MESSAGE_LOG_ENABLED";

    private final SystemConfigRepository repository;
    private final AtomicBoolean messageLogEnabled = new AtomicBoolean(true); // safe fallback to true

    private final long refreshMs;

    public SystemConfigService(SystemConfigRepository repository,
                               @Value("${app.system-config.refresh-ms:30000}") long refreshMs) {
        this.repository = repository;
        this.refreshMs = refreshMs;
    }

    @PostConstruct
    public void init() {
        refreshFromDb();
    }

    public boolean isMessageLogEnabled() {
        return messageLogEnabled.get();
    }

    @Scheduled(fixedDelayString = "${app.system-config.refresh-ms:30000}")
    public void scheduledRefresh() {
        refreshFromDb();
    }

    private void refreshFromDb() {
        try {
            repository.findById(MESSAGE_LOG_KEY)
                    .map(SystemConfig::isEnabled)
                    .ifPresentOrElse(
                            messageLogEnabled::set,
                            () -> messageLogEnabled.set(true) // fallback to true when missing
                    );
        } catch (Exception e) {
            // If error occurs, do not overwrite cached value. If first run and still default, keep default true.
        }
    }
}

