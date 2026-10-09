package com.codesync.backend.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeRunnerTest {

    private static final List<String> FAKE_DOCKER = List.of(
            "java", "-cp", System.getProperty("java.class.path"), FakeDocker.class.getName());

    private static CodeRunner runner(Duration wallClockLimit) {
        return new CodeRunner(FAKE_DOCKER, "gcc:14", 2, wallClockLimit, 64 * 1024);
    }

    private static ExecutionResult run(String source) {
        return runner(Duration.ofSeconds(20)).runCpp(source);
    }

    @Test
    void sandboxFlagsAreAlwaysPresent() {
        String command = String.join(" ", runner(Duration.ofSeconds(20)).buildRunCommand("c1"));
        for (String flag : List.of("--network none", "--memory 256m", "--memory-swap 256m", "--cpus 1",
                "--pids-limit 64", "--read-only", "--user 1000:1000", "--cap-drop ALL",
                "--security-opt no-new-privileges", "--rm", "timeout -k 1 2")) {
            assertTrue(command.contains(flag), "missing " + flag);
        }
    }

    @Test
    void successfulRun() {
        ExecutionResult result = run("// FAKE_HELLO");
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals("hello\n", result.stdout());
        assertEquals(Integer.valueOf(0), result.exitCode());
        assertFalse(result.outputTruncated());
    }

    @Test
    void sourceReachesDockerThroughStdinNotArguments() {
        String command = String.join(" ", runner(Duration.ofSeconds(20)).buildRunCommand("c1"));
        assertFalse(command.contains("SECRET_SOURCE"));
        ExecutionResult result = run("// FAKE_ECHO_ARGS SECRET_SOURCE");
        assertFalse(result.stdout().contains("SECRET_SOURCE"));
    }

    @Test
    void compileErrorIsRecognizedAndMarkerRemoved() {
        ExecutionResult result = run("// FAKE_COMPILE_ERROR");
        assertEquals(ExecutionStatus.COMPILE_ERROR, result.status());
        assertEquals("main.cpp:1:1: error: expected ';'\n", result.stderr());
    }

    @Test
    void nonZeroExitIsRuntimeError() {
        ExecutionResult result = run("// FAKE_CRASH");
        assertEquals(ExecutionStatus.RUNTIME_ERROR, result.status());
        assertEquals(Integer.valueOf(3), result.exitCode());
        assertEquals("boom\n", result.stderr());
    }

    @Test
    void timeoutExitCodeIsTimeLimit() {
        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, run("// FAKE_TIMEOUT").status());
    }

    @Test
    void killedExitCodeIsMemoryLimit() {
        assertEquals(ExecutionStatus.MEMORY_LIMIT_EXCEEDED, run("// FAKE_OOM").status());
    }

    @Test
    void floodedOutputIsCappedButProgramStillFinishes() {
        ExecutionResult result = run("// FAKE_FLOOD");
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals(64 * 1024, result.stdout().length());
        assertTrue(result.outputTruncated());
    }

    @Test
    void dockerNotRunningIsInternalError() {
        ExecutionResult result = run("// FAKE_DAEMON_DOWN");
        assertEquals(ExecutionStatus.INTERNAL_ERROR, result.status());
        assertTrue(result.stderr().contains("not available"));
    }

    @Test
    void hungContainerIsKilledByNameAfterWallClockLimit() {
        long start = System.nanoTime();
        ExecutionResult result = runner(Duration.ofSeconds(3)).runCpp("// FAKE_HANG");
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, result.status());
        assertTrue(seconds < 10, "took " + seconds + "s");
    }

    @Test
    void missingDockerBinaryIsInternalError() {
        CodeRunner noDocker = new CodeRunner(List.of("definitely-not-docker-xyz"), "gcc:14", 2, Duration.ofSeconds(5), 1024);
        assertEquals(ExecutionStatus.INTERNAL_ERROR, noDocker.runCpp("int main() {}").status());
    }
}
