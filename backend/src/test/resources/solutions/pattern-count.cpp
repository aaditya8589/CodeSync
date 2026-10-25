#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
// Z-function of pattern + '#' + text, independent of the KMP generator
int main() {
    std::string t, p;
    std::cin >> t >> p;
    std::string s = p + "#" + t;
    int n = (int) s.size(), m = (int) p.size();
    std::vector<int> z(n, 0);
    long long count = 0;
    for (int i = 1, l = 0, r = 0; i < n; i++) {
        if (i < r) z[i] = std::min(r - i, z[i - l]);
        while (i + z[i] < n && s[z[i]] == s[i + z[i]]) z[i]++;
        if (i + z[i] > r) { l = i; r = i + z[i]; }
        if (i > m && z[i] >= m) count++;
    }
    std::cout << count << "\n";
}
