#include <algorithm>
#include <cstdio>
#include <functional>
#include <queue>
#include <utility>
#include <vector>
// Lectures by start time; a min-heap of the end times of halls in use
int main() {
    int n;
    scanf("%d", &n);
    std::vector<std::pair<long long, long long>> lectures(n);
    for (auto& [s, e] : lectures) scanf("%lld %lld", &s, &e);
    std::sort(lectures.begin(), lectures.end());
    std::priority_queue<long long, std::vector<long long>, std::greater<long long>> ends;
    size_t best = 0;
    for (auto& [s, e] : lectures) {
        if (!ends.empty() && ends.top() <= s) ends.pop();
        ends.push(e);
        best = std::max(best, ends.size());
    }
    printf("%zu\n", best);
}
