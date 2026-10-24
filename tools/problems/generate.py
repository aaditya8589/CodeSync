"""Writes backend/src/main/resources/problems/*.txt: statements, examples and hidden tests.

Expected outputs come from the reference solutions below, so they are correct by construction.
Run from the repository root:  python tools/problems/generate.py
"""
import bisect
import heapq
import random
from collections import deque
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "backend" / "src" / "main" / "resources" / "problems"
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
        "tests": add_tests(),
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
        "constraints": "2 <= n <= 131072\n-10^9 <= values <= 10^9\nExactly one pair adds up to target.",
        "complexity": "O(n)",
        "samples": ["4 9\n2 7 11 15\n", "3 6\n3 2 4\n"],
        "tests": two_sum_tests(),
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
        "tests": bracket_tests(),
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
        "tests": max_subarray_tests(),
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
        "tests": island_tests(),
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
        "tests": shortest_path_tests(),
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
        "tests": balance_tests(),
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
        "tests": stock_tests(),
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
        "tests": distinct_tests(),
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
        "tests": range_count_tests(),
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
        "tests": interval_tests(),
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
        "tests": lis_tests(),
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
        "tests": groups_tests(),
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
        "tests": rectangle_tests(),
        "solve": solve_rectangle,
    },
]


def section(name, body):
    return f"=== {name}\n{body.rstrip(chr(10))}\n"


def test_block(kind, text, solve):
    return f"=== {kind}\n{text}--- expected\n{solve(text)}"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.txt"):
        old.unlink()
    # Listed easiest first; within a difficulty, in the order they were added. (Tests are still
    # generated in list order above, so adding a problem never changes another one's tests.)
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
        parts += [test_block("sample", t, problem["solve"]) for t in problem["samples"]]
        parts += [test_block("test", t, problem["solve"]) for t in problem["tests"]]
        (OUT / f"{problem['slug']}.txt").write_text("".join(parts), encoding="utf-8", newline="\n")
    (OUT / "index.txt").write_text("".join(p["slug"] + "\n" for p in listed), encoding="utf-8", newline="\n")
    print(f"wrote {len(PROBLEMS)} problems to {OUT}")


if __name__ == "__main__":
    main()
