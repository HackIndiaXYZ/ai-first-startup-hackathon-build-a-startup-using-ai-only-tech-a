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
    @Query("select o from Order o where o.id = :id and o.customer.ownerId = :ownerId")
    java.util.Optional<Order> findByIdForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = {"customer", "payments"})
    @Query("select o from Order o where o.customer.ownerId = :ownerId "
            + "order by o.orderDate desc, o.createdAt desc")
    java.util.List<Order> findAllByOwnerId(@Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = {"customer", "payments"})
    @Query("select o from Order o where o.customer.id = :customerId and o.customer.ownerId = :ownerId "
            + "order by o.orderDate desc, o.createdAt desc")
    java.util.List<Order> findAllByCustomerIdAndOwnerId(
            @Param("customerId") UUID customerId, @Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = {"customer", "payments"})
    @Query("select o from Order o where o.id = :id and o.customer.ownerId = :ownerId")
    java.util.Optional<Order> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    @EntityGraph(attributePaths = {"customer", "payments"})
    @Query("select o from Order o where o.status = :status and o.customer.ownerId = :ownerId")
    java.util.List<Order> findByStatusAndOwnerId(
            @Param("status") in.smarttech.dukaandost.domain.OrderStatus status,
            @Param("ownerId") UUID ownerId);
}
