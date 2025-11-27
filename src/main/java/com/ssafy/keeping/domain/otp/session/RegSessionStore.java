package com.ssafy.keeping.domain.otp.session;

import java.time.Duration;

public interface RegSessionStore {
    void setSession(String KEY_PREFIX, String regSessionId, RegSession regSession, Duration ttl);
    RegSession getSession(String KEY_PREFIX, String regSessionId);
    void deleteSession(String KEY_PREFIX, String regSessionId);
    Duration remainingTtl(String KEY_PREFIX, String regSessionId);
}
