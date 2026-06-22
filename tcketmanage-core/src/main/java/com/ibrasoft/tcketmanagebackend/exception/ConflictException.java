package com.ibrasoft.tcketmanagebackend.exception;

/**
 * Thrown when a request conflicts with current state (e.g. a uniqueness violation).
 * Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
