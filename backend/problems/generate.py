"""Writes the problem files: statements, examples and hidden tests.

Expected outputs come from the reference solutions below, so they are correct by construction.
The files are not kept in git (they are tens of megabytes of numbers); Maven runs this while
building, into target/generated-resources/problems, and skips it when nothing changed.

    python backend/problems/generate.py [--out DIR] [--force]
"""
import argparse
import bisect
import hashlib
import heapq
import random
import sys
from collections import deque
from pathlib import Path

HERE = Path(__file__).resolve()
DEFAULT_OUT = HERE.parents[1] / "target" / "generated-resources" / "problems"
rng = random.Random(20261012)


# ---------- reference solutions ----------

def solve_add(text):
    a, b = map(int, text.split())
    return f"{a + b}\n"


def solve_two_sum(text):
    data = list(map(int, text.split()))
    n, target, values = data[0], data[1], data[2:]
    seen = {}
    for j, x in enumerate(values):
        if target - x in seen:
            return f"{seen[target - x]} {j}\n"
        seen.setdefault(x, j)
    raise ValueError("no pair")


def solve_brackets(text):
    pairs = {")": "(", "]": "[", "}": "{"}
    stack = []
    for ch in text.strip():
        if ch in "([{":
            stack.append(ch)
        elif not stack or stack.pop() != pairs[ch]:
            return "NO\n"
    return "YES\n" if not stack else "NO\n"


def solve_max_subarray(text):
    data = list(map(int, text.split()))
    best, current = None, 0
    for x in data[1:]:
        current = max(x, current + x)
        best = current if best is None else max(best, current)
    return f"{best}\n"


def solve_islands(text):
    lines = text.split("\n")
    r, c = map(int, lines[0].split())
    grid = [list(line) for line in lines[1:1 + r]]
    count = 0
    for i in range(r):
        for j in range(c):
            if grid[i][j] == "#":
                count += 1
                grid[i][j] = "."
                queue = deque([(i, j)])
                while queue:
                    y, x = queue.popleft()
                    for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                        if 0 <= ny < r and 0 <= nx < c and grid[ny][nx] == "#":
                            grid[ny][nx] = "."
                            queue.append((ny, nx))
    return f"{count}\n"


def solve_shortest_paths(text):
    data = list(map(int, text.split()))
    n, m = data[0], data[1]
    graph = [[] for _ in range(n + 1)]
    for k in range(m):
        u, v, w = data[2 + 3 * k: 5 + 3 * k]
        graph[u].append((v, w))
        graph[v].append((u, w))
    dist = [None] * (n + 1)
    dist[1] = 0
    heap = [(0, 1)]
    while heap:
        d, u = heapq.heappop(heap)
        if d > dist[u]:
            continue
        for v, w in graph[u]:
            if dist[v] is None or d + w < dist[v]:
                dist[v] = d + w
                heapq.heappush(heap, (d + w, v))
    return " ".join(str(-1 if d is None else d) for d in dist[1:]) + "\n"


def solve_balance(text):
    data = list(map(int, text.split()))
    values = data[1:]
    total = sum(values)
    left = 0
    for i, x in enumerate(values):
        if left == total - left - x:
            return f"{i}\n"
        left += x
    return "-1\n"


def solve_stock(text):
    data = list(map(int, text.split()))
    best, cheapest = 0, None
    for price in data[1:]:
        if cheapest is not None:
            best = max(best, price - cheapest)
        cheapest = price if cheapest is None else min(cheapest, price)
    return f"{best}\n"


def solve_distinct(text):
    data = list(map(int, text.split()))
    last, start, best = {}, 0, 0
    for i, x in enumerate(data[1:]):
        if last.get(x, -1) >= start:
            start = last[x] + 1
        last[x] = i
        best = max(best, i - start + 1)
    return f"{best}\n"


def solve_range_count(text):
    data = list(map(int, text.split()))
    n, q = data[0], data[1]
    values = sorted(data[2:2 + n])
    out = []
    for k in range(q):
        a, b = data[2 + n + 2 * k], data[3 + n + 2 * k]
        out.append(bisect.bisect_right(values, b) - bisect.bisect_left(values, a))
    return "".join(f"{c}\n" for c in out)


def solve_intervals(text):
    data = list(map(int, text.split()))
    n = data[0]
    spans = sorted(zip(data[1:1 + 2 * n:2], data[2:2 + 2 * n:2]))
    blocks, covered = 0, 0
    current_l, current_r = None, None
    for l, r in spans:
        if current_r is None or l > current_r:
            if current_r is not None:
                blocks += 1
                covered += current_r - current_l
            current_l, current_r = l, r
        else:
            current_r = max(current_r, r)
    blocks += 1
    covered += current_r - current_l
    return f"{blocks} {covered}\n"


def solve_lis(text):
    data = list(map(int, text.split()))
    tails = []
    for x in data[1:]:
        k = bisect.bisect_left(tails, x)
        if k == len(tails):
            tails.append(x)
        else:
            tails[k] = x
    return f"{len(tails)}\n"


def solve_groups(text):
    data = list(map(int, text.split()))
    n, m = data[0], data[1]
    parent = list(range(n + 1))

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    groups = n
    for k in range(m):
        a, b = find(data[2 + 2 * k]), find(data[3 + 2 * k])
        if a != b:
            parent[a] = b
            groups -= 1
    return f"{groups}\n"


def solve_rectangle(text):
    data = list(map(int, text.split()))
    heights = data[1:] + [0]
    stack, best = [], 0
    for i, h in enumerate(heights):
        start = i
        while stack and stack[-1][1] >= h:
            start, top = stack.pop()
            best = max(best, top * (i - start))
        stack.append((start, h))
    return f"{best}\n"


def solve_halls(text):
    data = list(map(int, text.split()))
    n = data[0]
    starts = sorted(data[1:1 + 2 * n:2])
    ends = sorted(data[2:2 + 2 * n:2])
    best = busy = j = 0
    for s in starts:
        # A hall freed at time t can take a lecture starting at t
        while j < n and ends[j] <= s:
            j += 1
            busy -= 1
        busy += 1
        best = max(best, busy)
    return f"{best}\n"


def solve_coins(text):
    data = list(map(int, text.split()))
    n, amount, coins = data[0], data[1], data[2:]
    big = amount + 1
    best = [0] + [big] * amount
    for value in range(1, amount + 1):
        b = big
        for c in coins:
            if c <= value and best[value - c] + 1 < b:
                b = best[value - c] + 1
        best[value] = b
    return f"{best[amount] if best[amount] <= amount else -1}\n"


def solve_pairs(text):
    data = list(map(int, text.split()))
    n, k = data[0], data[1]
    values = sorted(data[2:2 + n])
    count, i, j = 0, 0, n - 1
    while i < j:
        if values[i] + values[j] <= k:
            count += j - i
            i += 1
        else:
            j -= 1
    return f"{count}\n"


def solve_ship(text):
    data = list(map(int, text.split()))
    n, days, weights = data[0], data[1], data[2:]

    def days_needed(capacity):
        used, load = 1, 0
        for w in weights:
            if load + w > capacity:
                used += 1
                load = 0
            load += w
        return used

    lo, hi = max(weights), sum(weights)
    while lo < hi:
        mid = (lo + hi) // 2
        if days_needed(mid) <= days:
            hi = mid
        else:
            lo = mid + 1
    return f"{lo}\n"


def solve_courses(text):
    data = list(map(int, text.split()))
    n, m = data[0], data[1]
    after = [[] for _ in range(n + 1)]
    needs = [0] * (n + 1)
    for k in range(m):
        a, b = data[2 + 2 * k], data[3 + 2 * k]
        after[a].append(b)
        needs[b] += 1
    ready = [v for v in range(1, n + 1) if needs[v] == 0]
    heapq.heapify(ready)
    order = []
    while ready:
        v = heapq.heappop(ready)
        order.append(v)
        for w in after[v]:
            needs[w] -= 1
            if needs[w] == 0:
                heapq.heappush(ready, w)
    if len(order) < n:
        return "IMPOSSIBLE\n"
    return " ".join(map(str, order)) + "\n"


