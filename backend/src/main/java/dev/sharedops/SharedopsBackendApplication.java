package dev.sharedops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SharedopsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(SharedopsBackendApplication.class, args);
  }
}
