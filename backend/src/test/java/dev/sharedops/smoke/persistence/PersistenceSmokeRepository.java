package dev.sharedops.smoke.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersistenceSmokeRepository extends JpaRepository<PersistenceSmokeRecord, UUID> {}
