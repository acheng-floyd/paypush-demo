编译：
mvn -DskipTests clean package
程序主要用来模拟trans 调用 push服务，再有push服务调用order服务的一个流程
测试httpclient和webclient对push服务的影响



## 一套“参数组合”，稳定复现三种模式差异

你的目标是：

- **blocking：必超**
- **offload：部分超**
- **webclient：几乎不超**

要稳定复现差异，关键是让系统在高压下暴露两类瓶颈：

1. blocking 的“event-loop 被阻塞”
2. offload 的“阻塞调用被丢到 boundedElastic，线程/连接池/排队仍会导致部分超时”
3. webclient 因为非阻塞，吞吐更高，能在同样压力下维持较低超时

### ✅ 推荐参数组合（稳定、可控）

#### A) order（让下游慢一点，但不要慢到所有模式都挂）

```
order/application.yml
order:
  sleep:
    minMs: 2500
    maxMs: 5500
```

#### B) trans（提高并发 + 提高频率，让 push 持续有压力）

参考Trans参数说明.md

#### C) push（让 offload 有“部分超时”的空间）

关键：**boundedElastic 线程池是有上限的**（默认是 CPU 核数相关），当你并发太高、下游太慢，就会排队，导致部分请求 > 4s。

push 端建议：

- `push.mode` 分别测 blocking/offload/webclient
- `push.orderTimeoutSeconds` 设成 60（不限制下游，让上游 4s 来判定）
- 连接池保持大（避免又被“连接池不足”掩盖问题）

`push/application.yml`（连接池保持大即可）

```
push:
  orderTimeoutSeconds: 60
  httpclient:
    maxTotal: 400
    maxPerRoute: 400
    connectionRequestTimeoutMs: 2000
    connectTimeoutMs: 2000
    readTimeoutMs: 60000
```

### 你会看到的现象（一般会很明显）

- **blocking**：push 的 event-loop 被阻塞，整体抖动很快变大 → trans 大量 TIMEOUT（“必超”）
- **offload**：event-loop 不阻塞，但 boundedElastic/排队导致一部分请求超过 4s → “部分超”
- **webclient**：event-loop 不阻塞，下游连接复用良好 → 在同样压力下超时最少（“几乎不超”，但如果压力极端大也可能少量超）

> 如果你发现 webclient 也开始明显超时，说明你把压力推得太极端了：把 `concurrency` 从 200 降到 120~160，或把 order 最大 sleep 从 5500 降到 4500。

------

## 一个很实用的小技巧：用连接池日志 + push_http 日志验证“差异来自哪里”

你现在 push 已经会打：

- `HTTP_POOL total leased/pending/available/max`
- `PUSH_HTTP start/end`
- `PUSH_BIZ ok/fail`

稳定复现后，你会看到：

- blocking：即使 `pending` 不高，仍会超（event-loop 处理不过来）
- offload：`pending` 可能不高，但 `boundedElastic` 排队导致整体耗时 > 4s
- webclient：`pending` 通常更平稳、push 的端到端耗时更低

------

如果你愿意，我还可以在 push 里再加一个**boundedElastic 排队/线程数**的观测（比如打印 `Schedulers.boundedElastic()` 的 worker 使用情况——需要一点点更底层的实现），这样 offload “部分超”会更容易解释给团队听。

