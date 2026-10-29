package com.shop.payment.service;

import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentStatus;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled job — chay dinh ky de:
 *   1. Kiem tra cac Payment PENDING qua 5 phut → goi MoMo query de sync.
 *   2. Danh dau EXPIRED cho Payment PENDING qua 15 phut (timeout cua MoMo).
 * <p>
 * Day la pattern reliability quan trong trong microservice:
 *   IPN co the bi mat (mat mang, server tam down, ...) → polling la backup.
 */
@ApplicationScoped
public class PaymentScheduler {

    private static final Logger LOG = Logger.getLogger(PaymentScheduler.class);

    @Inject
    PaymentService paymentService;

    /**
     * Chay moi 2 phut.
     * Cu phap cron: "every 2m" tuong duong "0 0/2 * * * ?"
     */
    @Scheduled(every = "2m", identity = "payment-sync-job")
    void syncPendingPayments() {
        // Lay cac payment PENDING tao tu hon 1 phut (de IPN co thoi gian den truoc)
        Instant cutoff = Instant.now().minus(1, ChronoUnit.MINUTES);
        List<Payment> pending = Payment.find(
                "status = ?1 and createdAt < ?2",
                PaymentStatus.PENDING, cutoff).list();

        if (pending.isEmpty()) {
            return;
        }

        LOG.infof("Sync %d payment PENDING voi MoMo", pending.size());
        for (Payment p : pending) {
            try {
                paymentService.queryAndSync(p.id);
            } catch (Exception e) {
                LOG.warnf("Khong sync duoc payment %d: %s", p.id, e.getMessage());
            }
        }
    }

    /**
     * Chay moi 5 phut — danh dau EXPIRED cac payment PENDING qua 15 phut.
     * MoMo co timeout 15p, sau do giao dich coi nhu het han.
     */
    @Scheduled(every = "5m", identity = "payment-expire-job")
    @Transactional
    void expireOldPayments() {
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);
        // Panache update: SET ... WHERE ...
        // Dung named parameter cho ro rang
        long expired = Payment.update(
                "status = :newStatus, message = :msg "
                        + "where status = :oldStatus and createdAt < :cutoff",
                io.quarkus.panache.common.Parameters
                        .with("newStatus", PaymentStatus.EXPIRED)
                        .and("msg", "Het han thanh toan (qua 15 phut)")
                        .and("oldStatus", PaymentStatus.PENDING)
                        .and("cutoff", cutoff)
                        .map());

        if (expired > 0) {
            LOG.infof("Da danh dau %d payment EXPIRED", expired);
        }
    }
}
