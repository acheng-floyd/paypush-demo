package demo.trans;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class TransApplication implements CommandLineRunner {

    @Value("${trans.pushUrl}")
    private String pushUrl;

    @Value("${trans.timeoutSeconds:4}")
    private long timeoutSeconds;

    @Value("${trans.intervalMs:200}")
    private long intervalMs;

    @Value("${trans.concurrency:20}")
    private int concurrency;

    public static void main(String[] args) {
        SpringApplication.run(TransApplication.class, args);
    }

    @Override
    public void run(String... args) {
        WebClient client = WebClient.builder().build();
        AtomicLong seq = new AtomicLong(0);

        Flux.interval(Duration.ofMillis(intervalMs))
                .flatMap(i -> {
                    String reqId = seq.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);

                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("reqId", reqId);
                    body.put("amt", 100);
                    body.put("ts", System.currentTimeMillis());

                    long start = System.currentTimeMillis();

                    return client.post()
                            .uri(pushUrl)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(timeoutSeconds))
                            .doOnSuccess(resp -> {
                                long cost = System.currentTimeMillis() - start;
                                System.out.println("[OK] reqId=" + reqId + " costMs=" + cost);
                            })
                            .onErrorResume(ex -> {
                                long cost = System.currentTimeMillis() - start;
                                System.out.println("[TIMEOUT/ERR] reqId=" + reqId + " costMs=" + cost
                                        + " ex=" + ex.getClass().getSimpleName());
                                return Mono.empty();
                            });
                }, concurrency)
                .subscribe();
    }
}
