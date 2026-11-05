package dev.sharedops.smoke;

import dev.sharedops.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SharedopsBackendApplicationIT {

  @Test
  void contextLoads() {}
}