def solve_edit(text):
    lines = text.split("\n")
    a, b = lines[0].strip(), lines[1].strip()
    previous = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        current = [i] + [0] * len(b)
        ai = a[i - 1]
        for j in range(1, len(b) + 1):
            if ai == b[j - 1]:
                current[j] = previous[j - 1]
            else:
                current[j] = 1 + min(previous[j - 1], previous[j], current[j - 1])
        previous = current
    return f"{previous[len(b)]}\n"


def solve_pattern(text):
    lines = text.split("\n")
    t, p = lines[0].strip(), lines[1].strip()
    # Knuth-Morris-Pratt: overlapping matches count
    fail = [0] * len(p)
    k = 0
    for i in range(1, len(p)):
        while k and p[i] != p[k]:
            k = fail[k - 1]
        if p[i] == p[k]:
            k += 1
        fail[i] = k
    count = k = 0
    for ch in t:
        while k and ch != p[k]:
            k = fail[k - 1]
        if ch == p[k]:
            k += 1
        if k == len(p):
            count += 1
            k = fail[k - 1]
    return f"{count}\n"


# ---------- test inputs ----------

# Hidden tests double in size up to the largest one. The judge times every test, so from a
# ladder like this it can see how a solution's running time grows with the input: doubled
# input and doubled time means about linear, four times the time about quadratic. The top size
# is chosen so the expected solution is fast and a quadratic one runs out of time.
def ladder(smallest, largest):
    sizes = []
    while smallest <= largest:
        sizes.append(smallest)
        smallest *= 2
    return sizes


def add_tests():
    tests = ["0 0\n", "-7 3\n", "1000000000000000000 1000000000000000000\n",
             "-1000000000000000000 -1000000000000000000\n", "123456789 -987654321\n"]
    tests += [f"{rng.randint(-10**18, 10**18)} {rng.randint(-10**18, 10**18)}\n" for _ in range(5)]
    return tests


