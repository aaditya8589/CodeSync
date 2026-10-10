package com.codesync.backend.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Which file names a room accepts, and what a new file starts with. */
public final class FileTemplates {

    public static final int MAX_FILES_PER_ROOM = 20;

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("cpp", "cc", "h", "hpp", "py", "java", "txt", "md");

    // Letters, digits, "_", "-" and "."; no spaces, no slashes, no leading dot
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private FileTemplates() {
    }

    /** Null if the name is acceptable, otherwise the reason it is not. */
    public static String problemWith(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "File name is required";
        }
        if (!VALID_NAME.matcher(fileName).matches()) {
            return "Use up to 64 letters, digits, '_', '-' or '.', without spaces or slashes";
        }
        if (fileName.contains("..")) {
            return "File name cannot contain '..'";
        }
        if (!ALLOWED_EXTENSIONS.contains(extension(fileName))) {
            return "Supported file types: .cpp, .cc, .h, .hpp, .py, .java, .txt, .md";
        }
        return null;
    }

    public static String starterContent(String fileName) {
        return switch (extension(fileName)) {
            case "cpp", "cc" -> """
                    #include <iostream>

                    using namespace std;

                    int main() {
                        cout << "Hello, CodeSync!" << endl;

                        return 0;
                    }
                    """;
            case "py" -> """
                    def main():
                        print("Hello, CodeSync!")


                    main()
                    """;
            case "java" -> {
                // The class is named after the file, so Solution.java holds class Solution
                String base = fileName.substring(0, fileName.length() - ".java".length());
                String className = JAVA_IDENTIFIER.matcher(base).matches() ? base : "Main";
                yield """
                        public class %s {
                            public static void main(String[] args) {
                                System.out.println("Hello, CodeSync!");
                            }
                        }
                        """.formatted(className);
            }
            default -> "";
        };
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
