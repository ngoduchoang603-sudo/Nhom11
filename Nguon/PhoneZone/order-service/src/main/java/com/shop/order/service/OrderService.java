package com.shop.order.service;

import com.shop.order.client.ProductClient;
import com.shop.order.client.UserClient;
import com.shop.order.entity.Cart;
import com.shop.order.entity.Item;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.Coupon;
import com.shop.order.entity.OrderTimelineEvent;
import com.shop.order.resource.CouponResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * OrderService — xu ly dat hang.
 * <p>
 * Day la noi the hien ro nhat dac trung microservice:
 *   1. Lay du lieu user tu user-service (qua UserClient).
 *   2. Lay/giam stock san pham tu product-service (qua ProductClient).
 *   3. Luu Order vao PostgreSQL cua minh.
 * <p>
 * LUU Y: Trong he thong thuc te, neu mot trong cac service that bai sau khi
 * service nay da commit, ta can pattern "Saga" (compensating transaction).
 * O day demo de don gian, dung distributed transaction "best-effort":
 * Order luu xong roi moi goi tru kho — neu tru kho fail, danh dau order
 * la CANCELLED va log lai.
 */
@ApplicationScoped
public class OrderService {

    private static final Logger LOG = Logger.getLogger(OrderService.class);

    @RestClient
    ProductClient productClient;

    @RestClient
    UserClient userClient;

    @Inject
    CartService cartService;

    @Inject
    OrderEventPublisher orderEventPublisher;

    public List<Order> getAll() {
        return Order.listAll();
    }

    public List<Order> getPage(int page, int size) {
        return Order.findAll(Sort.descending("date"))
                .page(Page.of(Math.max(0, page), Math.min(100, Math.max(1, size)))).list();
    }

    public Order getById(Long id) {
        Order order = Order.findById(id);
        if (order == null) {
            throw new NotFoundException("Order khong ton tai: " + id);
        }
        return order;
    }

    public List<Order> getByUser(Long userId) {
        return Order.findByUser(userId);
    }

    /**
     * Tao don hang tu gio hang cua user.
     * Truyen them paymentMethod de luu phuong thuc thanh toan vao Order.
     */
    @Transactional
    public Order checkout(Long userId, String deliveryAddress, String paymentMethod, String couponCode) {
        if (userId == null) {
            throw new WebApplicationException("Không xác định được người dùng đặt hàng", Response.Status.BAD_REQUEST);
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new WebApplicationException("Vui lòng nhập địa chỉ giao hàng", Response.Status.BAD_REQUEST);
        }
        if (paymentMethod == null || !(paymentMethod.equals("COD") || paymentMethod.equals("MOMO")
                || paymentMethod.equals("VNPAY") || paymentMethod.equals("ZALOPAY"))) {
            throw new WebApplicationException("Phương thức thanh toán không hợp lệ", Response.Status.BAD_REQUEST);
        }
        // 1. Lay thong tin user (best-effort: neu fail van tao duoc order)
        String userName = "Khach hang #" + userId;
        try {
            Map<String, Object> user = userClient.getUser(userId);
            if (user != null) {
                Object fullName = user.get("fullName");
                Object username = user.get("username");
                if (fullName != null && !fullName.toString().isBlank()) {
                    userName = fullName.toString();
                } else if (username != null) {
                    userName = username.toString();
                }
            }
        } catch (Exception e) {
            LOG.warnf("Khong lay duoc thong tin user %d: %s. Van tiep tuc dat hang.",
                    userId, e.getMessage());
            // Khong throw -- van cho phep dat hang
        }

        // 2. Lay gio hang
        List<Cart> cartItems = Cart.findByUser(userId);
        if (cartItems.isEmpty()) {
            throw new WebApplicationException(
                    "Gio hang trong, khong the dat hang",
                    Response.Status.BAD_REQUEST);
        }

        // 2.5. Kiem tra ton kho TRUOC khi tao don hang
        StringBuilder stockErrors = new StringBuilder();
        for (Cart cart : cartItems) {
            try {
                if (cart.variantId != null) {
                    var variants = productClient.getVariants(cart.productId);
                    var variant = variants.stream()
                            .filter(v -> v.get("id") instanceof Number n && n.longValue() == cart.variantId)
                            .findFirst().orElse(null);
                    long vStock = variant != null && variant.get("stock") instanceof Number number ? number.longValue() : 0L;
                    if (vStock < cart.quantity) {
                        stockErrors.append(String.format("• %s: yêu cầu %d, còn lại %d\n",
                                cart.productName != null ? cart.productName : cart.productId,
                                cart.quantity, vStock));
                    }
                } else {
                    Map<String, Object> product = productClient.getProduct(cart.productId);
                    long pStock = product != null && product.get("stock") instanceof Number number ? number.longValue() : 0L;
                    if (pStock < cart.quantity) {
                        stockErrors.append(String.format("• %s: yêu cầu %d, còn lại %d\n",
                                cart.productName != null ? cart.productName : cart.productId,
                                cart.quantity, pStock));
                    }
                }
            } catch (Exception e) {
                LOG.warnf("Khong kiem tra duoc ton kho cho %s: %s", cart.productId, e.getMessage());
                stockErrors.append(String.format("• %s: không thể kiểm tra tồn kho, vui lòng thử lại sau\n",
                        cart.productName != null ? cart.productName : cart.productId));
            }
        }
        if (stockErrors.length() > 0) {
            throw new WebApplicationException(
                    "Một hoặc nhiều sản phẩm không đủ hàng:\n" + stockErrors.toString(),
                    Response.Status.CONFLICT);
        }

        // 3. Tao Order
        Order order = new Order();
        order.userId = userId;
        order.userName = userName;
        order.deliveryAddress = deliveryAddress;
        order.paymentMethod = paymentMethod != null ? paymentMethod : "COD";
        order.date = Instant.now();
        order.status = OrderStatus.PENDING;

        long totalPrice = 0;
        long totalQuantity = 0;

        // 4. Tao Items + tinh tien (lay gia hien tai tu product-service neu duoc)
        for (Cart cart : cartItems) {
            long price = cart.productPrice != null ? cart.productPrice : 0L;
            long discount = 0L;
            try {
                Map<String, Object> product = productClient.getProduct(cart.productId);
                if (product != null) {
                    Object pPrice = product.get("price");
                    Object pDiscount = product.get("discount");
                    if (pPrice != null) price = ((Number) pPrice).longValue();
                    if (pDiscount != null) discount = ((Number) pDiscount).longValue();
                }
            } catch (Exception e) {
                LOG.warnf("Khong lay duoc gia product %s: %s. Dung gia cached trong cart.",
                        cart.productId, e.getMessage());
            }
            long itemTotal = Math.max(0L, price - discount) * cart.quantity;

            Item item = new Item();
            item.productId = cart.productId;
            item.variantId = cart.variantId;
            item.variantLabel = cart.variantLabel;
            item.productName = cart.productName;
            item.productImage = cart.productImage;
            item.quantity = cart.quantity;
            item.price = Math.max(0L, price - discount);
            item.discount = discount;
            item.total = itemTotal;
            item.order = order;
            order.items.add(item);

            totalPrice += itemTotal;
            totalQuantity += cart.quantity;
        }

        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = Coupon.findByCode(couponCode.trim());
            long couponDiscount = CouponResource.calculate(coupon, totalPrice);
            if (couponDiscount < 0) throw new WebApplicationException("Mã giảm giá không hợp lệ, hết hạn hoặc chưa đủ điều kiện", Response.Status.BAD_REQUEST);
            order.couponCode = coupon.code;
            order.couponDiscount = couponDiscount;
            totalPrice -= couponDiscount;
            coupon.usedCount = (coupon.usedCount == null ? 0 : coupon.usedCount) + 1;
        }
        order.total = totalPrice;
        order.quantity = totalQuantity;
        order.persist();
        recordTimeline(order, OrderStatus.PENDING, "Đơn hàng đã được tạo và đang chờ xác nhận.");
        orderEventPublisher.orderCreated(order);

