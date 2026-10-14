package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
