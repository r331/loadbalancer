package firefilter;


import java.net.URI;
import org.springframework.cloud.client.discovery.DiscoveryClient;

class RouteToLocalServicesFilterTest extends RouteToLocalServicesFilter {

    public RouteToLocalServicesFilterTest(
        DiscoveryClient discoveryClient) {
        super(discoveryClient);
    }


    @Override
    public boolean isAlive(URI uri) {
        return uri != null;
    }
}
