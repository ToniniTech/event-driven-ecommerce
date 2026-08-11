## Services and ports

| Service | Endpoint | Credentials |
|---------|----------|-------------|
| Auth Service | http://localhost:8084 | — |
| Order Service | http://localhost:8081 | — |
| Payment Service | http://localhost:8082 | — |
| Notification Service | http://localhost:8083 | — |
| Product Service | http://localhost:8085 | — |
| Zipkin | http://localhost:9411 | — |
| RabbitMQ Management UI | http://localhost:15672 | `guest / guest` |
| Auth DB | `localhost:3310` | `authuser / authpass` |
| Order DB | `localhost:3307` | `orderuser / orderpass` |
| Payment DB | `localhost:3308` | `paymentuser / paymentpass` |
| Notification DB | `localhost:3309` | `notifuser / notifpass` |
| Product DB | `localhost:3311` | `productuser / productpass` |

---

## Inspect the databases

```sql
-- Auth DB (port 3310)
SELECT customer_id, email, first_name, role, created_at FROM users;

-- Order DB (port 3307)
SELECT o.order_id, o.customer_id, o.status, o.total_amount, o.created_at,
       GROUP_CONCAT(i.product_name SEPARATOR ', ') AS products
FROM orders o
LEFT JOIN order_items i ON o.id = i.order_id
GROUP BY o.id ORDER BY o.created_at DESC;

-- Payment DB (port 3308)
SELECT payment_id, order_id, status, amount, failure_reason, created_at
FROM payments ORDER BY created_at DESC;

-- Notification DB (port 3309)
SELECT notification_type, status, recipient_email, sent_at
FROM notifications ORDER BY created_at DESC;

-- Product DB (port 3311)
SELECT product_id, name, price, stock, is_active
FROM products ORDER BY product_id LIMIT 20;
```

## Stop the system

```bash
# Stop containers (keeps data)
docker-compose down

# Clean start — deletes all data
docker-compose down -v
```

