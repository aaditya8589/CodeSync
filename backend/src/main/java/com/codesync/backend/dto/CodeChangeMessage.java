package com.codesync.backend.dto;

public class CodeChangeMessage {

    private String roomId;
    private String documentId;
    private String fileName;
    private String content;

    // Client → server: the revision this edit was based on
    private Long baseRevision;

    // Server → clients: the document's revision after this edit was saved
    private Long revision;

    public CodeChangeMessage() {
    }

    public CodeChangeMessage(String roomId, String documentId, String fileName, String content, Long revision) {
        this.roomId = roomId;
        this.documentId = documentId;
        this.fileName = fileName;
        this.content = content;
        this.revision = revision;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getBaseRevision() { return baseRevision; }
    public void setBaseRevision(Long baseRevision) { this.baseRevision = baseRevision; }

    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
}