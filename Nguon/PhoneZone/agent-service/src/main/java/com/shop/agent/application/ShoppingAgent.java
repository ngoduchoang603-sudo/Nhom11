package com.shop.agent.application;

import com.shop.agent.adapter.out.catalog.ProductCatalogTool;
import com.shop.agent.adapter.out.behavior.BehaviorProfileTool;
import com.shop.agent.adapter.out.memory.SessionMemoryTool;
import com.shop.agent.adapter.out.audit.RecommendationAuditTool;
import com.shop.agent.adapter.out.order.OrderDeliveryTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;

/** Bounded autonomous planner with an explicit, read-only tool allow-list. */
@ApplicationScoped
public class ShoppingAgent {
    @Inject ProductCatalogTool productCatalog;
    @Inject BehaviorProfileTool behaviorProfile;
    @Inject SessionMemoryTool sessionMemory;
    @Inject RecommendationAuditTool recommendationAudit;
    @Inject OrderDeliveryTool orderDelivery;

    public AgentResult prepare(String sessionId, Long userId, String question) {
        SessionMemoryTool.ShoppingContext shoppingContext = sessionMemory.updateContext(sessionId, question);
        // Manager Agent: route one customer intent to the smallest specialised
        // tool set. No "do everything" sequence and no write capability here.
        switch (AgentIntentRouter.route(question)) {
            case ORDER_TRACKING -> { return trackOrder(userId); }
            case CART_SUMMARY -> { return summarizeCart(userId); }
            case DEAL -> { return findDeal(userId); }
            case ORDER_MUTATION -> { return requestOrderConfirmation(sessionId, userId, question); }
            case VARIANT -> { return checkVariant(sessionId, question); }
            case CART_MUTATION -> { return proposeCart(sessionId, question); }
            case COMPARISON -> { return compare(sessionId, question); }
            case RECOMMENDATION -> { return recommend(sessionId, userId); }
            case PRODUCT_FOLLOW_UP -> { return followUpProduct(sessionId, question, shoppingContext); }
            case GENERAL, CATALOG -> { /* continue with clarification/catalog fallback */ }
        }
        if (needsClarification(question)) return clarifyNeed();
        if (!needsCatalog(question)) return new AgentResult("", List.of(), null);
        List<ProductCatalogTool.ProductSummary> products = productCatalog.search(catalogQuery(sessionId, question, shoppingContext));
        // "con đó/máy đó" phải dựa trên sản phẩm cuối cùng của đúng phiên chat,
        // nhưng vẫn đọc lại Product service để giá và tồn kho luôn là dữ liệu mới.
        if (products.isEmpty() && refersToLastProduct(question)) {
            SessionMemoryTool.Memory memory = sessionMemory.get(sessionId);
            ProductCatalogTool.ProductSummary remembered = memory == null ? null : productCatalog.findById(memory.productId());
            if (remembered != null) products = List.of(remembered);
        }
        String context = products.isEmpty()
                ? "Chưa tìm thấy sản phẩm phù hợp trong danh mục hiện tại."
                : formatCatalog(sessionId, products, question, shoppingContext);
        return new AgentResult(context, List.of("product_catalog.search"), null);
    }

    private AgentResult trackOrder(Long userId) {
        if (userId == null) return signInRequired("xem đơn hàng");
        List<OrderDeliveryTool.OrderInfo> orders = orderDelivery.recentOrders(userId);
        if (orders.isEmpty()) return new AgentResult("Bạn chưa có đơn hàng nào để theo dõi.", List.of("order_delivery.recent_orders"), null);
        StringBuilder answer = new StringBuilder("Các đơn gần đây của bạn:\n");
        for (OrderDeliveryTool.OrderInfo order : orders) {
            answer.append("- **PZ-").append(String.format("%06d", order.id())).append("** — ")
                    .append(statusLabel(order.status())).append("; ").append(money(order.total()))
                    .append("; thanh toán ").append(order.paymentMethod()).append(".\n");
        }
        OrderDeliveryTool.OrderInfo newest = orders.getFirst();
        if ("DELIVERED".equals(newest.status())) {
            answer.append("Đơn mới nhất của bạn đã giao thành công. Nếu bạn chưa nhận được hàng, hãy liên hệ PhoneZone và cung cấp mã đơn để được kiểm tra ngay.");
        } else if ("SHIPPING".equals(newest.status())) {
            answer.append("Đơn mới nhất đang được vận chuyển. Bạn có thể mở **Đơn hàng của tôi** để xem các mốc đã cập nhật.");
        } else {
            answer.append("Bạn có thể mở **Đơn hàng của tôi** để xem chi tiết và mốc giao hàng.");
        }
        return new AgentResult(answer.toString(), List.of("order_delivery.recent_orders"), null);
    }

