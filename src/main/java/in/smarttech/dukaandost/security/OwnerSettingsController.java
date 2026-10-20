package in.smarttech.dukaandost.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class OwnerSettingsController {

    private final OwnerSettingsService settings;

    public OwnerSettingsController(OwnerSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public OwnerSettingsService.SettingsResponse getSettings() {
        return settings.getSettings();
    }

    @PutMapping
    public OwnerSettingsService.SettingsResponse updateSettings(@Valid @RequestBody SettingsUpdateRequest request) {
        return settings.updateSettings(request);
    }

    public record SettingsUpdateRequest(
            @NotBlank @Pattern(regexp = "^(LIGHT|DARK|SYSTEM)$") String theme,
            boolean inAppNotifications,
            boolean browserPushEnabled,
            boolean paymentDueNotifications,
            boolean overdueNotifications,
            boolean newOrderNotifications,
            boolean paymentReceivedNotifications,
            boolean dailySummaryNotifications,
            @NotBlank @Pattern(regexp = "^(HINDI|HINGLISH|ENGLISH|MARATHI|BENGALI|TAMIL)$") String uiLanguage,
            @NotBlank @Pattern(regexp = "^(HINDI|HINGLISH|ENGLISH|MARATHI|BENGALI|TAMIL)$")
            String reminderDefaultLanguage) {
    }
}
