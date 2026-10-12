#include <iostream>
#include <queue>
#include <string>
#include <vector>
int main() {
    int r, c;
    std::cin >> r >> c;
    std::vector<std::string> g(r);
    for (auto& row : g) std::cin >> row;
    int count = 0;
    for (int i = 0; i < r; i++)
        for (int j = 0; j < c; j++) {
            if (g[i][j] != '#') continue;
            count++;
            std::queue<std::pair<int, int>> q;
            q.push({i, j});
            g[i][j] = '.';
            while (!q.empty()) {
                auto [y, x] = q.front();
                q.pop();
                const int dy[] = {1, -1, 0, 0}, dx[] = {0, 0, 1, -1};
                for (int d = 0; d < 4; d++) {
                    int ny = y + dy[d], nx = x + dx[d];
                    if (ny >= 0 && ny < r && nx >= 0 && nx < c && g[ny][nx] == '#') {
                        g[ny][nx] = '.';
                        q.push({ny, nx});
                    }
                }
            }
        }
    std::cout << count << "\n";
}
