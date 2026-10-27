package dev.renanrodrigues.orders.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.renanrodrigues.orders.error.InsufficientCreditException;
import dev.renanrodrigues.orders.error.InvalidStatusTransitionException;
import dev.renanrodrigues.orders.partner.Partner;
import dev.renanrodrigues.orders.partner.PartnerRepository;
import dev.renanrodrigues.orders.support.IntegrationTest;
import dev.renanrodrigues.orders.support.TestData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class OrderConcurrencyIntegrationTest {

    private static final int THREADS = 20;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PartnerRepository partnerRepository;

    @Test
    void concurrentApprovalsNeverSpendMoreThanTheCreditLimit() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        List<UUID> orderIds = new ArrayList<>();
        for (int orderIndex = 0; orderIndex < THREADS; orderIndex++) {
            orderIds.add(orderService.create(TestData.orderOf(partner.getId(), 1, "100.00"), null).order().id());
        }

        Map<String, Long> outcomes = runConcurrently(orderIds.stream()
                .map(orderId -> (Runnable) () -> orderService.changeStatus(orderId, OrderStatus.APROVADO, null))
                .toList());

        assertThat(outcomes).containsEntry("ok", 10L).containsEntry(InsufficientCreditException.class.getSimpleName(),
                10L);
        assertThat(partnerRepository.findAvailableCredit(partner.getId())).isEqualByComparingTo("0.00");
        assertThat(orderService.search(new OrderSearchFilter(partner.getId(), OrderStatus.APROVADO, null, null),
                PageRequest.of(0, 50)).totalElements()).isEqualTo(10);
    }

    @Test
    void approvingTheSameOrderConcurrentlyDebitsOnlyOnce() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        UUID orderId = orderService.create(TestData.orderOf(partner.getId(), 1, "100.00"), null).order().id();

        Map<String, Long> outcomes = runConcurrently(Collections.nCopies(THREADS,
                () -> orderService.changeStatus(orderId, OrderStatus.APROVADO, null)));

        assertThat(outcomes).containsEntry("ok", 1L)
                .containsEntry(InvalidStatusTransitionException.class.getSimpleName(), (long) THREADS - 1);
        assertThat(partnerRepository.findAvailableCredit(partner.getId())).isEqualByComparingTo("900.00");
    }

    @Test
    void concurrentRetriesWithTheSameIdempotencyKeyCreateOneOrder() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        CreateOrderRequest request = TestData.orderOf(partner.getId(), 1, "10.00");
        List<UUID> returnedIds = Collections.synchronizedList(new ArrayList<>());

        Map<String, Long> outcomes = runConcurrently(Collections.nCopies(THREADS,
                () -> returnedIds.add(orderService.create(request, "retry-key").order().id())));

        assertThat(outcomes).containsEntry("ok", (long) THREADS);
        assertThat(returnedIds).hasSize(THREADS).containsOnly(returnedIds.get(0));
        assertThat(orderService.search(new OrderSearchFilter(partner.getId(), null, null, null),
                PageRequest.of(0, 50)).totalElements()).isEqualTo(1);
    }

    private Map<String, Long> runConcurrently(List<Runnable> tasks) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<String>> results = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        startSignal.await();
                        try {
                            task.run();
                            return "ok";
                        } catch (RuntimeException failure) {
                            return failure.getClass().getSimpleName();
                        }
                    }))
                    .toList();
            startSignal.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }
    }
}
