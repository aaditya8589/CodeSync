package com.codesync.backend.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Compiles and runs one program in a throw-away Docker container, using the image for its language.
 * Source and input both go in through stdin, so no host folders are mounted into the container:
 * a header line with the source's length in bytes, then the source, then the program's input.
 */
public class CodeRunner {

    static final String COMPILE_ERROR_MARKER = "__CODESYNC_COMPILE_ERROR__";
    static final String TEST_HEADER = "__CODESYNC_TEST__";

    // Per test. Every expected output must be smaller (ProblemFileTest checks), so output that
    // was cut off is always wrong rather than unjudgeable.
    // Enough for the largest expected answer (65536 distances in Shortest Paths)
    public static final int MAX_TEST_OUTPUT_BYTES = 1024 * 1024;
    static final int MAX_TEST_ERROR_BYTES = 4 * 1024;

    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final int KILLED_EXIT_CODE = 137;
    private static final int DOCKER_RUN_FAILED_EXIT_CODE = 125;

    private final List<String> dockerCommand;
    private final Map<Language, String> images;
    private final Duration wallClockLimit;
    private final int maxOutputBytes;

    public CodeRunner(
            List<String> dockerCommand,
            Map<Language, String> images,
            Duration wallClockLimit,
            int maxOutputBytes
    ) {
        this.dockerCommand = List.copyOf(dockerCommand);
        this.images = new EnumMap<>(images);
        this.wallClockLimit = wallClockLimit;
        this.maxOutputBytes = maxOutputBytes;
    }

    List<String> buildRunCommand(Language language, String source, String containerName) {
        // dd with iflag=fullblock copies exactly n bytes, so everything after the source is
        // left for input.txt. timeout exits 124 when the time limit is hit.
        String script = readSourceScript(language, source)
                + " && cat > /tmp/input.txt"
                + " && " + compileScript(language, source)
                + " && exec timeout -k 1 " + language.timeLimitSeconds() + " "
                + language.runCommand(source) + " < /tmp/input.txt";
        return dockerRun(language, containerName, script);
    }

    /**
     * Compiles once, then runs every test input in the same container, each with its own time
     * limit. For each test it prints a header line followed by exactly the announced number of
     * output bytes, so a program cannot fake a result by printing something that looks like a
     * header. Stops after a time or memory limit, since later tests would most likely fail the
     * same way and only make the judge slower.
     */
    List<String> buildTestCommand(Language language, String source, int testCount, String containerName) {
        String script = readSourceScript(language, source)
                + " && read -r k && i=0"
                + " && while [ $i -lt $k ]; do read -r len"
                + " && if [ $len -gt 0 ]; then dd of=/tmp/in$i bs=$len count=1 iflag=fullblock status=none;"
                + " else : > /tmp/in$i; fi && i=$((i+1)); done"
                + " && " + compileScript(language, source)
                + " && i=0 && while [ $i -lt $k ]; do"
                + " s=$(date +%s%N);"
                + " timeout -k 1 " + language.timeLimitSeconds() + " " + language.runCommand(source)
                + " < /tmp/in$i > /tmp/out 2> /tmp/err; c=$?;"
                + " e=$(date +%s%N);"
                + " head -c " + MAX_TEST_OUTPUT_BYTES + " /tmp/out > /tmp/o;"
                + " head -c " + MAX_TEST_ERROR_BYTES + " /tmp/err > /tmp/e;"
                + " printf '" + TEST_HEADER + " %s %s %s %s %s %s\\n' $i $c $(( (e - s) / 1000000 ))"
                + " $(wc -c < /tmp/o) $(wc -c < /tmp/e) $(wc -c < /tmp/out);"
                + " cat /tmp/o /tmp/e;"
                + " if [ $c -eq " + TIMEOUT_EXIT_CODE + " ] || [ $c -eq " + KILLED_EXIT_CODE + " ]; then break; fi;"
                + " i=$((i+1)); done";
        return dockerRun(language, containerName, script);
    }

    private static String readSourceScript(Language language, String source) {
        String file = "/tmp/" + language.sourceFile(source);
        return "read -r n"
                + " && if [ $n -gt 0 ]; then dd of=" + file + " bs=$n count=1 iflag=fullblock status=none;"
                + " else : > " + file + "; fi";
    }

    // Compile errors are flagged with a marker so they are not confused with the program's stderr
    private static String compileScript(Language language, String source) {
        return "if ! " + language.compileCommand(source) + " 2> /tmp/compile.txt; then"
                + " echo " + COMPILE_ERROR_MARKER + " >&2; cat /tmp/compile.txt >&2; exit 1; fi";
    }

