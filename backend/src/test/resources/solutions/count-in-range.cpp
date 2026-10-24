#include <string>
#include <algorithm>
#include <cstdio>
#include <vector>
int main() {
    int n, q;
    scanf("%d %d", &n, &q);
    std::vector<int> a(n);
    for (auto& x : a) scanf("%d", &x);
    std::sort(a.begin(), a.end());
    std::string out;
    while (q--) {
        int lo, hi;
        scanf("%d %d", &lo, &hi);
        long count = std::upper_bound(a.begin(), a.end(), hi) - std::lower_bound(a.begin(), a.end(), lo);
        out += std::to_string(count);
        out += '\n';
    }
    fputs(out.c_str(), stdout);
}
