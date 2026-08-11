
## Architecture

```
CLIENT (browser / Postman)
    │
    ▼
┌─────────────────┐              ┌──────────────────────┐        ┌─────────────────────┐
│  AUTH SERVICE   │              │   ORDER SERVICE      │──REST─►│  PRODUCT SERVICE    │
│     :8084       │◄────JWT─────►│       :8081          │ price  │       :8085         │
│   DB: auth_db   │  shared      │   DB: order_db       │  +stock│   DB: product_db    │
│                 │  secret      │                      │◄───────│                     │
│ /register       │              │ - extracts customerId│        │ 300-product catalog │
│ /login          │              │   from the JWT       │        │ (CRUD + optimistic  │
│ /refresh        │              │ - resolves price     │        │  locking @Version)  │
│ /logout         │              │   from the catalog   │        └─────────────────────┘
│ /admin/**       │              │ - Outbox Pattern     │
└─────────────────┘              └──────────────────────┘
                                          │
                                          │ publishes OrderCreated (via Outbox)
                                          ▼
                                 ┌─────────────────────┐
                                 │   RABBITMQ :5672    │
                                 │  orders.exchange    │
                                 │  payments.exchange  │
                                 │  dlx.exchange (DLQ) │
                                 └─────────────────────┘
                                    │              │
                          ┌─────────┘              └──────────┐
                          ▼                                   ▼
               ┌──────────────────┐              ┌───────────────────────┐
               │ PAYMENT SERVICE  │              │ NOTIFICATION SERVICE  │
               │     :8082        │              │       :8083           │
               │ DB: payment_db   │              │ DB: notification_db   │
               │                  │              │                       │
               │ - gateway 80%    │              │ - order confirmation  │
               │   simulated      │              │ - payment success     │
               │   success        │              │ - payment failure     │
               └──────────────────┘              └───────────────────────┘
                          │                      
                          │ publishes PaymentProcessed / PaymentFailed
                          ▼
               ┌──────────────────┐
               │  ORDER SERVICE   │
               │ (updates status  │
               │  PAID / FAILED)  │
               └──────────────────┘

  All services export traces to ZIPKIN :9411 (traceId propagated over HTTP and via AMQP headers)
```

---


## Event flow

| Event | Published by | Consumed by |
|--------|--------------|---------------|
| `OrderCreated` | Order Service (via Outbox) | Payment Service, Notification Service |
| `PaymentProcessed` | Payment Service | Order Service, Notification Service |
| `PaymentFailed` | Payment Service | Order Service, Notification Service |

---

## Design decisions

**Why RabbitMQ and not REST between services?**

Synchronous HTTP communication creates temporal coupling — if Payment Service goes down, Order Service fails too. With RabbitMQ, Order Service publishes the event and continues independently. Messages wait in the queue until Payment Service comes back.

**Why is Product Service synchronous (REST), then?**

Price and availability must be resolved **at the moment** the order is created, not eventually: a "provisional" order without a reliable price cannot exist. That's why Order Service queries Product Service over REST. The coupling is bounded with explicit timeouts (connect 2s / read 3s) so a slow Product Service never exhausts Order Service's thread pool.

**Why a database per service?**

It lets each service evolve, scale, and fail completely independently. The cost is eventual consistency, handled through events and the `processed_events` table for idempotency.

**Why the Outbox pattern?**

Saving the order to MySQL and publishing the event to RabbitMQ are two separate systems: if the second one fails, the event is lost (dual-write problem). With Outbox, the event is persisted in the same transaction as the order, and a poller publishes it afterward. Delivery is guaranteed even if the broker is down at that instant.

**Why stateless JWT and not sessions?**

In microservices, sessions create coupling — every service would need to share the same session store. With JWT, each service validates the token locally using the shared secret. No HTTP calls to Auth Service on every request.

---

## Project structure

```
ecommerce-events/
├── auth-service/               # JWT: register, login, refresh, admin
│   ├── domain/                 # User, RefreshToken, Role
│   ├── security/               # JwtService, JwtAuthenticationFilter
│   ├── service/                # AuthService, UserDetailsServiceImpl
│   └── controller/             # AuthController, AuthAdmController + DTOs
│
├── order-service/              # JWT-protected REST API
│   ├── domain/                 # Order, OrderItem, OutboxEvent, ProcessedEvent
│   ├── client/                 # ProductInventoryClient (RestClient to Product Service)
│   ├── messaging/              # OrderEventPublisher, OutboxProcessor, PaymentEventConsumer
│   ├── service/                # OrderService
│   └── security/               # JwtService, JwtAuthenticationFilter
│
├── product-service/            # Synchronous catalog (REST-only), no events
│   ├── domain/                 # Product (@Version), ProductRepository
│   ├── mapper/                 # ProductMapper (entity ↔ DTO)
│   ├── config/                 # CatalogCsvLoader (UPSERT load from CSV at startup)
│   ├── service/                # ProductService (CRUD, stock, soft delete)
│   └── controller/             # ProductController + DTOs
│
├── payment-service/            # Consumes OrderCreated, simulates gateway
│   ├── domain/                 # Payment, PaymentStatus, ProcessedEvent
│   ├── messaging/              # OrderEventConsumer, PaymentEventPublisher
│   └── service/                # PaymentService, PaymentGatewaySimulator
│
├── notification-service/       # Consumes all events, sends emails
│   ├── domain/                 # Notification, NotificationType, ProcessedEvent
│   ├── messaging/              # NotificationEventConsumer
│   └── service/                # NotificationService, EmailTemplateBuilder
│
├── infrastructure/
│   ├── rabbitmq/rabbitmq.conf
│   └── mysql/                  # Init scripts per database
│
├── docker-compose.yml          # Full orchestration (12 containers)
└── test-flow.sh                # Automated E2E script
```
