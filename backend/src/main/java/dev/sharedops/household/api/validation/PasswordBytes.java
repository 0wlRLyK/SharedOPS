package dev.sharedops.household.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordBytesValidator.class)
public @interface PasswordBytes {
  String message() default "Password must not exceed 72 UTF-8 bytes";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
