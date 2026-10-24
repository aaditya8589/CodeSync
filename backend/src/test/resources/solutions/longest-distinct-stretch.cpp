#include <algorithm>
#include <cstdio>
#include <unordered_map>
#include <vector>
int main() {
    int n;
    scanf("%d", &n);
    std::vector<int> a(n);
    for (auto& x : a) scanf("%d", &x);
    std::unordered_map<int, int> count;
    count.reserve(2 * n);
    int best = 0;
    for (int left = 0, right = 0; right < n; right++) {
        while (count[a[right]] > 0) count[a[left++]]--;
        count[a[right]]++;
        best = std::max(best, right - left + 1);
    }
    printf("%d\n", best);
}
