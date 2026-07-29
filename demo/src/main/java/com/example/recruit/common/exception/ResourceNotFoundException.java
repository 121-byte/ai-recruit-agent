package com.example.recruit.common.exception;

/** Thrown when a requested domain resource does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + " not found: " + resourceId);
    }
}
