package com.shop.ai.adapter.in.rest;

import com.shop.ai.domain.TradeInCase;
import com.shop.ai.application.VisionTradeInEvaluator;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API backing the customer trade-in flow and the staff verification workflow. */
@Path("/trade-ins")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TradeInResource {
    private static final List<String> REQUIRED_VIEWS = List.of("front", "back", "left", "right", "top", "bottom");
    @Inject VisionTradeInEvaluator visionTradeInEvaluator;

    /** A quote is private and ephemeral; it does not create a store request until the customer confirms. */
    @POST @Path("/preview")
    public Response preview(@Valid PreviewRequest request) {
        if (request.images() == null || !request.images().keySet().containsAll(REQUIRED_VIEWS)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Cần đủ 6 ảnh: mặt trước, mặt sau và 4 cạnh máy.")).build();
        }
        long reference = Math.max(1_000_000L, request.referencePrice() == null ? 5_000_000L : request.referencePrice());
        VisionTradeInEvaluator.Evaluation evaluation = visionTradeInEvaluator.evaluate(request.deviceName().trim(), request.images(), reference);
        return Response.ok(new PreviewResponse(evaluation.min(), evaluation.max(), evaluation.result())).build();
    }

    @POST @Transactional
    public Response create(@Valid CreateRequest request) {
        if (request.images() == null || !request.images().keySet().containsAll(REQUIRED_VIEWS)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Cần đủ 6 ảnh: mặt trước, mặt sau và 4 cạnh máy.")).build();
        }
        TradeInCase tradeIn = new TradeInCase();
        tradeIn.userId = request.userId(); tradeIn.deviceName = request.deviceName().trim();
        tradeIn.storeId = request.storeId().trim(); tradeIn.status = "PENDING";
        long reference = Math.max(1_000_000L, request.referencePrice() == null ? 5_000_000L : request.referencePrice());
        VisionTradeInEvaluator.Evaluation evaluation = visionTradeInEvaluator.evaluate(tradeIn.deviceName, request.images(), reference);
        tradeIn.estimatedMin = evaluation.min(); tradeIn.estimatedMax = evaluation.max();
        tradeIn.images = new LinkedHashMap<>(request.images());
        tradeIn.visionResult = evaluation.result();
        tradeIn.createdAt = Instant.now(); tradeIn.updatedAt = tradeIn.createdAt; tradeIn.persist();
        return Response.status(Response.Status.CREATED).entity(tradeIn).build();
    }

    @GET @Path("/user/{userId}")
    public List<TradeInCase> byUser(@PathParam("userId") Long userId) {
        return TradeInCase.list("userId = ?1 order by createdAt desc", userId);
    }

    /** Staff-only UI will call this after a physical in-store inspection. */
    @PATCH @Path("/{id}/verify") @Transactional
    public Response verify(@PathParam("id") Long id, @Valid VerifyRequest request) {
        TradeInCase tradeIn = TradeInCase.findById(id);
        if (tradeIn == null) return Response.status(Response.Status.NOT_FOUND).build();
        tradeIn.verifiedPrice = request.verifiedPrice(); tradeIn.staffNote = request.staffNote();
        tradeIn.status = request.completed() ? "COMPLETED" : "VERIFIED"; tradeIn.updatedAt = Instant.now();
        return Response.ok(tradeIn).build();
    }

    public record CreateRequest(Long userId, @NotBlank String deviceName, @NotBlank String storeId,
                                Long referencePrice, @NotEmpty Map<String, String> images) { }
    public record PreviewRequest(@NotBlank String deviceName, Long referencePrice, @NotEmpty Map<String, String> images) { }
    public record PreviewResponse(long estimatedMin, long estimatedMax, Map<String, Object> visionResult) { }
    public record VerifyRequest(Long verifiedPrice, String staffNote, boolean completed) { }
}
