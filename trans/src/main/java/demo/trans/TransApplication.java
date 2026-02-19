package demo.trans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@SpringBootApplication
public class TransApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TransApplication.class);

    @Value("${trans.pushUrl}")
    private String pushUrl;

    @Value("${trans.timeoutSeconds:4}")
    private long timeoutSeconds;

    // rate control
    @Value("${trans.qps:10}")
    private double qps;

    @Value("${trans.tokenRefillMs:100}")
    private long tokenRefillMs;

    @Value("${trans.tokenBucketCapacity:200}")
    private int tokenBucketCapacity;

    // concurrency control
    @Value("${trans.concurrency:100}")
    private int concurrency;

    // jitter
    @Value("${trans.jitterMsMax:150}")
    private int jitterMsMax;

    // burst
    @Value("${trans.burst.enabled:true}")
    private boolean burstEnabled;

    @Value("${trans.burst.probabilityPerSecond:0.10}")
    private double burstProbabilityPerSecond;

    @Value("${trans.burst.durationMsMin:800}")
    private int burstDurationMsMin;

    @Value("${trans.burst.durationMsMax:1800}")
    private int burstDurationMsMax;

    @Value("${trans.burst.factorMin:2.0}")
    private double burstFactorMin;

    @Value("${trans.burst.factorMax:4.0}")
    private double burstFactorMax;

    // pause
    @Value("${trans.pause.enabled:true}")
    private boolean pauseEnabled;

    @Value("${trans.pause.probabilityPerSecond:0.06}")
    private double pauseProbabilityPerSecond;

    @Value("${trans.pause.durationMsMin:1200}")
    private int pauseDurationMsMin;

    @Value("${trans.pause.durationMsMax:4500}")
    private int pauseDurationMsMax;

    public static void main(String[] args) {
        SpringApplication.run(TransApplication.class, args);
    }

    @Override
    public void run(String... args) {
        final WebClient client = WebClient.builder().build();
        final AtomicLong seq = new AtomicLong(0);

        // 并发门闩：在途请求 <= concurrency
        final Semaphore inflight = new Semaphore(concurrency);

        // 令牌桶：控制平均QPS
        final TokenBucket bucket = new TokenBucket(tokenBucketCapacity);

        // 调度线程：单线程更可控（不会像 interval 那样溢出）
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("trans-scheduler");
            t.setDaemon(true);
            return t;
        });

        // worker：用于等待信号量/做少量 sleep，不阻塞 reactor
        final ExecutorService launcher = Executors.newFixedThreadPool(
                Math.min(8, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("trans-launcher");
                    t.setDaemon(true);
                    return t;
                }
        );

        // 每 tokenRefillMs 补充令牌
        final double baseTokensPerTick = qps * (tokenRefillMs / 1000.0);

        final TrafficShaper shaper = new TrafficShaper(
                scheduler,
                burstEnabled, burstProbabilityPerSecond, burstDurationMsMin, burstDurationMsMax, burstFactorMin, burstFactorMax,
                pauseEnabled, pauseProbabilityPerSecond, pauseDurationMsMin, pauseDurationMsMax
        );

        scheduler.scheduleAtFixedRate(() -> {
            try {
                double factor = shaper.currentFactor(); // 可能是 0（pause），也可能 >1（burst）
                double tokensToAdd = baseTokensPerTick * factor;
                bucket.add(tokensToAdd);

                // 尝试发尽可能多的请求：受 token + inflight 限制
                launcher.submit(() -> {
                    while (bucket.tryConsumeOne()) {
                        if (!inflight.tryAcquire()) {
                            // 并发满了，把 token 退回去（避免丢QPS）
                            bucket.add(1.0);
                            break;
                        }

                        // jitter：模拟不匀速（0~jitterMsMax）
                        if (jitterMsMax > 0) {
                            int jitter = ThreadLocalRandom.current().nextInt(0, jitterMsMax + 1);
                            if (jitter > 0) {
                                try { Thread.sleep(jitter); } catch (InterruptedException ignored) {}
                            }
                        }

                        final String reqId = seq.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
                        final long start = System.currentTimeMillis();

                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("reqId", reqId);
                        body.put("amt", 100);
                        body.put("ts", System.currentTimeMillis());

                        // 发请求（reactor异步），完成释放并发许可
                        client.post()
                                .uri(pushUrl)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(String.class)
                                .timeout(Duration.ofSeconds(timeoutSeconds))
                                .doOnSuccess(resp -> {
                                    long cost = System.currentTimeMillis() - start;
                                    log.info("[OK] reqId={} costMs={}", reqId, cost);
                                })
                                .doOnError(ex -> {
                                    long cost = System.currentTimeMillis() - start;
                                    log.warn("[TIMEOUT/ERR] reqId={} costMs={} ex={}", reqId, cost, ex.getClass().getSimpleName());
                                })
                                .doFinally(sig -> inflight.release())
                                .subscribe();
                    }
                });

            } catch (Throwable t) {
                log.error("scheduler tick failed", t);
            }
        }, 0, tokenRefillMs, TimeUnit.MILLISECONDS);

        log.info("Trans started: pushUrl={} qps={} concurrency={} tokenRefillMs={} cap={} jitterMsMax={}",
                pushUrl, qps, concurrency, tokenRefillMs, tokenBucketCapacity, jitterMsMax);
    }

    // ====== 简单的 token bucket（线程安全，支持小数累积）======
    static class TokenBucket {
        private final int capacity;
        private double tokens;

        TokenBucket(int capacity) {
            this.capacity = Math.max(1, capacity);
            this.tokens = 0.0;
        }

        synchronized void add(double n) {
            if (n <= 0) return;
            tokens = Math.min(capacity, tokens + n);
        }

        synchronized boolean tryConsumeOne() {
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    // ====== 突发/空窗塑形器（不依赖 interval，不会 overflow）======
    static class TrafficShaper {
        private final ScheduledExecutorService scheduler;

        private final boolean burstEnabled;
        private final double burstProbPerSec;
        private final int burstMinMs;
        private final int burstMaxMs;
        private final double burstFactorMin;
        private final double burstFactorMax;

        private final boolean pauseEnabled;
        private final double pauseProbPerSec;
        private final int pauseMinMs;
        private final int pauseMaxMs;

        private volatile double factor = 1.0;
        private volatile long untilEpochMs = 0;

        TrafficShaper(ScheduledExecutorService scheduler,
                      boolean burstEnabled, double burstProbPerSec,
                      int burstMinMs, int burstMaxMs, double burstFactorMin, double burstFactorMax,
                      boolean pauseEnabled, double pauseProbPerSec,
                      int pauseMinMs, int pauseMaxMs) {
            this.scheduler = scheduler;

            this.burstEnabled = burstEnabled;
            this.burstProbPerSec = burstProbPerSec;
            this.burstMinMs = burstMinMs;
            this.burstMaxMs = burstMaxMs;
            this.burstFactorMin = burstFactorMin;
            this.burstFactorMax = burstFactorMax;

            this.pauseEnabled = pauseEnabled;
            this.pauseProbPerSec = pauseProbPerSec;
            this.pauseMinMs = pauseMinMs;
            this.pauseMaxMs = pauseMaxMs;

            // 每秒检查一次是否触发 burst/pause（不会溢出）
            scheduler.scheduleAtFixedRate(this::maybeSwitch, 0, 1, TimeUnit.SECONDS);
        }

        double currentFactor() {
            long now = System.currentTimeMillis();
            if (now < untilEpochMs) return factor;
            // 到期则回到 1
            factor = 1.0;
            untilEpochMs = 0;
            return 1.0;
        }

        private void maybeSwitch() {
            long now = System.currentTimeMillis();
            if (now < untilEpochMs) return; // 正在 burst/pause 中

            // pause 优先（模拟“几秒一次”）
            if (pauseEnabled && ThreadLocalRandom.current().nextDouble() < pauseProbPerSec) {
                int dur = ThreadLocalRandom.current().nextInt(pauseMinMs, pauseMaxMs + 1);
                factor = 0.0; // 0 token => 暂停发请求
                untilEpochMs = now + dur;
                return;
            }

            // burst（模拟“1秒几次”）
            if (burstEnabled && ThreadLocalRandom.current().nextDouble() < burstProbPerSec) {
                int dur = ThreadLocalRandom.current().nextInt(burstMinMs, burstMaxMs + 1);
                double fac = burstFactorMin + ThreadLocalRandom.current().nextDouble() * (burstFactorMax - burstFactorMin);
                factor = fac;
                untilEpochMs = now + dur;
            }
        }
    }
}