package com.grupomariposa.orders.application.port.in;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;
import com.grupomariposa.orders.domain.model.FailureDetails;

public interface RecordTechnicalFailureUseCase {

    ProcessingOutcome.TechnicalFailure record(OrderCommand command, FailureDetails failure);
}
