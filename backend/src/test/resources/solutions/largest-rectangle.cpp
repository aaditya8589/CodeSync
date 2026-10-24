#include <algorithm>
#include <cstdio>
#include <vector>
// Nearest smaller bar on each side, then every bar's widest rectangle
int main() {
    int n;
    scanf("%d", &n);
    std::vector<long long> h(n);
    for (auto& x : h) scanf("%lld", &x);
    std::vector<int> left(n), right(n), stack;
    for (int i = 0; i < n; i++) {
        while (!stack.empty() && h[stack.back()] >= h[i]) stack.pop_back();
        left[i] = stack.empty() ? -1 : stack.back();
        stack.push_back(i);
    }
    stack.clear();
    for (int i = n - 1; i >= 0; i--) {
        while (!stack.empty() && h[stack.back()] >= h[i]) stack.pop_back();
        right[i] = stack.empty() ? n : stack.back();
        stack.push_back(i);
    }
    long long best = 0;
    for (int i = 0; i < n; i++) best = std::max(best, h[i] * (right[i] - left[i] - 1));
    printf("%lld\n", best);
}
