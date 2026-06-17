package com.ibrasoft.tcketmanagebackend.model.dto.response;

/**
 * Response DTO for validation operations
 * Used to indicate whether a validation was successful and provide error messages
 */
public class ValidationResult {
    private boolean valid;
    private String message;

    /**
     * Default constructor for Jackson serialization
     */
    public ValidationResult() {}

    /**
     * Constructor with all fields
     * @param valid whether the validation was successful
     * @param message descriptive message about the validation result
     */
    public ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    // Getters and setters
    public boolean isValid() { 
        return valid; 
    }
    
    public void setValid(boolean valid) { 
        this.valid = valid; 
    }
    
    public String getMessage() { 
        return message; 
    }
    
    public void setMessage(String message) { 
        this.message = message; 
    }
}