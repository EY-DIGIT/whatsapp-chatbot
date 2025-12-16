package in.indore.whatsappbot.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import in.indore.whatsappbot.model.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Slf4j
@Service
public class ConversationStateService {
    private final StringRedisTemplate redis;
    private final LanguageService languageService;
    private final RedisTemplate<String, ChatSession> stateTemplate; // may be null
    private final ConcurrentMap<String, ChatSession> inMemoryCache = new ConcurrentHashMap<>();
    private static final Duration STATE_TTL = Duration.ofMinutes(15);
    private final ObjectMapper objectMapper;

    public ConversationStateService(StringRedisTemplate redis, LanguageService languageService, RedisTemplate<String, ChatSession> stateTemplate, ObjectMapper objectMapper) {
        this.redis = redis;
        this.languageService = languageService;
        this.stateTemplate = stateTemplate; // may be null if Redis not configured
        this.objectMapper = objectMapper;
    }

    public void clearSession(String phone) {
        if (stateTemplate != null) {
            stateTemplate.delete(keyState(phone));
        } else {
            inMemoryCache.remove(keyState(phone));
        }
    }

    public void saveSession(String phone, ChatSession session) {
        if (stateTemplate != null) {
            stateTemplate.opsForValue().set(keyState(phone), session, STATE_TTL);
        } else {
            inMemoryCache.put(keyState(phone), session);
        }
    }

    public ChatSession getSession(String phone) {
        if (stateTemplate != null) {
            Object raw = stateTemplate.opsForValue().get(keyState(phone));
            if (raw == null) return null;
            if (raw instanceof ChatSession) return (ChatSession) raw;
            // Redis/Jackson might return a LinkedHashMap when deserializing - convert it
            if (raw instanceof Map) {
                try {
                    return objectMapper.convertValue(raw, ChatSession.class);
                } catch (Exception e) {
                    return null;
                }
            }
            // If it's a JSON string, try to parse
            if (raw instanceof String) {
                try {
                    return objectMapper.readValue((String) raw, ChatSession.class);
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        }
        return (ChatSession) inMemoryCache.get(keyState(phone));
    }

    public void setLanguage(String phone, String lang) { redis.opsForValue().set(keyLanguage(phone), lang); }
    public String getLanguage(String phone) {
        String lang = redis.opsForValue().get(keyLanguage(phone));
        if (lang != null) return lang;
        // fallback to DB via UserService; if found, cache into Redis and return
        try {
            log.info("Language not found in cache for {}, checking database", phone);
            String dbLang = languageService.getLanguage(phone);
            if (dbLang != null) {
                // cache in redis (no TTL, consistent with setLanguage)
                redis.opsForValue().set(keyLanguage(phone), dbLang);
                return dbLang;
            }
        } catch (Exception ignored) {
            // ignore and return null
        }
        return null;
    }

    private String keyState(String phone) { return "wa:user:" + phone + ":state"; }
    private String keyLanguage(String phone) { return "wa:user:" + phone + ":language"; }
}

