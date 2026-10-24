#include <algorithm>
#include <cstdio>
#include <utility>
#include <vector>
int main() {
    int n;
    scanf("%d", &n);
    std::vector<std::pair<long long, long long>> s(n);
    for (auto& [l, r] : s) scanf("%lld %lld", &l, &r);
    std::sort(s.begin(), s.end());
    long long blocks = 0, covered = 0, l = s[0].first, r = s[0].second;
    for (int i = 1; i < n; i++) {
        if (s[i].first > r) { blocks++; covered += r - l; l = s[i].first; r = s[i].second; }
        else r = std::max(r, s[i].second);
    }
    blocks++;
    covered += r - l;
    printf("%lld %lld\n", blocks, covered);
}