        // 5. Goi sang product-service de tru kho (best-effort, khong huy don neu fail)
        for (Item item : order.items) {
            try {
                if (item.variantId != null) productClient.decreaseVariantStock(item.productId, item.variantId, item.quantity);
                else productClient.decreaseStock(item.productId, item.quantity);
            } catch (Exception e) {
                LOG.warnf("Khong tru duoc kho cho product %s: %s (van giu order)",
                        item.productId, e.getMessage());
                // Khong throw -- order van duoc tao, admin co the xu ly thu cong sau
            }
        }

        // 6. Xoa gio hang
        try {
            cartService.clearCart(userId);
        } catch (Exception e) {
            LOG.warnf("Khong xoa duoc gio hang user %d: %s", userId, e.getMessage());
        }

        return order;
    }

    /** Backward-compat overload (khong co paymentMethod) */
    @Transactional
    public Order checkout(Long userId, String deliveryAddress) {
        return checkout(userId, deliveryAddress, "COD", null);
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = getById(orderId);
        if (order.status == newStatus) return order;
        order.status = newStatus;
        recordTimeline(order, newStatus, timelineNote(newStatus));
        return order;
    }

    private void recordTimeline(Order order, OrderStatus status, String note) {
        OrderTimelineEvent event = new OrderTimelineEvent();
        event.orderId = order.id; event.status = status; event.eventTime = Instant.now(); event.note = note; event.persist();
    }
    private String timelineNote(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Đơn hàng đang chờ xác nhận.";
            case CONFIRMED -> "Cửa hàng đã xác nhận đơn hàng.";
            case PACKING -> "Cửa hàng đang đóng gói đơn hàng.";
            case READY_TO_SHIP -> "Đơn hàng đã được bàn giao cho đơn vị vận chuyển.";
            case SHIPPING -> "Đơn hàng đã được bàn giao để vận chuyển.";
            case OUT_FOR_DELIVERY -> "Shipper đang giao hàng đến địa chỉ nhận.";
            case DELIVERED -> "Đơn hàng đã giao thành công.";
            case CANCELLED -> "Đơn hàng đã bị hủy.";
        };
    }
}
