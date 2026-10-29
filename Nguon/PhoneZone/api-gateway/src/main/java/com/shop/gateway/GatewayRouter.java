package com.shop.gateway;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;

/**
 * API Gateway — dinh tuyen request:
 *
 *   /api/users/**     ->  user-service (port 8081)
 *   /api/products/**  ->  product-service (port 8082)
 *   /api/carts/**     ->  order-service (port 8083)
 *   /api/orders/**    ->  order-service (port 8083)
 *
 * Day la gateway don gian dung Vert.x. Trong production thuc te
 * thuong dung Kong / Traefik / Spring Cloud Gateway / hoac Istio
 * (service mesh tren K8s).
 */
@ApplicationScoped
public class GatewayRouter {

    private static final Logger LOG = Logger.getLogger(GatewayRouter.class);

    @ConfigProperty(name = "shop.user-service.url")
    String userServiceUrl;

    @ConfigProperty(name = "shop.product-service.url")
    String productServiceUrl;

    @ConfigProperty(name = "shop.order-service.url")
    String orderServiceUrl;

    @ConfigProperty(name = "shop.payment-service.url")
    String paymentServiceUrl;

    @ConfigProperty(name = "shop.ai-service.url")
    String aiServiceUrl;

    @Inject
    Vertx vertx;

    void onStart(@Observes StartupEvent ev, Router router) {
        WebClient client = WebClient.create(vertx, new WebClientOptions().setKeepAlive(true));

        // Cho phep doc body cua request POST/PUT
        router.route().handler(BodyHandler.create());

        // Health check rieng cua gateway (KHONG dung "/" vi se chan static index.html)
        router.get("/gateway/status").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"service\":\"api-gateway\",\"status\":\"UP\"}"));

        // Dinh tuyen API toi cac microservice downstream
        router.route("/api/users/*").handler(ctx -> forward(client, ctx, userServiceUrl));
        router.route("/api/products/*").handler(ctx -> forward(client, ctx, productServiceUrl));
        router.route("/api/carts/*").handler(ctx -> forward(client, ctx, orderServiceUrl));
        router.route("/api/orders/*").handler(ctx -> forward(client, ctx, orderServiceUrl));
        router.route("/api/payments/*").handler(ctx -> forward(client, ctx, paymentServiceUrl));
        router.route("/chat").handler(ctx -> forward(client, ctx, aiServiceUrl));
        router.route("/behavior/*").handler(ctx -> forward(client, ctx, aiServiceUrl));
        router.route("/trade-ins/*").handler(ctx -> forward(client, ctx, aiServiceUrl));
        router.route("/agent-memory/*").handler(ctx -> forward(client, ctx, aiServiceUrl));
        router.route("/recommendation-audits/*").handler(ctx -> forward(client, ctx, aiServiceUrl));

        // Cac route khac (/, /index.html, /css/*, /js/*) duoc Quarkus tu serve
        // tu META-INF/resources/ (static file)

        LOG.info("API Gateway routes da dang ky.");
    }

    /** Forward request den service downstream */
    private void forward(WebClient client, io.vertx.ext.web.RoutingContext ctx, String targetBase) {
        String path = ctx.request().uri();
        URI target = URI.create(targetBase + path);
        LOG.infof("[%s] %s -> %s", ctx.request().method(), path, target);

        var req = client.requestAbs(ctx.request().method(), target.toString());
        // Copy headers (tru Host de Vert.x tu set)
        ctx.request().headers().forEach(h -> {
            if (!"host".equalsIgnoreCase(h.getKey())
                    && !"content-length".equalsIgnoreCase(h.getKey())) {
                req.putHeader(h.getKey(), h.getValue());
            }
        });

        // Body: chi gui cho POST/PUT/PATCH, GET/DELETE khong co body
        var body = ctx.body() != null ? ctx.body().buffer() : null;
        var sendFuture = (body != null && body.length() > 0)
                ? req.sendBuffer(body)
                : req.send();

        sendFuture.onComplete(ar -> {
            if (ar.succeeded()) {
                var resp = ar.result();
                ctx.response().setStatusCode(resp.statusCode());
                resp.headers().forEach(h -> {
                    // Bo qua transfer-encoding va content-length de Vert.x tu set
                    if (!"transfer-encoding".equalsIgnoreCase(h.getKey())
                            && !"content-length".equalsIgnoreCase(h.getKey())) {
                        ctx.response().putHeader(h.getKey(), h.getValue());
                    }
                });
                ctx.response().end(resp.body());
            } else {
                LOG.errorf(ar.cause(), "Gateway loi khi goi %s", target);
                ctx.response().setStatusCode(502)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"Bad gateway: " + ar.cause().getMessage() + "\"}");
            }
        });
    }
}
