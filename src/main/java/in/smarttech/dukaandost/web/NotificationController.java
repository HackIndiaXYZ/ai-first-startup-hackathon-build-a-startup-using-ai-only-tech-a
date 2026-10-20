package in.smarttech.dukaandost.web;

import in.smarttech.dukaandost.security.CurrentOwner;
import in.smarttech.dukaandost.service.NotificationService;
import in.smarttech.dukaandost.service.NotificationStreamService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationStreamService streams;
    private final CurrentOwner currentOwner;

    public NotificationController(NotificationService notifications, NotificationStreamService streams,
                                  CurrentOwner currentOwner) {
        this.notifications = notifications;
        this.streams = streams;
        this.currentOwner = currentOwner;
    }

    @GetMapping
    public NotificationService.NotificationList list() {
        return notifications.list(currentOwner.id());
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streams.open(currentOwner.id());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        notifications.markRead(currentOwner.id(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public Map<String, String> clear() {
        notifications.clearAll(currentOwner.id());
        return Map.of("message", "Notifications cleared.");
    }
}
