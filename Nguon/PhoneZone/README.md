# Shop Microservices - Quarkus + Kubernetes

## 1. Kiến trúc tổng quan

```
                              ┌─────────────────┐
                              │ Kong API Gateway│ (port 8000)
                              └────────┬────────┘
                                       │
                              ┌────────▼────────┐
                              │ Quarkus gateway │ (internal routing)
                              └─────────────────┘
       ┌──────────────┬────────────────┼────────────────┬──────────────┐
       ▼              ▼                ▼                ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ user-service │ │product-service│ │ order-service│ │payment-service│
│    :8081     │ │    :8082     │ │    :8083     │ │    :8084     │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │                │
       ▼                ▼                ▼                ▼
   ┌───────┐       ┌─────────┐     ┌──────────┐     ┌──────────┐
   │ MySQL │       │ MongoDB │     │ SQL Server│    │  MySQL   │
   └───────┘       └─────────┘     └──────────┘     └──────────┘
                                                          │
                                                          ▼
                                                ┌───────────────────┐
                                                │  MoMo Sandbox API │
                                                │  test-payment.    │
                                                │  momo.vn          │
                                                └───────────────────┘
```

- **order-service** gọi sang **product-service** (trừ kho) và **user-service** (thông tin user)
- **payment-service** gọi MoMo API + callback ngược về **order-service** khi thanh toán xong
- Tất cả qua **MicroProfile REST Client** với @Retry + @Timeout + @Fallback
- **Kong** là public entry point, cung cấp routing, rate limit và correlation ID.
- **Kafka** phát sự kiện `shop.order.created.v1` từ order-service, giúp các bounded context tích hợp bất đồng bộ.
- **ai-service** là bounded context độc lập, áp dụng Clean Architecture (`domain` → `application ports` → `adapters`) và lưu audit hội thoại trên PostgreSQL.

## 2. Cấu trúc thư mục

```
quarkus-microservices/
├── user-service/           # MySQL — user + đăng nhập + JWT
├── product-service/        # MongoDB — sản phẩm + comment
├── order-service/          # SQL Server — cart + order
├── payment-service/        # MySQL — thanh toán MoMo
│   └── test.html           # Trang test thanh toán
├── api-gateway/            # Định tuyến request nội bộ (Quarkus)
├── kong/                   # Kong declarative gateway configuration
├── ai-service/             # DDD/Clean Architecture AI assistant + PostgreSQL
├── k8s/                    # Kubernetes manifests
├── docker-compose.yml      # Chạy local
├── deploy.sh               # Deploy lên K8s
└── README.md
```

## 3. Chạy local bằng docker-compose

```bash
cd quarkus-microservices
# Chay toan bo he thong: Kong, REST services, Kafka, PostgreSQL va AI
docker compose up --build
```

Kafka publisher duoc bat mac dinh va phat event `shop.order.created.v1` khi co don hang moi.
Docker Compose tao san topic nay (3 partitions) va mo **Kafka UI** tai http://localhost:8087.

Lần đầu build có thể mất ~5-10 phút (tải dependency + build native image cho 5 service).

Sau khi xong, truy cập:
- **🛍️ Phone Shop (qua Kong)**: http://localhost:8000
- **📨 Kafka UI**: http://localhost:8087 — chon cluster `shop-local`, topic `shop.order.created.v1`, roi tao mot don hang de xem event.
- **🧪 Trang test thanh toán**: http://localhost:8084/test.html
- API Gateway: http://localhost:8080/api/...
- AI assistant: `POST http://localhost:8000/ai/chat` với body `{"message":"Tư vấn sản phẩm"}`

