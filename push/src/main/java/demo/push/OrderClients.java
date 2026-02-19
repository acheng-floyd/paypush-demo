package demo.push;

import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class OrderClients {
    private static final Logger log = LoggerFactory.getLogger(OrderClients.class);

    @Bean
    public PoolingHttpClientConnectionManager poolingConnectionManager(PushProperties props) {
        PushProperties.HttpClientProps hp = props.getHttpclient();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(hp.getMaxTotal());
        cm.setDefaultMaxPerRoute(hp.getMaxPerRoute());
        log.info("HTTP_POOL init maxTotal={} maxPerRoute={}", hp.getMaxTotal(), hp.getMaxPerRoute());
        return cm;
    }

    @Bean
    public CloseableHttpClient apacheHttpClient(PushProperties props,
                                                PoolingHttpClientConnectionManager cm) {
        PushProperties.HttpClientProps hp = props.getHttpclient();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(hp.getConnectTimeoutMs())
                .setSocketTimeout(hp.getReadTimeoutMs())
                .setConnectionRequestTimeout(hp.getConnectionRequestTimeoutMs())
                .build();

        ConnectionReuseStrategy reuseStrategy = hp.isConnectionReuse()
                ? org.apache.http.impl.DefaultConnectionReuseStrategy.INSTANCE
                : new ConnectionReuseStrategy() {
            @Override public boolean keepAlive(HttpResponse response, org.apache.http.protocol.HttpContext context) {
                return false;
            }
        };

        ConnectionKeepAliveStrategy keepAliveStrategy = new ConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, org.apache.http.protocol.HttpContext context) {
                // 优先读服务端 Keep-Alive: timeout=xx
                HeaderElementIterator it = new BasicHeaderElementIterator(
                        response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                while (it.hasNext()) {
                    HeaderElement he = it.nextElement();
                    String param = he.getName();
                    String value = he.getValue();
                    if (value != null && "timeout".equalsIgnoreCase(param)) {
                        try {
                            return Long.parseLong(value) * 1000L;
                        } catch (NumberFormatException ignore) {}
                    }
                }
                // 否则用配置
                return hp.getKeepAliveSeconds() * 1000L;
            }
        };

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionReuseStrategy(reuseStrategy)
                .setKeepAliveStrategy(keepAliveStrategy)
                .evictIdleConnections(hp.getKeepAliveSeconds(), TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public RestTemplate restTemplate(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }

    @Bean
    public WebClient webClient(PushProperties props) {
        long timeoutSeconds = props.getOrderTimeoutSeconds();

        // Boot 2.2.5 reactor-netty 0.9.x：没有 responseTimeout(Duration)，用 ReadTimeoutHandler
        HttpClient httpClient = HttpClient.create()
                .tcpConfiguration(tcp -> tcp.doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                ));

        return WebClient.builder()
                .baseUrl(props.getOrderBaseUrl())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }
}
