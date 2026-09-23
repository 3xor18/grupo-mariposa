package com.grupomariposa.orders.application.port.in;

import com.grupomariposa.orders.application.command.TaxRateProposalCommand;
import com.grupomariposa.orders.application.query.TaxRateFilter;
import com.grupomariposa.orders.domain.model.TaxRateProposal;
import java.util.List;

public interface TaxRateAdministration {

    List<TaxRateProposal> list(TaxRateFilter filter);

    TaxRateProposal propose(TaxRateProposalCommand command);

    TaxRateProposal approve(String id, String approver);

    TaxRateProposal reject(String id, String reviewer);
}
