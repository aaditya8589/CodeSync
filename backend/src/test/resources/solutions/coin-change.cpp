#include <cstdio>
#include <vector>
// Breadth-first over amounts paid so far: the first time amount is reached uses fewest coins
int main() {
    int n, amount;
    scanf("%d %d", &n, &amount);
    std::vector<int> coins(n);
    for (auto& c : coins) scanf("%d", &c);
    std::vector<int> steps(amount + 1, -1);
    std::vector<int> frontier{0};
    steps[0] = 0;
    for (int k = 0; !frontier.empty() && steps[amount] < 0; k++) {
        std::vector<int> next;
        for (int v : frontier)
            for (int c : coins)
                if (v + c <= amount && steps[v + c] < 0) { steps[v + c] = k + 1; next.push_back(v + c); }
        frontier.swap(next);
    }
    printf("%d\n", steps[amount]);
}
