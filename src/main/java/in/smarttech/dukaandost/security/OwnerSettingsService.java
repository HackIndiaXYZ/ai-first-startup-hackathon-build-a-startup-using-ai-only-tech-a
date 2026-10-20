package in.smarttech.dukaandost.security;

import in.smarttech.dukaandost.domain.Owner;
import in.smarttech.dukaandost.repository.OwnerRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnerSettingsService {

    private final OwnerRepository owners;
    private final CurrentOwner currentOwner;

    public OwnerSettingsService(OwnerRepository owners, CurrentOwner currentOwner) {
        this.owners = owners;
        this.currentOwner = currentOwner;
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        return response(requireOwner());
    }

    @Transactional
    public SettingsResponse updateSettings(OwnerSettingsController.SettingsUpdateRequest request) {
        Owner owner = requireOwner();
        owner.setTheme(request.theme());
        owner.setInAppNotifications(request.inAppNotifications());
        owner.setBrowserPushEnabled(request.browserPushEnabled());
        owner.setPaymentDueNotifications(request.paymentDueNotifications());
        owner.setOverdueNotifications(request.overdueNotifications());
        owner.setNewOrderNotifications(request.newOrderNotifications());
        owner.setPaymentReceivedNotifications(request.paymentReceivedNotifications());
        owner.setDailySummaryNotifications(request.dailySummaryNotifications());
        owner.setUiLanguage(request.uiLanguage());
        owner.setReminderDefaultLanguage(request.reminderDefaultLanguage());
        return response(owners.save(owner));
    }

    private Owner requireOwner() {
        UUID ownerId = currentOwner.id();
        return owners.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found."));
    }

    private static SettingsResponse response(Owner owner) {
        return new SettingsResponse(owner.getTheme(), owner.isInAppNotifications(), owner.isBrowserPushEnabled(),
                owner.isPaymentDueNotifications(), owner.isOverdueNotifications(),
                owner.isNewOrderNotifications(), owner.isPaymentReceivedNotifications(),
                owner.isDailySummaryNotifications(), owner.getUiLanguage(),
                owner.getReminderDefaultLanguage());
    }

    public record SettingsResponse(String theme, boolean inAppNotifications, boolean browserPushEnabled,
                                   boolean paymentDueNotifications, boolean overdueNotifications,
                                   boolean newOrderNotifications, boolean paymentReceivedNotifications,
                                   boolean dailySummaryNotifications, String uiLanguage,
                                   String reminderDefaultLanguage) {
    }
}
