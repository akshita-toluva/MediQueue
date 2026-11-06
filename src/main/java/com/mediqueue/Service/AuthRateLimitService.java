package com.mediqueue.Service;

import com.mediqueue.dsaLayer.RateLimiter;
import com.mediqueue.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

// Business rules for WHICH limits apply to WHICH auth endpoint.
// RateLimiter (dsaLayer) only knows "buckets"; this class knows "login" and "bootstrap".
@Service
public class AuthRateLimitService {

    private final RateLimiter rateLimiter;
    private final int loginPerAccount;
    private final int loginPerIp;
    private final Duration loginPeriod;
    private final int bootstrapPerIp;
    private final Duration bootstrapPeriod;

    // Explicit constructor (not Lombok) because @Value must sit on the parameters.
    // The defaults after ":" keep CI working even without the properties.
    public AuthRateLimitService(
            RateLimiter rateLimiter,
            @Value("${mediqueue.rate-limit.login.per-account:5}") int loginPerAccount,
            @Value("${mediqueue.rate-limit.login.per-ip:30}") int loginPerIp,
            @Value("${mediqueue.rate-limit.login.period-seconds:60}") long loginPeriodSeconds,
            @Value("${mediqueue.rate-limit.bootstrap.per-ip:10}") int bootstrapPerIp,
            @Value("${mediqueue.rate-limit.bootstrap.period-seconds:900}") long bootstrapPeriodSeconds) {
        this.rateLimiter = rateLimiter;
        this.loginPerAccount = loginPerAccount;
        this.loginPerIp = loginPerIp;
        this.loginPeriod = Duration.ofSeconds(loginPeriodSeconds);
        this.bootstrapPerIp = bootstrapPerIp;
        this.bootstrapPeriod = Duration.ofSeconds(bootstrapPeriodSeconds);
    }

    public void checkLogin(String clientIp, String email) {
        // 1) Whole machine first: cheap check that stops credential stuffing across many accounts.
        enforce("login:ip:" + clientIp, loginPerIp, loginPeriod,
                "Too many login attempts from this network");
        // 2) Then this account from this machine: stops password guessing on one account.
        //    Email normalised so "A@x.com" and "a@x.com " share one bucket.
        String account = (email == null) ? "" : email.trim().toLowerCase(Locale.ROOT);
        enforce("login:acct:" + clientIp + ":" + account, loginPerAccount, loginPeriod,
                "Too many login attempts for this account");
    }

    public void checkBootstrap(String clientIp) {
        enforce("bootstrap:ip:" + clientIp, bootstrapPerIp, bootstrapPeriod,
                "Too many bootstrap attempts");
    }

    private void enforce(String key, int capacity, Duration period, String message) {
        RateLimiter.Decision d = rateLimiter.tryConsume(key, capacity, period);
        if (!d.allowed()) {
            throw new RateLimitExceededException(
                    message + ". Try again in " + d.retryAfterSeconds() + "s.", d.retryAfterSeconds());
        }
    }
}