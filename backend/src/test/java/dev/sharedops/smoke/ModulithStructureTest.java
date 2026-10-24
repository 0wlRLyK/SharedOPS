package dev.sharedops.smoke;

import dev.sharedops.SharedopsBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithStructureTest {

  @Test
  void verifiesApplicationModuleStructure() {
    var modules = ApplicationModules.of(SharedopsBackendApplication.class);

    modules.verify();
  }
}
