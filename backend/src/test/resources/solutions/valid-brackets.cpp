#include <iostream>
#include <string>
int main() {
    std::string s, stack;
    std::cin >> s;
    for (char c : s) {
        if (c == '(' || c == '[' || c == '{') { stack.push_back(c); continue; }
        char open = c == ')' ? '(' : c == ']' ? '[' : '{';
        if (stack.empty() || stack.back() != open) { std::cout << "NO\n"; return 0; }
        stack.pop_back();
    }
    std::cout << (stack.empty() ? "YES" : "NO") << "\n";
}
