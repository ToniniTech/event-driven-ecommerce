## Product catalog

The catalog is served by **Product Service** (`:8085`) and is loaded from `products_300.csv` at startup via an idempotent import (UPSERT by `productId`): restarting the service does not duplicate rows.

- **300 products** (grocery items), with IDs `P0001` … `P0300`.
- Prices between **$0.99 and $7.47**.
- Each product has `stock`, an `active` flag (soft delete), and a `@Version` for optimistic locking.

Real examples from the catalog:

| productId | Name | Price | Stock |
|-----------|--------|--------|-------|
| P0001 | Cebolla 1kg | $1.13 | 7 |
| P0002 | Chocolate 100g | $2.20 | 63 |
| P0003 | Queso gauda 200g | $3.15 | 189 |
| P0149 | Leche entera 1L | $2.64 | 10 |
| P0300 | Carne molida 500g | $6.04 | 91 |

Explore the catalog via API:

```bash
# Paginated list (?active=true|false optional)
curl "http://localhost:8085/api/products?page=0&size=10"

# A single product
curl http://localhost:8085/api/products/P0002
```