    /**
     * On Windows, Java joins the arguments into one command line and does not escape double
     * quotes inside them, so a quoted part containing spaces is split into extra arguments and
     * the script reaching sh is cut short. The scripts therefore never use double quotes; every
     * value they expand is a number or a fixed path, so nothing needs them.
     */
    private List<String> dockerRun(Language language, String containerName, String script) {
        if (script.indexOf('"') >= 0) {
            throw new IllegalStateException("Container scripts must not contain double quotes");
        }
        String image = images.get(language);
        if (image == null) {
            throw new IllegalStateException("No image configured for " + language.displayName());
        }

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

    public ExecutionResult run(Language language, String source) {
        return run(language, source, "");
    }

    public ExecutionResult run(Language language, String source, String input) {
        String containerName = "codesync-run-" + UUID.randomUUID();
        Launch launch = launch(
                buildRunCommand(language, source, containerName),
                containerName,
                buildPayload(source, input),
                wallClockLimit,
                maxOutputBytes
        );

        if (launch.startError() != null) {
            return new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "", launch.startError(), null, launch.durationMs(), false);
        }

        String out = new String(launch.stdout(), StandardCharsets.UTF_8);
        String err = launch.stderr();

        if (!launch.finished()) {
            return new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, out,
                    err + "Stopped after " + wallClockLimit.toSeconds() + " seconds.", null, launch.durationMs(), launch.truncated());
        }

        int exitCode = launch.exitCode();

        if (err.startsWith(COMPILE_ERROR_MARKER)) {
            return new ExecutionResult(ExecutionStatus.COMPILE_ERROR, out, compilerOutput(err), exitCode, launch.durationMs(), launch.truncated());
        }

