package dev.renanrodrigues.orders.order;

public enum OrderStatus {
    PENDENTE,
    APROVADO,
    EM_PROCESSAMENTO,
    ENVIADO,
    ENTREGUE,
    CANCELADO;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDENTE -> target == APROVADO || target == CANCELADO;
            case APROVADO -> target == EM_PROCESSAMENTO || target == CANCELADO;
            case EM_PROCESSAMENTO -> target == ENVIADO || target == CANCELADO;
            case ENVIADO -> target == ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }

    public boolean holdsPartnerCredit() {
        return this == APROVADO || this == EM_PROCESSAMENTO || this == ENVIADO || this == ENTREGUE;
    }
}
