如何分析：

先抓 jstack（你也可以用 jstack_watch.sh  脚本抓出来的目录）
分析目录：
python3 analyze_jstack.py ./jstack_dumps_20260220_XXXX_pid12345

你用这套脚本怎么验证三种模式
push.mode=blocking（预期坏）

reactor_restTemplate_threads > 0

reactor 线程可能出现 socketRead0 / receiveResponseHeader

push.mode=offload（预期好）

reactor_restTemplate_threads ≈ 0

boundedElastic_restTemplate_threads > 0

push.mode=webclient（预期更好）

两边都不应出现 RestTemplate.exchange

reactor 线程即使有 socketRead0，也不应该大量堆积（取决于并发/下游慢）



