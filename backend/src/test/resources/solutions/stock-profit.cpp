#include <algorithm>
#include <cstdio>
int main() {
    int n;
    scanf("%d", &n);
    long long best = 0, cheapest = -1;
    for (int i = 0; i < n; i++) {
        long long p;
        scanf("%lld", &p);
        if (cheapest >= 0) best = std::max(best, p - cheapest);
        if (cheapest < 0 || p < cheapest) cheapest = p;
    }
    printf("%lld\n", best);
}
