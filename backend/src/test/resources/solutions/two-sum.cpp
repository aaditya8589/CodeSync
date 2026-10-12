#include <iostream>
#include <unordered_map>
#include <vector>
int main() {
    int n;
    long long target;
    std::cin >> n >> target;
    std::vector<long long> a(n);
    for (auto& x : a) std::cin >> x;
    std::unordered_map<long long, int> seen;
    for (int j = 0; j < n; j++) {
        auto it = seen.find(target - a[j]);
        if (it != seen.end()) { std::cout << it->second << " " << j << "\n"; return 0; }
        seen.emplace(a[j], j);
    }
}
