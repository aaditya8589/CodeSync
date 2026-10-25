package com.codesync.backend.tutor;

/** The server-sent events a tutor answer is streamed as. */
public final class TutorEvents {

    private TutorEvents() {
    }

    public static String text(String text) {
        return "data: {\"text\":" + Json.quote(text) + "}\n\n";
    }

    public static String done(boolean codeRemoved) {
        return "event: done\ndata: {\"codeRemoved\":" + codeRemoved + "}\n\n";
    }

    public static String error(String message) {
        return "event: error\ndata: {\"message\":" + Json.quote(message) + "}\n\n";
    }
}
