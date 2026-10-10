package com.codesync.backend.execution;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the runner needs to know about one language: which files it runs, the image with
 * its compiler, and the shell commands that check and run the program inside the container.
 */
public enum Language {

    CPP("C++", List.of(".cpp", ".cc"), 2) {
        @Override
        String sourceFile(String source) {
            return "main.cpp";
        }

        @Override
        String compileCommand(String source) {
            return "g++ -O2 -std=c++17 -o /tmp/main /tmp/main.cpp";
        }

        @Override
        String runCommand(String source) {
            return "/tmp/main";
        }
    },

    PYTHON("Python", List.of(".py"), 5) {
        @Override
        String sourceFile(String source) {
            return "main.py";
        }

        // Only checks the syntax, so a typo is reported like a compile error instead of
        // failing halfway through a run
        @Override
        String compileCommand(String source) {
            return "python3 -m py_compile /tmp/main.py";
        }

        @Override
        String runCommand(String source) {
            return "python3 /tmp/main.py";
        }
    },

    JAVA("Java", List.of(".java"), 4) {
        // javac requires a public class to be in a file of the same name, so the file is
        // named after the program's public class (Main if there is none)
        @Override
        String sourceFile(String source) {
            return mainClass(source) + ".java";
        }

        @Override
        String compileCommand(String source) {
            return "javac -d /tmp/classes /tmp/" + sourceFile(source);
        }

        // -Xss: deep recursion is common in DSA solutions. -XX:-UsePerfData: the JVM would
        // otherwise write a statistics file for every run.
        @Override
        String runCommand(String source) {
            return "java -Xss64m -XX:MaxRAMPercentage=75 -XX:-UsePerfData -cp /tmp/classes " + mainClass(source);
        }
    };

    // The class name is put into a shell command, so only plain identifiers are accepted
    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("\\bpublic\\s+(?:(?:final|abstract|static)\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]{0,63})\\b");

    private final String displayName;
    private final List<String> extensions;
    private final int timeLimitSeconds;

    Language(String displayName, List<String> extensions, int timeLimitSeconds) {
        this.displayName = displayName;
        this.extensions = extensions;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    abstract String sourceFile(String source);

    abstract String compileCommand(String source);

    abstract String runCommand(String source);

    public String displayName() {
        return displayName;
    }

    /** CPU time the program gets. Python and the JVM start slower than a C++ binary. */
    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public static Optional<Language> fromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            for (String extension : language.extensions) {
                if (lower.endsWith(extension)) return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    static String mainClass(String source) {
        Matcher matcher = PUBLIC_CLASS.matcher(source);
        return matcher.find() ? matcher.group(1) : "Main";
    }
}
