package com.codesync.backend.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs real attacks through real Docker. Skipped unless CODESYNC_DOCKER_TESTS=true,
 * because it needs Docker running and the images pulled (gcc:14, python:3.13-slim,
 * eclipse-temurin:21-jdk, or the ones named by CODESYNC_IMAGE_CPP / _PYTHON / _JAVA).
 */
@EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
class CodeRunnerDockerTest {

    private final CodeRunner runner = new CodeRunner(List.of("docker"), Map.of(
            Language.CPP, image("CODESYNC_IMAGE_CPP", "gcc:14"),
            Language.PYTHON, image("CODESYNC_IMAGE_PYTHON", "python:3.13-slim"),
            Language.JAVA, image("CODESYNC_IMAGE_JAVA", "eclipse-temurin:21-jdk")
    ), Duration.ofSeconds(30), 64 * 1024);

    private static String image(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void helloWorld() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <iostream>
                int main() { std::cout << "hello from the sandbox\\n"; }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("hello from the sandbox\n", result.stdout());
    }

    @Test
    void readsInput() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <iostream>
                int main() { long long a, b; std::cin >> a >> b; std::cout << a + b << "\\n"; }
                """, "3 4\n");
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("7\n", result.stdout());
    }

    @Test
    void readsMultilineInputWithoutTrailingNewline() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <iostream>
                #include <string>
                int main() { std::string line; int n = 0; while (std::getline(std::cin, line)) n++; std::cout << n; }
                """, "a\nb\nc");
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("3", result.stdout());
    }

    @Test
    void compileError() {
        ExecutionResult result = runner.run(Language.CPP, "int main() { return 0 }");
        assertEquals(ExecutionStatus.COMPILE_ERROR, result.status());
        assertTrue(result.stderr().contains("error"), result.stderr());
    }

    @Test
    void runtimeError() {
        ExecutionResult result = runner.run(Language.CPP, "int main() { return 3; }");
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertEquals(Integer.valueOf(3), result.exitCode());
    }

    @Test
    void infiniteLoopHitsTimeLimit() {
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, runner.run(Language.CPP, "int main() { while (true) {} }").status());
    }

    @Test
    void memoryBombIsKilled() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <vector>
                int main() { std::vector<char> v; while (true) v.resize(v.size() + 10000000, 1); }
                """);
        assertTrue(result.status() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                || result.status() == ExecutionStatus.RUNTIME_ERROR, result.status() + " " + result.stderr());
    }

    @Test
    void networkIsUnreachable() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <cstdio>
                #include <sys/socket.h>
                #include <netinet/in.h>
                #include <arpa/inet.h>
                int main() {
                    int s = socket(AF_INET, SOCK_STREAM, 0);
                    sockaddr_in addr{};
                    addr.sin_family = AF_INET;
                    addr.sin_port = htons(53);
                    inet_pton(AF_INET, "8.8.8.8", &addr.sin_addr);
                    if (connect(s, (sockaddr*)&addr, sizeof addr) == 0) { puts("CONNECTED"); return 0; }
                    perror("connect");
                    return 1;
                }
                """);
        assertFalse(result.stdout().contains("CONNECTED"));
        assertTrue(result.stderr().contains("unreachable"), result.stderr());
    }

    @Test
    void forkBombIsContained() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <unistd.h>
                int main() { while (true) fork(); }
                """);
        assertTrue(result.status() != ExecutionStatus.SUCCESS && result.status() != ExecutionStatus.INTERNAL_ERROR,
                result.status() + " " + result.stderr());
    }

    @Test
    void outputFloodIsCapped() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <cstdio>
                int main() { while (true) puts("spam spam spam spam spam spam spam"); }
                """);
        assertTrue(result.outputTruncated());
        assertEquals(64 * 1024, result.stdout().length());
    }

    @Test
    void cannotWriteOutsideTmp() {
        ExecutionResult result = runner.run(Language.CPP, """
                #include <cstdio>
                int main() { return fopen("/etc/hacked", "w") == nullptr ? 0 : 1; }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), "managed to write to /etc");
    }

    // ---------- Python ----------

    @Test
    void pythonReadsInput() {
        ExecutionResult result = runner.run(Language.PYTHON, """
                a, b = map(int, input().split())
                print(a + b)
                """, "3 4\n");
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("7\n", result.stdout());
    }

    @Test
    void pythonSyntaxErrorIsACompileError() {
        ExecutionResult result = runner.run(Language.PYTHON, "print('unclosed'\n");
        assertEquals(ExecutionStatus.COMPILE_ERROR, result.status(), result.stderr());
        assertTrue(result.stderr().contains("SyntaxError"), result.stderr());
    }

    @Test
    void pythonExceptionIsARuntimeError() {
        ExecutionResult result = runner.run(Language.PYTHON, "print(1 // 0)");
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertTrue(result.stderr().contains("ZeroDivisionError"), result.stderr());
    }

    @Test
    void pythonInfiniteLoopHitsTimeLimit() {
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, runner.run(Language.PYTHON, "while True: pass").status());
    }

    @Test
    void pythonCannotReachTheNetwork() {
        ExecutionResult result = runner.run(Language.PYTHON, """
                import socket
                socket.create_connection(("8.8.8.8", 53), timeout=3)
                print("CONNECTED")
                """);
        assertFalse(result.stdout().contains("CONNECTED"));
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status(), result.stderr());
    }

    // ---------- Java ----------

    @Test
    void javaReadsInput() {
        ExecutionResult result = runner.run(Language.JAVA, """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        System.out.println(in.nextLong() + in.nextLong());
                    }
                }
                """, "3 4\n");
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("7\n", result.stdout());
    }

    @Test
    void javaPublicClassWithAnotherName() {
        ExecutionResult result = runner.run(Language.JAVA, """
                public class Solution {
                    public static void main(String[] args) { System.out.println("solved"); }
                }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("solved\n", result.stdout());
    }

    @Test
    void javaCompileError() {
        ExecutionResult result = runner.run(Language.JAVA, "public class Main { int x = }");
        assertEquals(ExecutionStatus.COMPILE_ERROR, result.status());
        assertTrue(result.stderr().contains("error"), result.stderr());
    }

    @Test
    void javaExceptionIsARuntimeError() {
        ExecutionResult result = runner.run(Language.JAVA, """
                public class Main {
                    public static void main(String[] args) { int[] a = new int[1]; a[5] = 1; }
                }
                """);
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertTrue(result.stderr().contains("ArrayIndexOutOfBoundsException"), result.stderr());
    }

    @Test
    void javaDeepRecursionHasEnoughStack() {
        ExecutionResult result = runner.run(Language.JAVA, """
                public class Main {
                    static int depth(int n) { return n == 0 ? 0 : 1 + depth(n - 1); }
                    public static void main(String[] args) { System.out.println(depth(200000)); }
                }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("200000\n", result.stdout());
    }

    @Test
    void javaInfiniteLoopHitsTimeLimit() {
        ExecutionResult result = runner.run(Language.JAVA, """
                public class Main { public static void main(String[] args) { while (true) {} } }
                """);
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, result.status(), result.stderr());
    }

    @Test
    void javaMemoryBombIsAMemoryLimit() {
        ExecutionResult result = runner.run(Language.JAVA, """
                import java.util.*;
                public class Main {
                    public static void main(String[] args) {
                        List<long[]> blocks = new ArrayList<>();
                        while (true) blocks.add(new long[1_000_000]);
                    }
                }
                """);
        assertEquals(ExecutionStatus.MEMORY_LIMIT_EXCEEDED, result.status(), result.stderr());
    }

    @Test
    void pythonMemoryBombIsAMemoryLimit() {
        ExecutionResult result = runner.run(Language.PYTHON, "blocks = []\nwhile True: blocks.append(' ' * 10**7)");
        assertEquals(ExecutionStatus.MEMORY_LIMIT_EXCEEDED, result.status(), result.stderr());
    }

    @Test
    void pythonCannotWriteOutsideTmp() {
        ExecutionResult result = runner.run(Language.PYTHON, "open('/etc/hacked', 'w')");
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertTrue(result.stderr().contains("Read-only file system"), result.stderr());
    }
}
