-- PhoneZone: SQL demo Chapter 3.
-- This file uses fully qualified schema.table names so it runs from any active schema.
-- Original report document is not modified.

-- 3.2. Confirm the database and five business schemas
SELECT current_database() AS database_dang_demo;
SELECT schema_name
FROM information_schema.schemata
WHERE schema_name IN ('shop_user','shop_product','shop_order','shop_payment','shop_ai')
ORDER BY schema_name;

-- 3.4. Check products and sellable variants
SELECT p.id, p.name, v.id AS variant_id, v.sku, v.color, v.ram, v.storage,
       v.stock, (p.price + v.price_adjustment - COALESCE(p.discount,0)) AS current_price
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1'
ORDER BY p.name, v.id;

-- 3.5. Detect cart lines exceeding current variant stock
SELECT c.id, c.user_id, c.variant_id, c.quantity, v.stock
FROM shop_order.cart c
JOIN shop_product.product_variants v ON v.id = c.variant_id
WHERE c.quantity > v.stock;

-- 3.6. Reconcile orders with item snapshots
SELECT o.id, o.user_name_snapshot, o.order_date, o.status,
       i.product_name_snapshot, i.variant_label_snapshot,
       i.quantity, i.unit_price_snapshot, i.total
FROM shop_order.orders o
JOIN shop_order.items i ON i.order_id = o.id
ORDER BY o.order_date DESC, o.id, i.id;

-- 3.6. Integrity checks: every query should return no rows
SELECT v.id AS variant_without_parent
FROM shop_product.product_variants v
LEFT JOIN shop_product.products p ON p.id = v.product_id
WHERE p.id IS NULL;

SELECT c.id AS cart_with_invalid_variant
FROM shop_order.cart c
LEFT JOIN shop_product.product_variants v ON v.id = c.variant_id
WHERE v.id IS NULL;

SELECT request_id, COUNT(*) AS so_lan
FROM shop_payment.payment
GROUP BY request_id
HAVING COUNT(*) > 1;

-- 4.1. Products by brand/category and stock.
-- Corrected: stock belongs to product_variants, not products.
SELECT p.category,
       COUNT(DISTINCT p.id) AS so_san_pham,
       SUM(v.stock) AS tong_ton,
       AVG(p.price) AS gia_trung_binh
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1'
GROUP BY p.category
ORDER BY so_san_pham DESC;

-- 4.2. Revenue by day from delivered orders
SELECT DATE(order_date) AS ngay,
       COUNT(*) AS so_don,
       SUM(total_price) AS doanh_thu
FROM shop_order.orders
WHERE status = 'DELIVERED'
GROUP BY DATE(order_date)
ORDER BY ngay DESC;

-- 4.3. Top products by revenue.
-- Corrected: items stores variant_id and product_name_snapshot.
SELECT i.variant_id,
       MAX(i.product_name_snapshot) AS ten_san_pham,
       SUM(i.quantity) AS so_luong_ban,
       SUM(i.total) AS doanh_thu
FROM shop_order.items i
GROUP BY i.variant_id
ORDER BY doanh_thu DESC
LIMIT 10;

-- 4.4. Payment status statistics
SELECT status,
       COUNT(*) AS so_giao_dich,
       SUM(amount) AS tong_tien
FROM shop_payment.payment
GROUP BY status
ORDER BY status;

-- 4.5. Coupons
SELECT code, name, type, value,
       min_order_value, used_count, usage_limit,
       active, start_at, end_at
FROM shop_order.coupons
ORDER BY active DESC, end_at NULLS LAST;

-- 4.6. After-sales cases by status
SELECT status, COUNT(*) AS so_ho_so
FROM shop_order.after_sales_cases
GROUP BY status
ORDER BY so_ho_so DESC;

-- 4.7. EXPLAIN ANALYZE for products by category.
-- Corrected: stock is in product_variants.
EXPLAIN ANALYZE
SELECT p.id, p.name, p.price, p.discount, v.stock
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1' AND p.category = 'Apple'
ORDER BY p.create_date DESC;

-- 4.8. EXPLAIN ANALYZE for delivered orders by time.
-- Corrected: the order snapshot column is user_name_snapshot.
EXPLAIN ANALYZE
SELECT id, user_name_snapshot, total_price, order_date
FROM shop_order.orders
WHERE status = 'DELIVERED'
  AND order_date >= TIMESTAMPTZ '2026-08-01 00:00:00+07'