Chatbot đã hỗ trợ Gemini qua endpoint OpenAI-compatible. Sao chép `.env.example` thành `.env`, giữ `AI_PROVIDER_URL` mặc định, rồi điền `AI_API_KEY` lấy từ Google AI Studio và chạy lại `docker compose up --build`. Mặc định dùng `gemini-3.7-flash`; có thể đổi bằng `AI_MODEL`. Không commit file `.env` hoặc API key. Khi chưa cấu hình, AI service dùng phản hồi an toàn dự phòng và vẫn lưu audit vào PostgreSQL.
- Swagger các service: http://localhost:808[1-4]/swagger

## 4. Test thanh toán MoMo

Xem chi tiết tại [payment-service/README.md](payment-service/README.md).

Tóm tắt nhanh:
1. Mở http://localhost:8084/test.html
2. Login: `user01` / `123456`
3. Nhập số tiền → Bấm "Tạo QR thanh toán"
4. Quét QR bằng **app MoMo Test** (xem hướng dẫn của MoMo developer)
5. Trang tự động cập nhật khi thanh toán xong

## 5. Test API qua curl

Đăng ký user:
```bash
curl -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"newuser","password":"pass123","email":"a@gmail.com","fullName":"Nguyễn Văn A"}'
```

Đăng nhập:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user01","password":"123456"}' | jq -r .token)
```

Tạo thanh toán MoMo:
```bash
curl -X POST http://localhost:8084/api/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"userId":2,"amount":10000,"orderInfo":"Test","method":"MOMO"}'
```

## 6. Deploy lên Kubernetes

```bash
# Build image
for s in user-service product-service order-service payment-service api-gateway ai-service; do
  docker build -t shop/$s:1.0.0 $s/
done

# Nếu dùng minikube
for s in user-service product-service order-service payment-service api-gateway ai-service; do
  minikube image load shop/$s:1.0.0
done