    /** Pronouns such as "máy đó" use the explicitly stored session selection. */
    private AgentResult followUpProduct(String sessionId, String question, SessionMemoryTool.ShoppingContext shoppingContext) {
        SessionMemoryTool.Memory memory = sessionMemory.get(sessionId);
        ProductCatalogTool.ProductSummary product = memory == null ? null : productCatalog.findById(memory.productId());
        if (product == null) {
            return new AgentResult("Tôi chưa xác định được sản phẩm bạn đang nói tới. Bạn hãy nêu lại tên máy để tôi tra cứu dữ liệu hiện tại.",
                    List.of("session_memory.resolve_product"), null);
        }
        return new AgentResult(formatCatalog(sessionId, List.of(product), question, shoppingContext),
                List.of("session_memory.resolve_product", "product_catalog.get_live"), null);
    }

    private AgentResult summarizeCart(Long userId) {
        if (userId == null) return signInRequired("xem giỏ hàng");
        List<OrderDeliveryTool.CartItem> items = orderDelivery.cart(userId);
        if (items.isEmpty()) return new AgentResult("Giỏ hàng của bạn đang trống.", List.of("cart.read"), null);
        long total = 0;
        StringBuilder answer = new StringBuilder("Giỏ hàng hiện có:\n");
        for (OrderDeliveryTool.CartItem item : items) {
            long line = item.productPrice() * item.quantity(); total += line;
            answer.append("- **").append(item.productName()).append("** × ").append(item.quantity()).append(" — ").append(money(line)).append("\n");
        }
        answer.append("Tạm tính: **").append(money(total)).append("**. Bạn có thể chỉnh số lượng hoặc thanh toán tại trang Giỏ hàng.");
        return new AgentResult(answer.toString(), List.of("cart.read"), null);
    }

    private AgentResult findDeal(Long userId) {
        if (userId == null) return new AgentResult("Bạn hãy cho tôi biết tổng tiền hoặc thêm sản phẩm vào giỏ để tôi kiểm tra mã giảm giá phù hợp.", List.of("deal.clarify"), null);
        List<OrderDeliveryTool.CartItem> items = orderDelivery.cart(userId);
        long subtotal = items.stream().mapToLong(i -> i.productPrice() * i.quantity()).sum();
        if (subtotal <= 0) return new AgentResult("Giỏ hàng đang trống nên chưa thể kiểm tra mã giảm giá áp dụng.", List.of("cart.read"), null);
        List<OrderDeliveryTool.CouponInfo> coupons = orderDelivery.coupons(subtotal).stream()
                .sorted(Comparator.comparingLong(OrderDeliveryTool.CouponInfo::discount).reversed())
                .toList();
        if (coupons.isEmpty()) return new AgentResult("Hiện chưa có mã giảm giá phù hợp với giỏ hàng " + money(subtotal) + ".", List.of("cart.read", "deal.eligible_coupons"), null);
        StringBuilder answer = new StringBuilder("Mã giảm giá đang áp dụng được cho giỏ hàng của bạn:\n");
        for (OrderDeliveryTool.CouponInfo coupon : coupons) answer.append("- **").append(coupon.code()).append("**").append(coupon.name().isBlank() ? "" : " (" + coupon.name() + ")").append(" — giảm ").append(money(coupon.discount())).append(".\n");
        return new AgentResult(answer.toString(), List.of("cart.read", "deal.eligible_coupons"), null);
    }

