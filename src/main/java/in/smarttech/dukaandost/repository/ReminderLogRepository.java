package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.ReminderLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

    Optional<ReminderLog> findFirstByOwnerIdAndOrderIdOrderByOccurredAtDesc(UUID ownerId, UUID orderId);
}
