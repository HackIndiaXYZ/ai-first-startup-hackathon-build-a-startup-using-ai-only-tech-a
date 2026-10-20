package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    java.util.Optional<Customer> findFirstByOwnerIdAndNameIgnoreCase(UUID ownerId, String name);

    java.util.List<Customer> findByOwnerIdAndNameContainingIgnoreCase(UUID ownerId, String name);

    java.util.Optional<Customer> findByIdAndOwnerId(UUID id, UUID ownerId);
}
