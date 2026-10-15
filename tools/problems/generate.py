"""Writes backend/src/main/resources/problems/*.txt: statements, examples and hidden tests.

Expected outputs come from the reference solutions below, so they are correct by construction.
Run from the repository root:  python tools/problems/generate.py
"""
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
]


def section(name, body):
    return f"=== {name}\n{body.rstrip(chr(10))}\n"


def test_block(kind, text, solve):
    return f"=== {kind}\n{text}--- expected\n{solve(text)}"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.txt"):
        old.unlink()
    for position, problem in enumerate(PROBLEMS, start=1):
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
    (OUT / "index.txt").write_text("".join(p["slug"] + "\n" for p in PROBLEMS), encoding="utf-8", newline="\n")
    print(f"wrote {len(PROBLEMS)} problems to {OUT}")


if __name__ == "__main__":
    main()
