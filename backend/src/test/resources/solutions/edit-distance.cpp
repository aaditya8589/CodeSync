#include <algorithm>
#include <iostream>
#include <string>
#include <vector>
// Full table, independent of the two-row generator
int main() {
    std::string a, b;
    std::cin >> a >> b;
    size_t n = a.size(), m = b.size();
    std::vector<std::vector<int>> d(n + 1, std::vector<int>(m + 1));
    for (size_t i = 0; i <= n; i++) d[i][0] = (int) i;
    for (size_t j = 0; j <= m; j++) d[0][j] = (int) j;
    for (size_t i = 1; i <= n; i++)
        for (size_t j = 1; j <= m; j++)
            d[i][j] = std::min({d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (a[i - 1] != b[j - 1])});
    std::cout << d[n][m] << "\n";
}
