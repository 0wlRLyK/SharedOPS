package dev.sharedops;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import dev.sharedops.smoke.persistence.PersistenceSmokeRecord;
import dev.sharedops.smoke.persistence.PersistenceSmokeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "spring.flyway.locations=classpath:db/persistence-smoke",
      "spring.jpa.hibernate.ddl-auto=validate"
    })
@Transactional
class PersistenceSmokeIT {

  @Autowired PersistenceSmokeRepository repository;

  @PersistenceContext EntityManager entityManager;

  @Test
  void persistsAndReloadsEntity() {
    UUID id = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 10, 18);

    var original = new PersistenceSmokeRecord(id, "Buy milk", date);

    repository.save(original);
    repository.flush();
    entityManager.clear();

    var restored = repository.findById(id).orElseThrow();

    assertThat(restored.getId()).isEqualTo(id);
    assertThat(restored.getTitle()).isEqualTo("Buy milk");
    assertThat(restored.getCreatedOn()).isEqualTo(date);
  }
}
