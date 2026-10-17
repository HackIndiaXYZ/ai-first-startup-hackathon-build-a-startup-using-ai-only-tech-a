package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.OrderDraft;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from OrderDraft draft where draft.id = :id")
    Optional<OrderDraft> findByIdForUpdate(@Param("id") UUID id);
}
