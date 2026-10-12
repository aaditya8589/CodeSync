import sys

data = sys.stdin.read().split()
n, target = int(data[0]), int(data[1])
seen = {}
for j, x in enumerate(map(int, data[2:2 + n])):
    if target - x in seen:
        print(seen[target - x], j)
        break
    seen.setdefault(x, j)
