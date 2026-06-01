package com.start.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "character_profile_revision", indexes = {
        @Index(name = "idx_cp_rev", columnList = "novel_id, character_profile_id, revision_number")
})
public class CharacterProfileRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "novel_id", nullable = false)
    private Long novelId;

    @Column(name = "character_profile_id", nullable = false)
    private Long characterProfileId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "character_name", length = 200)
    private String characterName;

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

    public CharacterProfileRevision() {}

    public CharacterProfileRevision(Long novelId, Long characterProfileId, int revisionNumber,
                                    String characterName, String content, String editNote) {
        this.novelId = novelId;
        this.characterProfileId = characterProfileId;
        this.revisionNumber = revisionNumber;
        this.characterName = characterName;
        this.content = content;
        this.editNote = editNote;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public Long getCharacterProfileId() { return characterProfileId; }
    public void setCharacterProfileId(Long characterProfileId) { this.characterProfileId = characterProfileId; }
    public Integer getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(Integer revisionNumber) { this.revisionNumber = revisionNumber; }
    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEditNote() { return editNote; }
    public void setEditNote(String editNote) { this.editNote = editNote; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
