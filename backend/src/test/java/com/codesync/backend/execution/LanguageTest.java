package com.codesync.backend.execution;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageTest {

    @Test
    void languageComesFromTheFileExtension() {
        assertEquals(Optional.of(Language.CPP), Language.fromFileName("main.cpp"));
        assertEquals(Optional.of(Language.CPP), Language.fromFileName("x.cc"));
        assertEquals(Optional.of(Language.PYTHON), Language.fromFileName("solve.py"));
        assertEquals(Optional.of(Language.JAVA), Language.fromFileName("Main.JAVA"));
        assertEquals(Optional.empty(), Language.fromFileName("notes.txt"));
        assertEquals(Optional.empty(), Language.fromFileName("main.cpp.txt"));
        assertEquals(Optional.empty(), Language.fromFileName("python"));
    }

    @Test
    void mainClassIsThePublicClass() {
        assertEquals("Main", Language.mainClass("class Helper {}"));
        assertEquals("Solution", Language.mainClass("class Helper {}\npublic class Solution { }"));
        assertEquals("Solver", Language.mainClass("public final class Solver {}"));
    }

    @Test
    void mainClassNeverContainsShellCharacters() {
        assertEquals("X", Language.mainClass("public class X$(rm -rf /) {}"));
        assertEquals("Main", Language.mainClass("public class `id` {}"));
        assertEquals("Main", Language.mainClass("public class ;rm {}"));
    }
}
