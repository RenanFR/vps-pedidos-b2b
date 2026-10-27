create table partner (
    id               uuid primary key,
    name             varchar(150)             not null,
    credit_limit     numeric(19, 2)           not null,
    available_credit numeric(19, 2)           not null,
    created_at       timestamp with time zone not null,
    updated_at       timestamp with time zone not null,
    constraint ck_partner_credit_limit check (credit_limit >= 0),
    constraint ck_partner_available_credit check (available_credit >= 0 and available_credit <= credit_limit)
);

create table purchase_order (
    id              uuid primary key,
    partner_id      uuid                     not null references partner (id),
    total_amount    numeric(19, 2)           not null,
    status          varchar(20)              not null,
    idempotency_key varchar(100),
    request_hash    varchar(64),
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    version         bigint                   not null,
    constraint uk_purchase_order_idempotency unique (partner_id, idempotency_key),
    constraint ck_purchase_order_total check (total_amount > 0)
);

create index idx_purchase_order_status_created on purchase_order (status, created_at);
create index idx_purchase_order_partner_created on purchase_order (partner_id, created_at);
create index idx_purchase_order_created on purchase_order (created_at);

create table order_item (
    order_id    uuid           not null references purchase_order (id) on delete cascade,
    line_number integer        not null,
    product_id  varchar(100)   not null,
    quantity    integer        not null,
    unit_price  numeric(19, 2) not null,
    primary key (order_id, line_number),
    constraint ck_order_item_quantity check (quantity > 0),
    constraint ck_order_item_unit_price check (unit_price > 0)
);

create table outbox_event (
    id           uuid primary key,
    aggregate_id uuid                     not null,
    event_type   varchar(50)              not null,
    routing_key  varchar(100)             not null,
    payload      text                     not null,
    created_at   timestamp with time zone not null,
    published_at timestamp with time zone,
    attempts     integer                  not null default 0,
    last_error   varchar(500)
);

create index idx_outbox_event_pending on outbox_event (created_at) where published_at is null;
