package com.codesync.backend.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Compiles and runs one C++ program in a throw-away Docker container.
 * The source goes in through stdin, so no host folders are mounted into the container.
 */
public class CodeRunner {

    static final String COMPILE_ERROR_MARKER = "__CODESYNC_COMPILE_ERROR__";

    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final int KILLED_EXIT_CODE = 137;
    private static final int DOCKER_RUN_FAILED_EXIT_CODE = 125;

    private final List<String> dockerCommand;
    private final String image;
    private final int runSeconds;
    private final Duration wallClockLimit;
    private final int maxOutputBytes;

    public CodeRunner(
            List<String> dockerCommand,
            String image,
            int runSeconds,
            Duration wallClockLimit,
            int maxOutputBytes
    ) {
        this.dockerCommand = List.copyOf(dockerCommand);
        this.image = image;
        this.runSeconds = runSeconds;
        this.wallClockLimit = wallClockLimit;
        this.maxOutputBytes = maxOutputBytes;
    }

    List<String> buildRunCommand(String containerName) {
        // Compile errors are reported with a marker so they are not confused with the
        // program's own stderr. timeout exits 124 when the time limit is hit.
        String script = "cat > /tmp/main.cpp"
                + " && if ! g++ -O2 -std=c++17 -o /tmp/main /tmp/main.cpp 2> /tmp/compile.txt; then"
                + " echo " + COMPILE_ERROR_MARKER + " >&2; cat /tmp/compile.txt >&2; exit 1; fi"
                + " && exec timeout -k 1 " + runSeconds + " /tmp/main";

        List<String> command = new ArrayList<>(dockerCommand);
        command.addAll(List.of(
                "run", "--rm", "-i",
                "--name", containerName,
                "--network", "none",
                "--memory", "256m",
                "--memory-swap", "256m",
                "--cpus", "1",
                "--pids-limit", "64",
                "--read-only",
                "--tmpfs", "/tmp:rw,exec,size=64m",
                "--user", "1000:1000",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                image,
                "sh", "-c", script
        ));
        return command;
    }

    public ExecutionResult runCpp(String source) {
        String containerName = "codesync-run-" + UUID.randomUUID();
        long start = System.nanoTime();

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(buildRunCommand(containerName));
            // Stops Docker Desktop from appending its "What's next" adverts to stderr
            builder.environment().put("DOCKER_CLI_HINTS", "false");
            process = builder.start();
        } catch (IOException exception) {
            return internalError("Could not start Docker: " + exception.getMessage(), start);
        }

        OutputCollector stdout = OutputCollector.start(process.getInputStream(), maxOutputBytes);
        OutputCollector stderr = OutputCollector.start(process.getErrorStream(), maxOutputBytes);

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(source.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // Docker exited before reading the source; its stderr explains why
        }

        boolean finished;
        try {
            finished = process.waitFor(wallClockLimit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finished = false;
        }

        if (!finished) {
            // Killing the docker CLI process does not stop the container, so kill it by name
            killContainer(containerName);
            process.destroyForcibly();
        }

        String out = stdout.finish();
        String err = stderr.finish();
        boolean truncated = stdout.truncated() || stderr.truncated();
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        if (!finished) {
            return new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, out,
                    err + "Stopped after " + wallClockLimit.toSeconds() + " seconds.", null, durationMs, truncated);
        }

        int exitCode = process.exitValue();

        if (err.startsWith(COMPILE_ERROR_MARKER)) {
            String compilerOutput = err.substring(COMPILE_ERROR_MARKER.length()).stripLeading();
            return new ExecutionResult(ExecutionStatus.COMPILE_ERROR, out, compilerOutput, exitCode, durationMs, truncated);
        }

        if (exitCode == DOCKER_RUN_FAILED_EXIT_CODE || isDockerUnavailable(exitCode, err)) {
            return new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "",
                    "The code runner is not available: " + err.strip(), exitCode, durationMs, false);
        }

        ExecutionStatus status = switch (exitCode) {
            case 0 -> ExecutionStatus.SUCCESS;
            case TIMEOUT_EXIT_CODE -> ExecutionStatus.TIME_LIMIT_EXCEEDED;
            case KILLED_EXIT_CODE -> ExecutionStatus.MEMORY_LIMIT_EXCEEDED;
            default -> ExecutionStatus.RUNTIME_ERROR;
        };

        return new ExecutionResult(status, out, err, exitCode, durationMs, truncated);
    }

    private boolean isDockerUnavailable(int exitCode, String stderr) {
        return exitCode == 1
                && (stderr.contains("failed to connect to the docker API")
                || stderr.contains("Cannot connect to the Docker daemon"));
    }

    private void killContainer(String containerName) {
        List<String> command = new ArrayList<>(dockerCommand);
        command.addAll(List.of("kill", containerName));
        try {
            Process kill = new ProcessBuilder(command).redirectErrorStream(true).start();
            kill.getInputStream().transferTo(OutputStream.nullOutputStream());
            kill.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException exception) {
            // Nothing more to do; --rm removes the container once it stops
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ExecutionResult internalError(String message, long start) {
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "", message, null, durationMs, false);
    }

    /**
     * Reads a stream on its own thread, keeping the first maxBytes and discarding the rest.
     * It must keep reading after the limit, or a program that prints forever would block on
     * a full pipe instead of running into its time limit.
     */
    private static final class OutputCollector {

        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private final int maxBytes;
        private volatile boolean truncated = false;
        private Thread thread;

        private OutputCollector(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        static OutputCollector start(InputStream stream, int maxBytes) {
            OutputCollector collector = new OutputCollector(maxBytes);
            collector.thread = Thread.ofVirtual().start(() -> collector.drain(stream));
            return collector;
        }

        private void drain(InputStream stream) {
            byte[] buffer = new byte[8192];
            try (stream) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    synchronized (kept) {
                        int room = maxBytes - kept.size();
                        if (room > 0) kept.write(buffer, 0, Math.min(room, read));
                        if (read > room) truncated = true;
                    }
                }
            } catch (IOException ignored) {
                // The process was killed; keep what was read
            }
        }

        String finish() {
            try {
                thread.join(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            synchronized (kept) {
                return kept.toString(StandardCharsets.UTF_8);
            }
        }

        boolean truncated() {
            return truncated;
        }
    }
}
