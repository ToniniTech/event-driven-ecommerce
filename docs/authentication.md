## Admin endpoints

Require a JWT with the `ADMIN` role. The admin user is created automatically when the service starts (see `DataSeeder`).

### Admin credentials (demo)

The admin user is created at startup with these default credentials:

- **Email:** `admin@ecommerce.local`
- **Password:** `neymarsantos123`

For a real deployment, override them with the `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables.

### Login as admin

```bash
curl -X POST http://localhost:8084/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@ecommerce.local",
    "password": "neymarsantos123"
  }'
```

### Lock an account

Deactivates the user and revokes all their refresh tokens. The account is locked after **3 failed login attempts** or manually with this endpoint.

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/lockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

### Unlock an account

```bash
curl -X PATCH http://localhost:8084/api/auth/admin/unlockUser/{customerId} \
  -H "Authorization: Bearer <adminAccessToken>"
```

Both endpoints return `204 No Content` if the operation succeeds.

---
