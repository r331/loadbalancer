import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import firefilter.RouteToLocalServicesFilter;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class FireBackCustomFilterTests {


  @Test
  public void testFire() {
    HashMap<String, String> variables = new HashMap<>();
    variables.put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, "lb://UI/api");
    testFireFilter("lb://UI/api", "http://192.168.1.45:8090/api", variables);
  }

  private void testFireFilter(String template, String expectedPath,
      HashMap<String, String> variables) {

    MockServerHttpRequest request = MockServerHttpRequest.get(template)
        .remoteAddress(new InetSocketAddress("192.168.1.45", 45678))
        .build();

    ServerWebExchange exchange = MockServerWebExchange.from(request);
    ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
    //
    try {
      exchange.getAttributes()
          .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(template));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    EurekaClient eurekaClient = Mockito.mock(EurekaClient.class);
    when(eurekaClient.getApplications()).thenReturn(eurekaApplications());

    GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

    GlobalFilter filter = new RouteToLocalServicesFilter((DiscoveryClient) eurekaClient);

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
        .forClass(ServerWebExchange.class);
    when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

    filter.filter(exchange, filterChain);

    ServerWebExchange webExchange = captor.getValue();

    URI uris = webExchange
        .getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
    assertEquals(uris.toString(), expectedPath);
  }

  @Test
  public void testFireBad() {
    HashMap<String, String> variables = new HashMap<>();
    variables.put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, "lb://SERVER/api");
    testFireFilterBad("lb://SERVER/api", "lb://SERVER/api", variables);
  }

  private void testFireFilterBad(String template, String expectedPath,
      HashMap<String, String> variables) {

    MockServerHttpRequest request = MockServerHttpRequest.get(template)
        .remoteAddress(new InetSocketAddress("192.168.1.45", 45678))
        .build();

    ServerWebExchange exchange = MockServerWebExchange.from(request);
    ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
    //
    try {
      exchange.getAttributes()
          .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI("lb://SERVER/api"));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    EurekaClient eurekaClient = Mockito.mock(EurekaClient.class);
    when(eurekaClient.getApplications()).thenReturn(eurekaApplications());

    GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

    GlobalFilter filter = new RouteToLocalServicesFilter((DiscoveryClient) eurekaClient);

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
        .forClass(ServerWebExchange.class);
    when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

    filter.filter(exchange, filterChain);

    ServerWebExchange webExchange = captor.getValue();

    URI uris = webExchange
        .getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
    assertEquals(uris.toString(), expectedPath);
  }

  public Applications eurekaApplications() {

    //Создаем приложения
    DataCenterInfo myDCI = () -> Name.MyOwn;

    InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder().setAppName("UI2")
        .setInstanceId("192.168.1.45:ui:8090")
        .setIPAddr("192.168.1.45")
        .setHostName("UI")
        .setHomePageUrl("192.168.1.45", "http://192.168.1.45:8090/")
        .setDataCenterInfo(myDCI).setHostName("UI")
        .build();

    Application application = new Application("UI");
    application.addInstance(instanceInfo);

    InstanceInfo instanceInfo2 = InstanceInfo.Builder.newBuilder().setAppName("UI1")
        .setIPAddr("192.168.41.152")
        .setHostName("UI")
        .setHomePageUrl("192.168.41.152", "http://192.168.41.152:8090/")
        .setDataCenterInfo(myDCI).setHostName("UI")
        .build();

    application.addInstance(instanceInfo2);

    InstanceInfo instanceInfo3 = InstanceInfo.Builder.newBuilder().setAppName("SERVER")
        .setIPAddr("192.168.41.152")
        .setDataCenterInfo(myDCI).setHostName("SERVER")
        .build();

    Application application3 = new Application("SERVER");
    application3.addInstance(instanceInfo3);

    Applications applications = new Applications();
    applications.addApplication(application);
    applications.addApplication(application3);
    applications.shuffleInstances(true);

    return applications;
  }
}
