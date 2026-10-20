package in.smarttech.dukaandost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owners")
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 20)
    private String phone;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "google_subject", length = 255, unique = true)
    private String googleSubject;

    @Column(length = 200)
    private String street;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "welcome_pending", nullable = false)
    private boolean welcomePending;

    @Column(name = "shop_name", length = 200)
    private String shopName;

    @Column(name = "avatar_key", length = 100)
    private String avatarKey;

    @Column(nullable = false, length = 16)
    private String theme = "SYSTEM";

    @Column(name = "in_app_notifications", nullable = false)
    private boolean inAppNotifications = true;

    @Column(name = "browser_push_enabled", nullable = false)
    private boolean browserPushEnabled;

    @Column(name = "payment_due_notifications", nullable = false)
    private boolean paymentDueNotifications = true;

    @Column(name = "overdue_notifications", nullable = false)
    private boolean overdueNotifications = true;

    @Column(name = "new_order_notifications", nullable = false)
    private boolean newOrderNotifications = true;

    @Column(name = "payment_received_notifications", nullable = false)
    private boolean paymentReceivedNotifications = true;

    @Column(name = "daily_summary_notifications", nullable = false)
    private boolean dailySummaryNotifications = true;

    @Column(name = "ui_language", nullable = false, length = 16)
    private String uiLanguage = "ENGLISH";

    @Column(name = "reminder_default_language", nullable = false, length = 16)
    private String reminderDefaultLanguage = "HINGLISH";

    @Column(name = "password_login_enabled", nullable = false)
    private boolean passwordLoginEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Owner() {
    }

    public Owner(String name, String email, String passwordHash, String phone) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
        this.emailVerified = false;
        this.phoneVerified = false;
        this.welcomePending = true;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public void setGoogleSubject(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public boolean isWelcomePending() {
        return welcomePending;
    }

    public void setWelcomePending(boolean welcomePending) {
        this.welcomePending = welcomePending;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getAvatarKey() {
        return avatarKey;
    }

    public void setAvatarKey(String avatarKey) {
        this.avatarKey = avatarKey;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isInAppNotifications() {
        return inAppNotifications;
    }

    public void setInAppNotifications(boolean inAppNotifications) {
        this.inAppNotifications = inAppNotifications;
    }

    public boolean isBrowserPushEnabled() {
        return browserPushEnabled;
    }

    public void setBrowserPushEnabled(boolean browserPushEnabled) {
        this.browserPushEnabled = browserPushEnabled;
    }

    public boolean isPaymentDueNotifications() {
        return paymentDueNotifications;
    }

    public void setPaymentDueNotifications(boolean paymentDueNotifications) {
        this.paymentDueNotifications = paymentDueNotifications;
    }

    public boolean isOverdueNotifications() {
        return overdueNotifications;
    }

    public void setOverdueNotifications(boolean overdueNotifications) {
        this.overdueNotifications = overdueNotifications;
    }

    public boolean isNewOrderNotifications() {
        return newOrderNotifications;
    }

    public void setNewOrderNotifications(boolean newOrderNotifications) {
        this.newOrderNotifications = newOrderNotifications;
    }

    public boolean isPaymentReceivedNotifications() {
        return paymentReceivedNotifications;
    }

    public void setPaymentReceivedNotifications(boolean paymentReceivedNotifications) {
        this.paymentReceivedNotifications = paymentReceivedNotifications;
    }

    public boolean isDailySummaryNotifications() {
        return dailySummaryNotifications;
    }

    public void setDailySummaryNotifications(boolean dailySummaryNotifications) {
        this.dailySummaryNotifications = dailySummaryNotifications;
    }

    public String getUiLanguage() {
        return uiLanguage;
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage;
    }

    public String getReminderDefaultLanguage() {
        return reminderDefaultLanguage;
    }

    public void setReminderDefaultLanguage(String reminderDefaultLanguage) {
        this.reminderDefaultLanguage = reminderDefaultLanguage;
    }

    public boolean isPasswordLoginEnabled() {
        return passwordLoginEnabled;
    }

    public void setPasswordLoginEnabled(boolean passwordLoginEnabled) {
        this.passwordLoginEnabled = passwordLoginEnabled;
    }
}
