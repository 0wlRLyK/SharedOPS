package dev.sharedops.household.application;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateHouseholdService {
  private static final Set<String> CURRENCIES =
      Set.of("EUR", "UAH", "USD", "GBP", "PLN", "CHF", "CZK", "DKK", "NOK", "SEK");
  private final CurrentAccount currentAccount;
  private final HouseholdStore households;
  private final MembershipStore memberships;

  public CreateHouseholdService(
      CurrentAccount currentAccount, HouseholdStore households, MembershipStore memberships) {
    this.currentAccount = currentAccount;
    this.households = households;
    this.memberships = memberships;
  }

  @Transactional
  public HouseholdSnapshot create(HouseholdDraft command) {
    var actor =
        currentAccount
            .authenticatedAccountId()
            .orElseThrow(UnauthenticatedHouseholdAccessException::new);
    HouseholdDraft valid = validate(command);
    if (memberships.findActiveHouseholdId(actor).isPresent()) {
      throw new ActiveHouseholdExistsException();
    }
    var created = households.insert(valid);
    memberships.insertActive(actor, created.id());
    return created;
  }

  private static HouseholdDraft validate(HouseholdDraft value) {
    if (value == null
        || value.name() == null
        || value.currency() == null
        || !CURRENCIES.contains(value.currency())
        || value.timezone() == null) {
      throw new InvalidHouseholdException();
    }
    String name = trimWhitespace(value.name());
    if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
      throw new InvalidHouseholdException();
    }
    String icon = value.icon();
    if (icon != null
        && (trimWhitespace(icon).isEmpty() || icon.codePointCount(0, icon.length()) > 100)) {
      throw new InvalidHouseholdException();
    }
    try {
      if (!ZoneId.getAvailableZoneIds().contains(value.timezone())) {
        throw new InvalidHouseholdException();
      }
      ZoneId.of(value.timezone());
    } catch (DateTimeException e) {
      throw new InvalidHouseholdException();
    }
    return new HouseholdDraft(name, icon, value.currency(), value.timezone());
  }

  private static String trimWhitespace(String s) {
    int start = 0, end = s.length();
    while (start < end && white(s.codePointAt(start))) {
      start += Character.charCount(s.codePointAt(start));
    }
    while (start < end && white(s.codePointBefore(end))) {
      end -= Character.charCount(s.codePointBefore(end));
    }
    return s.substring(start, end);
  }

  private static boolean white(int c) {
    return (c >= 0x09 && c <= 0x0d)
        || c == 0x20
        || c == 0x85
        || c == 0xa0
        || c == 0x1680
        || (c >= 0x2000 && c <= 0x200a)
        || c == 0x2028
        || c == 0x2029
        || c == 0x202f
        || c == 0x205f
        || c == 0x3000;
  }
}
