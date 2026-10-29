# Payment Service - Thanh toán MoMo

## Tổng quan

`payment-service` xử lý thanh toán qua MoMo sandbox với 3 cơ chế xác nhận kết quả:

1. **Webhook IPN** (chính) — MoMo POST về `/api/payments/momo/ipn` khi giao dịch xong.
2. **Polling** (backup) — Scheduled job mỗi 2 phút query MoMo cho các giao dịch PENDING.
3. **Manual confirm** (admin fallback) — Endpoint `/api/payments/{id}/manual-confirm` cho admin xử lý thủ công.

## Sandbox credentials đã có sẵn

Đã cấu hình trong `application.properties`:
```
momo.partner-code  = MOMO
momo.access-key    = F8BBA842ECF85
momo.secret-key    = K951B6PE1waDMi640xX08PD3vg6EkVlz
momo.endpoint      = https://test-payment.momo.vn
```

Đây là credentials test công khai của MoMo, ai cũng dùng được để demo.

## Cách chạy local

### Option 1: docker-compose (đầy đủ)

```bash
docker-compose up --build
```

Truy cập trang test: http://localhost:8084/test.html

### Option 2: dev mode (nhanh hơn)

Cần đã chạy MySQL trước. Sau đó:

```bash
cd payment-service
./mvnw quarkus:dev
```

## Test flow

### 1. Mở trang test

http://localhost:8084/test.html

### 2. Đăng nhập

Dùng user mẫu đã seed:
- Username: `user01`
- Password: `123456`

(Hoặc admin: `admin` / `admin123`)

### 3. Tạo giao dịch

Nhập số tiền (≥ 1000 VND) và bấm "Tạo QR thanh toán".

Backend sẽ:
- Tính chữ ký HMAC-SHA256 với secret key MoMo
- Gửi request đến `https://test-payment.momo.vn/v2/gateway/api/create`
- Lưu Payment vào MySQL với status PENDING
- Trả về `qrCodeUrl`, `payUrl`, `deeplink`

### 4. Quét QR

Cần **app MoMo Test** (KHÁC với app MoMo thường):
- Tải tại https://developers.momo.vn/v3/vi/docs/payment/onboarding/test-instructions/
- Hoặc dùng tài khoản test trên app MoMo bằng thẻ ATM test:
  - Số thẻ: `9704 0000 0000 0018`
  - Tên: `NGUYEN VAN A`
  - Ngày hết hạn: `03/07`
  - OTP: nhập bất kỳ 6 số

### 5. Kết quả

- Nếu thanh toán thành công trong app, MoMo gọi về IPN → status chuyển sang SUCCESS.
- Trang test polling mỗi 3 giây để cập nhật.

## Vấn đề IPN khi chạy localhost

MoMo không thể gọi vào `localhost` của bạn. Có 2 cách:

### Cách 1: Dùng polling (đã có sẵn)

Scheduled job tự query MoMo mỗi 2 phút. Hoặc bấm "🔄 Kiểm tra ngay" trên trang test.

### Cách 2: Expose localhost qua ngrok

```bash
# Cài ngrok: https://ngrok.com/download
ngrok http 8084

# Sẽ thấy URL: https://abc123.ngrok-free.app
# Set biến môi trường:
export MOMO_IPN_URL="https://abc123.ngrok-free.app/api/payments/momo/ipn"

# Rồi chạy lại payment-service hoặc docker-compose
```

Với ngrok, MoMo sẽ gọi được IPN về máy bạn, status sẽ tự cập nhật trong ~5 giây sau khi thanh toán.

## API Endpoints

| Method | Path | Mô tả |
|---|---|---|
| POST | `/api/payments` | Tạo giao dịch, trả về QR + payUrl |
| POST | `/api/payments/momo/ipn` | MoMo callback (không gọi thủ công) |
| POST | `/api/payments/{id}/query` | Query trạng thái từ MoMo |
| GET | `/api/payments/{id}` | Xem 1 giao dịch |
| GET | `/api/payments/order/{orderId}` | Xem các giao dịch của 1 order |
| POST | `/api/payments/{id}/manual-confirm?success=true&note=...` | Admin xác nhận thủ công |

Swagger UI: http://localhost:8084/swagger

## Bảo mật

- **HMAC-SHA256 signature** với secret key — verify cả request đi và IPN về.
- **Idempotency** — IPN có thể được MoMo gọi nhiều lần, code chỉ xử lý lần đầu.
- **Amount check** — verify amount trong IPN match với DB để chống giả mạo.
- **JWT** — các endpoint trừ `/momo/ipn` đều yêu cầu JWT của user-service.

## Mở rộng ZaloPay / VNPay

Code đã thiết kế sẵn `PaymentMethod` enum. Để thêm gateway khác:
1. Tạo `ZaloPayClient` tương tự `MomoClient`
2. Thêm `signCreateZaloPay()` trong utility chữ ký
3. Trong `PaymentService.createPayment()`, switch theo `req.method`

## Lỗi thường gặp

| resultCode | Nghĩa | Hướng xử lý |
|---|---|---|
| 0 | Thành công | — |
| 9000 | Giao dịch đang xử lý | Đợi IPN hoặc query lại |
| 1006 | User huỷ | Cho user tạo giao dịch mới |
| 99 | Sai signature | Check thứ tự field khi tính HMAC |
| 41 | Sai amount/orderId | Check format request |
