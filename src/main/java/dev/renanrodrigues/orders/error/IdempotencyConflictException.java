package dev.renanrodrigues.orders.error;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("A chave de idempotência " + idempotencyKey + " já foi usada com um pedido diferente");
    }
}
