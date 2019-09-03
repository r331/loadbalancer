package firefilter;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Slf4j
@SuppressWarnings("ALL")
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(value = "spring.cloud.gateway.discovery.locator.routeToLocalService")
public class RouteToLocalServicesFilter implements GlobalFilter, Ordered {

  private static final String PING_CONTEXT = "/actuator/info";

  private final DiscoveryClient discoveryClient;
  private final Map<String, URI> cache = new ConcurrentHashMap<>();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String serviceName = getServiceName(exchange);

    String userIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    String cacheKey = serviceName + ":" + userIp;

    discoveryClient.getInstances(serviceName)
        .stream()
        .filter(i -> i.getHost().equals(userIp))
        .findFirst()
        .ifPresent(localInstance ->
            cache.put(cacheKey, localInstance.getUri())
        );

    URI localUri = cache.get(cacheKey);
    if (isAlive(localUri)) {
      routeTo(localUri, exchange);
    }

    return chain.filter(exchange);
  }

  private String getServiceName(ServerWebExchange exchange) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    return route == null ? null : route.getUri().getHost().toLowerCase();
  }

  private boolean isAlive(URI uri) {
    if (uri == null) {
      return false;
    }

    try {
      URLConnection urlConnection = new URL(uri + PING_CONTEXT).openConnection();
      urlConnection.setConnectTimeout(100);
      urlConnection.setReadTimeout(100);
      urlConnection.connect();
      return true;
    } catch (Exception e) {
      log.info("Failed to ping {}", uri);
      return false;
    }
  }

  @SneakyThrows
  private void routeTo(URI uri, ServerWebExchange exchange) {
    URI destination = new URI(uri.toString() + exchange.getRequest().getPath());
    log.info("Routing {} to {}", exchange.getRequest().getURI(), destination);
    exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, destination);
  }

  @Override
  public int getOrder() {
    return ROUTE_TO_URL_FILTER_ORDER + 1;
  }
}