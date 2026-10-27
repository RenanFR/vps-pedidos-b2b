package dev.renanrodrigues.orders.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.renanrodrigues.orders.partner.Partner;
import dev.renanrodrigues.orders.partner.PartnerRepository;
import dev.renanrodrigues.orders.support.IntegrationTest;
import dev.renanrodrigues.orders.support.TestData;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartnerRepository partnerRepository;

    @Test
    void createsOrderWithComputedTotalAndLocation() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");

        postOrder(TestData.orderOf(partner.getId(), 4, "24.90"), null)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.totalAmount").value(99.60))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void rejectsOrderAbovePartnerCredit() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "50.00");

        postOrder(TestData.orderOf(partner.getId(), 3, "20.00"), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Crédito insuficiente"))
                .andExpect(jsonPath("$.requiredAmount").value(60.00))
                .andExpect(jsonPath("$.availableCredit").value(50.00));
    }

    @Test
    void rejectsInvalidPayloadWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerId\":null,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void returnsNotFoundForUnknownOrderAndPartner() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID())).andExpect(status().isNotFound());
        postOrder(TestData.orderOf(UUID.randomUUID(), 1, "10.00"), null).andExpect(status().isNotFound());
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalOrder() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        CreateOrderRequest request = TestData.orderOf(partner.getId(), 2, "10.00");

        String firstId = idOf(postOrder(request, "pedido-123").andExpect(status().isCreated()));
        String replayId = idOf(postOrder(request, "pedido-123").andExpect(status().isOk()));

        assertThat(replayId).isEqualTo(firstId);
        postOrder(TestData.orderOf(partner.getId(), 3, "10.00"), "pedido-123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chave de idempotência reutilizada"));
    }

    @Test
    void approvalDebitsCreditAndCancellationRefundsIt() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "500.00");
        String orderId = idOf(postOrder(TestData.orderOf(partner.getId(), 10, "30.00"), null));

        changeStatus(orderId, "APROVADO").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADO"));
        assertThat(availableCreditOf(partner)).isEqualByComparingTo("200.00");

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        assertThat(availableCreditOf(partner)).isEqualByComparingTo("500.00");
    }

    @Test
    void cancellingPendingOrderDoesNotTouchCredit() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "500.00");
        String orderId = idOf(postOrder(TestData.orderOf(partner.getId(), 1, "100.00"), null));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isOk());

        assertThat(availableCreditOf(partner)).isEqualByComparingTo("500.00");
    }

    @Test
    void approvalFailsWhenCreditWasConsumedByAnotherOrder() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "100.00");
        String firstOrderId = idOf(postOrder(TestData.orderOf(partner.getId(), 1, "80.00"), null));
        String secondOrderId = idOf(postOrder(TestData.orderOf(partner.getId(), 1, "80.00"), null));

        changeStatus(firstOrderId, "APROVADO").andExpect(status().isOk());
        changeStatus(secondOrderId, "APROVADO")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.availableCredit").value(20.00));

        mockMvc.perform(get("/api/v1/orders/{orderId}", secondOrderId))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    void walksTheFullFlowAndRejectsSkippingSteps() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "500.00");
        String orderId = idOf(postOrder(TestData.orderOf(partner.getId(), 1, "100.00"), null));

        changeStatus(orderId, "ENVIADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("PENDENTE"));

        for (String nextStatus : new String[] {"APROVADO", "EM_PROCESSAMENTO", "ENVIADO", "ENTREGUE"}) {
            changeStatus(orderId, nextStatus).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", orderId)).andExpect(status().isConflict());
        assertThat(availableCreditOf(partner)).isEqualByComparingTo("400.00");
    }

    @Test
    void searchesByPartnerStatusAndPeriod() throws Exception {
        Partner partner = TestData.partnerWithCredit(partnerRepository, "1000.00");
        String approvedId = idOf(postOrder(TestData.orderOf(partner.getId(), 1, "10.00"), null));
        idOf(postOrder(TestData.orderOf(partner.getId(), 1, "20.00"), null));
        changeStatus(approvedId, "APROVADO").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders").param("partnerId", partner.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/orders").param("partnerId", partner.getId().toString())
                        .param("status", "APROVADO"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(approvedId));

        Instant now = Instant.now();
        mockMvc.perform(get("/api/v1/orders").param("partnerId", partner.getId().toString())
                        .param("from", now.minus(1, ChronoUnit.HOURS).toString())
                        .param("to", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/orders").param("partnerId", partner.getId().toString())
                        .param("from", now.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsInvertedPeriodAndUnknownStatus() throws Exception {
        Instant now = Instant.now();
        mockMvc.perform(get("/api/v1/orders").param("from", now.toString())
                        .param("to", now.minus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/orders").param("status", "PERDIDO")).andExpect(status().isBadRequest());
    }

    @Test
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/orders']").exists());
    }

    private ResultActions postOrder(CreateOrderRequest request, String idempotencyKey) throws Exception {
        var builder = post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    private ResultActions changeStatus(String orderId, String targetStatus) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{orderId}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + targetStatus + "\"}"));
    }

    private String idOf(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private BigDecimal availableCreditOf(Partner partner) {
        return partnerRepository.findAvailableCredit(partner.getId());
    }
}
