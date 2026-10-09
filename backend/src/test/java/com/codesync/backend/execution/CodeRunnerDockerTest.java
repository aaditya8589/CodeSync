package com.codesync.backend.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs real attacks through real Docker. Skipped unless CODESYNC_DOCKER_TESTS=true,
 * because it needs Docker running and the gcc:14 image pulled.
 */
@EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
class CodeRunnerDockerTest {

    private final CodeRunner runner = new CodeRunner(List.of("docker"), "gcc:14", 2, Duration.ofSeconds(30), 64 * 1024);

    @Test
    void helloWorld() {
        ExecutionResult result = runner.runCpp("""
                #include <iostream>
                int main() { std::cout << "hello from the sandbox\\n"; }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), result.stderr());
        assertEquals("hello from the sandbox\n", result.stdout());
    }

    @Test
    void compileError() {
        ExecutionResult result = runner.runCpp("int main() { return 0 }");
        assertEquals(ExecutionStatus.COMPILE_ERROR, result.status());
        assertTrue(result.stderr().contains("error"), result.stderr());
    }

    @Test
    void runtimeError() {
        ExecutionResult result = runner.runCpp("int main() { return 3; }");
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertEquals(Integer.valueOf(3), result.exitCode());
    }

    @Test
    void infiniteLoopHitsTimeLimit() {
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, runner.runCpp("int main() { while (true) {} }").status());
    }

    @Test
    void memoryBombIsKilled() {
        ExecutionResult result = runner.runCpp("""
                #include <vector>
                int main() { std::vector<char> v; while (true) v.resize(v.size() + 10000000, 1); }
                """);
        assertTrue(result.status() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                || result.status() == ExecutionStatus.RUNTIME_ERROR, result.status() + " " + result.stderr());
    }

    @Test
    void networkIsUnreachable() {
        ExecutionResult result = runner.runCpp("""
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
        ExecutionResult result = runner.runCpp("""
                #include <unistd.h>
                int main() { while (true) fork(); }
                """);
        assertTrue(result.status() != ExecutionStatus.SUCCESS && result.status() != ExecutionStatus.INTERNAL_ERROR,
                result.status() + " " + result.stderr());
    }

    @Test
    void outputFloodIsCapped() {
        ExecutionResult result = runner.runCpp("""
                #include <cstdio>
                int main() { while (true) puts("spam spam spam spam spam spam spam"); }
                """);
        assertTrue(result.outputTruncated());
        assertEquals(64 * 1024, result.stdout().length());
    }

    @Test
    void cannotWriteOutsideTmp() {
        ExecutionResult result = runner.runCpp("""
                #include <cstdio>
                int main() { return fopen("/etc/hacked", "w") == nullptr ? 0 : 1; }
                """);
        assertEquals(ExecutionStatus.SUCCESS, result.status(), "managed to write to /etc");
    }
}
