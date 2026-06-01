package com.start.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outline_revision", indexes = {
        @Index(name = "idx_outline_rev_novel", columnList = "novel_id, revision_number")
})
public class OutlineRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "edit_note", length = 500)
    private String editNote;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
    }

    public OutlineRevision() {}

    public OutlineRevision(Long novelId, int revisionNumber, String content, String editNote) {
        this.novelId = novelId;
        this.revisionNumber = revisionNumber;
        this.content = content;
        this.editNote = editNote;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEditNote() { return editNote; }
    public void setEditNote(String editNote) { this.editNote = editNote; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
