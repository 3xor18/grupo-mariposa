package com.grupomariposa.orders.infrastructure.persistence;

import static com.grupomariposa.orders.domain.DomainFixtures.goldenInput;

import com.grupomariposa.orders.application.ApplicationFixtures;
import com.grupomariposa.orders.application.command.OrderCommand;
import com.grupomariposa.orders.application.service.OrderAssembler;
import com.grupomariposa.orders.domain.DomainFixtures;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.Decision;
import com.grupomariposa.orders.domain.model.EvaluationInput;
import com.grupomariposa.orders.domain.model.FailureDetails;
import com.grupomariposa.orders.domain.model.Lookup;
import com.grupomariposa.orders.domain.model.Order;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.crypto.PiiKeys;
import java.time.Instant;
import java.util.Base64;

public final class PersistenceFixtures {

    public static final Instant PROCESSED_AT = Instant.parse("2026-09-18T15:42:12Z");
    private static final OrderAssembler ASSEMBLER = new OrderAssembler(() -> PROCESSED_AT,
            DomainFixtures.CURRENCIES);

    private PersistenceFixtures() {
    }

    public static AesGcmPiiCipher cipher() {
        return new AesGcmPiiCipher(PiiKeys.single("k1",
                Base64.getEncoder().encodeToString(new byte[32])));
    }

    public static Order approvedOrder() {
        final EvaluationInput input = goldenInput();
        final Decision decision = DomainFixtures.evaluator().evaluate(input);
        return ASSEMBLER.decided(ApplicationFixtures.goldenCommand(), input, decision);
    }

    public static Order rejectedOrder() {
        final EvaluationInput golden = goldenInput();
        final EvaluationInput input = new EvaluationInput(Markets.MX,
                DomainFixtures.digits(Markets.MX), Lookup.notFound(),
                golden.items(), DomainFixtures.APPLIED_RATES);
        final Decision decision = DomainFixtures.evaluator().evaluate(input);
        return ASSEMBLER.decided(ApplicationFixtures.goldenCommand(), input, decision);
    }

    public static Order technicalFailure() {
        final OrderCommand command = ApplicationFixtures.goldenCommand();
        return ASSEMBLER.technicalFailure(command,
                new FailureDetails("EXTERNAL_TRANSIENT", "products-api responded 503", 4));
    }
}
