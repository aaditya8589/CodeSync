package com.codesync.backend.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stands in for the docker CLI in CodeRunnerTest, so the runner's handling of every outcome
 * can be tested without Docker. The program's source decides what it pretends happened.
 */
public class FakeDocker {

    public static void main(String[] args) throws Exception {
        if (args[0].equals("kill")) {
            Files.writeString(killFile(args[1]), "killed");
            return;
        }

        String name = args[indexOf(args, "--name") + 1];

        // Same format the real container script reads: "<n>\n", n bytes of source, then input
        int length = Integer.parseInt(readLine());
        String source = new String(System.in.readNBytes(length), StandardCharsets.UTF_8);

        if (source.contains("FAKE_IGNORES_INPUT")) {
            while (!Files.exists(killFile(name))) Thread.sleep(50);
            Files.delete(killFile(name));
            System.exit(137);
        }

        String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);

        if (source.contains("FAKE_HELLO")) {
            System.out.print("hello\n");
        } else if (source.contains("FAKE_ECHO_INPUT")) {
            // Raw UTF-8 bytes, like real Docker; print() would use the platform's default encoding
            System.out.write(input.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
        } else if (source.contains("FAKE_ECHO_ARGS")) {
            System.out.print(String.join(" ", args));
        } else if (source.contains("FAKE_COMPILE_ERROR")) {
            System.err.print(CodeRunner.COMPILE_ERROR_MARKER + "\nmain.cpp:1:1: error: expected ';'\n");
            System.exit(1);
        } else if (source.contains("FAKE_CRASH")) {
            System.err.print("boom\n");
            System.exit(3);
        } else if (source.contains("FAKE_TIMEOUT")) {
            System.exit(124);
        } else if (source.contains("FAKE_OOM")) {
            System.exit(137);
        } else if (source.contains("FAKE_FLOOD")) {
            byte[] line = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 50_000; i++) System.out.write(line);
            System.out.flush();
        } else if (source.contains("FAKE_DAEMON_DOWN")) {
            System.err.print("failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine\n");
            System.exit(1);
        } else if (source.contains("FAKE_HANG")) {
            while (!Files.exists(killFile(name))) Thread.sleep(50);
            Files.delete(killFile(name));
            System.exit(137);
        }
    }

    private static String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = System.in.read()) != '\n') {
            if (c == -1) throw new IOException("missing header line");
            line.append((char) c);
        }
        return line.toString();
    }

    static Path killFile(String containerName) throws IOException {
        return Path.of(System.getProperty("java.io.tmpdir"), containerName + ".killed");
    }

    private static int indexOf(String[] args, String value) {
        for (int i = 0; i < args.length; i++) if (args[i].equals(value)) return i;
        throw new IllegalArgumentException("missing " + value);
    }
}
