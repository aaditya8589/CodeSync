package com.codesync.backend.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTemplatesTest {

    @Test
    void acceptsNormalFileNames() {
        for (String name : List.of("main.py", "Solution.java", "two_sum.cpp", "notes.md", "a-b.c.txt", "x.HPP")) {
            assertNull(FileTemplates.problemWith(name), name);
        }
    }

    @Test
    void rejectsUnsafeOrUnsupportedNames() {
        for (String name : List.of("", "   ", "../main.py", "dir/main.py", "dir\\\\main.py", ".hidden.py",
                "my file.py", "main", "virus.exe", "a..py", "x".repeat(62) + ".py")) {
            assertNotNull(FileTemplates.problemWith(name), name);
        }
        assertNotNull(FileTemplates.problemWith(null));
    }

    @Test
    void starterContentMatchesTheLanguage() {
        assertTrue(FileTemplates.starterContent("a.cpp").contains("#include <iostream>"));
        assertTrue(FileTemplates.starterContent("a.py").contains("print(\"Hello, CodeSync!\")"));
        assertEquals("", FileTemplates.starterContent("notes.md"));
    }

    @Test
    void javaStarterClassIsNamedAfterTheFile() {
        assertTrue(FileTemplates.starterContent("Solution.java").startsWith("public class Solution {"));
        // "two-sum" is not a valid class name, so the class falls back to Main
        assertTrue(FileTemplates.starterContent("two-sum.java").startsWith("public class Main {"));
    }
}
