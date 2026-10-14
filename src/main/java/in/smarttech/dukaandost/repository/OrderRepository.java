package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
