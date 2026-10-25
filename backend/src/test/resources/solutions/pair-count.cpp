#include <algorithm>
#include <cstdio>
#include <vector>
// Sorted: for each value, binary search how many later values keep the sum within k
int main() {
    int n;
    long long k;
    scanf("%d %lld", &n, &k);
    std::vector<long long> a(n);
    for (auto& x : a) scanf("%lld", &x);
    std::sort(a.begin(), a.end());
    long long count = 0;
    for (int i = 0; i < n; i++) {
        auto end = std::upper_bound(a.begin() + i + 1, a.end(), k - a[i]);
        count += end - (a.begin() + i + 1);
    }
    printf("%lld\n", count);
}
