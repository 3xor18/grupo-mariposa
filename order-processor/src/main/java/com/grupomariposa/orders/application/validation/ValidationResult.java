package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.application.command.OrderCommand;
import java.util.List;
import java.util.stream.Collectors;

public sealed interface ValidationResult permits ValidationResult.Valid, ValidationResult.Invalid {

    record Valid(OrderCommand command) implements ValidationResult {
    }

    record Invalid(List<ValidationError> errors) implements ValidationResult {

        private static final String SEPARATOR = "; ";

        public Invalid {
            errors = List.copyOf(errors);
        }

        public String summary() {
            return errors.stream().map(ValidationError::describe)
                    .collect(Collectors.joining(SEPARATOR));
        }
    }
}
