# Event-Driven E-Commerce

![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=flat&logo=springboot&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=flat&logo=jsonwebtokens&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=flat&logo=springsecurity&logoColor=white)
![Zipkin](https://img.shields.io/badge/Tracing-Zipkin-FF6C37?style=flat&logo=jaeger&logoColor=white)
[![CI](https://github.com/ToniniTech/Event-Driven-E-commerce/actions/workflows/maven.yml/badge.svg)](https://github.com/ToniniTech/Event-Driven-E-commerce/actions/workflows/maven.yml)
[![Kubernetes E2E](https://github.com/ToniniTech/Event-Driven-E-commerce/actions/workflows/kubernetes-e2e.yml/badge.svg)](https://github.com/ToniniTech/Event-Driven-E-commerce/actions/workflows/kubernetes-e2e.yml)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Kind_Local_Deployment-326CE5?style=flat&logo=kubernetes&logoColor=white)

E-commerce microservices system with an event-driven architecture, mostly asynchronous communication over RabbitMQ, a synchronous catalog isolated via REST, stateless JWT authentication, and distributed tracing with OpenTelemetry + Zipkin.

**[▶ Watch the demo (3 min)](https://www.youtube.com/watch?v=CXLOlrV1YR0)** — the full purchase flow running against a live AWS EC2 deployment, tested end to end with Postman.

---

## Why this project?

Most microservices tutorials use REST between services, which creates hidden coupling. This project demonstrates how to build services that communicate **without knowing about each other**, tolerating partial failures without affecting the whole system.

If Payment Service goes down, orders keep being created. When it comes back, it processes all pending events automatically. That's real resilience.

The only synchronous coupling is **deliberate**: Order Service queries Product Service over REST to resolve price and availability in real time, so the client never dictates the price. It's the exception that proves the rule, and it's isolated with explicit timeouts to prevent cascading failures.

---

## Tech stack

| Layer | Technology                                                   |
|------|--------------------------------------------------------------|
| Language | Java 17                                                      |
| Framework | Spring Boot 3.2                                              |
| Messaging | RabbitMQ 3.12 + Spring AMQP                                  |
| Synchronous communication | Spring `RestClient` (Order → Product) with explicit timeouts |
| Security | Spring Security + JWT (JJWT 0.12)                            |
| Persistence | Spring Data JPA + MySQL 8.0                                  |
| Observability | Micrometer Tracing + OpenTelemetry + Zipkin                  |
| Testing | JUnit 5, Testcontainers, Postman + Newman E2E |
| Build | Maven 3                                                      |
| Containerization | Docker                                                       |
| Orchestration | Kubernetes, Kind, Traefik Gateway API |
| CI | GitHub Actions: service builds + ephemeral Kubernetes E2E |
---

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

## Production patterns implemented

| Pattern | Purpose |
|---------|---------|
| **Transactional Outbox** | Prevents inconsistent database and message-broker writes |
| **Saga Choreography** | Coordinates the purchase flow without a central orchestrator |
| **Idempotent Consumers** | Prevents duplicated events from being processed twice |
| **DLQ + Retry** | Handles transient and permanent message-processing failures |
| **Database per Service** | Keeps services independently owned and deployed |
| **Optimistic Locking** | Protects product stock from concurrent updates |
| **Distributed Tracing** | Propagates one trace across HTTP and RabbitMQ |
| **JWT Security** | Provides stateless authentication and role-based access |

[Read all the design decisions](docs/architecture.md#design-decisions).

---

## Quick Start

**Requirements:** Docker Desktop installed.

```bash
# 1. Clone the repository
git clone https://github.com/ToniniTech/Event-Driven-E-commerce.git
cd Event-Driven-E-commerce

# 2. Bring up the whole infrastructure
docker-compose up --build -d

# 3. Check everything is healthy (~2 minutes)
docker-compose ps

# 4. Follow logs in real time
docker-compose logs -f order-service payment-service notification-service product-service

# 5. Explore the distributed traces
#    Zipkin UI → http://localhost:9411
```

---

## Test the flow with Postman (quick path)

The fastest way to see the whole system in action. The collection chains the entire flow automatically: it captures the JWT at login and the `orderId` from the created order, so you never copy tokens by hand.

**Requirement:** the system must be running (`docker-compose up --build -d`).

1. Import the two files from the [`/postman`](./postman) folder into Postman:
    - `ecommerce-events.postman_collection.json`
    - `ecommerce-events-local.postman_environment.json`
2. Select the **ecommerce-events-local** environment from the dropdown (top right). *Without this, the variables won't resolve.*
3. Open the **End-to-End Flow** folder and run it in order:

   | # | Request | What it does                                                 |
      |---|---------|--------------------------------------------------------------|
   | 1 | register user | Creates the user and **stores the JWT** automatically        |
   | 2 | create order | Creates the order and **stores the `orderId`** automatically |
   | 3 | get order by id | Order status (`PENDING` → `PAID`)                            |
   | 4 | get payment by id | Payment result                                               |
   | 5 | get notification by orderId | Notifications generated from events                          |

> **Note on the async flow:** steps 4 and 5 depend on events traveling through RabbitMQ after the order is created. If you query immediately and they aren't there yet, wait 1–2 seconds and retry — that small delay **is** the event-driven nature of the system, not a bug.

Prefer curl? follow the:
- [Complete local development guide](docs/local-development.md)
- [Test the complete purchase flow](docs/testing-guide.md)

---

## Kubernetes deployment

The complete platform runs locally on Kind behind Traefik using Kubernetes
Gateway API. It includes all five microservices, isolated MySQL databases,
RabbitMQ, persistent storage, health probes, resource limits and distributed
tracing.

Every pull request can be deployed to an ephemeral Kind cluster and validated
through Traefik using the complete Newman end-to-end workflow.

[View the Kubernetes E2E workflow](.github/workflows/kubernetes-e2e.yml).
---

## Deployment (AWS EC2)

The complete platform was deployed and verified on AWS EC2 using
Docker Compose.

[View AWS deployment evidence](docs/aws-deployment.md).

---

## Documentation

- [Architecture and design decisions](docs/architecture.md)
- [Local development](docs/local-development.md)
- [Testing and E2E flow](docs/testing-guide.md)
- [Authentication and administration](docs/authentication.md)
- [Product catalog](docs/product-catalog.md)
- [AWS deployment](docs/aws-deployment.md)
- [Service CI workflow](.github/workflows/maven.yml)
- [Kubernetes E2E workflow](.github/workflows/kubernetes-e2e.yml)
