#include <algorithm>
#include <cstdio>
#include <vector>
int main() {
    int n;
    scanf("%d", &n);
    std::vector<int> tails;
    for (int i = 0; i < n; i++) {
        int x;
        scanf("%d", &x);
        auto it = std::lower_bound(tails.begin(), tails.end(), x);
        if (it == tails.end()) tails.push_back(x); else *it = x;
    }
    printf("%zu\n", tails.size());
}
