package firefilter;

import java.io.IOException;
import java.net.URI;
import lombok.AllArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;

@AllArgsConstructor
public class CustomLoadBalancerClient implements LoadBalancerClient {

    private final LoadBalancerClientFactory loadBalancerClientFactory;

    @Override
    public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
        return null;
    }

    @Override
    public <T> T execute(String serviceId, ServiceInstance serviceInstance,
        LoadBalancerRequest<T> request) throws IOException {
        return null;
    }

    @Override
    public URI reconstructURI(ServiceInstance instance, URI original) {
        return null;
    }

    @Override
    public ServiceInstance choose(String serviceId) {
        return null;
    }
}
