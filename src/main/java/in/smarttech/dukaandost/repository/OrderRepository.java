package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.Order;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"customer", "items"})
    java.util.Optional<Order> findByOriginatingDraft_Id(UUID draftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select order from Order order where order.id = :id")
    java.util.Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"customer", "payments"})
    java.util.List<Order> findByStatus(in.smarttech.dukaandost.domain.OrderStatus status);
}
