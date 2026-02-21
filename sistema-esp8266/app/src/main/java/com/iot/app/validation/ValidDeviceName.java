package com.iot.app.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a device name contains only alphanumeric characters, underscores, and hyphens.
 * Device names must be between 1 and 50 characters long.
 */
@Documented
@Constraint(validatedBy = DeviceNameValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDeviceName {
    String message() default "Device name must contain only alphanumeric characters, underscores, and hyphens (1-50 characters)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
