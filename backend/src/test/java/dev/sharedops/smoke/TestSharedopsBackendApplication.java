package dev.sharedops.smoke;

import dev.sharedops.SharedopsBackendApplication;
import dev.sharedops.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestSharedopsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.from(SharedopsBackendApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
