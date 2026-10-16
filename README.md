# Payment Service

Payment links and payment status for the ecom platform (HLD 3.5, PRD 5).
Port **8083**. Uses the User Service (`USERSERVICE_URL`, default `http://localhost:8082`) to validate login tokens.

## Endpoints (`/api/v1/payments`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/` | Bearer | Create a payment link for one of your orders: `{"orderId": "1", "phoneNumber": "+91..."}`. The amount charged is the order total from the Order Service |
| GET | `/{id}` | Bearer | Get one of your payments |
| GET | `/order/{orderId}` | Bearer | All of your payment attempts for an order |
| POST | `/webhook` | `X-Razorpay-Signature` | Razorpay payment-link events (`paid`, `expired`, `cancelled`) |

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `PAYMENT_GATEWAY` | `mock` | `razorpay` for real links, `mock` for local development |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | – | Required when `PAYMENT_GATEWAY=razorpay` |
| `RAZORPAY_WEBHOOK_SECRET` | – | Webhook signing secret; webhooks are rejected until it is set |
| `PAYMENT_CALLBACK_URL` | `http://localhost:3000/payment/callback` | Where Razorpay redirects the customer after paying |
| `USERSERVICE_URL` | `http://localhost:8082` | Token validation |
| `ORDERSERVICE_URL` | `http://localhost:8084` | Order totals, and payment-completed notifications |
| `INTERNAL_API_TOKEN` | – | Shared secret for the Order Service internal API; must match the Order Service |

Never commit gateway keys. Use environment variables or a secrets manager.

## Run

```bash
./gradlew build
java -jar build/libs/paymentservice-0.0.1-SNAPSHOT.jar                                  # H2 + mock gateway
java -jar build/libs/paymentservice-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql   # MySQL
```
