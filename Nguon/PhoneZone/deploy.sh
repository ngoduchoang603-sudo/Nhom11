#!/bin/bash
# ============================================================
# deploy.sh - Script deploy toan bo he thong len Kubernetes
# ============================================================
set -e

echo "========================================="
echo "  Deploy Quarkus Shop Microservices"
echo "========================================="

# 1. Tao namespace
echo ""
echo "[1/4] Tao namespace 'shop'..."
kubectl apply -f k8s/00-namespace.yaml

# 2. Apply ConfigMap va Secret
echo ""
echo "[2/4] Apply ConfigMap va Secret..."
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secrets.yaml

# 3. Deploy database (cho database san sang truoc khi deploy service)
echo ""
echo "[3/4] Deploy PostgreSQL and Kafka..."
kubectl apply -f k8s/11-ai-kafka-kong.yaml

echo ""
echo "  Cho database san sang..."
kubectl -n shop wait --for=condition=ready pod -l app=postgres-ai --timeout=180s
echo "  Database OK"

# 4. Deploy cac microservice
echo ""
echo "[4/4] Deploy microservices..."
kubectl apply -f k8s/06-user-service.yaml
kubectl apply -f k8s/07-product-service.yaml
kubectl apply -f k8s/08-order-service.yaml
kubectl apply -f k8s/09-api-gateway.yaml
kubectl apply -f k8s/10-payment-service.yaml

echo ""
echo "========================================="
echo "  Deploy hoan tat!"
echo "========================================="
echo ""
echo "Kiem tra trang thai:"
kubectl -n shop get all
echo ""
echo "Lay URL cua gateway:"
kubectl -n shop get svc api-gateway
echo ""
echo "Xem log:"
echo "  kubectl -n shop logs -f deployment/user-service"
echo "  kubectl -n shop logs -f deployment/product-service"
echo "  kubectl -n shop logs -f deployment/order-service"
echo "  kubectl -n shop logs -f deployment/api-gateway"
