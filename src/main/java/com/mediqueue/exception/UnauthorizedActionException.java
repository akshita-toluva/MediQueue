package com.mediqueue.exception;

public class UnauthorizedActionException extends RuntimeException
{
        public UnauthorizedActionException(String message) {
            super(message);
        }
}
