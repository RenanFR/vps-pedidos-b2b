# Pedidos B2B

Microserviço de gestão de pedidos de parceiros comerciais: cadastro, consulta, ciclo de status, limite de crédito por parceiro e notificação de cada mudança por mensageria.

Java 21, Spring Boot 3.4, PostgreSQL 16, RabbitMQ 3.13, Flyway, springdoc-openapi.

## Como executar

Pré-requisito: Docker com Compose.

```bash
docker compose up --build
```

Sobem três contêineres: PostgreSQL, RabbitMQ e a aplicação. A aplicação só inicia depois que banco e broker passam no healthcheck, e no perfil `docker` ela já cadastra dois parceiros de exemplo.

| O quê | Endereço |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Métricas Prometheus | http://localhost:8080/actuator/prometheus |
| Painel do RabbitMQ | http://localhost:15672 (guest / guest) |

Parceiros de exemplo:

| ID | Nome | Limite |
|---|---|---|
| `11111111-1111-1111-1111-111111111111` | Distribuidora Aurora | 50.000,00 |
| `22222222-2222-2222-2222-222222222222` | Mercado Boa Praça | 1.000,00 |

### Rodar fora do Docker

Com PostgreSQL e RabbitMQ acessíveis em `localhost` (dá para subir só os dois com `docker compose up postgres rabbitmq`):

```bash
mvn spring-boot:run
```

Variáveis aceitas: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` e `NOTIFICATIONS_BROKER` (`rabbitmq` ou `log`, que só escreve a notificação no log).

## Testes

```bash
mvn test
```

Os testes de integração rodam contra um **PostgreSQL de verdade embutido** (binário oficial iniciado pelo Zonky), sem Docker e sem H2. Isso importa aqui: o comportamento de `SELECT ... FOR UPDATE`, `SKIP LOCKED`, `UPDATE` condicional sob concorrência e violação de chave única é exatamente o que o projeto precisa provar, e um banco em memória de outro fabricante não garante a mesma semântica.

| Suíte | O que cobre |
|---|---|
| `OrderStatusTest` | Todas as 36 combinações de transição de status |
| `PurchaseOrderTest` | Cálculo do total e recusa de transição no próprio domínio |
| `OrderApiIntegrationTest` | Fluxos HTTP: criação, validação, 404, idempotência, débito e estorno de crédito, fluxo completo de status, filtros por parceiro, status e período, documento OpenAPI |
| `OrderConcurrencyIntegrationTest` | 20 aprovações simultâneas disputando um crédito que cobre só 10; 20 aprovações simultâneas do mesmo pedido; 20 tentativas simultâneas com a mesma chave de idempotência |
| `OutboxRelayIntegrationTest` | Ordem e conteúdo das notificações, ausência de evento quando a operação falha, retentativa com broker fora do ar |

## API

Base: `/api/v1`. Erros seguem o RFC 9457 (`application/problem+json`).

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/partners` | Cadastra parceiro com limite de crédito |
| `GET` | `/partners/{id}` | Consulta parceiro e crédito disponível |
| `POST` | `/orders` | Cadastra pedido (aceita `Idempotency-Key`) |
| `GET` | `/orders/{id}` | Consulta pedido por ID |
| `GET` | `/orders?partnerId=&status=&from=&to=&page=&size=&sort=` | Lista com filtros opcionais e paginação |
| `PATCH` | `/orders/{id}/status` | Muda o status |
| `POST` | `/orders/{id}/cancel` | Cancela o pedido |

### Exemplo de uso

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pedido-0001' \
  -d '{
        "partnerId": "22222222-2222-2222-2222-222222222222",
        "items": [
          {"productId": "SKU-CAFE-500G", "quantity": 10, "unitPrice": 24.90},
          {"productId": "SKU-ACUCAR-1KG", "quantity": 5, "unitPrice": 5.49}
        ]
      }'

curl -s -X PATCH localhost:8080/api/v1/orders/{id}/status \
  -H 'Content-Type: application/json' \
  -d '{"status": "APROVADO", "reason": "Pagamento confirmado"}'

curl -s localhost:8080/api/v1/partners/22222222-2222-2222-2222-222222222222

