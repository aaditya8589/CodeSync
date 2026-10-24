#include <cstdio>
#include <vector>
// Breadth-first search with an explicit queue, independent of the union-find generator
int main() {
    int n, m;
    scanf("%d %d", &n, &m);
    std::vector<std::vector<int>> g(n + 1);
    for (int i = 0; i < m; i++) {
        int a, b;
        scanf("%d %d", &a, &b);
        g[a].push_back(b);
        g[b].push_back(a);
    }
    std::vector<char> seen(n + 1, 0);
    std::vector<int> queue;
    int groups = 0;
    for (int s = 1; s <= n; s++) {
        if (seen[s]) continue;
        groups++;
        seen[s] = 1;
        queue.assign(1, s);
        for (size_t k = 0; k < queue.size(); k++)
            for (int v : g[queue[k]])
                if (!seen[v]) { seen[v] = 1; queue.push_back(v); }
    }
    printf("%d\n", groups);
}
