#include <algorithm>
#include <cstdio>
#include <vector>
int main() {
    int n, d;
    scanf("%d %d", &n, &d);
    std::vector<long long> w(n);
    long long lo = 0, hi = 0;
    for (auto& x : w) { scanf("%lld", &x); lo = std::max(lo, x); hi += x; }
    auto fits = [&](long long cap) {
        int days = 1;
        long long load = 0;
        for (long long x : w) {
            if (load + x > cap) { days++; load = 0; }
            load += x;
        }
        return days <= d;
    };
    while (lo < hi) {
        long long mid = lo + (hi - lo) / 2;
        if (fits(mid)) hi = mid; else lo = mid + 1;
    }
    printf("%lld\n", lo);
}
