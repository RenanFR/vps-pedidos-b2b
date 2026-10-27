package dev.renanrodrigues.orders.order;

import dev.renanrodrigues.orders.error.IdempotencyConflictException;
import dev.renanrodrigues.orders.error.InsufficientCreditException;
import dev.renanrodrigues.orders.error.InvalidRequestException;
import dev.renanrodrigues.orders.error.InvalidStatusTransitionException;
import dev.renanrodrigues.orders.error.ResourceNotFoundException;
import dev.renanrodrigues.orders.notification.OutboxWriter;
import dev.renanrodrigues.orders.partner.Partner;
import dev.renanrodrigues.orders.partner.PartnerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository orderRepository;
    private final PartnerRepository partnerRepository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public OrderService(PurchaseOrderRepository orderRepository, PartnerRepository partnerRepository,
            OutboxWriter outboxWriter, TransactionTemplate transactionTemplate, Clock clock,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.partnerRepository = partnerRepository;
        this.outboxWriter = outboxWriter;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public OrderCreation create(CreateOrderRequest request, String idempotencyKey) {
        Optional<String> key = Optional.ofNullable(idempotencyKey).filter(candidate -> !candidate.isBlank());
        String requestHash = RequestFingerprint.of(request);
        Optional<OrderCreation> replay = key.flatMap(existingKey -> findReplay(request, existingKey, requestHash));
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return transactionTemplate.execute(status -> createInTransaction(request, key.orElse(null), requestHash));
        } catch (DataIntegrityViolationException violation) {
            return key.flatMap(existingKey -> findReplay(request, existingKey, requestHash))
                    .orElseThrow(() -> violation);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> search(OrderSearchFilter filter, Pageable pageable) {
        if (filter.createdFrom() != null && filter.createdTo() != null
                && !filter.createdFrom().isBefore(filter.createdTo())) {
            throw new InvalidRequestException("O início do período precisa ser anterior ao fim");
        }
        return PageResponse.from(orderRepository.findAll(OrderSpecifications.matching(filter), pageable)
                .map(OrderResponse::from));
    }

    @Transactional
    public OrderResponse changeStatus(UUID orderId, OrderStatus target, String reason) {
        PurchaseOrder order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId));
        OrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(orderId, previous, target);
        }
        Instant now = clock.instant();
        if (target == OrderStatus.APROVADO) {
            debitPartnerCredit(order, now);
        }
        if (target == OrderStatus.CANCELADO && previous.holdsPartnerCredit()) {
            partnerRepository.refund(order.getPartnerId(), order.getTotalAmount(), now);
            log.info("Crédito de {} devolvido ao parceiro [{}] pelo cancelamento do pedido [{}]",
                    order.getTotalAmount(), order.getPartnerId(), orderId);
        }
        order.changeStatus(target, now);
        outboxWriter.orderStatusChanged(order, previous, reason);
        meterRegistry.counter("orders.status.changed", "status", target.name()).increment();
        log.info("Pedido [{}] passou de {} para {}", orderId, previous, target);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID orderId, String reason) {
        return changeStatus(orderId, OrderStatus.CANCELADO, reason);
    }

    private OrderCreation createInTransaction(CreateOrderRequest request, String idempotencyKey,
            String requestHash) {
        Partner partner = partnerRepository.findById(request.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Parceiro", request.partnerId()));
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();
        PurchaseOrder order = PurchaseOrder.create(partner.getId(), items, idempotencyKey, requestHash,
                clock.instant());
        if (order.getTotalAmount().compareTo(partner.getAvailableCredit()) > 0) {
            meterRegistry.counter("orders.rejected", "reason", "insufficient_credit").increment();
            throw new InsufficientCreditException(partner.getId(), order.getTotalAmount(),
                    partner.getAvailableCredit());
        }
        orderRepository.saveAndFlush(order);
        outboxWriter.orderCreated(order);
        meterRegistry.counter("orders.created").increment();
        log.info("Pedido [{}] cadastrado para o parceiro [{}] no valor de {}", order.getId(), partner.getId(),
                order.getTotalAmount());
        return new OrderCreation(OrderResponse.from(order), true);
    }

    private Optional<OrderCreation> findReplay(CreateOrderRequest request, String idempotencyKey,
            String requestHash) {
        return orderRepository.findByPartnerIdAndIdempotencyKey(request.partnerId(), idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(idempotencyKey);
                    }
                    log.info("Pedido [{}] devolvido de novo pela chave de idempotência [{}]", existing.getId(),
                            idempotencyKey);
                    return new OrderCreation(OrderResponse.from(existing), false);
                });
    }

    private void debitPartnerCredit(PurchaseOrder order, Instant now) {
        int debited = partnerRepository.debitIfAvailable(order.getPartnerId(), order.getTotalAmount(), now);
        if (debited == 0) {
            meterRegistry.counter("orders.rejected", "reason", "insufficient_credit_on_approval").increment();
            throw new InsufficientCreditException(order.getPartnerId(), order.getTotalAmount(),
                    partnerRepository.findAvailableCredit(order.getPartnerId()));
        }
        log.info("Crédito de {} debitado do parceiro [{}] pela aprovação do pedido [{}]", order.getTotalAmount(),
                order.getPartnerId(), order.getId());
    }
}
