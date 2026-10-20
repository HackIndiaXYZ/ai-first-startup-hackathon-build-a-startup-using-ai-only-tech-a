package in.smarttech.dukaandost.service;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@EnableScheduling
public class NotificationStreamService {

    private final ConcurrentHashMap<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter open(UUID ownerId) {
        SseEmitter emitter = new SseEmitter(0L);
        streams.computeIfAbsent(ownerId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(ownerId, emitter));
        emitter.onTimeout(() -> remove(ownerId, emitter));
        emitter.onError(error -> remove(ownerId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ready"));
        } catch (IOException exception) {
            remove(ownerId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(NotificationService.NotificationEvent event) {
        Set<SseEmitter> ownerStreams = streams.get(event.ownerId());
        if (ownerStreams == null) return;
        for (SseEmitter emitter : ownerStreams) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(event.notification()));
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(event.ownerId(), emitter);
            }
        }
    }

    private void remove(UUID ownerId, SseEmitter emitter) {
        Set<SseEmitter> ownerStreams = streams.get(ownerId);
        if (ownerStreams != null) {
            ownerStreams.remove(emitter);
            if (ownerStreams.isEmpty()) streams.remove(ownerId, ownerStreams);
        }
    }
}
