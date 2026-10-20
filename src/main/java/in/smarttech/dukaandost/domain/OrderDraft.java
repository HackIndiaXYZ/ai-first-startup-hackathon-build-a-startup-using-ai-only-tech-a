package in.smarttech.dukaandost.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_drafts")
public class OrderDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String transcript;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DraftStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json", columnDefinition = "jsonb")
    private JsonNode extractionJson;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderDraft() {
    }

    @Column(name = "owner_id")
    private java.util.UUID ownerId;

    public java.util.UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(java.util.UUID ownerId) {
        this.ownerId = ownerId;
    }

    @jakarta.persistence.Column(name = "original_transcript", updatable = false)
    private String originalTranscript;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @jakarta.persistence.Column(name = "language_hint", length = 16)
    private NoteLanguage languageHint;

    public String getOriginalTranscript() {
        return originalTranscript;
    }

    public NoteLanguage getLanguageHint() {
        return languageHint;
    }

    public void setLanguageHint(NoteLanguage languageHint) {
        this.languageHint = languageHint;
    }

    public OrderDraft(String transcript, SourceType sourceType, DraftStatus status) {
        this.transcript = transcript;
        this.originalTranscript = transcript;
        this.sourceType = sourceType;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public DraftStatus getStatus() {
        return status;
    }

    public void setStatus(DraftStatus status) {
        this.status = status;
    }

    public JsonNode getExtractionJson() {
        return extractionJson;
    }

    public void setExtractionJson(JsonNode extractionJson) {
        this.extractionJson = extractionJson;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
