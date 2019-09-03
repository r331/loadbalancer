package firefilter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient.EurekaServiceInstance;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class FireBackCustomFilterTests {

    private static final String MY_IP = "192.168.41.152";
    private static final String REMOTE_IP = "192.168.1.45";

    private static final String LB_UI_API = "lb://UI/api";
    @Test
    public void testFire() {
        HashMap<String, String> variables = new HashMap<>();
        variables.put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, LB_UI_API);
        testFireFilter(LB_UI_API, "http://192.168.1.45:8090/api", variables);
    }

    @SneakyThrows
    private void testFireFilter(String template, String expectedPath,
        HashMap<String, String> variables) {

        MockServerHttpRequest request = MockServerHttpRequest.get(template)
            .remoteAddress(new InetSocketAddress(REMOTE_IP, 45678))
            .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
        //
        exchange.getAttributes()
            .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(template));
        Route.AsyncBuilder routeBuilder = Route.async().id("123").uri(template)
            .predicate(exchange1 -> true);
        exchange.getAttributes()
            .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                routeBuilder.build());
        DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
        when(discoveryClient.getInstances(Matchers.contains("ui"))).thenReturn(getInstances("ui"));

        GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

        GlobalFilter filter = new RouteToLocalServicesFilterTest(discoveryClient);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
            .forClass(ServerWebExchange.class);
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, filterChain);

        ServerWebExchange webExchange = captor.getValue();

        URI uris = webExchange
            .getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertEquals(uris.toString(), expectedPath);
    }

    private static final String LB_SERVER_API = "lb://SERVER/api";
    private static final String SERVER = "SERVER";

    @Test
    public void testFireBad() {
        HashMap<String, String> variables = new HashMap<>();
        variables.put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, LB_SERVER_API);
        testFireFilterBad(LB_SERVER_API, LB_SERVER_API, variables);
    }

    @SneakyThrows
    private void testFireFilterBad(String template, String expectedPath,
        HashMap<String, String> variables) {

        MockServerHttpRequest request = MockServerHttpRequest.get(template)
            .remoteAddress(new InetSocketAddress(REMOTE_IP, 45678))
            .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
        //
        exchange.getAttributes()
            .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(template));
        Route.AsyncBuilder routeBuilder = Route.async().id("123").uri(template)
            .predicate(exchange1 -> true);
        exchange.getAttributes()
            .put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                routeBuilder.build());
        DiscoveryClient discoveryClient = Mockito.mock(DiscoveryClient.class);
        when(discoveryClient.getInstances(Matchers.contains(SERVER)))
            .thenReturn(getInstances(SERVER));

        GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

        GlobalFilter filter = new RouteToLocalServicesFilterTest(discoveryClient);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
            .forClass(ServerWebExchange.class);
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, filterChain);

        ServerWebExchange webExchange = captor.getValue();

        URI uris = webExchange
            .getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        assertEquals(uris.toString(), expectedPath);
    }

    private List<ServiceInstance> getInstances(String service) {
        return eurekaApplications().getRegisteredApplications(service).getInstances()
            .stream()
            .map(EurekaServiceInstance::new).collect(Collectors.toList());
    }

    private Applications eurekaApplications() {

        DataCenterInfo myDCI = () -> Name.MyOwn;

        InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("UI2")
            .setInstanceId("192.168.1.45:ui:8090")
            .setIPAddr(REMOTE_IP)
            .setHostName(REMOTE_IP)
            .setPort(8090)
            .setHomePageUrl(REMOTE_IP, "http://192.168.1.45:8090/")
            .setDataCenterInfo(myDCI)
            .build();

        Application application = new Application("UI");
        application.addInstance(instanceInfo);

        InstanceInfo instanceInfo2 = InstanceInfo.Builder.newBuilder().setAppName("UI1")
            .setIPAddr(MY_IP)
            .setHostName(MY_IP)
            .setPort(8090)
            .setHomePageUrl(MY_IP, "http://192.168.41.152:8090/")
            .setDataCenterInfo(myDCI)
            .build();

        application.addInstance(instanceInfo2);

        InstanceInfo instanceInfo3 = InstanceInfo.Builder.newBuilder().setAppName(SERVER)
            .setIPAddr(MY_IP)
            .setHostName(MY_IP)
            .setPort(8090)
            .setDataCenterInfo(myDCI).setHostName(SERVER)
            .build();

        Application application3 = new Application(SERVER);
        application3.addInstance(instanceInfo3);

        Applications applications = new Applications();
        applications.addApplication(application);
        applications.addApplication(application3);
        applications.shuffleInstances(true);
        return applications;
    }
}
