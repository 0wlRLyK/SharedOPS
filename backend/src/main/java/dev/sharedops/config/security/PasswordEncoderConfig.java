package dev.sharedops.config.security;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12) {
      @Override
      protected boolean matchesNonNull(String rawPassword, String encodedPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length <= 72
            && super.matchesNonNull(rawPassword, encodedPassword);
      }
    };
  }
}
