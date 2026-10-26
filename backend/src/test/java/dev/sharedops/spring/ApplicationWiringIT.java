package dev.sharedops.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringJUnitConfig(classes = ApplicationWiringIT.TestConfig.class)
public class ApplicationWiringIT {
  @Autowired TestController controller;

  interface TestPort {
    String readValue();
  }

  @Repository
  static class TestAdapter implements TestPort {
    @Override
    public String readValue() {
      return "wired";
    }
  }

  @Service
  static class TestApplicationService {
    private final TestPort port;

    public TestApplicationService(TestPort port) {
      this.port = port;
    }

    public String readValue() {
      return port.readValue();
    }
  }

  @RestController
  static class TestController {
    private final TestApplicationService service;

    public TestController(TestApplicationService service) {
      this.service = service;
    }

    @GetMapping("/_test/wiring")
    public String readValue() {
      return service.readValue();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    ApplicationWiringIT.TestAdapter.class,
    ApplicationWiringIT.TestApplicationService.class,
    ApplicationWiringIT.TestController.class
  })
  static class TestConfig {}

  @Test
  void wiresControllerServicePortAndAdapter() {
    assertThat(controller.readValue()).isEqualTo("wired");
  }
}
