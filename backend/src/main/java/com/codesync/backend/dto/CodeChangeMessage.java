package com.codesync.backend.dto;

public class CodeChangeMessage {

    private String roomId;
    private String documentId;
    private String fileName;
    private String content;

    public CodeChangeMessage() {
    }

    public CodeChangeMessage(String roomId, String documentId, String fileName, String content) {
        this.roomId = roomId;
        this.documentId = documentId;
        this.fileName = fileName;
        this.content = content;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}