# Deploy
./deploy.sh
```

## 7. Bảng tính năng đối chiếu với đề

| Yêu cầu | Vị trí trong project |
|---|---|
| Phát triển ứng dụng với Quarkus | Tất cả 5 service |
| CRUD với MySQL | `user-service`, `payment-service` (Hibernate Panache) |
| CRUD với MongoDB | `product-service` (MongoDB Panache) |
| CRUD với SQL Server | `order-service` (Hibernate Panache + mssql JDBC) |
| Microservice communication | `*Client.java` (MicroProfile REST Client) |
| Fault tolerance | `@Retry @Timeout @Fallback` |
| JWT security | `user-service/UserService.login()` |
| Health check (K8s) | `quarkus-smallrye-health` |
| Metrics (K8s) | `quarkus-micrometer-registry-prometheus` |
| K8s Deployment | `k8s/*.yaml` |
| StatefulSet cho DB | `k8s/03-mysql.yaml`, `04-mssql.yaml`, `05-mongodb.yaml` |
| ConfigMap + Secret | `k8s/01-configmap.yaml`, `02-secrets.yaml` |
| HPA (auto-scale) | Trong từng file service |
| Thanh toán QR (MoMo) | `payment-service` |
| Webhook IPN | `PaymentResource.handleMomoIpn()` |
| Scheduled polling | `PaymentScheduler` |
| HMAC-SHA256 signature | `MomoSignature.java` |
| Kong API Gateway | `kong/kong.yml` (routing, rate limit, correlation ID) |
| Apache Kafka | `order-service/OrderEventPublisher.java` → `shop.order.created.v1` |
| PostgreSQL | `postgres-ai` + `ai-service` conversation audit |
| DDD / Clean Architecture | `ai-service/{domain,application,adapter}` |
| Agentic AI | `ai-service` uses a read-only product-catalog tool, persists audit data and constrains the LLM from mutations |
| Hibernate ORM / JPA | `order-service` and `ai-service` (`PanacheEntity` on top of Hibernate ORM/JPA) |
| Kubernetes (AI/Kafka/PostgreSQL/Kong) | `k8s/11-ai-kafka-kong.yaml` |

## 2. Cấu trúc thư mục

```
quarkus-microservices/
├── user-service/           # MySQL — quản lý user + JWT login
├── product-service/        # MongoDB — quản lý sản phẩm + comment
├── order-service/          # SQL Server — quản lý cart + order
├── api-gateway/            # Định tuyến request
├── k8s/                    # Kubernetes manifests
├── docker-compose.yml      # Chạy local
└── deploy.sh               # Deploy lên K8s
```

## 3. Chạy local bằng docker-compose

```bash
cd quarkus-microservices
docker-compose up --build
```

Kiểm tra:
- API Gateway: http://localhost:8080
- User Swagger: http://localhost:8081/swagger
- Product Swagger: http://localhost:8082/swagger
- Order Swagger: http://localhost:8083/swagger

## 4. Chạy mỗi service ở dev mode (live reload)

```bash
cd user-service
./mvnw quarkus:dev
```

Khi sửa code, Quarkus tự reload không cần restart.

## 5. Deploy lên Kubernetes

```bash
# Cần kubectl + cluster đang chạy (minikube/kind/cloud)

# Build image lên Docker registry
docker build -t shop/user-service:1.0.0 user-service/
docker build -t shop/product-service:1.0.0 product-service/
docker build -t shop/order-service:1.0.0 order-service/
docker build -t shop/api-gateway:1.0.0 api-gateway/

# Nếu dùng minikube, load image vào cluster:
minikube image load shop/user-service:1.0.0
minikube image load shop/product-service:1.0.0
minikube image load shop/order-service:1.0.0
minikube image load shop/api-gateway:1.0.0

# Deploy
./deploy.sh
```

## 6. Test API

Đăng ký user:
```bash
curl -X POST http://localhost:8081/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user01","password":"123456","email":"a@gmail.com","fullName":"Nguyễn Văn A"}'
```

Đăng nhập (lấy JWT token):
```bash
curl -X POST http://localhost:8081/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user01","password":"123456"}'
```

Tạo sản phẩm (cần JWT của ADMIN):
```bash
curl -X POST http://localhost:8082/api/products \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 16","price":25000000,"category":"Apple","stock":100}'
```

Đặt hàng:
```bash
# 1. Thêm vào giỏ
curl -X POST "http://localhost:8083/api/carts?userId=1&productId=<PRODUCT_ID>&quantity=1" \
  -H "Authorization: Bearer <TOKEN>"

# 2. Checkout
curl -X POST "http://localhost:8083/api/orders/checkout?userId=1&address=Hà Nội" \
  -H "Authorization: Bearer <TOKEN>"
```

## 7. Monitor

- Health: `/q/health`, `/q/health/live`, `/q/health/ready`
- Metrics (Prometheus): `/q/metrics`
- OpenAPI/Swagger: `/swagger`

## 8. Các tính năng đã demo

| Yêu cầu | Vị trí trong project |
|---|---|
| Quarkus REST API | Mọi `*Resource.java` |
| CRUD với MySQL | `user-service` (Hibernate Panache) |
| CRUD với MongoDB | `product-service` (MongoDB Panache) |
| CRUD với SQL Server | `order-service` (Hibernate Panache + mssql JDBC) |
| Microservice communication | `order-service/client/*Client.java` (REST Client) |
| Fault tolerance | `@Retry @Timeout @Fallback` trên Client |
| JWT security | `user-service/UserService.login()` |
| Health check cho K8s | Tự sinh bởi `quarkus-smallrye-health` |
| Metrics cho K8s | `quarkus-micrometer-registry-prometheus` |
| K8s Deployment | `k8s/*.yaml` |
| StatefulSet cho DB | `k8s/03-mysql.yaml`, `04-mssql.yaml`, `05-mongodb.yaml` |
| Service + Ingress | `k8s/09-api-gateway.yaml` |
| ConfigMap + Secret | `k8s/01-configmap.yaml`, `02-secrets.yaml` |
| HPA (auto-scale) | Trong từng file service |
