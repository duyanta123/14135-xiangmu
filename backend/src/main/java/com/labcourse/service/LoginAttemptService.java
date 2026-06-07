package com.labcourse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;
    private static final int ATTEMPT_WINDOW_MINUTES = 15;

    private final ConcurrentHashMap<String, LoginAttempt> attemptsCache = new ConcurrentHashMap<>();

    public LoginResult checkLoginAttempt(String key) {
        LoginAttempt attempt = attemptsCache.get(key);

        if (attempt == null) {
            return LoginResult.ALLOWED;
        }

        if (attempt.isLocked()) {
            long remainingMinutes = attempt.getRemainingLockMinutes();
            logger.warn("账号 {} 已被锁定，剩余 {} 分钟", key, remainingMinutes);
            return LoginResult.locked(remainingMinutes);
        }

        if (attempt.isWindowExpired()) {
            attemptsCache.remove(key);
            return LoginResult.ALLOWED;
        }

        return LoginResult.ALLOWED;
    }

    public void recordFailedAttempt(String key) {
        LoginAttempt attempt = attemptsCache.computeIfAbsent(key, k -> new LoginAttempt());
        attempt.recordFailure();

        if (attempt.getAttempts() >= MAX_ATTEMPTS) {
            attempt.lock(LOCK_DURATION_MINUTES);
            logger.warn("账号 {} 登录失败 {} 次，已锁定 {} 分钟", key, MAX_ATTEMPTS, LOCK_DURATION_MINUTES);
        }
    }

    public void resetAttempts(String key) {
        attemptsCache.remove(key);
        logger.info("账号 {} 登录成功，已重置失败计数", key);
    }

    public int getRemainingAttempts(String key) {
        LoginAttempt attempt = attemptsCache.get(key);
        if (attempt == null || attempt.isWindowExpired()) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - attempt.getAttempts());
    }

    private static class LoginAttempt {
        private int attempts;
        private LocalDateTime firstAttemptTime;
        private LocalDateTime lockUntil;

        void recordFailure() {
            LocalDateTime now = LocalDateTime.now();
            if (firstAttemptTime == null || isWindowExpired()) {
                attempts = 0;
                firstAttemptTime = now;
            }
            attempts++;
        }

        void lock(int durationMinutes) {
            lockUntil = LocalDateTime.now().plusMinutes(durationMinutes);
        }

        boolean isLocked() {
            return lockUntil != null && LocalDateTime.now().isBefore(lockUntil);
        }

        boolean isWindowExpired() {
            return firstAttemptTime != null
                    && LocalDateTime.now().isAfter(firstAttemptTime.plusMinutes(ATTEMPT_WINDOW_MINUTES));
        }

        long getRemainingLockMinutes() {
            if (lockUntil == null) return 0;
            long remaining = java.time.Duration.between(LocalDateTime.now(), lockUntil).toMinutes();
            return Math.max(0, remaining);
        }

        int getAttempts() {
            return attempts;
        }
    }

    public static class LoginResult {
        private final boolean allowed;
        private final boolean locked;
        private final long remainingLockMinutes;
        private final int remainingAttempts;

        public static final LoginResult ALLOWED = new LoginResult(true, false, 0, 0);

        private LoginResult(boolean allowed, boolean locked, long remainingLockMinutes, int remainingAttempts) {
            this.allowed = allowed;
            this.locked = locked;
            this.remainingLockMinutes = remainingLockMinutes;
            this.remainingAttempts = remainingAttempts;
        }

        public static LoginResult locked(long remainingMinutes) {
            return new LoginResult(false, true, remainingMinutes, 0);
        }

        public static LoginResult failed(int remainingAttempts) {
            return new LoginResult(false, false, 0, remainingAttempts);
        }

        public boolean isAllowed() { return allowed; }
        public boolean isLocked() { return locked; }
        public long getRemainingLockMinutes() { return remainingLockMinutes; }
        public int getRemainingAttempts() { return remainingAttempts; }
    }
}