curl -s 'localhost:8080/api/v1/orders?status=APROVADO&from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z'
```

Cada mudança aparece no log da aplicação pelo consumidor de demonstração e na fila `orders.notifications` do painel do RabbitMQ.

### Respostas de erro

| Status | Quando |
|---|---|
| 400 | Campo inválido (com a lista em `errors`), filtro de período invertido, status desconhecido |
| 404 | Pedido ou parceiro inexistente |
| 409 | Transição fora do fluxo, chave de idempotência reutilizada com outro conteúdo, conflito de concorrência |
| 422 | Crédito insuficiente (com `requiredAmount` e `availableCredit`) |

## Fluxo de status

```
PENDENTE ──> APROVADO ──> EM_PROCESSAMENTO ──> ENVIADO ──> ENTREGUE
   │            │                │
   └────────────┴────────────────┴──> CANCELADO
```

A regra mora no enum `OrderStatus`, e a entidade recusa qualquer transição fora dela. Pedido enviado ou entregue não cancela: a partir desse ponto o caminho seria devolução, que é outro processo.

## Decisões técnicas

### Crédito

O enunciado pede duas coisas diferentes: verificar o crédito na criação e debitar na aprovação. Segui as duas ao pé da letra.

- **Na criação** o total é comparado com o crédito disponível. É uma checagem rápida que barra o pedido claramente impossível, sem travar nada.
- **Na aprovação** o débito é um único `UPDATE` condicional:

  ```sql
  update partner set available_credit = available_credit - :valor
   where id = :parceiro and available_credit >= :valor
  ```

  O PostgreSQL trava a linha do parceiro, reavalia a condição depois de conseguir o lock e devolve 0 linhas afetadas quando o saldo não cobre. Não há janela entre ler o saldo e gravar, então duas aprovações simultâneas nunca deixam o saldo negativo. Uma `CHECK` no banco (`available_credit >= 0 and available_credit <= credit_limit`) fica como última barreira.
- **No cancelamento** de um pedido que já tinha debitado (aprovado ou em processamento) o valor volta pelo mesmo tipo de `UPDATE`.

Consequência aceita: dois pedidos pendentes podem passar na checagem da criação e só um conseguir ser aprovado. O segundo recebe 422 na aprovação e continua pendente. A alternativa seria reservar o crédito já na criação; ver "Próximos passos".

O teste `concurrentApprovalsNeverSpendMoreThanTheCreditLimit` dispara 20 aprovações de 100,00 ao mesmo tempo contra um limite de 1.000,00 e confere que exatamente 10 passam, 10 recebem crédito insuficiente e o saldo termina em zero.

### Concorrência na mudança de status

A mudança de status lê o pedido com `SELECT ... FOR UPDATE` (lock pessimista, com timeout de 5 s). Duas requisições aprovando o mesmo pedido ficam em fila: a primeira aprova, a segunda encontra o pedido já `APROVADO` e recebe 409. O crédito é debitado uma vez só. A ordem dos locks é sempre pedido e depois parceiro, o que evita deadlock entre aprovação e cancelamento.

Escolhi lock pessimista em vez de só `@Version` porque, nesse ponto, o conflito é esperado (parceiro reenviando, operador clicando duas vezes) e a fila dá uma resposta determinística. O `@Version` continua na entidade como proteção para qualquer outro caminho de escrita.

### Idempotência na criação

O cabeçalho `Idempotency-Key` é opcional. Com ele:

- A chave fica gravada no pedido com uma restrição única por `(partner_id, idempotency_key)`, junto com o SHA-256 do conteúdo da requisição.
- Repetir a mesma requisição devolve **200** com o pedido original em vez de criar outro.
- Repetir a chave com outro conteúdo devolve **409**.
- Se duas tentativas chegam ao mesmo tempo, o banco aceita uma e recusa a outra pela restrição única. A que perdeu busca e devolve o pedido da vencedora. O teste com 20 tentativas simultâneas confirma que nasce um pedido só.

Em B2B isso é o que importa de verdade: o integrador do parceiro reenvia quando a rede cai, e sem idempotência cada reenvio vira pedido duplicado consumindo crédito.

### Notificações: outbox transacional

Publicar no RabbitMQ dentro da transação do pedido tem dois modos de falha: o broker recebe a mensagem e o commit falha (notificação de algo que não aconteceu), ou o commit passa e o broker está fora (mudança sem notificação).

Por isso cada mudança grava um registro em `outbox_event` **na mesma transação** do pedido. Um relay agendado (a cada 500 ms) publica os pendentes:

- `SELECT ... FOR UPDATE SKIP LOCKED` em lotes de 100, então várias réplicas da aplicação dividem o trabalho sem publicar o mesmo evento duas vezes ao mesmo tempo.
- Publicação com *publisher confirms*: o evento só é marcado como publicado depois que o broker confirma o recebimento.
- Falha incrementa `attempts` e guarda o erro; o evento volta no ciclo seguinte. Depois de 10 tentativas ele para de ser tentado e gera log de erro.
- Exchange `orders.events` do tipo *topic*, com rotas `order.created` e `order.status.<status>`, para que cada sistema consumidor assine só o que interessa. Mensagens persistentes, com o ID do evento em `messageId`.

A entrega é **pelo menos uma vez**. Se a aplicação cair entre a confirmação do broker e o commit do relay, o evento sai de novo; o consumidor deduplica pelo `eventId`.

O payload leva `eventId`, `eventType`, `orderId`, `partnerId`, `previousStatus`, `currentStatus`, `totalAmount`, `reason`, `correlationId` e `occurredAt`.

### Desempenho e escala

- **Stateless**: nada fica em memória entre requisições, então a aplicação escala horizontalmente atrás de um balanceador. As garantias de crédito e idempotência estão no banco, não em lock de JVM.
- **Virtual threads** (Java 21) no Tomcat: requisições presas em I/O não ocupam thread de plataforma. O limite real passa a ser o pool de conexões (`DB_POOL_SIZE`, 20 por padrão).
- **Transações curtas**: a criação não trava o parceiro, e a aprovação trava duas linhas por milissegundos.
- **Índices** em `(status, created_at)`, `(partner_id, created_at)` e `created_at` para as consultas por status e período; índice parcial só sobre os eventos pendentes do outbox.
- **Paginação** obrigatória na listagem, máximo de 100 por página, ordenação padrão por data de criação decrescente.
- **Sem N+1**: os itens são carregados em lote (`default_batch_fetch_size`) e o *open session in view* está desligado, então nenhuma consulta escapa da camada de serviço.
- Valores em `numeric(19,2)` e `BigDecimal`, nunca ponto flutuante.

### Observabilidade

- Cada requisição recebe um `X-Correlation-Id` (o enviado pelo cliente ou um novo), devolvido na resposta, gravado no MDC dos logs e propagado no payload da notificação.
- No perfil `docker` os logs saem em JSON no formato ECS, prontos para Elastic, Loki ou CloudWatch.
- Métricas Micrometer em `/actuator/prometheus`, além das do Spring: `orders_created_total`, `orders_status_changed_total{status}`, `orders_rejected_total{reason}`, `outbox_events_published_total` e `outbox_events_failed_total`.
- Probes de liveness e readiness em `/actuator/health/liveness` e `/actuator/health/readiness`.

### Esquema e migrações

Flyway em `db/migration`. Os dados de exemplo ficam em `db/seed` e só entram no perfil `docker`, para não irem parar num banco de produção. O Hibernate apenas valida o esquema (`ddl-auto: validate`).

## Estrutura

```
src/main/java/dev/renanrodrigues/orders
├── config          relógio, OpenAPI, filtro de correlation id
├── error           exceções de negócio e tradução para problem+json
├── notification    outbox, relay, publicação no RabbitMQ, consumidor de demonstração
├── order           entidade, fluxo de status, serviço, controller, DTOs
└── partner         entidade, débito e estorno atômicos, serviço, controller
```

## Próximos passos

O que eu faria antes de colocar em produção, fora do escopo do desafio:

- **Reserva de crédito na criação**: separar `reservado` de `consumido`, para que o pedido aceito tenha garantia de aprovação. Exige expirar reservas de pedidos que ficam pendentes para sempre.
- **Autenticação por parceiro** (OAuth2 client credentials ou mTLS), com o parceiro tirado do token em vez do corpo da requisição.
- **Rate limiting por parceiro** no gateway.
- **Backoff exponencial** no outbox e uma fila de dead-letter para os eventos que esgotam as tentativas, com reprocessamento manual.
- **Limpeza do outbox** (apagar ou arquivar publicados com mais de N dias) e particionamento de `purchase_order` por data quando o volume pedir.
- Testes de carga (Gatling ou k6) para dimensionar pool de conexões e réplicas.
