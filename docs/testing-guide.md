## Test the flow with Postman (quick path)

The fastest way to see the whole system in action. The collection chains the entire flow automatically: it captures the JWT at login and the `orderId` from the created order, so you never copy tokens by hand.

**Requirement:** the system must be running (`docker-compose up --build -d`).

1. Import the two files from the [`/postman`](./postman) folder into Postman:
    - `ecommerce-events.postman_collection.json`
    - `ecommerce-events-local.postman_environment.json`
2. Select the **ecommerce-events-local** environment from the dropdown (top right). *Without this, the variables won't resolve.*
3. Open the **End-to-End Flow** folder and run it in order:

   | # | Request | What it does |
         |---|---------|--------------|
   | 1 | register user | Creates the user and **stores the JWT** automatically |
   | 2 | create order | Creates the order and **stores the `orderId`** automatically |
   | 3 | get order by id | Order status (`PENDING` → `PAID`) |
   | 4 | get payment by id | Payment result |
   | 5 | get notification by orderId | Notifications generated from events |

> **Note on the async flow:** steps 4 and 5 depend on events traveling through RabbitMQ after the order is created. If you query immediately and they aren't there yet, wait 1–2 seconds and retry — that small delay **is** the event-driven nature of the system, not a bug.

Prefer curl? The same flow, step by step, is below.

---

## How to test the full flow (curl)

### Step 1 — Register a user

```bash
curl -X POST http://localhost:8084/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Juan",
    "lastName": "Pérez",
    "email": "juan@example.com",
    "password": "12345678"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-...",
  "customerId": "cust-a1b2c3d4",
  "email": "juan@example.com",
  "role": "CUSTOMER"
}
```

### Step 2 — Create an order

The `customerId` and `customerEmail` are extracted from the JWT automatically. The client only sends `productId` + `quantity`; the price and name are resolved from Product Service.

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "items": [
      { "productId": "P0002", "quantity": 1 },
      { "productId": "P0003", "quantity": 2 }
    ]
  }'
```

> **Idempotency (optional):** to protect against a double-click on the frontend, send an `Idempotency-Key` header. If you repeat the same key, the second request is rejected with `409 Conflict`.
>
> ```bash
>   -H "Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000"
> ```

### Step 3 — Check the order status

```bash
curl http://localhost:8081/api/orders/{orderId} \
  -H "Authorization: Bearer <accessToken>"
```

Async status: `PENDING` → `PAYMENT_PROCESSING` → `PAID` or `PAYMENT_FAILED`

### Step 4 — Check the payment

```bash
curl http://localhost:8082/api/payments/order/{orderId}
```

### Step 5 — See sent notifications

```bash
curl http://localhost:8083/api/notifications/order/{orderId}
```

### Force a failed payment (deterministic)

The simulated gateway rejects the payment when the **total amount** matches one of these business rules:

- **Amount > $1000** → `AMOUNT_EXCEEDS_LIMIT` (you'd need a large quantity, since no product exceeds ~$7).
- **Amount ending in `.13`** → `CARD_EXPIRED`.

Product `P0001` (*Cebolla 1kg*, $1.13) with `quantity: 1` gives a total of $1.13 and **always** fails with card expired:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "items": [{ "productId": "P0001", "quantity": 1 }]
  }'
```

In any other case, the gateway approves ~80% of the time (`payment.gateway.success-rate`).

---