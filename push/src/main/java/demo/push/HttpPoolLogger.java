package demo.push;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HttpPoolLogger {
    private static final Logger log = LoggerFactory.getLogger(HttpPoolLogger.class);

    private final PushProperties props;
    private final PoolingHttpClientConnectionManager cm;

    public HttpPoolLogger(PushProperties props, PoolingHttpClientConnectionManager cm) {
        this.props = props;
        this.cm = cm;
    }

    // 每秒检查一次，是否需要输出由配置控制（间隔秒数）
    @Scheduled(fixedDelay = 1000)
    public void logPool() {
        if (!props.getHttpclient().isPoolLogEnabled()) return;

        int interval = props.getHttpclient().getPoolLogIntervalSeconds();
        if (interval <= 0) interval = 2;

        long now = System.currentTimeMillis() / 1000;
        if (now % interval != 0) return;

        PoolStats total = cm.getTotalStats();
        log.info("HTTP_POOL total leased={} pending={} available={} max={}",
                total.getLeased(), total.getPending(), total.getAvailable(), total.getMax());
    }
}
