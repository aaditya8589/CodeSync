#include <cstdio>
#include <vector>
int main() {
    int n;
    scanf("%d", &n);
    std::vector<long long> a(n);
    long long total = 0;
    for (auto& x : a) { scanf("%lld", &x); total += x; }
    long long left = 0;
    for (int i = 0; i < n; i++) {
        if (left == total - left - a[i]) { printf("%d\n", i); return 0; }
        left += a[i];
    }
    printf("-1\n");
}
