#include <cstdio>
#include <functional>
#include <queue>
#include <string>
#include <vector>
int main() {
    int n, m;
    scanf("%d %d", &n, &m);
    std::vector<std::vector<int>> after(n + 1);
    std::vector<int> needs(n + 1, 0);
    for (int i = 0; i < m; i++) {
        int a, b;
        scanf("%d %d", &a, &b);
        after[a].push_back(b);
        needs[b]++;
    }
    std::priority_queue<int, std::vector<int>, std::greater<int>> ready;
    for (int v = 1; v <= n; v++) if (needs[v] == 0) ready.push(v);
    std::vector<int> order;
    while (!ready.empty()) {
        int v = ready.top();
        ready.pop();
        order.push_back(v);
        for (int w : after[v]) if (--needs[w] == 0) ready.push(w);
    }
    if ((int) order.size() < n) { puts("IMPOSSIBLE"); return 0; }
    std::string out;
    for (size_t i = 0; i < order.size(); i++) {
        if (i) out += ' ';
        out += std::to_string(order[i]);
    }
    puts(out.c_str());
}
