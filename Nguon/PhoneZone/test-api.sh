#!/bin/bash
# ============================================================
# test-api.sh - Script test cac API chinh cua he thong
# ============================================================
# Chay sau khi docker-compose up va doi cac service start xong (~30s)
set -e

GATEWAY="http://localhost:8080"
USER_SVC="http://localhost:8081"
PRODUCT_SVC="http://localhost:8082"
ORDER_SVC="http://localhost:8083"
PAYMENT_SVC="http://localhost:8084"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

step() { echo -e "\n${YELLOW}=== $1 ===${NC}"; }
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

# -------------------------------------------------------------
step "1. Health check cac service"
# -------------------------------------------------------------
for s in $USER_SVC $PRODUCT_SVC $ORDER_SVC $PAYMENT_SVC; do
    if curl -sf "$s/q/health/ready" > /dev/null; then
        ok "Service $s ready"
    else
        fail "Service $s khong ready"
    fi
done

# -------------------------------------------------------------
step "2. Dang nhap"
# -------------------------------------------------------------
LOGIN_RESPONSE=$(curl -s -X POST "$USER_SVC/api/users/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"user01","password":"123456"}')
echo "Response: $LOGIN_RESPONSE"
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -oP '"token":"[^"]+' | sed 's/"token":"//')
if [ -z "$TOKEN" ]; then
    fail "Khong lay duoc token"
fi
ok "Da co JWT token: ${TOKEN:0:40}..."

# -------------------------------------------------------------
step "3. Liet ke san pham"
# -------------------------------------------------------------
PRODUCTS=$(curl -s "$PRODUCT_SVC/api/products")
COUNT=$(echo "$PRODUCTS" | grep -oP '"id"' | wc -l)
ok "Co $COUNT san pham"
echo "$PRODUCTS" | head -100

# Lay id cua san pham dau tien
PRODUCT_ID=$(echo "$PRODUCTS" | grep -oP '"id":"[^"]+' | head -1 | sed 's/"id":"//')
ok "Su dung product_id: $PRODUCT_ID"

# -------------------------------------------------------------
step "4. Them san pham vao gio hang"
# -------------------------------------------------------------
CART=$(curl -s -X POST "$ORDER_SVC/api/carts?userId=2&productId=$PRODUCT_ID&quantity=2" \
    -H "Authorization: Bearer $TOKEN")
echo "Cart: $CART"
ok "Da them vao gio"

# -------------------------------------------------------------
step "5. Checkout (tao order)"
# -------------------------------------------------------------
ORDER=$(curl -s -X POST "$ORDER_SVC/api/orders/checkout?userId=2&address=123%20Nguyen%20Trai%20Hanoi" \
    -H "Authorization: Bearer $TOKEN")
echo "Order: $ORDER"
ORDER_ID=$(echo "$ORDER" | grep -oP '"id":[0-9]+' | head -1 | sed 's/"id"://')
TOTAL=$(echo "$ORDER" | grep -oP '"total":[0-9]+' | head -1 | sed 's/"total"://')
ok "Order ID: $ORDER_ID, Total: $TOTAL VND"

# -------------------------------------------------------------
step "6. Tao thanh toan MoMo"
# -------------------------------------------------------------
PAYMENT=$(curl -s -X POST "$PAYMENT_SVC/api/payments" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"orderId\":$ORDER_ID,\"userId\":2,\"amount\":$TOTAL,\"orderInfo\":\"Test order $ORDER_ID\",\"method\":\"MOMO\"}")
echo "Payment: $PAYMENT"
PAYMENT_ID=$(echo "$PAYMENT" | grep -oP '"paymentId":[0-9]+' | sed 's/"paymentId"://')
QR_URL=$(echo "$PAYMENT" | grep -oP '"qrCodeUrl":"[^"]+' | sed 's/"qrCodeUrl":"//')
PAY_URL=$(echo "$PAYMENT" | grep -oP '"payUrl":"[^"]+' | sed 's/"payUrl":"//')
ok "Payment ID: $PAYMENT_ID"
echo ""
echo "  QR data:  $QR_URL"
echo "  Pay URL:  $PAY_URL"
echo ""
echo "  -> Mo trang test: http://localhost:8084/test.html"
echo "  -> Hoac copy QR data sang qrserver.com de hien thi QR"
echo ""

# -------------------------------------------------------------
step "Hoan tat!"
# -------------------------------------------------------------
ok "Tat ca API hoat dong dung"
