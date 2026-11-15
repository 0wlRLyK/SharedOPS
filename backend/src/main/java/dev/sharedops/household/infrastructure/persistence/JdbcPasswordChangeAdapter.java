package dev.sharedops.household.infrastructure.persistence;

import dev.sharedops.household.application.port.PasswordChangePort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPasswordChangeAdapter implements PasswordChangePort {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;

  public JdbcPasswordChangeAdapter(JdbcTemplate jdbc, PasswordEncoder encoder) {
    this.jdbc = jdbc;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void change(UUID id, String currentPassword, String newPassword) {
    var rows =
        jdbc.query(
            "SELECT email, password_hash FROM accounts WHERE id = ? FOR UPDATE",
            (rs, n) -> new String[] {rs.getString(1), rs.getString(2)},
            id);
    if (rows.isEmpty() || !encoder.matches(currentPassword, rows.get(0)[1])) {
      throw new BadCredentialsException("Invalid credentials");
    }
    jdbc.update(
        "UPDATE accounts SET password_hash = ?, credential_version = credential_version + 1 WHERE id = ?",
        encoder.encode(newPassword),
        id);
    jdbc.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", rows.get(0)[0]);
  }
}
