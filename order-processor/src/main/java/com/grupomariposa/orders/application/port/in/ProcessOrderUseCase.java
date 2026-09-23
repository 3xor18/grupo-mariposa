package com.grupomariposa.orders.application.port.in;

import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.outcome.ProcessingOutcome;

public interface ProcessOrderUseCase {

    ProcessingOutcome process(OrderCommand command);
}
