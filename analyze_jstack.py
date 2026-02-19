#!/usr/bin/env python3
import re
import sys
from pathlib import Path

THREAD_HDR = re.compile(r'^"([^"]+)"')
SOCKET_READ = re.compile(r'(SocketInputStream\.socketRead0|receiveResponseHeader|DefaultHttpResponseParser\.parseHead)')
REST_EX = re.compile(r'org\.springframework\.web\.client\.RestTemplate\.exchange')

def analyze_file(p: Path):
    text = p.read_text(errors="ignore").splitlines()
    threads = []
    cur = None
    for line in text:
        m = THREAD_HDR.match(line)
        if m:
            if cur:
                threads.append(cur)
            cur = {"name": m.group(1), "lines": []}
        if cur is not None:
            cur["lines"].append(line)
    if cur:
        threads.append(cur)

    def is_reactor(t): return t["name"].startswith("reactor-http")
    def is_elastic(t): return t["name"].startswith("boundedElastic")

    reactor = [t for t in threads if is_reactor(t)]
    elastic = [t for t in threads if is_elastic(t)]

    reactor_socket = sum(1 for t in reactor if any(SOCKET_READ.search(x) for x in t["lines"]))
    reactor_rest = sum(1 for t in reactor if any(REST_EX.search(x) for x in t["lines"]))
    elastic_rest = sum(1 for t in elastic if any(REST_EX.search(x) for x in t["lines"]))

    total_rest_hits = sum(1 for t in threads if any(REST_EX.search(x) for x in t["lines"]))
    total_socket_hits = sum(1 for t in threads if any(SOCKET_READ.search(x) for x in t["lines"]))

    return {
        "file": p.name,
        "threads_total": len(threads),
        "reactor_threads": len(reactor),
        "reactor_socketRead_threads": reactor_socket,
        "reactor_restTemplate_threads": reactor_rest,
        "boundedElastic_threads": len(elastic),
        "boundedElastic_restTemplate_threads": elastic_rest,
        "any_restTemplate_threads": total_rest_hits,
        "any_socketRead_threads": total_socket_hits,
    }

def main():
    if len(sys.argv) < 2:
        print("Usage: analyze_jstack.py <jstack_dir_or_files...>")
        sys.exit(1)

    paths = []
    for arg in sys.argv[1:]:
        p = Path(arg)
        if p.is_dir():
            paths.extend(sorted(p.glob("*.txt")))
        else:
            paths.append(p)

    if not paths:
        print("No files found.")
        sys.exit(2)

    results = [analyze_file(p) for p in paths]

    # Pretty print
    header = [
        "file","threads_total","reactor_threads",
        "reactor_socketRead_threads","reactor_restTemplate_threads",
        "boundedElastic_threads","boundedElastic_restTemplate_threads",
        "any_restTemplate_threads","any_socketRead_threads"
    ]
    print("\t".join(header))
    for r in results:
        print("\t".join(str(r[k]) for k in header))

    # Final interpretation hint
    print("\nInterpretation:")
    print("- reactor_restTemplate_threads > 0  => event-loop 正在被阻塞(坏)")
    print("- offload模式应当: reactor_restTemplate_threads ~= 0 且 boundedElastic_restTemplate_threads > 0")
    print("- reactor_socketRead_threads 高 => 常见是下游慢导致等待响应头/读阻塞")

if __name__ == "__main__":
    main()

