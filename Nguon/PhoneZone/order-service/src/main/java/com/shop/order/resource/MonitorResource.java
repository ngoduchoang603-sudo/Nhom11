package com.shop.order.resource;
import com.shop.order.entity.MonitorAlert;
import com.shop.order.service.MonitorAgent;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;import jakarta.ws.rs.POST;import jakarta.ws.rs.Path;import jakarta.ws.rs.Produces;import jakarta.ws.rs.core.MediaType;
import java.util.List;
@Path("/api/monitor-alerts") @Produces(MediaType.APPLICATION_JSON) @RolesAllowed("ADMIN")
public class MonitorResource {
 @Inject MonitorAgent monitor;
 @GET @Blocking public List<MonitorAlert> open(){return MonitorAlert.list("resolved = false order by updatedAt desc");}
 @POST @Path("/scan") @Blocking public java.util.Map<String,Integer> scan(){return java.util.Map.of("alerts",monitor.scan());}
}