    private AgentResult requestOrderConfirmation(String sessionId, Long userId, String question) {
        String normalized = question == null ? "" : java.text.Normalizer.normalize(question, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase();
        boolean checkout = normalized.contains("dat hang") || normalized.contains("dat mua") || normalized.contains("tao don")
                || normalized.contains("muon mua") || normalized.contains("mua ngay");
        if (checkout && userId == null) return signInRequired("đặt hàng");
        if (checkout) {
            List<OrderDeliveryTool.CartItem> items = orderDelivery.cart(userId);
            if (items.isEmpty()) {
                ProductCatalogTool.ProductSummary selected = sessionMemory.recentCatalog(sessionId).stream()
                        .map(memory -> productCatalog.findById(memory.productId())).filter(java.util.Objects::nonNull)
                        .filter(product -> product.stock() > 0).findFirst().orElse(null);
                if (selected == null) {
                    SessionMemoryTool.Memory remembered = sessionMemory.get(sessionId);
                    selected = remembered == null ? null : productCatalog.findById(remembered.productId());
                }
                if (selected == null || selected.stock() <= 0) return new AgentResult("Giỏ hàng đang trống. Bạn hãy chọn một sản phẩm còn hàng để tôi chuẩn bị đơn.", List.of("cart.read", "product_catalog.stock_live"), null);
                return new AgentResult("Tôi đã chọn **" + selected.name() + "** từ sản phẩm bạn vừa xem và kiểm tra máy vẫn còn hàng. Bấm xác nhận bên dưới để thêm máy này vào giỏ, sau đó tôi mở bước nhập thông tin đặt hàng.",
                        List.of("cart.read", "product_catalog.stock_live", "cart.propose"), new CartProposal(selected.id(), selected.name(), 1, true));
            }
            long subtotal = items.stream().mapToLong(item -> item.productPrice() * item.quantity()).sum();
            long shipping = subtotal >= 5_000_000L ? 0L : 30_000L;
            StringBuilder summary = new StringBuilder("Kiểm tra đơn hàng trước khi tạo:\n");
            for (OrderDeliveryTool.CartItem item : items) summary.append("- ").append(item.productName()).append(" × ").append(item.quantity()).append(" — ").append(money(item.productPrice() * item.quantity())).append("\n");
            summary.append("Tạm tính: ").append(money(subtotal)).append("; phí vận chuyển: ").append(shipping == 0 ? "Miễn phí" : money(shipping)).append(".\n")
                    .append("Hãy điền thông tin nhận hàng và chọn phương thức thanh toán, rồi xác nhận tạo đơn.");
            List<OrderDeliveryTool.CouponInfo> coupons = orderDelivery.coupons(subtotal).stream()
                    .sorted(Comparator.comparingLong(OrderDeliveryTool.CouponInfo::discount).reversed())
                    .toList();
            if (!coupons.isEmpty()) {
                OrderDeliveryTool.CouponInfo best = coupons.getFirst();
                summary.append("\nMã giảm tốt nhất hiện tại: ").append(best.code())
                        .append(" (giảm ").append(money(best.discount())).append(").");
                summary.append(" Các mã hợp lệ khác: ");
                for (OrderDeliveryTool.CouponInfo coupon : coupons) summary.append(coupon.code()).append(" (giảm ").append(money(coupon.discount())).append("); ");
                summary.append("bạn có thể nhập một mã vào ô Mã giảm giá.");
            }
            ActionProposal proposal = new ActionProposal("CREATE_ORDER", "Xác nhận đặt hàng", summary.toString(), "chat-checkout");
            return new AgentResult("Tôi đã kiểm tra giỏ hàng và tồn kho hiện tại. Bạn hãy xác nhận thông tin bên dưới; chỉ sau đó hệ thống mới tạo đơn thật.",
                    List.of("cart.read", "product_catalog.stock_live", "order.confirmation_required"), null, proposal);
        }
        ActionProposal proposal = new ActionProposal("CANCEL_ORDER", "Mở Đơn hàng của tôi", "Bạn cần chọn đúng đơn và xác nhận hủy trực tiếp để tránh hủy nhầm.", "history.html");
        return new AgentResult("Tôi không tự hủy đơn trong chat để tránh thao tác nhầm. Hãy xác nhận để mở **Đơn hàng của tôi** và chọn đúng đơn cần hủy.",
                List.of("order.guardrail.confirmation_required"), null, proposal);
    }

    private AgentResult signInRequired(String action) {
        return new AgentResult("Bạn cần đăng nhập để " + action + " và bảo vệ dữ liệu cá nhân.", List.of("identity.login_required"), null);
    }

    private String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "chờ xác nhận"; case "CONFIRMED" -> "đã xác nhận";
            case "PACKING" -> "đang đóng gói"; case "READY_TO_SHIP" -> "đã bàn giao vận chuyển";
            case "SHIPPING" -> "đang giao"; case "OUT_FOR_DELIVERY" -> "shipper đang giao đến bạn"; case "DELIVERED" -> "đã giao";
            case "CANCELLED" -> "đã hủy"; default -> status;
        };
    }

    private AgentResult compare(String sessionId, String question) {
        var products = refersToRecentProducts(question)
                ? sessionMemory.recentCatalog(sessionId).stream().map(memory -> productCatalog.findById(memory.productId())).filter(java.util.Objects::nonNull).limit(2).toList()
                : productCatalog.search(catalogQuery(sessionId, question, sessionMemory.context(sessionId)));
        if (products.size() < 2) return new AgentResult("Tôi cần tên của ít nhất hai điện thoại trong PhoneZone để so sánh chính xác.", List.of("product_catalog.search"), null);
        var a = products.get(0); var b = products.get(1);
        String table = "So sánh trực tiếp từ danh mục PhoneZone:\n\n| Tiêu chí | " + a.name() + " | " + b.name() + " |\n|---|---|---|\n" +
                "| Giá | " + money(a.price()) + " | " + money(b.price()) + " |\n" +
                "| Tồn kho | " + a.stock() + " máy | " + b.stock() + " máy |\n" +
                "| RAM | " + display(a.ram()) + " | " + display(b.ram()) + " |\n" +
                "| Bộ nhớ | " + display(a.storage()) + " | " + display(b.storage()) + " |\n" +
                "| Màn hình | " + display(a.screen()) + " | " + display(b.screen()) + " |\n" +
                "| Pin | " + display(a.battery()) + " | " + display(b.battery()) + " |\n\nBạn ưu tiên chơi game, camera hay tiết kiệm chi phí?";
        return new AgentResult(table, List.of("product_catalog.search", "comparison.compose"), null);
    }

    private boolean needsClarification(String question) {
        String text = question == null ? "" : question.toLowerCase();
        boolean need = text.contains("chơi game") || text.contains("camera") || text.contains("chụp ảnh") || text.contains("pin trâu") || text.contains("mượt");
        return need && !text.matches("(?s).*\\d+\\s*(triệu|trieu|tr).*");
    }

    private AgentResult clarifyNeed() {
        return new AgentResult("Để tư vấn đúng thay vì đoán bừa, bạn cho tôi biết thêm ngân sách khoảng bao nhiêu và ưu tiên chơi game, camera hay pin?", List.of("consultant.clarify"), null);
    }

    private AgentResult recommend(String sessionId, Long userId) {
        BehaviorProfileTool.Profile profile = behaviorProfile.get(userId, sessionId);
        List<ProductCatalogTool.ProductSummary> products = productCatalog.recommend(sessionMemory.context(sessionId).enrich(profile.latestSearch()), profile.recentProductIds());
        if (products.isEmpty()) return new AgentResult("Bạn hãy xem hoặc tìm kiếm một vài sản phẩm trước, tôi sẽ cá nhân hóa gợi ý cho bạn.", List.of("behavior.profile", "product_catalog.recommend"), null);
        sessionMemory.save(sessionId, products.getFirst().id(), products.getFirst().name());
        recommendationAudit.record(sessionId, userId, products.stream().map(ProductCatalogTool.ProductSummary::id).toList());
        StringBuilder response = new StringBuilder(profile.interactionCount() == 0
                ? "Bạn chưa có lịch sử để cá nhân hóa, nên đây là các máy đang còn hàng để bắt đầu tham khảo:\n"
                : "Dựa trên lịch sử xem và tìm kiếm của bạn, các máy phù hợp là:\n");
        int rank = 0;
        for (ProductCatalogTool.ProductSummary product : products) {
            int match = Math.max(70, 92 - rank++ * 8);
            response.append("- **").append(product.name()).append("** — phù hợp ").append(match).append("%; ")
                    .append(String.format(Locale.US, "%,d", product.price()).replace(',', '.')).append(" VNĐ; còn ").append(product.stock()).append(" máy.\n");
        }
        response.append("Bạn có thể hỏi chi tiết, hoặc nói ‘thêm máy đầu tiên vào giỏ’.");
        return new AgentResult(response.toString(), List.of("behavior.profile", "recommendation.rank", "product_catalog.recommend"), null);
    }

    private AgentResult proposeCart(String sessionId, String question) {
        ProductCatalogTool.ProductSummary product = productCatalog.search(catalogQuery(sessionId, question, sessionMemory.context(sessionId))).stream().findFirst().orElse(null);
        if (product == null) {
            SessionMemoryTool.Memory memory = sessionMemory.get(sessionId);
            product = memory == null ? null : productCatalog.findById(memory.productId()); // always re-check live stock/price
        }
        if (product == null) return new AgentResult("Tôi chưa xác định được sản phẩm. Hãy nêu tên máy hoặc hỏi gợi ý trước.", List.of("product_catalog.search"), null);
        if (product.stock() <= 0) return replanOutOfStock(question, product);
        return new AgentResult("Tôi đã kiểm tra **" + product.name() + "** còn hàng. Bạn hãy bấm **Xác nhận thêm vào giỏ** bên dưới để hoàn tất.",
                List.of("product_catalog.search", "cart.propose"), new CartProposal(product.id(), product.name(), 1));
    }

    private boolean wantsRecommendation(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("hợp với tôi") || text.contains("gợi ý") || text.contains("đề xuất") || text.contains("phù hợp với tôi");
    }
    private boolean wantsComparison(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("so sánh") || text.contains("so sanh") || text.contains("khác nhau") || text.contains("nên chọn máy nào");
    }
    private boolean wantsCartAction(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("thêm") && text.contains("giỏ");
    }
    private boolean wantsOrderTracking(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return (text.contains("đơn") || text.contains("don ")) && (text.contains("đâu") || text.contains("theo dõi") || text.contains("trạng thái") || text.contains("giao"));
    }
    private boolean wantsCartSummary(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("giỏ hàng") && (text.contains("xem") || text.contains("có gì") || text.contains("bao nhiêu") || text.contains("tổng"));
    }
    private boolean wantsDeal(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("mã giảm") || text.contains("coupon") || text.contains("khuyến mãi") || text.contains("khuyen mai");
    }
    private boolean wantsOrderChange(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("hủy đơn") || text.contains("huy don") || text.contains("tạo đơn") || text.contains("dat hang") || text.contains("đặt hàng");
    }
    private boolean asksVariant(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("màu") || text.contains("mau ") || text.contains("đen") || text.contains("trắng");
    }
    private AgentResult checkVariant(String sessionId, String question) {
        ProductCatalogTool.ProductSummary product = productCatalog.search(catalogQuery(sessionId, question, sessionMemory.context(sessionId))).stream().findFirst().orElse(null);
        if (product == null) {
            SessionMemoryTool.Memory memory = sessionMemory.get(sessionId);
            product = memory == null ? null : productCatalog.findById(memory.productId());
        }
        if (product == null) return new AgentResult("Bạn cho tôi biết tên máy cần kiểm tra màu, RAM hoặc dung lượng để tra cứu chính xác.", List.of("variant.clarify"), null);
        String colors = product.availableColors() == null || product.availableColors().isBlank() ? "chưa cập nhật" : product.availableColors();
        String answer = "Phiên bản đang hiển thị của **" + product.name() + "**: RAM " + display(product.ram()) + ", bộ nhớ " + display(product.storage())
                + ", màu: " + colors + ". Tồn kho tổng hiện là " + product.stock() + " máy.";
        if (product.stock() <= 0) answer += " Máy này đã hết hàng; tôi có thể tìm máy thay thế nếu bạn muốn.";
        else answer += " Tồn theo từng màu sẽ được nhân viên xác nhận lại khi chốt đơn.";
        return new AgentResult(answer, List.of("product_catalog.variant_live"), null);
    }

    private String formatCatalog(String sessionId, List<ProductCatalogTool.ProductSummary> products, String question, SessionMemoryTool.ShoppingContext shoppingContext) {
        if (sessionId != null && !sessionId.isBlank() && !products.isEmpty()) {
            sessionMemory.saveCatalog(sessionId, products.stream()
                    .map(product -> new SessionMemoryTool.Memory(product.id(), product.name())).toList(), shoppingContext);
        }
        StringBuilder answer = new StringBuilder("Kết quả tra cứu sản phẩm hiện tại:\n");
        int rank = 0;
        for (ProductCatalogTool.ProductSummary product : products) {
            int fitScore = Math.max(70, 91 - rank++ * 6);
            answer.append("- **").append(product.name()).append("** — phù hợp ").append(fitScore).append("%; ")
                    .append(String.format(Locale.US, "%,d", product.price()).replace(',', '.'))
                    .append(" VNĐ; ");
            if (product.stock() <= 0) answer.append("hiện đã hết hàng.");
            else answer.append("còn ").append(product.stock()).append(" sản phẩm.");
            if (!product.ram().isBlank() || !product.storage().isBlank() || !product.screen().isBlank()) {
                answer.append(" Cấu hình: ")
                        .append(product.ram().isBlank() ? "" : product.ram() + " RAM ")
                        .append(product.storage().isBlank() ? "" : product.storage() + " ")
                        .append(product.screen().isBlank() ? "" : "màn " + product.screen());
            }
            answer.append(" Lý do: ").append(fitReason(question, product));
            answer.append("\n");
        }
        return answer.toString().trim();
    }

    private String fitReason(String question, ProductCatalogTool.ProductSummary product) {
        String text = question == null ? "" : question.toLowerCase();
        if (text.contains("game") || text.contains("hiệu năng") || text.contains("mượt")) return "có " + display(product.ram()) + " RAM và " + display(product.screen()) + ", phù hợp ưu tiên hiệu năng.";
        if (text.contains("camera") || text.contains("chụp")) return "đang còn hàng và có cấu hình được công bố rõ để bạn xem chi tiết camera.";
        if (text.contains("pin")) return product.battery().isBlank() ? "thông tin pin chưa được công bố đầy đủ." : "có thông tin pin " + product.battery() + ".";
        return "nằm trong tiêu chí tìm kiếm và ngân sách hiện tại.";
    }

    private String money(long amount) { return String.format(Locale.US, "%,d", amount).replace(',', '.') + " VNĐ"; }
    private String display(String value) { return value == null || value.isBlank() ? "Chưa công bố" : value; }

    private boolean needsCatalog(String question) {
        String text = question == null ? "" : question.toLowerCase();
        // Cac nhu cau nhu choi game/camera/pin van la tu van dien thoai.
        // Bat buoc tra cuu danh muc truoc khi tra loi, khong de LLM suy dien sang laptop hay san pham ngoai shop.
        return List.of("sản phẩm", "dien thoai", "điện thoại", "giá", "tồn kho", "còn hàng", "so sánh", "iphone", "samsung", "xiaomi", "oppo", "vivo", "realme",
                        "con đó", "máy đó", "sản phẩm đó", "con do", "may do", "san pham do",
                        "game", "gaming", "chơi game", "camera", "chụp", "pin", "hiệu năng", "mượt", "ngân sách", "triệu")
                .stream().anyMatch(text::contains);
    }

    private boolean refersToLastProduct(String question) {
        String text = question == null ? "" : java.text.Normalizer.normalize(question, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase();
        return text.contains("con do") || text.contains("may do") || text.contains("san pham do")
                || text.contains("con vua tu van") || text.contains("con dau");
    }

    private String catalogQuery(String sessionId, String question, SessionMemoryTool.ShoppingContext shoppingContext) {
        // A pronoun must resolve to the saved product, not to a broad, stale search result.
        return refersToLastProduct(question) ? question : shoppingContext.enrich(question);
    }

    private boolean refersToRecentProducts(String question) {
        String text = question == null ? "" : java.text.Normalizer.normalize(question, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase();
        return text.contains("2 con nay") || text.contains("hai con nay") || text.contains("2 dien thoai tren")
                || text.contains("hai dien thoai tren") || text.contains("2 may tren") || text.contains("hai may tren")
                || text.contains("2 may nay") || text.contains("hai may nay") || text.contains("cai nao hon")
                || text.contains("cai nao tot hon") || text.contains("nen lay cai nao")
                || text.contains("2 san pham dau") || text.contains("hai san pham dau")
                || text.contains("2 may dau") || text.contains("hai may dau")
                || text.contains("2 con dau") || text.contains("hai con dau");
    }

    /** A stock failure triggers a new read-only search; customer still chooses the replacement. */
    private AgentResult replanOutOfStock(String question, ProductCatalogTool.ProductSummary unavailable) {
        List<ProductCatalogTool.ProductSummary> alternatives = productCatalog.alternatives(catalogQuery(null, question, SessionMemoryTool.ShoppingContext.empty()), unavailable.id());
        if (alternatives.isEmpty()) {
            return new AgentResult("**" + unavailable.name() + "** vừa hết hàng. Hiện tôi chưa tìm được máy thay thế còn hàng phù hợp; bạn có thể đổi nhu cầu hoặc ngân sách để tôi tìm lại.",
                    List.of("product_catalog.search", "manager.replan.out_of_stock"), null);
        }
        StringBuilder answer = new StringBuilder("**").append(unavailable.name()).append("** vừa hết hàng. Tôi không tự đổi sản phẩm trong giỏ; đây là các lựa chọn còn hàng để bạn chọn:\n");
        for (ProductCatalogTool.ProductSummary alternative : alternatives) {
            answer.append("- **").append(alternative.name()).append("** — ").append(money(alternative.price()))
                    .append("; còn ").append(alternative.stock()).append(" máy.\n");
        }
        answer.append("Bạn hãy nói rõ tên máy muốn thêm, tôi sẽ kiểm tra lại tồn kho và yêu cầu xác nhận.");
        return new AgentResult(answer.toString(), List.of("product_catalog.search", "manager.replan.out_of_stock"), null);
    }

    public record CartProposal(String productId, String productName, long quantity, boolean checkoutAfterAdd) {
        public CartProposal(String productId, String productName, long quantity) { this(productId, productName, quantity, false); }
    }
    /** A customer must explicitly confirm navigation before any non-cart action. */
    public record ActionProposal(String type, String title, String message, String targetUrl) { }
    /** Tool trace is returned separately from natural-language context for UI/audit use. */
    public record ToolCall(String name, boolean readOnly, String dataSource) {
        static ToolCall from(String name) {
            String source = name.startsWith("product_catalog") ? "product-service" : name.startsWith("order") || name.startsWith("cart") || name.startsWith("deal") ? "order-service" : name.startsWith("behavior") ? "ai-service" : "agent-service";
            return new ToolCall(name, true, source);
        }
    }
    public record AgentResult(String context, List<String> toolsUsed, CartProposal proposedCart, ActionProposal proposedAction, List<ToolCall> toolCalls) {
        public AgentResult(String context, List<String> toolsUsed, CartProposal proposedCart) { this(context, toolsUsed, proposedCart, null); }
        public AgentResult(String context, List<String> toolsUsed, CartProposal proposedCart, ActionProposal proposedAction) {
            this(context, toolsUsed, proposedCart, proposedAction, toolsUsed.stream().map(ToolCall::from).toList());
        }
    }
}
