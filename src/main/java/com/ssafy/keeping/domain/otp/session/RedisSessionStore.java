package com.ssafy.keeping.domain.otp.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisSessionStore implements RegSessionStore{

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    private static final Duration REG_TTL = Duration.ofMinutes(30);

    @PostConstruct
    void init() {
        om.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    @Override
    public void setSession(String KEY_PREFIX, String regSessionId, RegSession regSession, Duration ttl) {
        try {
            redis.opsForValue().set(KEY_PREFIX + regSessionId, om.writeValueAsString(regSession), ttl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RegSession getSession(String KEY_PREFIX, String regSessionId) {
        String key = KEY_PREFIX + regSessionId;
        String json = redis.opsForValue().get(key);

        if(json == null) {
            redis.delete(key);
            throw new IllegalStateException("가입 세션이 만료되었습니다. 다시 시도해주세요.");
        }

        try {
            return om.readValue(json, RegSession.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteSession(String KEY_PREFIX, String regSessionId) {
        redis.delete(KEY_PREFIX + regSessionId);
    }

    @Override
    public Duration remainingTtl(String KEY_PREFIX, String regSessionId) {
        Long seconds = redis.getConnectionFactory() != null ? redis.getConnectionFactory().getConnection()
                .keyCommands().ttl((KEY_PREFIX + regSessionId).getBytes()) : null;

        if(seconds == null || seconds < 0 ) {
            return REG_TTL;
        }

        return Duration.ofSeconds(seconds);
    }
}
