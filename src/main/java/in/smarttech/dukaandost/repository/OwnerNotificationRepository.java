package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.OwnerNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OwnerNotificationRepository extends JpaRepository<OwnerNotification, UUID> {

    List<OwnerNotification> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerIdAndReadAtIsNull(UUID ownerId);

    Optional<OwnerNotification> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndKindAndReferenceId(UUID ownerId,
            in.smarttech.dukaandost.domain.NotificationType kind, UUID referenceId);

    @Modifying
    @Query("delete from OwnerNotification n where n.ownerId = :ownerId")
    void deleteAllByOwnerId(UUID ownerId);
}
