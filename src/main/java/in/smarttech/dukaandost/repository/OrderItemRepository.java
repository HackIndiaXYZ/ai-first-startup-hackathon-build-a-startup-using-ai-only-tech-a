package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.OrderItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
