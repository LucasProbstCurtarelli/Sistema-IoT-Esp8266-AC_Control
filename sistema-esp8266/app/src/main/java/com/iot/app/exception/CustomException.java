package com.iot.app.exception;

/**
 * Base custom exception class.
 */
public class CustomException extends RuntimeException {
    
    public CustomException(String message) {
        super(message);
    }
    
    public CustomException(String message, Throwable cause) {
        super(message, cause);
    }
}