def two_sum_case(n, lo, hi, late=False):
    while True:
        values = [rng.randint(lo, hi) for _ in range(n)]
        # For the size ladder the pair sits near the end, so trying every pair really costs
        # about n^2 / 2 steps on every size and the growth estimate sees a clean curve
        start = n - max(2, n // 10) if late else 0
        i, j = sorted(rng.sample(range(start, n), 2))
        target = values[i] + values[j]
        # Exactly one pair must work, so the answer is unique
        pairs = 0
        seen = {}
        for x in values:
            pairs += seen.get(target - x, 0)
            seen[x] = seen.get(x, 0) + 1
        if pairs == 1:
            return f"{n} {target}\n{' '.join(map(str, values))}\n"


def two_sum_tests():
    tests = ["2 0\n-5 5\n", "3 10\n10 0 7\n"]
    tests += [two_sum_case(n, -50, 50) for n in (5, 8, 12)]
    tests += [two_sum_case(n, -10**9, 10**9) for n in (100, 500)]
    tests += [two_sum_case(n, -10**9, 10**9, late=True) for n in ladder(1024, 131072)]
    # One step further up the ladder, so trying every pair runs out of time even on a fast
    # machine. Built with its own random numbers so the other problems' tests stay the same; the
    # only pair reaching the target is the two largest values, placed near the end.
    extra = random.Random(262144)
    n = 262144
    values = [extra.randint(-10**9, 9 * 10**8) for _ in range(n)]
    i, j = sorted(extra.sample(range(n - n // 10, n), 2))
    values[i], values[j] = 10**9 - 1, 10**9 - 2
    tests.append(f"{n} {2 * 10**9 - 3}\n{' '.join(map(str, values))}\n")
    return tests


def bracket_string(length, balanced):
    if balanced:
        out, stack = [], []
        while len(out) + len(stack) < length:
            if stack and (rng.random() < 0.5 or len(out) + len(stack) + 2 > length):
                out.append({"(": ")", "[": "]", "{": "}"}[stack.pop()])
            else:
                ch = rng.choice("([{")
                stack.append(ch)
                out.append(ch)
        while stack:
            out.append({"(": ")", "[": "]", "{": "}"}[stack.pop()])
        return "".join(out)
    return "".join(rng.choice("()[]{}") for _ in range(length))


def bracket_tests():
    tests = ["(\n", ")(\n", "([)]\n", "((((((((((\n", "{[()()]}\n"]
    s = bracket_string(5000, True)
    tests += [s + "\n", s[:-1] + "\n", s + "(\n"]
    tests += [bracket_string(100000, False) + "\n", "(" * 50000 + ")" * 50000 + "\n"]
    tests += [bracket_string(length, True) + "\n" for length in ladder(2048, 262144)]
    return tests


def max_subarray_tests():
    tests = ["1\n-7\n", "5\n-3 -1 -2 -5 -4\n", "4\n1 2 3 4\n"]
    for n in [10, 100, 500] + ladder(1024, 131072):
        values = [rng.randint(-10**9, 10**9) for _ in range(n)]
        tests.append(f"{n}\n{' '.join(map(str, values))}\n")
    big = [10**9] * 50000
    tests.append(f"50000\n{' '.join(map(str, big))}\n")
    return tests


def grid_case(r, c, density):
    rows = ["".join("#" if rng.random() < density else "." for _ in range(c)) for _ in range(r)]
    return f"{r} {c}\n" + "\n".join(rows) + "\n"


def island_tests():
    tests = ["1 1\n.\n", "1 1\n#\n", "2 2\n#.\n.#\n", "3 3\n###\n#.#\n###\n"]
    tests += [grid_case(10, 10, 0.4), grid_case(50, 80, 0.45), grid_case(300, 300, 0.5)]
    snake = []
    for i in range(300):
        if i % 2 == 0:
            snake.append("#" * 300)
        else:
            snake.append(("." * 299 + "#") if i % 4 == 1 else ("#" + "." * 299))
    tests.append("300 300\n" + "\n".join(snake) + "\n")  # one long winding island: deep recursion
    tests.append("300 300\n" + "\n".join("#" * 300 for _ in range(300)) + "\n")
    tests += [grid_case(side, side, 0.5) for side in ladder(32, 512)]
    return tests


def graph_case(n, m, max_w, connected=True):
    edges = []
    if connected:
        for v in range(2, n + 1):
            edges.append((rng.randint(1, v - 1), v, rng.randint(0, max_w)))
    while len(edges) < m:
        u, v = rng.randint(1, n), rng.randint(1, n)
        edges.append((u, v, rng.randint(0, max_w)))
    rng.shuffle(edges)
    return f"{n} {len(edges)}\n" + "".join(f"{u} {v} {w}\n" for u, v, w in edges)


def shortest_path_tests():
    tests = ["1 0\n", "3 1\n1 2 5\n", "4 4\n1 2 1\n2 3 1\n1 3 5\n3 4 0\n"]
    tests += [graph_case(10, 15, 20), graph_case(200, 600, 1000, connected=False)]
    # Weights up to 10^6 on paths of up to 65536 roads: distances need 64-bit integers
    tests += [graph_case(n, 2 * n, 10**6) for n in ladder(1024, 65536)]
    return tests


def numbers_line(values):
    return " ".join(map(str, values))


def balance_case(n, has_point=True):
    values = [rng.randint(-10**6, 10**6) for _ in range(n)]
    if has_point and n >= 3:
        # Put the balance point near the end and move the numbers after it until both sides
        # match, so checking every position by adding up both sides costs about n^2 steps
        p = n - max(1, n // 10) - 1
        diff = sum(values[:p]) - sum(values[p + 1:])
        # Raise the right side first; if it cannot take all of it, lower the left side
        for k, sign in [(k, 1) for k in range(p + 1, n)] + [(k, -1) for k in range(p)]:
            if diff == 0:
                break
            want = diff * sign
            step = max(-10**6 - values[k], min(10**6 - values[k], want))
            values[k] += step
            diff -= step * sign
        assert diff == 0
    return f"{n}\n{numbers_line(values)}\n"


def balance_tests():
    tests = ["1\n5\n", "2\n0 0\n", "3\n1 2 3\n", "5\n-1 3 2 -4 1\n", "4\n0 0 0 0\n"]
    tests += [balance_case(n) for n in (10, 100, 1000)] + [balance_case(500, has_point=False)]
    tests += [balance_case(n) for n in ladder(1024, 131072)]
    return tests


def stock_tests():
    tests = ["1\n5\n", "2\n5 3\n", "2\n3 5\n", "5\n9 7 5 3 1\n", "6\n3 3 3 3 3 3\n"]
    tests += [f"{n}\n{numbers_line(rng.randint(1, 10**6) for _ in range(n))}\n" for n in (10, 100, 1000)]
    # Prices drift down with noise, so the best trade is short and a cheapest-so-far scan
    # cannot stop early
    for n in ladder(1024, 131072):
        prices = [max(1, 10**6 - i * (10**6 // n) + rng.randint(-5000, 5000)) for i in range(n)]
        tests.append(f"{n}\n{numbers_line(prices)}\n")
    return tests


def distinct_case(n, repeats):
    values = rng.sample(range(1, 10**9), n)
    # A few repeats spread out, so the longest distinct stretch is long and trying every start
    # costs about n^2 steps
    for _ in range(repeats):
        i = rng.randrange(n)
        j = min(n - 1, i + rng.randint(1, max(1, n // 3)))
        values[j] = values[i]
    return f"{n}\n{numbers_line(values)}\n"


def distinct_tests():
    tests = ["1\n7\n", "4\n1 1 1 1\n", "5\n1 2 3 2 1\n", "6\n5 1 2 3 4 5\n"]
    tests += [f"{n}\n{numbers_line(rng.randint(1, 20) for _ in range(n))}\n" for n in (30, 1000)]
    tests += [distinct_case(n, 6) for n in (100, 1000)]
    tests += [distinct_case(n, 6) for n in ladder(1024, 131072)]
    return tests


def range_count_case(n, q, hi):
    values = [rng.randint(0, hi) for _ in range(n)]
    queries = []
    for _ in range(q):
        a, b = sorted((rng.randint(0, hi), rng.randint(0, hi)))
        queries.append(f"{a} {b}\n")
    return f"{n} {q}\n{numbers_line(values)}\n" + "".join(queries)


def range_count_tests():
    tests = ["1 1\n5\n5 5\n", "3 2\n1 1 1\n0 0\n1 1\n", "5 3\n4 1 3 1 5\n2 4\n1 5\n6 9\n"]
    tests += [range_count_case(20, 20, 10), range_count_case(1000, 500, 10**9)]
    tests += [range_count_case(n, n // 2, 10**6) for n in ladder(2048, 131072)]
    return tests


def interval_case(n, hi, length):
    spans = []
    for _ in range(n):
        l = rng.randint(0, hi - 1)
        spans.append(f"{l} {min(hi, l + rng.randint(1, length))}")
    return f"{n}\n" + "\n".join(spans) + "\n"


def interval_tests():
    tests = ["1\n0 10\n", "2\n0 5\n5 10\n", "2\n0 5\n6 10\n", "3\n1 9\n2 3\n4 5\n",
             "3\n0 1000000000\n0 1000000000\n500 600\n"]
    tests += [interval_case(10, 100, 10), interval_case(1000, 10**6, 300)]
    # Short intervals over a wide range: most stay separate, so merging pairs one at a time
    # takes many rounds
    tests += [interval_case(n, 10**9, 2000) for n in ladder(1024, 65536)]
    return tests


def lis_tests():
    tests = ["1\n5\n", "5\n1 2 3 4 5\n", "5\n5 4 3 2 1\n", "6\n2 2 2 2 2 2\n", "8\n0 8 4 12 2 10 6 14\n"]
    tests += [f"{n}\n{numbers_line(rng.randint(1, 10**6) for _ in range(n))}\n" for n in (100, 1000)]
    tests += [f"{n}\n{numbers_line(rng.randint(1, 10**6) for _ in range(n))}\n" for n in ladder(1024, 131072)]
    return tests


def groups_case(n, m, chain):
    pairs = []
    if chain:
        # One long line of friends: deep recursion, and naive merging makes long trees
        order = list(range(1, n + 1))
        rng.shuffle(order)
        pairs = [(order[i], order[i + 1]) for i in range(n - 1)][:m]
    while len(pairs) < m:
        pairs.append((rng.randint(1, n), rng.randint(1, n)))
    rng.shuffle(pairs)
    return f"{n} {m}\n" + "".join(f"{a} {b}\n" for a, b in pairs)


def groups_tests():
    tests = ["1 0\n", "3 0\n", "3 1\n1 2\n", "4 3\n1 2\n2 1\n3 3\n", "5 4\n1 2\n2 3\n3 4\n4 5\n"]
    tests += [groups_case(100, 60, False), groups_case(1000, 900, True)]
    tests += [groups_case(n, n - n // 64, True) for n in ladder(1024, 65536)]
    return tests


def rectangle_tests():
    tests = ["1\n5\n", "2\n2 4\n", "6\n2 1 5 6 2 3\n", "5\n0 0 0 0 0\n", "3\n1000000000 1000000000 1000000000\n"]
    tests += [f"{n}\n{numbers_line(rng.randint(0, 10**4) for _ in range(n))}\n" for n in (100, 1000)]
    tests.append(f"50000\n{numbers_line([7] * 50000)}\n")
    # Rising with noise: growing a rectangle outwards from every bar costs about n^2 steps
    for n in ladder(1024, 131072):
        heights = [i * (10**6 // n) + rng.randint(0, 10**6 // n) for i in range(n)]
        tests.append(f"{n}\n{numbers_line(heights)}\n")
    return tests


def halls_case(n, overlap):
    span = 10**9
    length = max(1, span // n * overlap)
    lines = []
    for _ in range(n):
        s = rng.randint(0, span - 1)
        lines.append(f"{s} {min(span, s + rng.randint(1, length))}")
    return f"{n}\n" + "\n".join(lines) + "\n"


def halls_tests():
    tests = ["1\n0 1\n", "2\n0 5\n5 10\n", "2\n0 5\n4 10\n", "3\n1 10\n2 3\n4 5\n", "4\n0 3\n0 3\n0 3\n3 6\n"]
    tests += [halls_case(10, 3), halls_case(1000, 20)]
    tests += [halls_case(n, 40) for n in ladder(1024, 131072)]
    return tests


def coins_case(n, amount, low=1, high=None, even=False):
    high = high or amount
    coins = set()
    while len(coins) < n:
        c = rng.randint(low, high)
        coins.add(c * 2 if even else c)
    coins = sorted(coins)
    rng.shuffle(coins)
    return f"{len(coins)} {amount}\n{' '.join(map(str, coins))}\n"


def coins_tests():
    tests = ["1 0\n5\n", "1 3\n2\n", "3 6\n1 3 4\n", "3 30\n1 10 25\n", "2 7\n2 4\n", "1 100000\n1\n"]
    tests += [coins_case(5, 100, 1, 40), coins_case(10, 5000, 50, 900)]
    tests += [coins_case(30, 50000, 200, 3000), coins_case(50, 50000, 1000, 5000), coins_case(50, 49999, 1, 2000, even=True)]
    tests += [coins_case(40, 99991, 400, 9000), coins_case(3, 99999, 97, 103)]
    return tests


def pairs_tests():
    tests = ["1 0\n5\n", "2 0\n-1 1\n", "2 0\n1 1\n", "4 5\n1 2 3 4\n", "5 -1\n-3 -3 -3 2 2\n"]
    tests += [f"{n} {rng.randint(-10**9, 10**9)}\n{' '.join(str(rng.randint(-10**9, 10**9)) for _ in range(n))}\n" for n in (100, 1000)]
    # k near the top: most pairs count, so the answer needs 64 bits on the largest tests
    tests += [f"{n} {rng.randint(10**9, 15 * 10**8)}\n{' '.join(str(rng.randint(-10**9, 10**9)) for _ in range(n))}\n" for n in ladder(1024, 131072)]
    return tests


def ship_case(n, days):
    weights = [rng.randint(1, 10**6) for _ in range(n)]
    return f"{n} {days}\n{' '.join(map(str, weights))}\n"


def ship_tests():
    tests = ["1 1\n7\n", "3 3\n1 2 3\n", "3 1\n1 2 3\n", "5 2\n9 1 1 1 9\n", "4 10\n4 4 4 4\n"]
    tests += [ship_case(100, 7), ship_case(1000, 1), ship_case(1000, 1000), ship_case(1000, 37)]
    tests += [ship_case(n, max(2, n // 50)) for n in ladder(1024, 131072)]
    return tests


def courses_case(n, m, cycle=False):
    order = list(range(1, n + 1))
    rng.shuffle(order)
    edges = set()
    while len(edges) < m:
        i, j = sorted(rng.sample(range(n), 2))
        edges.add((order[i], order[j]))
    edges = list(edges)
    if cycle:
        a, b = edges[rng.randrange(len(edges))]
        edges.append((b, a))
    rng.shuffle(edges)
    return f"{n} {len(edges)}\n" + "".join(f"{a} {b}\n" for a, b in edges)


def courses_tests():
    tests = ["1 0\n", "3 0\n", "3 2\n3 1\n3 2\n", "2 2\n1 2\n2 1\n", "4 3\n4 3\n3 2\n2 1\n"]
    tests += [courses_case(10, 12), courses_case(1000, 3000), courses_case(1000, 3000, cycle=True)]
    tests += [courses_case(n, 2 * n) for n in ladder(1024, 65536)]
    return tests


def letters(n, alphabet):
    return "".join(rng.choice(alphabet) for _ in range(n))


def edit_tests():
    tests = ["a\na\n", "a\nb\n", "horse\nros\n", "intention\nexecution\n", "abc\nabcabc\n", "kitten\nsitting\n"]
    tests += [f"{letters(rng.randint(1, 50), 'ab')}\n{letters(rng.randint(1, 50), 'ab')}\n" for _ in range(3)]
    for n in ladder(128, 2048):
        a = letters(n, "abcd")
        # A related string: some changes, so the distance is not just about the length
        b = list(a)
        for _ in range(n // 4):
            b[rng.randrange(len(b))] = rng.choice("abcd")
        tests.append(f"{a}\n{''.join(b)}\n")
    return tests


def pattern_tests():
    tests = ["aaaa\naa\n", "abc\nabcd\n", "abababa\naba\n", "hello\nll\n", "a\na\n", "abc\nd\n"]
    tests += [f"{letters(1000, 'ab')}\n{letters(3, 'ab')}\n", f"{letters(5000, 'ab')}\n{letters(12, 'ab')}\n"]
    # Long runs of a's against a^(m-1) b: comparing from every position costs n * m
    for n in ladder(2048, 262144):
        m = n // 8
        text = list("a" * n)
        for _ in range(16):
            text[rng.randrange(m, n)] = "b"
        tests.append(f"{''.join(text)}\n{'a' * (m - 1)}b\n")
    return tests


# ---------- problems ----------

PROBLEMS = [
    {
        "slug": "add-two-numbers",
        "title": "Add Two Numbers",
        "difficulty": "Easy",
        "statement": "Read two integers and print their sum.\n\n"
                     "A warm-up to check how input and output work here: read from standard input, "
                     "print to standard output, nothing else.",
        "input": "One line with two integers `a` and `b`.",
        "output": "Print `a + b`.",
        "constraints": "-10^18 <= a, b <= 10^18",
        "complexity": "O(1)",
        "samples": ["3 4\n", "-10 25\n"],
        "tests": lambda: add_tests(),
        "solve": solve_add,
    },
    {
        "slug": "two-sum",
        "title": "Two Sum",
        "difficulty": "Easy",
        "statement": "Given an array of `n` integers and a number `target`, find the two different "
                     "positions whose values add up to `target`. Exactly one such pair exists.",
        "input": "The first line has `n` and `target`. The second line has the `n` integers.",
        "output": "Print the two positions `i j` (0-based, with `i < j`).",
        "constraints": "2 <= n <= 262144\n-10^9 <= values <= 10^9\nExactly one pair adds up to target.",
        "complexity": "O(n)",
        "samples": ["4 9\n2 7 11 15\n", "3 6\n3 2 4\n"],
        "tests": lambda: two_sum_tests(),
        "solve": solve_two_sum,
    },
    {
        "slug": "valid-brackets",
        "title": "Valid Brackets",
        "difficulty": "Easy",
        "statement": "A string made of the characters `()[]{}` is valid if every opening bracket is "
                     "closed by the same kind of bracket, in the right order. Decide whether the "
                     "given string is valid.",
        "input": "One line with the string.",
        "output": "Print `YES` if the string is valid, otherwise `NO`.",
        "constraints": "1 <= length <= 262144",
        "complexity": "O(n)",
        "samples": ["()[]{}\n", "([)]\n", "{[]}\n"],
        "tests": lambda: bracket_tests(),
        "solve": solve_brackets,
    },
    {
        "slug": "maximum-subarray",
        "title": "Maximum Subarray Sum",
        "difficulty": "Medium",
        "statement": "Find the largest sum of a contiguous, non-empty part of the array.\n\n"
                     "Watch out: all numbers can be negative, and the sum can be larger than a "
                     "32-bit integer.",
        "input": "The first line has `n`. The second line has the `n` integers.",
        "output": "Print the largest sum.",
        "constraints": "1 <= n <= 131072\n-10^9 <= values <= 10^9",
        "complexity": "O(n)",
        "samples": ["9\n-2 1 -3 4 -1 2 1 -5 4\n", "3\n-3 -1 -2\n"],
        "tests": lambda: max_subarray_tests(),
        "solve": solve_max_subarray,
    },
    {
        "slug": "count-islands",
        "title": "Count the Islands",
        "difficulty": "Medium",
        "statement": "A map is a grid of land `#` and water `.`. Land cells that touch up, down, "
                     "left or right belong to the same island. Count the islands.\n\n"
                     "One island can wind through the whole grid, so deep recursion may run out "
                     "of stack.",
        "input": "The first line has the number of rows `r` and columns `c`. Then `r` lines of `c` "
                 "characters follow.",
        "output": "Print the number of islands.",
        "constraints": "1 <= r, c <= 512",
        "complexity": "O(r * c)",
        "samples": ["4 5\n##...\n##..#\n....#\n.#.##\n", "3 3\n...\n...\n...\n"],
        "tests": lambda: island_tests(),
        "solve": solve_islands,
    },
    {
        "slug": "shortest-paths",
        "title": "Shortest Paths",
        "difficulty": "Hard",
        "statement": "A country has `n` cities joined by `m` two-way roads, each with a length. "
                     "Find the length of the shortest route from city 1 to every city.",
        "input": "The first line has `n` and `m`. Each of the next `m` lines has a road `u v w`: "
                 "cities `u` and `v` joined by a road of length `w`.",
        "output": "Print `n` numbers on one line: the shortest distance from city 1 to cities "
                  "1, 2, ..., n, or `-1` for a city that cannot be reached.",
        "constraints": "1 <= n <= 65536\n0 <= m <= 131072\n0 <= w <= 10^6",
        "complexity": "O((n + m) log n)",
        "samples": ["4 4\n1 2 3\n2 3 4\n1 3 10\n3 4 1\n", "3 1\n1 2 7\n"],
        "tests": lambda: shortest_path_tests(),
        "solve": solve_shortest_paths,
    },
    {
        "slug": "balance-point",
        "title": "Balance Point",
        "difficulty": "Easy",
        "statement": "Find the first position in the array where the sum of the numbers to its left "
                     "equals the sum of the numbers to its right. The number at that position "
                     "belongs to neither side, and an empty side adds up to 0.\n\n"
                     "The sums can be larger than a 32-bit integer.",
        "input": "The first line has `n`. The second line has the `n` integers.",
        "output": "Print the first such position (0-based), or `-1` if there is none.",
        "constraints": "1 <= n <= 131072\n-10^6 <= values <= 10^6",
        "complexity": "O(n)",
        "samples": ["6\n1 7 3 6 5 6\n", "3\n1 2 3\n", "3\n2 1 -1\n"],
        "tests": lambda: balance_tests(),
        "solve": solve_balance,
    },
    {
        "slug": "stock-profit",
        "title": "Stock Profit",
        "difficulty": "Easy",
        "statement": "You know a stock's price for each of the next `n` days. Buy one share on one day "
                     "and sell it on a later day. What is the largest profit you can make? If every "
                     "trade loses money, do not trade: the profit is 0.",
        "input": "The first line has `n`. The second line has the `n` prices, in order of days.",
        "output": "Print the largest profit.",
        "constraints": "1 <= n <= 131072\n1 <= price <= 10^6",
        "complexity": "O(n)",
        "samples": ["6\n7 1 5 3 6 4\n", "5\n7 6 4 3 1\n"],
        "tests": lambda: stock_tests(),
        "solve": solve_stock,
    },
    {
        "slug": "longest-distinct-stretch",
        "title": "Longest Distinct Stretch",
        "difficulty": "Medium",
        "statement": "Find the length of the longest contiguous part of the array in which no number "
                     "appears twice.",
        "input": "The first line has `n`. The second line has the `n` integers.",
        "output": "Print the length of the longest stretch.",
        "constraints": "1 <= n <= 131072\n1 <= values <= 10^9",
        "complexity": "O(n)",
        "samples": ["8\n3 1 4 1 5 9 2 6\n", "5\n2 2 2 2 2\n"],
        "tests": lambda: distinct_tests(),
        "solve": solve_distinct,
    },
    {
        "slug": "count-in-range",
        "title": "Count in Range",
        "difficulty": "Medium",
        "statement": "You are given `n` numbers and `q` questions. Each question gives a range "
                     "`a b` and asks how many of the numbers are at least `a` and at most `b`.",
        "input": "The first line has `n` and `q`. The second line has the `n` numbers. Each of the "
                 "next `q` lines has a question `a b`.",
        "output": "Print `q` lines: the answer to each question, in order.",
        "constraints": "1 <= n <= 131072\n1 <= q <= 65536\n0 <= numbers <= 10^6\n0 <= a <= b <= 10^6",
        "complexity": "O((n + q) log n)",
        "samples": ["5 3\n4 1 3 1 5\n2 4\n1 1\n6 9\n"],
        "tests": lambda: range_count_tests(),
        "solve": solve_range_count,
    },
    {
        "slug": "covered-length",
        "title": "Covered Length",
        "difficulty": "Medium",
        "statement": "Painters each paint a stretch of a long wall, from position `l` to position "
                     "`r`. Stretches may overlap or touch. Joining all stretches that overlap or "
                     "touch gives separate painted blocks. Count the blocks and the total painted "
                     "length.",
        "input": "The first line has `n`. Each of the next `n` lines has a stretch `l r`.",
        "output": "Print the number of blocks and the total painted length.",
        "constraints": "1 <= n <= 65536\n0 <= l < r <= 10^9",
        "complexity": "O(n log n)",
        "samples": ["4\n1 3\n2 6\n8 10\n10 12\n", "2\n0 5\n1 2\n"],
        "tests": lambda: interval_tests(),
        "solve": solve_intervals,
    },
    {
        "slug": "longest-increasing-subsequence",
        "title": "Longest Increasing Subsequence",
        "difficulty": "Medium",
        "statement": "Pick some numbers from the array, keeping their order, so that each picked "
                     "number is strictly larger than the one before. What is the most numbers you "
                     "can pick?",
        "input": "The first line has `n`. The second line has the `n` integers.",
        "output": "Print the length of the longest increasing subsequence.",
        "constraints": "1 <= n <= 131072\n1 <= values <= 10^6",
        "complexity": "O(n log n)",
        "samples": ["8\n10 9 2 5 3 7 101 18\n", "4\n7 7 7 7\n"],
        "tests": lambda: lis_tests(),
        "solve": solve_lis,
    },
    {
        "slug": "friend-groups",
        "title": "Friend Groups",
        "difficulty": "Medium",
        "statement": "There are `n` people, numbered 1 to `n`, and a list of friendships. Friends of "
                     "friends are in the same group, however long the chain. How many groups are "
                     "there? Someone with no friends is a group on their own.\n\n"
                     "One chain of friends can include everyone, so deep recursion may run out of "
                     "stack.",
        "input": "The first line has `n` and `m`. Each of the next `m` lines has two people `a b` "
                 "who are friends.",
        "output": "Print the number of groups.",
        "constraints": "1 <= n <= 65536\n0 <= m <= 65536\n1 <= a, b <= n",
        "complexity": "O(n + m)",
        "samples": ["5 3\n1 2\n2 3\n4 5\n", "4 0\n"],
        "tests": lambda: groups_tests(),
        "solve": solve_groups,
    },
    {
        "slug": "largest-rectangle",
        "title": "Largest Rectangle",
        "difficulty": "Hard",
        "statement": "A skyline is made of `n` bars of width 1 standing side by side, with the given "
                     "heights. Find the area of the largest rectangle that fits inside the skyline.\n\n"
                     "The area can be larger than a 32-bit integer.",
        "input": "The first line has `n`. The second line has the `n` heights.",
        "output": "Print the largest area.",
        "constraints": "1 <= n <= 131072\n0 <= height <= 10^9",
        "complexity": "O(n)",
        "samples": ["6\n2 1 5 6 2 3\n", "2\n2 4\n"],
        "tests": lambda: rectangle_tests(),
        "solve": solve_rectangle,
    },
    {
        "slug": "lecture-halls",
        "title": "Lecture Halls",
        "difficulty": "Medium",
        "statement": "Each lecture runs from time `s` up to time `e`. Two lectures that overlap need "
                     "different halls; a hall freed at time `t` can take a lecture starting at `t`. "
                     "What is the smallest number of halls that fits every lecture?",
        "input": "The first line has `n`. Each of the next `n` lines has a lecture `s e`.",
        "output": "Print the smallest number of halls.",
        "constraints": "1 <= n <= 131072\n0 <= s < e <= 10^9",
        "complexity": "O(n log n)",
        "samples": ["3\n0 30\n5 10\n15 20\n", "2\n7 10\n2 4\n"],
        "tests": lambda: halls_tests(),
        "solve": solve_halls,
    },
    {
        "slug": "coin-change",
        "title": "Coin Change",
        "difficulty": "Medium",
        "statement": "You have coins of `n` different values and as many of each as you like. Pay "
                     "exactly `amount` with as few coins as possible.\n\n"
                     "Taking the largest coin first does not always work: with coins 1, 3 and 4, "
                     "the best way to pay 6 is 3 + 3.",
        "input": "The first line has `n` and `amount`. The second line has the `n` coin values.",
        "output": "Print the smallest number of coins, or `-1` if the amount cannot be paid exactly.",
        "constraints": "1 <= n <= 50\n0 <= amount <= 10^5\n1 <= coin <= 10^5\nn * amount <= 4 * 10^6",
        "complexity": "O(n * amount)",
        "samples": ["3 11\n1 2 5\n", "1 3\n2\n", "3 6\n1 3 4\n"],
        "tests": lambda: coins_tests(),
        "solve": solve_coins,
    },
    {
        "slug": "pair-count",
        "title": "Pair Count",
        "difficulty": "Medium",
        "statement": "Count the pairs of different positions `i < j` whose values add up to at "
                     "most `k`.\n\n"
                     "The count can be larger than a 32-bit integer.",
        "input": "The first line has `n` and `k`. The second line has the `n` integers.",
        "output": "Print the number of pairs.",
        "constraints": "1 <= n <= 131072\n-10^9 <= values <= 10^9\n-2 * 10^9 <= k <= 2 * 10^9",
        "complexity": "O(n log n)",
        "samples": ["4 5\n1 2 3 4\n", "3 0\n5 5 5\n"],
        "tests": lambda: pairs_tests(),
        "solve": solve_pairs,
    },
    {
        "slug": "ship-capacity",
        "title": "Ship Capacity",
        "difficulty": "Medium",
        "statement": "Packages wait in a line and must be shipped in that order, within `d` days. "
                     "Each day the ship takes packages from the front of the line until the next "
                     "one would make the load heavier than its capacity. What is the smallest "
                     "capacity that ships everything in `d` days?",
        "input": "The first line has `n` and `d`. The second line has the `n` package weights, "
                 "in order.",
        "output": "Print the smallest capacity.",
        "constraints": "1 <= n <= 131072\n1 <= d <= n\n1 <= weight <= 10^6",
        "complexity": "O(n log(sum of weights))",
        "samples": ["10 5\n1 2 3 4 5 6 7 8 9 10\n", "3 3\n3 2 2\n"],
        "tests": lambda: ship_tests(),
        "solve": solve_ship,
    },
    {
        "slug": "course-order",
        "title": "Course Order",
        "difficulty": "Medium",
        "statement": "Courses are numbered 1 to `n`. Some must be taken before others. Find an "
                     "order to take all of them. When several courses could come next, always "
                     "take the one with the smallest number, so the answer is unique.",
        "input": "The first line has `n` and `m`. Each of the next `m` lines has `a b`: course `a` "
                 "must be taken before course `b`.",
        "output": "Print the order as `n` numbers on one line, or `IMPOSSIBLE` if the "
                  "requirements go round in a circle.",
        "constraints": "1 <= n <= 65536\n0 <= m <= 131072\n1 <= a, b <= n",
        "complexity": "O((n + m) log n)",
        "samples": ["4 3\n2 1\n3 1\n4 3\n", "2 2\n1 2\n2 1\n"],
        "tests": lambda: courses_tests(),
        "solve": solve_courses,
    },
    {
        "slug": "edit-distance",
        "title": "Edit Distance",
        "difficulty": "Hard",
        "statement": "Change the first word into the second one. Each step inserts one letter, "
                     "deletes one letter, or replaces one letter with another. What is the "
                     "smallest number of steps?\n\n"
                     "The work here grows with the product of the two lengths, so the judge sees "
                     "the time grow about four times when both words double. That is expected.",
        "input": "Two lines, each with a word of lowercase letters.",
        "output": "Print the smallest number of steps.",
        "constraints": "1 <= length of each word <= 2048",
        "complexity": "O(n * m)",
        "samples": ["horse\nros\n", "intention\nexecution\n"],
        "tests": lambda: edit_tests(),
        "solve": solve_edit,
    },
    {
        "slug": "pattern-count",
        "title": "Pattern Count",
        "difficulty": "Hard",
        "statement": "Count how many times the pattern appears in the text. Appearances may "
                     "overlap: `aa` appears 3 times in `aaaa`.\n\n"
                     "Comparing the pattern at every position can take `n * m` steps when the text "
                     "and the pattern are long and nearly match everywhere.",
        "input": "The first line has the text, the second line the pattern, both of lowercase "
                 "letters.",
        "output": "Print the number of appearances.",
        "constraints": "1 <= length of text <= 262144\n1 <= length of pattern <= 262144",
        "complexity": "O(n + m)",
        "samples": ["abababa\naba\n", "hello\nworld\n"],
        "tests": lambda: pattern_tests(),
        "solve": solve_pattern,
    },
]


# Interviewer-only material: shown to the interviewer in a mock interview, never to the candidate
# or the tutor. guide: what a good answer looks like; hints: given one at a time, mildest first.
INTERVIEW = {
    "add-two-numbers": {
        "guide": """**Good answer:** read two numbers, print the sum. Use a 64-bit type: the values reach 10^18.

**Watch for:** `int` overflow; extra output like prompts ("Enter a:").

**Follow-ups:**
- What if the numbers had 1000 digits? (Add digit by digit with a carry.)
- How would you test it? (Negative, zero, the largest values.)""",
        "hints": [
            "How large can the numbers get? Will your type hold them?",
            "10^18 needs a 64-bit integer: `long long` in C++, `long` in Java. Python has no limit.",
            "Read both, print `a + b`, and print nothing else: the judge compares the output exactly.",
        ],
    },
    "two-sum": {
        "guide": """**Good answer:** one pass with a hash map from value to position. For each number x, check whether `target - x` was seen; if not, remember x. O(n) time, O(n) space.

**Acceptable first idea:** try every pair, O(n²). It times out on the largest tests; a strong candidate says so before coding.

**Alternative:** sort (value, index) pairs and use two pointers, O(n log n).

**Watch for:** using the same position twice; printing positions in the wrong order (i < j).

**Follow-ups:**
- The array is sorted: can you use O(1) extra space? (Two pointers.)
- Count all pairs instead of finding one.""",
        "hints": [
            "Brute force is fine as a start. How long does it take for n = 200,000?",
            "When you are at position j, what would you like to know about the numbers before it?",
            "Keep a map from each value seen so far to its position; look up `target - x` before storing x.",
        ],
    },
    "valid-brackets": {
        "guide": """**Good answer:** a stack. Push opening brackets; on a closing bracket the top must be its partner, then pop. Valid if the stack ends empty. O(n).

**Watch for:** forgetting to check the stack is empty at the end ("(("); popping an empty stack (")("); only counting brackets, which accepts "([)]".

**Follow-ups:**
- Only one kind of bracket: can you do it without a stack? (A counter.)
- Return the length of the longest valid substring.""",
        "hints": [
            "Which opening bracket must a closing bracket match?",
            "The most recent unmatched opening bracket is the one that must close first. What structure gives you the most recent item?",
            "Use a stack: push openers, and on a closer check and pop the top. At the end the stack must be empty.",
        ],
    },
    "balance-point": {
        "guide": """**Good answer:** compute the total once; walk left to right keeping the left sum. At i the right sum is `total - left - a[i]`. O(n), 64-bit sums.

**Acceptable first idea:** sum both sides for every i, O(n²): times out.

**Watch for:** 32-bit overflow; off-by-one (the element at i is on neither side); returning a later match instead of the first.

**Follow-ups:**
- Return every balance point.
- Same question on a stream where you can only read once (needs the total first: two passes or storing).""",
        "hints": [
            "What repeated work happens if you add up both sides for every position?",
            "If you know the total of the whole array and the sum to the left, what is the sum to the right?",
            "One pass for the total, one pass keeping the left sum; compare `left` with `total - left - a[i]`. Use 64-bit numbers.",
        ],
    },
    "stock-profit": {
        "guide": """**Good answer:** one pass keeping the cheapest price so far; at each day the best sale today is `price - cheapest`. O(n), O(1) space.

**Acceptable first idea:** every buy/sell pair, O(n²): times out.

**Watch for:** selling before buying; returning a negative profit instead of 0.

**Follow-ups:**
- Any number of trades (sum all rises).
- At most two trades (DP with four states).
- A fee per trade.""",
        "hints": [
            "If you sell on day j, which day would you have liked to buy on?",
            "The best buy for selling on day j is the cheapest day before j. Can you keep that as you go?",
            "Walk once, keeping the minimum price so far and the best `price - minimum`.",
        ],
    },
    "maximum-subarray": {
        "guide": """**Good answer:** Kadane: the best sum ending at i is `max(a[i], best ending at i-1 + a[i])`; the answer is the largest of these. O(n), 64-bit.

**Alternative:** prefix sums, keeping the minimum prefix so far (same idea as Stock Profit).

**Watch for:** starting the best at 0 (wrong when every number is negative); 32-bit overflow (a hidden test has 50,000 copies of 10^9).

**Follow-ups:**
- Return the positions, not just the sum.
- Circular array.
- Maximum product instead of sum.""",
        "hints": [
            "Think about the best sum of a part that ends exactly at position i.",
            "Given the best part ending at i - 1, the best ending at i either extends it or starts fresh at i. When does starting fresh win?",
            "Keep `current = max(a[i], current + a[i])` and the largest `current` seen. Start from the first element, not 0, and use 64 bits.",
        ],
    },
    "count-islands": {
        "guide": """**Good answer:** scan the grid; at each unvisited land cell, count an island and flood it (BFS, or DFS with an explicit stack). O(r × c).

**Watch for:** recursive DFS overflowing the stack (one hidden island winds through the whole grid); diagonal neighbours (they do not connect); marking cells visited only when popped, which adds them to the queue many times.

**Follow-ups:**
- Size of the largest island.
- Islands appear one at a time (online): union-find.""",
        "hints": [
            "When you find a land cell, how can you find everything that belongs to the same island?",
            "Visit all land reachable from it and mark it, so you never count it again. BFS or DFS both work.",
            "A recursive DFS can be 250,000 calls deep here. Use a queue (BFS) or your own stack, and mark cells when you add them.",
        ],
    },
    "longest-distinct-stretch": {
        "guide": """**Good answer:** sliding window with a map from value to its last position. When a[i] was seen inside the window, move the left end past that position. O(n).

**Acceptable first idea:** grow a set from every start, O(n × length): times out.

**Watch for:** moving the left end backwards (take the max); counting values outside the window as repeats.

**Follow-ups:**
- At most k distinct values instead of all distinct.
- Return the stretch itself.""",
        "hints": [
            "Suppose the stretch from l to r has no repeats and a[r + 1] repeats something. Do you need to restart from scratch?",
            "Keep a window [l, r]. When a new number is already in the window, where must the window now start?",
            "Store each value's last position; on a repeat inside the window, set l to one past that position (never move l back).",
        ],
    },
    "count-in-range": {
        "guide": """**Good answer:** sort once; each question is `upper_bound(b) - lower_bound(a)`. O((n + q) log n).

**Alternative:** counting array plus prefix sums over values up to 10^6: O(n + q + V).

**Acceptable first idea:** scan all numbers for each question, O(n × q): times out.

**Watch for:** off-by-one at the bounds (both inclusive); slow output with many lines.

**Follow-ups:**
- The numbers change between questions (Fenwick tree).""",
        "hints": [
            "Each question scans everything. What could you prepare once so every question is fast?",
            "In a sorted list, all numbers between a and b sit next to each other. How do you find where that block starts and ends?",
            "Sort, then use binary search: first position ≥ a and first position > b; the answer is the difference.",
        ],
    },
    "covered-length": {
        "guide": """**Good answer:** sort stretches by start; sweep, extending the current block while the next start ≤ current end, otherwise close the block. O(n log n).

**Watch for:** touching stretches (`l == current end`) must join; a stretch fully inside another; summing lengths before merging.

**Follow-ups:**
- Insert one new stretch into an already merged list.
- Points instead: how many stretches cover the busiest point? (Lecture Halls.)""",
        "hints": [
            "In what order would you like to look at the stretches?",
            "Sorted by start, a stretch either joins the block you are building or starts a new one. What decides which?",
            "Sort by l; keep the current block's end. If l ≤ end, extend end to max(end, r); otherwise add the block's length and start a new one.",
        ],
    },
    "longest-increasing-subsequence": {
        "guide": """**Good answer:** patience sorting: keep `tails[k]` = the smallest possible last value of an increasing subsequence of length k + 1. For each x, binary search the first tail ≥ x and replace it (or append). O(n log n).

**Acceptable first idea:** DP over all earlier positions, O(n²): times out.

**Watch for:** non-strict comparisons (equal values don't count: use lower_bound); thinking `tails` is the subsequence itself.

**Follow-ups:**
- Print the subsequence (keep parent links).
- Longest non-decreasing.""",
        "hints": [
            "Start with the O(n²) DP: the best length ending at each position. Which part is slow?",
            "For each length, only the smallest possible ending value matters. Why?",
            "Keep those smallest endings in an array; it stays sorted, so binary search where each new number goes.",
        ],
    },
    "friend-groups": {
        "guide": """**Good answer:** count connected components: BFS/DFS over an adjacency list, or union-find with path compression. O(n + m).

**Watch for:** recursive DFS with 65,536 people in one chain (stack overflow, and Python's recursion limit); people with no friends are groups too; duplicate and self friendships.

**Follow-ups:**
- Friendships arrive one by one; answer the number of groups after each (union-find).
- Size of the largest group.""",
        "hints": [
            "Draw it: people are points and friendships are lines. What is a group in that picture?",
            "Start from someone not yet in a group and visit everyone reachable. How many times do you start?",
            "Use BFS with a queue (or union-find); recursion may be 65,536 deep here.",
        ],
    },
    "lecture-halls": {
        "guide": """**Good answer:** sort by start; keep a min-heap of end times of halls in use. Before placing a lecture, free the hall that ends earliest if it ends ≤ start. The answer is the largest heap size. O(n log n).

**Alternative:** sort starts and ends separately and sweep (+1 at a start, −1 at an end; ends first on ties).

**Watch for:** a lecture ending at t and one starting at t share a hall.

**Follow-ups:**
- Assign hall numbers to each lecture.
- Maximum number of lectures one person can attend (greedy by end time).""",
        "hints": [
            "Think about one moment in time: how many halls are busy then?",
            "The answer is the largest number of lectures running at the same moment. Going through lectures by start time, what do you need to know about the ones already running?",
            "Keep the end times of running lectures in a min-heap; drop those that end by the current start; the biggest heap size is the answer.",
        ],
    },
    "coin-change": {
        "guide": """**Good answer:** DP over amounts: `best[v] = 1 + min(best[v - c])` over coins c ≤ v, `best[0] = 0`. O(n × amount). (BFS over amounts works too.)

**Watch for:** greedy (largest coin first) fails: coins 1, 3, 4 and amount 6; unreachable amounts (use a sentinel, answer −1); plain recursion without memo is exponential.

**Follow-ups:**
- Count the number of ways (order does not matter: loop coins outside).
- Each coin at most once (0/1 knapsack).""",
        "hints": [
            "Try greedy on coins 1, 3, 4 and amount 6. What goes wrong?",
            "If you knew the fewest coins for every smaller amount, how would you get the answer for amount v?",
            "Fill an array for amounts 0..amount: best[v] = 1 + the smallest best[v - c]. Mark unreachable amounts.",
        ],
    },
    "pair-count": {
        "guide": """**Good answer:** sort; two pointers i (low) and j (high). If a[i] + a[j] ≤ k, all pairs (i, i+1..j) count: add j − i and move i; else move j down. O(n log n). Binary search per element also works.

**Watch for:** a[i] + a[j] overflowing 32 bits (values up to 10^9, k up to 2·10^9); the count needs 64 bits; counting a pair twice or a position with itself.

**Follow-ups:**
- Pairs with sum exactly k.
- Triples with sum ≤ k (O(n²)).""",
        "hints": [
            "Does the order of the numbers matter for the answer? What could you do with that freedom?",
            "After sorting, if the smallest plus the largest is ≤ k, what do you know about the smallest with everything else?",
            "Two pointers from both ends: when a[i] + a[j] ≤ k add j − i and move i up, otherwise move j down. Use 64-bit sums and count.",
        ],
    },
    "ship-capacity": {
        "guide": """**Good answer:** binary search on the capacity between max(weight) and sum(weight); for a capacity, a greedy pass counts the days needed. O(n log sum).

**Key insight to listen for:** "if capacity C works, every larger capacity works too" (monotonic), which is what makes binary search valid.

**Acceptable first idea:** try capacities upward one by one: far too slow.

**Watch for:** the lower bound must be the heaviest package; sum needs 64 bits; off-by-one in the search.

**Follow-ups:**
- Split an array into k parts minimising the largest sum (same problem).
- Koko eating bananas.""",
        "hints": [
            "If you are told the capacity, how would you check whether d days are enough?",
            "If capacity C is enough, is C + 1 enough too? What does that let you do instead of trying every capacity?",
            "Binary search the smallest capacity between the heaviest package and the total, using the greedy day count as the test.",
        ],
    },
    "course-order": {
        "guide": """**Good answer:** Kahn's algorithm with a min-heap instead of a queue: start with all courses that need nothing; repeatedly take the smallest, lower its followers' counts, add those reaching 0. If fewer than n come out, there is a cycle. O((n + m) log n).

**Watch for:** DFS-based topological order does not give the smallest-number-first order; scanning all courses for the next one is O(n²).

**Follow-ups:**
- Any valid order (plain queue).
- The fewest terms if any number of courses fit in a term (longest path in a DAG).""",
        "hints": [
            "Which courses can you take right at the start?",
            "Taking a course may make others available. Track, for each course, how many requirements are still missing.",
            "Keep the available courses in a min-heap; take the smallest, update counts, push those that reach 0. If you finish early, print IMPOSSIBLE.",
        ],
    },
    "shortest-paths": {
        "guide": """**Good answer:** Dijkstra with a min-heap of (distance, city); skip stale entries. O((n + m) log n). 64-bit distances.

**Watch for:** BFS ignores weights; Bellman-Ford is O(n·m): too slow; 32-bit overflow (paths up to ~6.5·10^10); weight-0 roads; unreachable cities (−1).

**Follow-ups:**
- Print the path to a city.
- Negative weights (Bellman-Ford; why Dijkstra breaks).""",
        "hints": [
            "Would BFS work if every road had length 1? What changes when lengths differ?",
            "Always settle the closest city not yet settled. How do you find it quickly?",
            "Dijkstra with a priority queue of (distance, city); skip entries older than the city's best distance. Use 64-bit numbers.",
        ],
    },
    "largest-rectangle": {
        "guide": """**Good answer:** monotonic stack. For each bar find the nearest lower bar on each side; its rectangle is h × (right − left − 1). One pass with a stack of increasing heights does both. O(n).

**Acceptable first idea:** expand from each bar, O(n²) on rising skylines: times out.

**Watch for:** 64-bit area; equal heights; flushing the stack at the end (a 0-height sentinel).

**Follow-ups:**
- Largest rectangle of 1s in a binary matrix (this, row by row).""",
        "hints": [
            "Suppose the rectangle's height is exactly the height of bar i. How wide can it be?",
            "It stretches until a lower bar on each side. How can you find the nearest lower bar for every position quickly?",
            "Keep a stack of bars with increasing heights; when a lower bar arrives, pop and compute each popped bar's rectangle. Add a 0 at the end.",
        ],
    },
    "edit-distance": {
        "guide": """**Good answer:** DP table d[i][j] = distance between the first i letters of a and the first j of b; if letters match take d[i−1][j−1], else 1 + min(insert, delete, replace). O(n × m); two rows are enough for memory.

**Watch for:** base cases (d[i][0] = i, d[0][j] = j); recursion without memo is exponential.

**Follow-ups:**
- Print the steps.
- Only insertions and deletions (relates to the longest common subsequence).""",
        "hints": [
            "Look at the last letters of both words. What are your options for them?",
            "Each option leaves a smaller version of the same question. Which smaller questions, and how do you combine them?",
            "Fill a table over prefixes: equal letters cost nothing, otherwise 1 + the best of insert, delete and replace. Start from the empty prefixes.",
        ],
    },
    "pattern-count": {
        "guide": """**Good answer:** KMP (prefix function) or Z-function on `pattern + '#' + text`, counting overlapping matches. O(n + m).

**Acceptable first idea:** compare at every position, O(n × m) in the worst case (text of a's, pattern a…ab).

**Watch for:** missing overlapping matches; a pattern longer than the text.

**Follow-ups:**
- Rolling hash (Rabin-Karp) and its collision risk.
- Many patterns at once (Aho-Corasick).""",
        "hints": [
            "When a comparison fails after matching many letters, do you really need to go back and recompare them?",
            "What you matched is a prefix of the pattern. How far into the pattern can you continue from, using only the pattern itself?",
            "Precompute for each prefix of the pattern its longest proper prefix that is also a suffix (KMP), then scan the text once.",
        ],
    },
}


def section(name, body):
    return f"=== {name}\n{body.rstrip(chr(10))}\n"


def test_block(kind, text, solve):
    return f"=== {kind}\n{text}--- expected\n{solve(text)}"


def main():
    parser = argparse.ArgumentParser(description="Writes the problem files")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="folder to write into")
    parser.add_argument("--force", action="store_true", help="write even if nothing changed")
    args = parser.parse_args()
    out = args.out.resolve()

    # The files depend only on this script (the random seed is fixed), so its hash says
    # whether the files already there are current
    stamp = hashlib.sha256(HERE.read_bytes()).hexdigest()
    stamp_file = out / ".generated-by"
    if not args.force and stamp_file.is_file() and stamp_file.read_text().strip() == stamp \
            and (out / "index.txt").is_file():
        print(f"problems are up to date in {out}")
        return

    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.txt"):
        old.unlink()
    # Tests are made in list order, so adding a problem never changes another one's tests;
    # they are listed easiest first, within a difficulty in the order they were added
    missing = [p["slug"] for p in PROBLEMS if p["slug"] not in INTERVIEW or len(INTERVIEW[p["slug"]]["hints"]) < 2]
    if missing:
        sys.exit(f"no interviewer guide or hints for: {', '.join(missing)}")
    for problem in PROBLEMS:
        problem["tests"] = problem["tests"]()
    rank = {"Easy": 0, "Medium": 1, "Hard": 2}
    listed = sorted(PROBLEMS, key=lambda p: (rank[p["difficulty"]], PROBLEMS.index(p)))
    for position, problem in enumerate(listed, start=1):
        parts = [
            f"title: {problem['title']}\n",
            f"difficulty: {problem['difficulty']}\n",
            f"position: {position}\n",
            f"complexity: {problem['complexity']}\n",
            section("statement", problem["statement"]),
            section("input", problem["input"]),
            section("output", problem["output"]),
            section("constraints", problem["constraints"]),
        ]
        material = INTERVIEW[problem["slug"]]
        parts.append(section("guide", material["guide"]))
        parts += [section("hint", hint) for hint in material["hints"]]
        parts += [test_block("sample", t, problem["solve"]) for t in problem["samples"]]
        parts += [test_block("test", t, problem["solve"]) for t in problem["tests"]]
        (out / f"{problem['slug']}.txt").write_text("".join(parts), encoding="utf-8", newline="\n")
    (out / "index.txt").write_text("".join(p["slug"] + "\n" for p in listed), encoding="utf-8", newline="\n")
    stamp_file.write_text(stamp + "\n", encoding="utf-8")
    print(f"wrote {len(PROBLEMS)} problems to {out}")


if __name__ == "__main__":
    if sys.version_info < (3, 9):
        sys.exit("generate.py needs Python 3.9 or newer")
    main()
