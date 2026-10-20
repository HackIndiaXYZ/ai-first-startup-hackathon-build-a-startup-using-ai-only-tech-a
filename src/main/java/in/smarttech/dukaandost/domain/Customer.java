package in.smarttech.dukaandost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 16)
    private String phone;

    @Column(name = "preferred_reminder_language", length = 32)
    private String preferredReminderLanguage;

    @Column(name = "owner_id")
    private UUID ownerId;

    protected Customer() {
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public Customer(String name, String phone, String preferredReminderLanguage) {
        this.name = name;
        this.phone = phone;
        this.preferredReminderLanguage = preferredReminderLanguage;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreferredReminderLanguage() {
        return preferredReminderLanguage;
    }

    public void setPreferredReminderLanguage(String preferredReminderLanguage) {
        this.preferredReminderLanguage = preferredReminderLanguage;
    }
}