        if (exitCode == DOCKER_RUN_FAILED_EXIT_CODE || isDockerUnavailable(exitCode, err)) {
            return new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "",
                    "The code runner is not available: " + err.strip(), exitCode, launch.durationMs(), false);
        }

        return new ExecutionResult(statusFor(exitCode, err), out, err, exitCode, launch.durationMs(), launch.truncated());
    }

    /** One test's run inside a judged submission. */
    public record TestOutcome(
            ExecutionStatus status,
            int exitCode,
            long timeMs,
            String stdout,
            String stderr,
            boolean outputTruncated
    ) {
    }

    /**
     * The result of running a program against several inputs. If failure is set (compile error,
     * runner unavailable, or the whole run took too long), message explains it and outcomes holds
     * whatever tests finished before that.
     */
    public record TestRun(
            ExecutionStatus failure,
            String message,
            List<TestOutcome> outcomes,
            long durationMs
    ) {
    }

    public TestRun runTests(Language language, String source, List<String> inputs) {
        String containerName = "codesync-judge-" + UUID.randomUUID();
        // Every test may use its full time limit, plus compiling and starting the container
        Duration limit = wallClockLimit.plusSeconds((long) inputs.size() * (language.timeLimitSeconds() + 1));
        int maxBytes = inputs.size() * (MAX_TEST_OUTPUT_BYTES + MAX_TEST_ERROR_BYTES + 128) + maxOutputBytes;

        Launch launch = launch(
                buildTestCommand(language, source, inputs.size(), containerName),
                containerName,
                buildTestPayload(source, inputs),
                limit,
                maxBytes
        );

        if (launch.startError() != null) {
            return new TestRun(ExecutionStatus.INTERNAL_ERROR, launch.startError(), List.of(), launch.durationMs());
        }

        String err = launch.stderr();
        if (launch.finished() && err.startsWith(COMPILE_ERROR_MARKER)) {
            return new TestRun(ExecutionStatus.COMPILE_ERROR, compilerOutput(err), List.of(), launch.durationMs());
        }
        if (launch.finished() && (launch.exitCode() == DOCKER_RUN_FAILED_EXIT_CODE || isDockerUnavailable(launch.exitCode(), err))) {
            return new TestRun(ExecutionStatus.INTERNAL_ERROR, "The code runner is not available: " + err.strip(),
                    List.of(), launch.durationMs());
        }

        List<TestOutcome> outcomes = parseTestOutput(launch.stdout());
        if (!launch.finished()) {
            return new TestRun(ExecutionStatus.TIME_LIMIT_EXCEEDED,
                    "Stopped after " + limit.toSeconds() + " seconds.", outcomes, launch.durationMs());
        }
        return new TestRun(null, "", outcomes, launch.durationMs());
    }

    static List<TestOutcome> parseTestOutput(byte[] stdout) {
        List<TestOutcome> outcomes = new ArrayList<>();
        int position = 0;
        while (position < stdout.length) {
            int lineEnd = indexOf(stdout, (byte) '\n', position);
            if (lineEnd < 0) break;

            String[] header = new String(stdout, position, lineEnd - position, StandardCharsets.US_ASCII).split(" ");
            if (header.length != 7 || !header[0].equals(TEST_HEADER)) break;

            int exitCode = Integer.parseInt(header[2]);
            long timeMs = Long.parseLong(header[3]);
            int outBytes = Integer.parseInt(header[4]);
            int errBytes = Integer.parseInt(header[5]);
            long fullOutBytes = Long.parseLong(header[6]);

            int outStart = lineEnd + 1;
            int errStart = outStart + outBytes;
            int next = errStart + errBytes;
            if (next > stdout.length) break;  // cut off mid-test by the wall-clock limit

            String out = new String(stdout, outStart, outBytes, StandardCharsets.UTF_8);
            String err = new String(stdout, errStart, errBytes, StandardCharsets.UTF_8);
            outcomes.add(new TestOutcome(statusFor(exitCode, err), exitCode, timeMs, out, err, fullOutBytes > outBytes));
            position = next;
        }
        return outcomes;
    }

    private static int indexOf(byte[] bytes, byte value, int from) {
        for (int i = from; i < bytes.length; i++) if (bytes[i] == value) return i;
        return -1;
    }

    private static ExecutionStatus statusFor(int exitCode, String stderr) {
        return switch (exitCode) {
            case 0 -> ExecutionStatus.SUCCESS;
            case TIMEOUT_EXIT_CODE -> ExecutionStatus.TIME_LIMIT_EXCEEDED;
            case KILLED_EXIT_CODE -> ExecutionStatus.MEMORY_LIMIT_EXCEEDED;
            default -> ranOutOfMemory(stderr) ? ExecutionStatus.MEMORY_LIMIT_EXCEEDED : ExecutionStatus.RUNTIME_ERROR;
        };
    }

    private static String compilerOutput(String stderr) {
        return stderr.substring(COMPILE_ERROR_MARKER.length()).stripLeading();
    }

    private record Launch(
            String startError,
            boolean finished,
            int exitCode,
            byte[] stdout,
            String stderr,
            boolean truncated,
            long durationMs
    ) {
    }

    private Launch launch(List<String> command, String containerName, byte[] payload, Duration limit, int maxBytes) {
        long start = System.nanoTime();

        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            // Stops Docker Desktop from appending its "What's next" adverts to stderr
            builder.environment().put("DOCKER_CLI_HINTS", "false");
            process = builder.start();
        } catch (IOException exception) {
            return new Launch("Could not start Docker: " + exception.getMessage(), false, -1,
                    new byte[0], "", false, (System.nanoTime() - start) / 1_000_000);
        }

        OutputCollector stdout = OutputCollector.start(process.getInputStream(), maxBytes);
        OutputCollector stderr = OutputCollector.start(process.getErrorStream(), maxOutputBytes);

        // On its own thread: if the container stops reading, a blocked write must not keep
        // us from reaching the wall-clock limit below
        Thread.ofVirtual().start(() -> {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(payload);
            } catch (IOException ignored) {
                // Docker exited before reading everything; its stderr explains why
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finished = false;
        }

        if (!finished) {
            // Killing the docker CLI process does not stop the container, so kill it by name
            killContainer(containerName);
            process.destroyForcibly();
        }

        byte[] out = stdout.finishBytes();
        String err = new String(stderr.finishBytes(), StandardCharsets.UTF_8);
        return new Launch(null, finished, finished ? process.exitValue() : -1, out, err,
                stdout.truncated() || stderr.truncated(), (System.nanoTime() - start) / 1_000_000);
    }

    static byte[] buildPayload(String source, String input) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] header = (sourceBytes.length + "\n").getBytes(StandardCharsets.US_ASCII);

        byte[] payload = new byte[header.length + sourceBytes.length + inputBytes.length];
        System.arraycopy(header, 0, payload, 0, header.length);
        System.arraycopy(sourceBytes, 0, payload, header.length, sourceBytes.length);
        System.arraycopy(inputBytes, 0, payload, header.length + sourceBytes.length, inputBytes.length);
        return payload;
    }

    // "<source bytes>\n" source "<test count>\n" then "<input bytes>\n" input for every test
    static byte[] buildTestPayload(String source, List<String> inputs) {
        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        payload.writeBytes((sourceBytes.length + "\n").getBytes(StandardCharsets.US_ASCII));
        payload.writeBytes(sourceBytes);
        payload.writeBytes((inputs.size() + "\n").getBytes(StandardCharsets.US_ASCII));
        for (String input : inputs) {
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            payload.writeBytes((inputBytes.length + "\n").getBytes(StandardCharsets.US_ASCII));
            payload.writeBytes(inputBytes);
        }
        return payload.toByteArray();
    }

    // Java and Python report running out of memory themselves before the container is killed
    static boolean ranOutOfMemory(String stderr) {
        String trimmed = stderr.strip();
        return trimmed.contains("java.lang.OutOfMemoryError") || trimmed.endsWith("MemoryError");
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

        byte[] finishBytes() {
            try {
                thread.join(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            synchronized (kept) {
                return kept.toByteArray();
            }
        }

        boolean truncated() {
            return truncated;
        }
    }
}
