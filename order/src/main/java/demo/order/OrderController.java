package demo.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderProperties props;

    public OrderController(OrderProperties props) {
        this.props = props;
    }

    @PostMapping("/order")
    public Map<String, Object> order(@RequestBody Map<String, Object> body) throws Exception {
        String reqId = String.valueOf(body.getOrDefault("reqId", "NA"));
        long start = System.currentTimeMillis();

        int min = props.getMinMs();
        int max = props.getMaxMs();
        if (max <= min) { // 防御
            max = min + 1;
        }

        log.info("ORDER_BIZ start reqId={} sleepRange=[{},{}] thread={}",
                reqId, min, max, Thread.currentThread().getName());

        int sleepMs = ThreadLocalRandom.current().nextInt(min, max + 1);
        Thread.sleep(sleepMs);

        long cost = System.currentTimeMillis() - start;
        log.info("ORDER_BIZ done reqId={} sleepMs={} costMs={}", reqId, sleepMs, cost);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("sleepMs", sleepMs);
        resp.put("echo", body);
        resp.put("thread", Thread.currentThread().getName());
        return resp;
    }
}
