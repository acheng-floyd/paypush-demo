package demo.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "push")
public class PushProperties {

    private String mode = "blocking";
    private String orderBaseUrl = "http://localhost:27000";
    private long orderTimeoutSeconds = 60;

    private HttpClientProps httpclient = new HttpClientProps();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getOrderBaseUrl() { return orderBaseUrl; }
    public void setOrderBaseUrl(String orderBaseUrl) { this.orderBaseUrl = orderBaseUrl; }

    public long getOrderTimeoutSeconds() { return orderTimeoutSeconds; }
    public void setOrderTimeoutSeconds(long orderTimeoutSeconds) { this.orderTimeoutSeconds = orderTimeoutSeconds; }

    public HttpClientProps getHttpclient() { return httpclient; }
    public void setHttpclient(HttpClientProps httpclient) { this.httpclient = httpclient; }

    public static class HttpClientProps {
        private int maxTotal = 200;
        private int maxPerRoute = 200;

        private boolean connectionReuse = true;
        private int keepAliveSeconds = 30;

        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 60000;
        private int connectionRequestTimeoutMs = 2000;

        private boolean poolLogEnabled = true;
        private int poolLogIntervalSeconds = 2;

        public int getMaxTotal() { return maxTotal; }
        public void setMaxTotal(int maxTotal) { this.maxTotal = maxTotal; }

        public int getMaxPerRoute() { return maxPerRoute; }
        public void setMaxPerRoute(int maxPerRoute) { this.maxPerRoute = maxPerRoute; }

        public boolean isConnectionReuse() { return connectionReuse; }
        public void setConnectionReuse(boolean connectionReuse) { this.connectionReuse = connectionReuse; }

        public int getKeepAliveSeconds() { return keepAliveSeconds; }
        public void setKeepAliveSeconds(int keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

        public int getConnectionRequestTimeoutMs() { return connectionRequestTimeoutMs; }
        public void setConnectionRequestTimeoutMs(int connectionRequestTimeoutMs) { this.connectionRequestTimeoutMs = connectionRequestTimeoutMs; }

        public boolean isPoolLogEnabled() { return poolLogEnabled; }
        public void setPoolLogEnabled(boolean poolLogEnabled) { this.poolLogEnabled = poolLogEnabled; }

        public int getPoolLogIntervalSeconds() { return poolLogIntervalSeconds; }
        public void setPoolLogIntervalSeconds(int poolLogIntervalSeconds) { this.poolLogIntervalSeconds = poolLogIntervalSeconds; }
    }
}
