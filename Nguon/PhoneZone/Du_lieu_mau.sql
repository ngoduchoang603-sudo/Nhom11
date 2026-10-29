-- PhoneZone - dữ liệu mẫu cho toàn bộ dự án.
-- Chạy SAU file Cau_truc_CSDL.sql, khi đang kết nối database phonezone bằng user shop.
-- Có thể chạy lại: các bản ghi mẫu cùng ID sẽ được bỏ qua.

-- ============================================================
-- 1. shop_user: tài khoản và địa chỉ
-- ============================================================
INSERT INTO shop_user.users
    (id, username, password, full_name, email, phone, city, district, address, gender, role, status)
VALUES
    (1, 'admin_demo', '$2a$10$demo.hash.not.a.real.password', 'Quản trị viên Demo',
     'admin@phonezone.local', '0900000001', 'Hà Nội', 'Cầu Giấy', 'Số 1 Đường Demo, Cầu Giấy, Hà Nội', 'OTHER', 'ADMIN', '1'),
    (2, 'user_demo', '$2a$10$demo.hash.not.a.real.password', 'Khách hàng Demo',
     'user@phonezone.local', '0900000002', 'Hà Nội', 'Nam Từ Liêm', 'Số 2 Đường Demo, Nam Từ Liêm, Hà Nội', 'OTHER', 'USER', '1'),
    (10, 'softdelete_user', '$2a$10$demo.hash.not.a.real.password', 'Tài khoản kiểm thử xóa mềm',
     'softdelete@phonezone.local', '0900000010', 'Hà Nội', 'Đống Đa', 'Số 10 Đường Demo, Đống Đa, Hà Nội', 'OTHER', 'USER', '1')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_user.user_addresses
    (id, user_id, recipient_name, phone, address, address_label, is_default)
VALUES
    (1, 2, 'Khách hàng Demo', '0900000002', 'Số 2 Đường Demo, Nam Từ Liêm, Hà Nội', 'Nhà riêng', TRUE)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 2. shop_product: sản phẩm, biến thể và đánh giá
-- ============================================================
INSERT INTO shop_product.products
    (id, name, price, discount, category, image, description, screen, battery, status, create_date)
