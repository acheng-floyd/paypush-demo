package demo.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PushController {
    private static final Logger log = LoggerFactory.getLogger(PushController.class);

    private final PushProperties props;
    private final RestTemplate restTemplate;
    private final WebClient webClient;

    public PushController(PushProperties props, RestTemplate restTemplate, WebClient webClient) {
        this.props = props;
        this.restTemplate = restTemplate;
        this.webClient = webClient;
    }

    @PostMapping("/push")
    public Mono<Map<String, Object>> push(@RequestBody Map<String, Object> body) {
        final long start = System.currentTimeMillis();
        final String reqId = String.valueOf(body.getOrDefault("reqId", "NA"));
        final String inThread = Thread.currentThread().getName();
        final String mode = props.getMode();

        log.info("PUSH_BIZ recv reqId={} mode={} thread={}", reqId, mode, inThread);

        if ("blocking".equalsIgnoreCase(mode)) {
            Map<String, Object> orderResp = callOrderByRestTemplate(body);
            Map<String, Object> out = wrapOk(reqId, start, inThread, orderResp, mode);
            log.info("PUSH_BIZ ok reqId={} mode={} costMs={}", reqId, mode, out.get("pushCostMs"));
            return Mono.just(out);
        }

        if ("offload".equalsIgnoreCase(mode)) {
            return Mono.fromCallable(() -> callOrderByRestTemplate(body))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(orderResp -> {
                        Map<String, Object> out = wrapOk(reqId, start, inThread, orderResp, mode);
                        log.info("PUSH_BIZ ok reqId={} mode={} costMs={} endThread={}",
                                reqId, mode, out.get("pushCostMs"), out.get("pushEndThread"));
                        return out;
                    })
                    .doOnError(e -> log.error("PUSH_BIZ fail reqId={} mode={} err={}", reqId, mode, e.toString(), e));
        }

        // webclient
        return webClient.post()
                .uri("/order")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(props.getOrderTimeoutSeconds()))
                .map(orderResp -> {
                    Map<String, Object> out = wrapOk(reqId, start, inThread, cast(orderResp), mode);
                    log.info("PUSH_BIZ ok reqId={} mode={} costMs={} endThread={}",
                            reqId, mode, out.get("pushCostMs"), out.get("pushEndThread"));
                    return out;
                })
                .doOnError(e -> log.error("PUSH_BIZ fail reqId={} mode={} err={}", reqId, mode, e.toString(), e));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Object obj) {
        return (Map<String, Object>) obj;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOrderByRestTemplate(Map<String, Object> body) {
        String url = props.getOrderBaseUrl() + "/order";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        return (Map<String, Object>) resp.getBody();
    }

    private Map<String, Object> wrapOk(String reqId, long startMs, String inThread,
                                       Map<String, Object> orderResp, String mode) {
        long cost = System.currentTimeMillis() - startMs;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("retCode", "000000");
        out.put("retMsg", "OK");
        out.put("reqId", reqId);
        out.put("pushMode", mode);
        out.put("pushInThread", inThread);
        out.put("pushEndThread", Thread.currentThread().getName());
        out.put("pushCostMs", cost);
        out.put("ts", Instant.now().toString());
        out.put("orderResp", orderResp);
        return out;
    }
}
