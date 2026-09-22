package com.grupomariposa.orders.application.port.out;

import java.time.Instant;

public interface TimeProvider {

    Instant now();
}
