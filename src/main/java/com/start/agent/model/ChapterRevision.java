package com.start.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chapter_revision", indexes = {
        @Index(name = "idx_revision_chapter", columnList = "novel_id, chapter_number, revision_number")
})
public class ChapterRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "chapter_number", nullable = false)
    private Integer chapterNumber;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 200)
    private String title;

    @Column(name = "edit_note", length = 500)
    private String editNote;

    @Column(name = "commit_type", length = 24)
    private String commitType;

    @Column(name = "review_result_json", columnDefinition = "TEXT")
    private String reviewResultJson;

    @Column(name = "projection_status", length = 48)
    private String projectionStatus;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
    }

    public ChapterRevision() {}

    public ChapterRevision(Long novelId, Integer chapterNumber, int revisionNumber,
                           String content, String title, String editNote) {
        this.novelId = novelId;
        this.chapterNumber = chapterNumber;
        this.revisionNumber = revisionNumber;
        this.content = content;
        this.title = title;
        this.editNote = editNote;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public Integer getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(Integer chapterNumber) { this.chapterNumber = chapterNumber; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEditNote() { return editNote; }
    public void setEditNote(String editNote) { this.editNote = editNote; }
    public String getCommitType() { return commitType; }
    public void setCommitType(String commitType) { this.commitType = commitType; }
    public String getReviewResultJson() { return reviewResultJson; }
    public void setReviewResultJson(String reviewResultJson) { this.reviewResultJson = reviewResultJson; }
    public String getProjectionStatus() { return projectionStatus; }
    public void setProjectionStatus(String projectionStatus) { this.projectionStatus = projectionStatus; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
