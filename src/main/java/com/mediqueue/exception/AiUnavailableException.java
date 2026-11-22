package com.mediqueue.exception;

// The AI provider failed (no key, timeout, quota, blocked). The message is for the LOG only.
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) { super(message); }
}