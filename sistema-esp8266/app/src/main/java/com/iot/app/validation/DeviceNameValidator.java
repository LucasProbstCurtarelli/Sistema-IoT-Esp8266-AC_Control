package com.iot.app.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Validator for device names.
 * Allows only alphanumeric characters, underscores, and hyphens.
 * Length must be between 1 and 50 characters.
 */
public class DeviceNameValidator implements ConstraintValidator<ValidDeviceName, String> {
    
    private static final Pattern DEVICE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    /**
     * Same rule as {@link #isValid(String, ConstraintValidatorContext)}, for maps and other values
     * that are not a single bean field.
     */
    public static boolean isAcceptable(String deviceName) {
        return deviceName != null && DEVICE_NAME_PATTERN.matcher(deviceName).matches();
    }
    
    @Override
    public void initialize(ValidDeviceName constraintAnnotation) {
        // No initialization needed
    }
    
    @Override
    public boolean isValid(String deviceName, ConstraintValidatorContext context) {
        return isAcceptable(deviceName);
    }
}
