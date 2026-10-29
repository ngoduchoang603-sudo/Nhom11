package com.shop.order.service;

import com.shop.order.entity.AfterSalesCase;
import com.shop.order.entity.AfterSalesStatus;
import com.shop.order.entity.Coupon;
import com.shop.order.entity.MonitorAlert;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.Cart;
import com.shop.order.entity.MonitorPriceSnapshot;
import com.shop.order.client.ProductClient;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Monitor sub-agent: detects issues, writes alerts, and never changes orders/coupons/cases. */
@ApplicationScoped
public class MonitorAgent {
    @RestClient ProductClient productClient;
    @Scheduled(every="15m", delayed="30s") @Transactional public void scheduledScan() { scan(); }

    @Transactional public int scan() {
        Instant now=Instant.now(); Set<String> open=new HashSet<>();
        for(Order order: Order.<Order>list("status = ?1 and date < ?2", OrderStatus.PENDING, now.minus(Duration.ofHours(2))))
            open.add(upsert("ORDER_PENDING_"+order.id,"ORDER_PENDING","HIGH","Đơn PZ-%06d chờ xác nhận quá 2 giờ.".formatted(order.id),now));
        for(AfterSalesCase c: AfterSalesCase.<AfterSalesCase>list("status in (?1,?2) and createdAt < ?3", AfterSalesStatus.CREATED, AfterSalesStatus.PENDING_APPROVAL, now.minus(Duration.ofHours(24))))
            open.add(upsert("AFTER_SALES_"+c.id,"AFTER_SALES","MEDIUM","Yêu cầu %s chờ xử lý quá 24 giờ.".formatted(c.caseCode),now));
        for(Coupon c: Coupon.<Coupon>list("active = true and endAt is not null and endAt between ?1 and ?2", now, now.plus(Duration.ofHours(48))))
            open.add(upsert("COUPON_"+c.id,"COUPON_EXPIRING","LOW","Mã %s sắp hết hạn trong 48 giờ.".formatted(c.code),now));
        for(Cart cart: Cart.<Cart>list("createdAt is not null and createdAt < ?1", now.minus(Duration.ofHours(24))))
            open.add(upsert("CART_ABANDONED_"+cart.id,"CART_ABANDONED","LOW","Giỏ hàng #%d của khách #%d chưa thanh toán quá 24 giờ.".formatted(cart.id,cart.userId),now));
        for(java.util.Map<String,Object> product: productClient.getProducts()) {
            String productId=String.valueOf(product.get("id")); String name=String.valueOf(product.getOrDefault("name","Sản phẩm"));
            long stock=product.get("stock") instanceof Number n?n.longValue():0L;
            long price=product.get("price") instanceof Number n?n.longValue():0L;
            if(stock <= 5) open.add(upsert("STOCK_LOW_"+productId,"STOCK_LOW","HIGH","%s chỉ còn %d máy trong kho.".formatted(name,stock),now));
            MonitorPriceSnapshot snapshot=MonitorPriceSnapshot.find("productId",productId).firstResult();
            if(snapshot==null){snapshot=new MonitorPriceSnapshot();snapshot.productId=productId;snapshot.productName=name;snapshot.lastPrice=price;snapshot.updatedAt=now;snapshot.persist();}
            else {if(price<snapshot.lastPrice) open.add(upsert("PRICE_DROP_"+productId,"PRICE_DROP","MEDIUM","%s giảm %,.0fđ (từ %,.0fđ xuống %,.0fđ).".formatted(name,(double)(snapshot.lastPrice-price),(double)snapshot.lastPrice,(double)price),now)); snapshot.productName=name;snapshot.lastPrice=price;snapshot.updatedAt=now;}
        }
        for(MonitorAlert alert: MonitorAlert.<MonitorAlert>list("resolved = false")) if(!open.contains(alert.fingerprint)){alert.resolved=true;alert.updatedAt=now;}
        return open.size();
    }
    private String upsert(String fingerprint,String type,String severity,String message,Instant now){
        MonitorAlert a=MonitorAlert.find("fingerprint",fingerprint).firstResult();
        if(a==null){
            a=new MonitorAlert();a.fingerprint=fingerprint;a.type=type;a.severity=severity;
            a.message=message;a.createdAt=now;a.updatedAt=now;a.resolved=false;a.persist();
        }
        a.resolved=false;a.updatedAt=now;a.message=message;return fingerprint;
    }
}
