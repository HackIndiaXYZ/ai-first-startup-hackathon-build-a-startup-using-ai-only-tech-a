package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.OrderDraft;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, UUID> {
}
