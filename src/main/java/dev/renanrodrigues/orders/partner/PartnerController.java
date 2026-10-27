package dev.renanrodrigues.orders.partner;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Parceiros", description = "Cadastro de parceiros e consulta do crédito disponível")
@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {

    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @Operation(summary = "Cadastra um parceiro com limite de crédito")
    @PostMapping
    public ResponseEntity<PartnerResponse> create(@Valid @RequestBody CreatePartnerRequest request) {
        PartnerResponse partner = partnerService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/partners/" + partner.id())).body(partner);
    }

    @Operation(summary = "Consulta um parceiro e o crédito disponível")
    @GetMapping("/{partnerId}")
    public PartnerResponse findById(@PathVariable UUID partnerId) {
        return partnerService.findById(partnerId);
    }
}
