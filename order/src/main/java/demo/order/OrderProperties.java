package demo.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "order.sleep")
public class OrderProperties {

    private int minMs = 1000;
    private int maxMs = 3000;

    public int getMinMs() { return minMs; }
    public void setMinMs(int minMs) { this.minMs = minMs; }

    public int getMaxMs() { return maxMs; }
    public void setMaxMs(int maxMs) { this.maxMs = maxMs; }
}