VALUES
    (1, 'iPhone 15 128GB', 19990000, 1000000, 'Apple', 'https://example.invalid/iphone15.jpg',
     'Sản phẩm demo cho báo cáo', '6.1 inch OLED', '3349 mAh', '1', '2026-09-01 08:00:00+07'),
    (2, 'Samsung Galaxy S24', 18990000, 500000, 'Samsung', 'https://example.invalid/s24.jpg',
     'Sản phẩm demo cho báo cáo', '6.2 inch AMOLED', '4000 mAh', '1', '2026-09-02 08:00:00+07'),
    (25, 'Sản phẩm kiểm thử xóa mềm', 1000000, 0, 'Demo', 'https://example.invalid/softdelete.jpg',
     'Sản phẩm phục vụ kiểm tra xóa mềm', 'Demo', 'Demo', '1', '2026-09-03 08:00:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_product.product_variants
    (id, product_id, sku, color, ram, storage, stock, price_adjustment)
VALUES
    (1, 1, 'IP15-128-BLK', 'Đen', '6GB', '128GB', 24, 0),
    (2, 1, 'IP15-128-BLU', 'Xanh', '6GB', '128GB', 8, 0),
    (3, 2, 'S24-256-GRY', 'Xám', '8GB', '256GB', 15, 1200000),
    (25, 25, 'DEMO-SOFTDELETE', 'Đen', '4GB', '64GB', 5, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_product.product_comments
    (id, product_id, user_id, username_snapshot, rating, message, create_date)
VALUES
    (1, 1, 2, 'user_demo', 5, 'Sản phẩm dùng tốt, dữ liệu đánh giá phục vụ demo.', '2026-09-03 09:00:00+07')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 3. shop_order: giỏ hàng, mã giảm giá, đơn hàng và hậu mãi
-- ============================================================
INSERT INTO shop_order.cart
    (id, user_id, variant_id, quantity, created_at)
VALUES
    (1, 2, 2, 1, '2026-09-03 09:30:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_order.coupons
    (id, code, name, type, value, min_order_value, start_at, end_at, active, usage_limit, used_count)
VALUES
    (1, 'WELCOME10', 'Giảm giá khách mới', 'PERCENT', 10, 5000000,
     '2026-01-01 00:00:00+07', '2026-12-31 23:59:59+07', TRUE, 100, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_order.orders
    (id, user_id, user_name_snapshot, delivery_address_snapshot, order_date, payment_method,
     coupon_code_snapshot, coupon_discount, quantity_product, total_price, status)
VALUES
    (1, 2, 'Khách hàng Demo', 'Số 2 Đường Demo, Nam Từ Liêm, Hà Nội',
     '2026-09-03 10:00:00+07', 'MOMO', 'WELCOME10', 1000000, 1, 18990000, 'DELIVERED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_order.items
    (id, order_id, variant_id, product_name_snapshot, variant_label_snapshot,
     unit_price_snapshot, discount_snapshot, product_image_snapshot, quantity, total)
VALUES
    (1, 1, 1, 'iPhone 15 128GB', 'Đen - 6GB - 128GB',
     19990000, 1000000, 'https://example.invalid/iphone15.jpg', 1, 18990000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_order.after_sales_cases
    (id, case_code, order_id, item_id, user_id, imei, request_type, description, status, created_at, updated_at)
VALUES
    (1, 'RMA-DEMO-001', 1, 1, 2, '356000000000001', 'WARRANTY',
     'Hồ sơ hậu mãi demo', 'UNDER_REVIEW', '2026-09-03 11:00:00+07', '2026-09-03 11:00:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_order.after_sales_evidence
    (id, case_id, evidence_url, evidence_type, created_at)
VALUES
    (1, 1, 'https://example.invalid/evidence-demo.jpg', 'IMAGE', '2026-09-03 11:05:00+07')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 4. shop_payment: giao dịch thanh toán
-- ============================================================
INSERT INTO shop_payment.payment
    (id, request_id, order_id, user_id, amount, method, status, transaction_id,
     result_code, message, created_at, updated_at)
VALUES
    (1, 'REQ-DEMO-001', 1, 2, 18990000, 'MOMO', 'SUCCESS', 'TXN-DEMO-001',
     0, 'Thanh toán thành công', '2026-09-03 10:05:00+07', '2026-09-03 10:05:00+07')
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- 5. shop_ai: hội thoại, hành vi và lịch sử gợi ý
-- ============================================================
INSERT INTO shop_ai.chat_messages
    (id, session_id, role, content, created_at)
VALUES
    (1, 'SESSION-DEMO-001', 'USER', 'Tôi cần tư vấn điện thoại chụp ảnh tốt.', '2026-09-03 09:00:00+07'),
    (2, 'SESSION-DEMO-001', 'ASSISTANT', 'Bạn có thể tham khảo iPhone 15 128GB.', '2026-09-03 09:00:05+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_ai.behavior_events
    (id, user_id, session_id, event_type, product_id, search_query, created_at)
VALUES
    (1, 2, 'SESSION-DEMO-001', 'SEARCH', NULL, 'iphone', '2026-09-03 09:01:00+07'),
    (2, 2, 'SESSION-DEMO-001', 'VIEW_PRODUCT', 1, NULL, '2026-09-03 09:02:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_ai.agent_memories
    (id, session_id, product_id, expires_at)
VALUES
    (1, 'SESSION-DEMO-001', 1, '2026-10-03 00:00:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_ai.recommendation_audits
    (id, subject_key, outcome, created_at)
VALUES
    (1, 'user:2', 'ACCEPTED', '2026-09-03 09:03:00+07')
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_ai.recommendation_audit_items
    (id, audit_id, product_id, rank_position)
VALUES
    (1, 1, 1, 1),
    (2, 1, 2, 2)
ON CONFLICT (id) DO NOTHING;

-- Đồng bộ bộ đếm ID sau khi INSERT ID thủ công.
SELECT setval(pg_get_serial_sequence('shop_user.users', 'id'), (SELECT MAX(id) FROM shop_user.users));
SELECT setval(pg_get_serial_sequence('shop_user.user_addresses', 'id'), (SELECT MAX(id) FROM shop_user.user_addresses));
SELECT setval(pg_get_serial_sequence('shop_product.products', 'id'), (SELECT MAX(id) FROM shop_product.products));
SELECT setval(pg_get_serial_sequence('shop_product.product_variants', 'id'), (SELECT MAX(id) FROM shop_product.product_variants));
SELECT setval(pg_get_serial_sequence('shop_product.product_comments', 'id'), (SELECT MAX(id) FROM shop_product.product_comments));
SELECT setval(pg_get_serial_sequence('shop_order.cart', 'id'), (SELECT MAX(id) FROM shop_order.cart));
SELECT setval(pg_get_serial_sequence('shop_order.coupons', 'id'), (SELECT MAX(id) FROM shop_order.coupons));
SELECT setval(pg_get_serial_sequence('shop_order.orders', 'id'), (SELECT MAX(id) FROM shop_order.orders));
SELECT setval(pg_get_serial_sequence('shop_order.items', 'id'), (SELECT MAX(id) FROM shop_order.items));
SELECT setval(pg_get_serial_sequence('shop_order.after_sales_cases', 'id'), (SELECT MAX(id) FROM shop_order.after_sales_cases));
SELECT setval(pg_get_serial_sequence('shop_order.after_sales_evidence', 'id'), (SELECT MAX(id) FROM shop_order.after_sales_evidence));
SELECT setval(pg_get_serial_sequence('shop_payment.payment', 'id'), (SELECT MAX(id) FROM shop_payment.payment));
SELECT setval(pg_get_serial_sequence('shop_ai.chat_messages', 'id'), (SELECT MAX(id) FROM shop_ai.chat_messages));
SELECT setval(pg_get_serial_sequence('shop_ai.behavior_events', 'id'), (SELECT MAX(id) FROM shop_ai.behavior_events));
SELECT setval(pg_get_serial_sequence('shop_ai.agent_memories', 'id'), (SELECT MAX(id) FROM shop_ai.agent_memories));
SELECT setval(pg_get_serial_sequence('shop_ai.recommendation_audits', 'id'), (SELECT MAX(id) FROM shop_ai.recommendation_audits));
SELECT setval(pg_get_serial_sequence('shop_ai.recommendation_audit_items', 'id'), (SELECT MAX(id) FROM shop_ai.recommendation_audit_items));
