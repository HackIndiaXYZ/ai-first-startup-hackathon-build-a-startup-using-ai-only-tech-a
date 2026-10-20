package in.smarttech.dukaandost.repository;

import in.smarttech.dukaandost.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int claim(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now "
            + "where t.ownerId = :ownerId and t.usedAt is null")
    int invalidateAllForOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    java.util.Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
}
