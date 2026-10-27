package dev.renanrodrigues.orders.partner;

import dev.renanrodrigues.orders.error.ResourceNotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerService {

    private static final Logger log = LoggerFactory.getLogger(PartnerService.class);

    private final PartnerRepository partnerRepository;
    private final Clock clock;

    public PartnerService(PartnerRepository partnerRepository, Clock clock) {
        this.partnerRepository = partnerRepository;
        this.clock = clock;
    }

    @Transactional
    public PartnerResponse create(CreatePartnerRequest request) {
        Partner partner = partnerRepository.save(Partner.create(request.name(), request.creditLimit(), clock.instant()));
        log.info("Parceiro [{}] cadastrado com limite de crédito de {}", partner.getId(), partner.getCreditLimit());
        return PartnerResponse.from(partner);
    }

    @Transactional(readOnly = true)
    public PartnerResponse findById(UUID partnerId) {
        return partnerRepository.findById(partnerId)
                .map(PartnerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Parceiro", partnerId));
    }
}
