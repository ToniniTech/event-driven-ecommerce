## Deployment (AWS EC2)

The system was deployed and verified on AWS — the whole stack running on a single EC2 instance orchestrated with Docker Compose.

| Item | Value |
|------|-------|
| Cloud | AWS EC2 |
| Instance type | `c7i-flex.large` |
| OS | Ubuntu 24.04 LTS |
| Region | South America (São Paulo) — `sa-east-1` |
| Orchestration | Docker + Docker Compose (12 containers) |
| Public address | Elastic IP (static across instance restarts) |
| Access control | Security group — SSH restricted to a single IP, service ports exposed individually |
| Secrets | `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` injected at runtime via a `.env` file on the server — never committed to the repository |

### What runs on the instance

All 12 containers healthy: 5 Spring Boot microservices, 5 MySQL databases (one per service), RabbitMQ, and Zipkin.

![Docker Compose status on the EC2 instance](./docs/images/docker-compose-ps.png)

### Verified end to end on the deployed instance

The complete purchase flow was executed against the public IP — user registration, order creation, payment processing through RabbitMQ, and the resulting notifications.

![Postman request against the deployed instance](./docs/images/postman-register.png)

Distributed tracing confirmed across services: a single `traceId` propagating from `order-service` through to `product-service` over HTTP.

![Zipkin distributed trace](./docs/images/zipkin-trace.png)

### Notes on this deployment

- **Single-instance by design.** Running everything on one EC2 box with Docker Compose keeps costs predictable and reuses the exact same `docker-compose.yml` that runs locally. A production setup would split the databases out to RDS, run the services on ECS or EKS, and put them behind a load balancer.
- **The instance is not kept running permanently** to avoid unnecessary cost. The demo video and the screenshots above document a live run; the instance can be brought back up on request.

---