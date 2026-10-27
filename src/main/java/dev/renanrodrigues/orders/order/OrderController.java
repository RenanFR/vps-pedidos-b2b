package dev.renanrodrigues.orders.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pedidos", description = "Cadastro, consulta e ciclo de vida dos pedidos")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Cadastra um pedido",
            description = "Confere se o parceiro tem crédito para o valor total. Com o cabeçalho Idempotency-Key, "
                    + "repetir a mesma requisição devolve o pedido já criado em vez de criar outro.")
    @ApiResponse(responseCode = "201", description = "Pedido criado")
    @ApiResponse(responseCode = "200", description = "Pedido já criado antes com a mesma chave de idempotência")
    @ApiResponse(responseCode = "409", description = "Chave de idempotência usada com outro conteúdo")
    @ApiResponse(responseCode = "422", description = "Crédito insuficiente")
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Parameter(description = "Chave escolhida pelo parceiro para tornar a criação idempotente")
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderCreation creation = orderService.create(request, idempotencyKey);
        if (!creation.created()) {
            return ResponseEntity.ok(creation.order());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + creation.order().id())).body(creation.order());
    }

    @Operation(summary = "Consulta um pedido pelo ID")
    @GetMapping("/{orderId}")
    public OrderResponse findById(@PathVariable UUID orderId) {
        return orderService.findById(orderId);
    }

    @Operation(summary = "Lista pedidos por parceiro, status e período de criação",
            description = "Todos os filtros são opcionais. O período inclui o início e exclui o fim.")
    @GetMapping
    public PageResponse<OrderResponse> search(
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(required = false) OrderStatus status,
            @Parameter(example = "2026-10-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(example = "2026-11-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.search(new OrderSearchFilter(partnerId, status, from, to), pageable);
    }

    @Operation(summary = "Atualiza o status de um pedido",
            description = "Aprovar debita o valor do crédito do parceiro. Cancelar um pedido aprovado devolve o "
                    + "crédito. Transições fora do fluxo respondem 409.")
    @ApiResponse(responseCode = "409", description = "Transição de status não permitida")
    @ApiResponse(responseCode = "422", description = "Crédito insuficiente na aprovação")
    @PatchMapping("/{orderId}/status")
    public OrderResponse changeStatus(@PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.changeStatus(orderId, request.status(), request.reason());
    }

    @Operation(summary = "Cancela um pedido",
            description = "Permitido até EM_PROCESSAMENTO. Se o pedido já tinha debitado crédito, o valor volta.")
    @ApiResponse(responseCode = "409", description = "Pedido já enviado, entregue ou cancelado")
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(orderId, Optional.ofNullable(request).map(CancelOrderRequest::reason).orElse(null));
    }
}
