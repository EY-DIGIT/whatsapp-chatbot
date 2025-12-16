package in.indore.whatsappbot.service;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class FormDataService {

    private final RedisTemplate<String, Object> redisTemplate;

    public FormDataService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Stores the provided form data map in Redis as a hash.
     * Generates a 12-char NanoID as the key and returns it.
     */
    public String saveFormData(Map<String, Object> formData) {
        String key = NanoIdUtils.randomNanoId();
        redisTemplate.opsForValue().set(key, formData, Duration.ofMinutes(15));
        return key;
    }

    /**
     * Retrieves form data stored under the given key.
     * Returns an empty map if key not found.
     */
    public Map<String, Object> getFormData(String key) {
        Object raw = redisTemplate.opsForValue().getAndDelete(key);
        if (raw == null) {
            return new HashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }
}
