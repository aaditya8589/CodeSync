package com.codesync.backend.execution;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeRunnerTest {

    private static final List<String> FAKE_DOCKER = List.of(
            "java", "-cp", System.getProperty("java.class.path"), FakeDocker.class.getName());

    private static final Map<Language, String> IMAGES = Map.of(
            Language.CPP, "gcc:14", Language.PYTHON, "python:3.13-slim", Language.JAVA, "eclipse-temurin:21-jdk");

    private static CodeRunner runner(Duration wallClockLimit) {
        return new CodeRunner(FAKE_DOCKER, IMAGES, wallClockLimit, 64 * 1024);
    }

    private static String command(Language language, String source) {
        return String.join(" ", runner(Duration.ofSeconds(20)).buildRunCommand(language, source, "c1"));
    }

    private static ExecutionResult run(String source) {
        return runner(Duration.ofSeconds(20)).run(Language.CPP, source);
    }

    @Test
    void sandboxFlagsAreAlwaysPresent() {
        String command = command(Language.CPP, "");
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
        String command = command(Language.CPP, "");
        assertFalse(command.contains("SECRET_SOURCE"));
        ExecutionResult result = run("// FAKE_ECHO_ARGS SECRET_SOURCE");
        assertFalse(result.stdout().contains("SECRET_SOURCE"));
    }

    @Test
    void inputReachesTheProgramExactly() {
        String input = "3 4\nline \u00e9 \ud83d\ude00\n\nno trailing newline";
        ExecutionResult result = runner(Duration.ofSeconds(20)).run(Language.CPP, "// FAKE_ECHO_INPUT \u00e9", input);
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals(input, result.stdout());
    }

    @Test
    void emptyInputIsAllowed() {
        ExecutionResult result = runner(Duration.ofSeconds(20)).run(Language.CPP, "// FAKE_ECHO_INPUT", "");
        assertEquals(ExecutionStatus.SUCCESS, result.status());
        assertEquals("", result.stdout());
    }

    @Test
    void payloadIsLengthHeaderThenSourceThenInput() {
        byte[] payload = CodeRunner.buildPayload("\u00e9x", "in");
        // "é" is 2 bytes in UTF-8, so the source is 3 bytes long
        assertEquals("3\n\u00e9xin", new String(payload, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void containerThatNeverReadsLargeInputIsStillKilledOnTime() {
        long start = System.nanoTime();
        String hugeInput = "x".repeat(5_000_000);
        ExecutionResult result = runner(Duration.ofSeconds(3)).run(Language.CPP, "// FAKE_IGNORES_INPUT", hugeInput);
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, result.status());
        assertTrue(seconds < 10, "took " + seconds + "s");
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
        ExecutionResult result = runner(Duration.ofSeconds(3)).run(Language.CPP, "// FAKE_HANG");
        long seconds = (System.nanoTime() - start) / 1_000_000_000;

        assertEquals(ExecutionStatus.TIME_LIMIT_EXCEEDED, result.status());
        assertTrue(seconds < 10, "took " + seconds + "s");
    }

    @Test
    void missingDockerBinaryIsInternalError() {
        CodeRunner noDocker = new CodeRunner(List.of("definitely-not-docker-xyz"), IMAGES, Duration.ofSeconds(5), 1024);
        assertEquals(ExecutionStatus.INTERNAL_ERROR, noDocker.run(Language.CPP, "int main() {}").status());
    }

    @Test
    void eachLanguageUsesItsOwnImageAndCommands() {
        String cpp = command(Language.CPP, "");
        assertTrue(cpp.contains(" gcc:14 ") && cpp.contains("g++ -O2") && cpp.contains("timeout -k 1 2 /tmp/main"), cpp);

        String python = command(Language.PYTHON, "print(1)");
        assertTrue(python.contains(" python:3.13-slim ") && python.contains("python3 -m py_compile /tmp/main.py")
                && python.contains("timeout -k 1 5 python3 /tmp/main.py"), python);

        String java = command(Language.JAVA, "public class Main { }");
        assertTrue(java.contains(" eclipse-temurin:21-jdk ") && java.contains("javac -d /tmp/classes /tmp/Main.java")
                && java.contains("timeout -k 1 4 java ") && java.endsWith("-cp /tmp/classes Main < /tmp/input.txt"), java);
    }

    @Test
    void everyLanguageGetsTheSameSandboxFlags() {
        for (Language language : Language.values()) {
            String command = command(language, "");
            for (String flag : List.of("--network none", "--memory 256m", "--pids-limit 64", "--read-only",
                    "--user 1000:1000", "--cap-drop ALL", "--security-opt no-new-privileges")) {
                assertTrue(command.contains(flag), language + " missing " + flag);
            }
        }
    }

    @Test
    void javaFileIsNamedAfterThePublicClass() {
        String java = command(Language.JAVA, "import java.util.*;\npublic final class Solution {\n}");
        assertTrue(java.contains("of=/tmp/Solution.java") && java.contains("javac -d /tmp/classes /tmp/Solution.java")
                && java.contains("-cp /tmp/classes Solution <"), java);
    }

    @Test
    void sourceTextNeverReachesTheShellCommand() {
        // Only a plain identifier is taken from the source; anything else falls back to Main
        String java = command(Language.JAVA, "public class X$(touch /tmp/pwned) {}");
        assertFalse(java.contains("pwned"), java);
        String other = command(Language.JAVA, "public class `id` {}");
        assertTrue(other.contains("/tmp/Main.java"), other);
    }

    @Test
    void outOfMemoryReportedByTheProgramIsAMemoryLimit() {
        assertEquals(ExecutionStatus.MEMORY_LIMIT_EXCEEDED, run("// FAKE_JAVA_OOM").status());
        assertTrue(CodeRunner.ranOutOfMemory("Traceback (most recent call last):\n  File \"/tmp/main.py\", line 2\nMemoryError\n"));
        assertFalse(CodeRunner.ranOutOfMemory("ZeroDivisionError: division by zero\n"));
    }
}
