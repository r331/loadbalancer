package firefilter;

import static org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.loadbalancer.PingUrl;
import com.netflix.loadbalancer.Server;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@SuppressWarnings("ALL")
@Component
public class FireBackCustomFilter implements GatewayFilter, Ordered {

  private static final Logger logger = LoggerFactory.getLogger(FireBackCustomFilter.class);
  private final EurekaClient eurekaClient;
  private final Map<String, InstanceInfo> instances;

  public FireBackCustomFilter(EurekaClient eurekaClient) {
    this.eurekaClient = eurekaClient;
    instances = new ConcurrentHashMap<>();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    URI originalUri = (URI) exchange.getAttributes()
        .get(GATEWAY_REQUEST_URL_ATTR);
    String userIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    InstanceInfo instFromMap = instances
        .get(userIp + ":" + originalUri.getHost() + ":" + originalUri.getPort());

    if (instFromMap != null && isAlive(instFromMap)) {
      logger.info("Get instance from map: {}", instFromMap.getAppName());
      exchange.getAttributes()
          .put(GATEWAY_REQUEST_URL_ATTR, toLocalUrl(instFromMap, originalUri));
    } else {
      eurekaClient.getApplications()
          .getRegisteredApplications(originalUri.getHost())
          .getInstances()
          .stream()
          .filter(i -> i.getIPAddr().equals(userIp))
          .filter(this::isAlive)
          .map(i -> toLocalUrl(i, originalUri))
          .findFirst()
          .ifPresent(localInfo -> {
            logger
                .info("User ip: {} for EurekaInstance: {} is found", userIp, originalUri.getHost());
            exchange.getAttributes()
                .put(GATEWAY_REQUEST_URL_ATTR, localInfo);
          });
    }
    return chain.filter(exchange);
  }

  private boolean isAlive(InstanceInfo i) {
    boolean isAlive = false;
    final PingUrl pingUrl = new PingUrl();
    pingUrl.setPingAppendString("/actuator/info");

    isAlive = pingUrl.isAlive(new Server(i.getHomePageUrl()));
    if (isAlive) {
      instances.put(i.getInstanceId(), i);
    } else {
      if (instances.get(i.getInstanceId()) != null) {
        isAlive = pingUrl.isAlive(new Server(instances.get(i.getInstanceId()).getHomePageUrl()));
        if (!isAlive) {
          instances.remove(i);
        }
      }
    }
    return isAlive;
  }

  @SneakyThrows
  private URI toLocalUrl(InstanceInfo localInfo, URI originalUri) {
    String newURI = localInfo.getHomePageUrl() + originalUri.getPath().replaceFirst("/", "");
    logger.info("URI succesfully updated {}", newURI);
    return new URI(newURI);
  }

  @Override
  public int getOrder() {
    return ROUTE_TO_URL_FILTER_ORDER + 1;
  }
}