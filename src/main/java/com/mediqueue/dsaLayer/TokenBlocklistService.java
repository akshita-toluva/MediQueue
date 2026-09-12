package com.mediqueue.dsaLayer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class TokenBlocklistService
{
    private static final String KEY_PREFIX = "jwt:revoked:";
    private final StringRedisTemplate redisTemplate;
    public TokenBlocklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    public void revoke(String token, long remainingValidityMillis) {
        if (remainingValidityMillis <= 0) {
            return; // already expired on its own — nothing to blocklist
        }
        redisTemplate.opsForValue().set(
                KEY_PREFIX + token, "1", Duration.ofMillis(remainingValidityMillis));
    }
    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}