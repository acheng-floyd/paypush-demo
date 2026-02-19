package demo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;

import reactor.core.publisher.Mono;

@Component
public class PushWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(PushWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        // 只关注 /push
        if (!"/push".equals(req.getURI().getPath())) {
            return chain.filter(exchange);
        }

        final long start = System.currentTimeMillis();
        final String method = req.getMethodValue();
        final String path = req.getURI().getPath();

        log.info("PUSH_HTTP start method={} path={} thread={}",
                method, path, Thread.currentThread().getName());

        return chain.filter(exchange)
                .doFinally(sig -> {
                    long cost = System.currentTimeMillis() - start;
                    int status = exchange.getResponse().getStatusCode() == null ? 0 : exchange.getResponse().getStatusCode().value();
                    log.info("PUSH_HTTP end method={} path={} status={} costMs={} thread={}",
                            method, path, status, cost, Thread.currentThread().getName());
                });
    }
}