ORDER BY order_date DESC;

-- Appendix B.1.1. Active users
SELECT id, username, full_name, email, role, status
FROM shop_user.users
WHERE status = '1'
ORDER BY id;

-- Appendix B.1.2. Product catalogue
SELECT p.id, p.name, p.category, p.price, p.discount, p.status,
       v.id AS variant_id, v.sku, v.color, v.ram, v.storage, v.stock
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1'
ORDER BY p.category, p.name, v.sku;

-- Appendix B.2.1. Orders and details
SELECT o.id, o.user_name_snapshot, o.status, o.total_price,
       i.product_name_snapshot, i.variant_label_snapshot,
       i.quantity, i.unit_price_snapshot
FROM shop_order.orders o
JOIN shop_order.items i ON i.order_id = o.id
ORDER BY o.id DESC, i.id;

-- Appendix B.2.2. Delivered revenue and top variants
SELECT SUM(total_price) AS doanh_thu_da_giao
FROM shop_order.orders
WHERE status = 'DELIVERED';

SELECT i.variant_id, MAX(i.product_name_snapshot) AS san_pham,
       SUM(i.quantity) AS so_luong,
       SUM(i.quantity * i.unit_price_snapshot) AS doanh_thu
FROM shop_order.items i
JOIN shop_order.orders o ON o.id = i.order_id
WHERE o.status = 'DELIVERED'
GROUP BY i.variant_id
ORDER BY doanh_thu DESC
LIMIT 5;

-- Appendix B.3.1. Active coupons
SELECT code, name, type, value, min_order_value,
       used_count, usage_limit, start_at, end_at
FROM shop_order.coupons
WHERE active = TRUE
ORDER BY end_at NULLS LAST;

-- Appendix B.3.2. Payments requiring reconciliation
SELECT id, order_id, amount, method, status, created_at
FROM shop_payment.payment
WHERE status IN ('PENDING', 'FAILED')
ORDER BY created_at DESC;

-- Appendix B.3.3. Open after-sales cases.
-- Corrected: product name is held by the related item snapshot.
SELECT a.case_code, a.order_id, i.product_name_snapshot AS product_name, a.imei,
       a.request_type, a.status, a.created_at
FROM shop_order.after_sales_cases a
LEFT JOIN shop_order.items i ON i.id = a.item_id
WHERE a.status NOT IN ('REJECTED','CLOSED','RESOLVED_REFUND',
                       'RESOLVED_REPLACEMENT','RESOLVED_REPAIRED')
ORDER BY a.created_at DESC;

-- Appendix B.4.1. Execution plan
EXPLAIN ANALYZE
SELECT p.id, p.name, v.id AS variant_id, v.sku, v.stock
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1' AND p.category = 'Apple'
ORDER BY p.create_date DESC;

-- Appendix C.4.1. Accounts by role
SELECT role, COUNT(*) AS so_tai_khoan
FROM shop_user.users
WHERE status = '1'
GROUP BY role
ORDER BY role;

-- Appendix C.4.2. Price after discount
SELECT name, price, discount,
       GREATEST(price - COALESCE(discount, 0), 0) AS gia_sau_giam
FROM shop_product.products
WHERE status = '1'
ORDER BY price DESC;

-- Appendix C.4.3. Low-stock variants.
-- Corrected: stock belongs to the sellable variant.
SELECT p.id, p.name, p.category, v.stock
FROM shop_product.products p
JOIN shop_product.product_variants v ON v.product_id = p.id
WHERE p.status = '1' AND v.stock <= 10
ORDER BY v.stock, p.name;

-- Appendix C.4.4. Compare the order total with item totals
SELECT o.id,
       o.total_price AS tong_luu_trong_don,
       COALESCE(SUM(i.total), 0) AS tong_cac_dong,
       o.coupon_discount
FROM shop_order.orders o
LEFT JOIN shop_order.items i ON i.order_id = o.id
GROUP BY o.id, o.total_price, o.coupon_discount
ORDER BY o.id DESC;

-- Appendix C.4.5. Duplicate payment request IDs: expected no rows
SELECT request_id, COUNT(*) AS so_lan
FROM shop_payment.payment
GROUP BY request_id
HAVING COUNT(*) > 1;
