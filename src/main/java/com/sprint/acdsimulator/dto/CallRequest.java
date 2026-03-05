package com.sprint.acdsimulator.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 📚 LESSON — @RequestBody + Bean Validation:
 *
 * When you annotate a controller parameter with @RequestBody, Spring uses Jackson
 * (the JSON library) to deserialise the incoming JSON into a Java object.
 *
 * Bean Validation (JSR-380) lets you declare constraints directly on the fields:
 *   @NotBlank  — string must not be null/empty/whitespace
 *   @Min/@Max  — numeric bounds
 *   @Size      — string or collection length
 *
 * You trigger validation by adding @Valid to the @RequestBody parameter in the controller.
 * If validation fails, Spring throws MethodArgumentNotValidException which our
 * GlobalExceptionHandler catches and returns as a clean 400 Bad Request.
 *
 * Why @NoArgsConstructor + @AllArgsConstructor?
 * Jackson needs a no-arg constructor to deserialise JSON into the object.
 * @Data only generates an all-args constructor via @RequiredArgsConstructor.
 * Adding @NoArgsConstructor explicitly satisfies Jackson.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallRequest {

    @NotBlank(message = "Caller ID must not be blank")
    private String callerId;

    /**
     * Priority: 1 = VIP (highest urgency), 5 = Regular (lowest urgency).
     * The PriorityBlockingQueue sorts by this — lower number = served first.
     */
    @Min(value = 1, message = "Priority must be at least 1 (VIP)")
    @Max(value = 5, message = "Priority must be at most 5 (Regular)")
    private int priority = 3;
}

