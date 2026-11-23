package dev.sharedops.household.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InvitationHttpIT {
  private static final String CREATE = "/api/v1/households/{householdId}/invitations";
  private static final String REVOKE =
      "/api/v1/households/{householdId}/invitations/{invitationId}";
  private static final String ACCEPT = "/api/v1/invitations/accept";

  @Autowired MockMvc mvc;
  @Autowired JsonMapper jsonMapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository<? extends Session> sessions;
  private final List<UUID> accounts = new ArrayList<>();
  private final List<UUID> households = new ArrayList<>();
  private Cookie owner;
  private Cookie recipient;
  private Cookie outsider;
  private UUID household;

  private <S extends Session> Cookie cookie(SessionRepository<S> repository, UUID account) {
    var principal = new AccountPrincipal(account, account + "@example.com", "hash", 0);
    Authentication auth =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    MockHttpSession policySession = new MockHttpSession();
    SessionPolicy.initialize(policySession, false, Instant.now());
    S session = repository.createSession();
    var names = policySession.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      session.setAttribute(name, policySession.getAttribute(name));
    }
    session.setMaxInactiveInterval(Duration.ofSeconds(policySession.getMaxInactiveInterval()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    repository.save(session);
    return new Cookie(
        "SESSION",
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
  }

  private Cookie account() {
    UUID id = UUID.randomUUID();
    accounts.add(id);
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        id,
        id + "@example.com",
        "hash");
    return cookie(sessions, id);
  }

  @BeforeEach
  void setup() throws Exception {
    owner = account();
    recipient = account();
    outsider = account();
    var result =
        mvc.perform(
                post("/api/v1/households")
                    .cookie(owner)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Home\",\"currency\":\"EUR\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    household = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    households.add(household);
  }

  @AfterEach
  void cleanup() {
    for (UUID id : households) {
      jdbc.update("DELETE FROM household_invitations WHERE household_id = ?", id);
      jdbc.update("DELETE FROM household_memberships WHERE household_id = ?", id);
      jdbc.update("DELETE FROM households WHERE id = ?", id);
    }
    for (UUID id : accounts) {
      jdbc.update("DELETE FROM accounts WHERE id = ?", id);
    }
    households.clear();
    accounts.clear();
  }

  private MvcResult issue() throws Exception {
    MvcResult result =
        mvc.perform(post(CREATE, household).cookie(owner).with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn();
    assertContract(CREATE, "post", result);
    return result;
  }

  private MvcResult accept(Cookie actor, String token) throws Exception {
    MvcResult result =
        mvc.perform(
                post(ACCEPT)
                    .cookie(actor)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + token + "\"}"))
            .andReturn();
    assertContract(ACCEPT, "post", result);
    return result;
  }

  private static String field(MvcResult result, String name) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$." + name);
  }

  private static void noSecretHeaders(MvcResult result, String token) {
    for (String name : result.getResponse().getHeaderNames()) {
      for (String value : result.getResponse().getHeaders(name)) {
        assertThat(value).doesNotContain(token);
      }
    }
    assertThat(result.getResponse().getHeader("Location")).isNull();
  }

  /** Validate the actual response against its declared OpenAPI status, media type and schema. */
  private void assertContract(String path, String method, MvcResult result) throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    Map<?, ?> operation = map(map(map(contract.get("paths")).get(path)).get(method));
    String statusCode = Integer.toString(result.getResponse().getStatus());
    Map<?, ?> responses = map(operation.get("responses"));
    assertThat(responses.containsKey(statusCode))
        .as("documented %s %s status %s", method, path, statusCode)
        .isTrue();
    Map<?, ?> response = resolve(contract, map(responses.get(statusCode)));
    Object content = response.get("content");
    if (content == null) {
      assertThat(result.getResponse().getContentAsString()).isEmpty();
      return;
    }
    MediaType actualMedia = MediaType.parseMediaType(result.getResponse().getContentType());
    String mediaType = actualMedia.getType() + "/" + actualMedia.getSubtype();
    Map<?, ?> media = map(content);
    assertThat(media.containsKey(mediaType)).as("documented response media type").isTrue();
    Map<?, ?> schema = map(map(media.get(mediaType)).get("schema"));
    assertSchema(
        contract, schema, jsonMapper.readTree(result.getResponse().getContentAsString()), "$");
    if (response.containsKey("headers")) {
      Map<?, ?> headers = map(response.get("headers"));
      for (var entry : headers.entrySet()) {
        String name = (String) entry.getKey();
        Object expected = map(map(entry.getValue()).get("schema")).get("const");
        if (expected != null) {
          assertThat(result.getResponse().getHeader(name)).isEqualTo(expected);
        }
      }
    }
  }

  private static Map<?, ?> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<?, ?>) value;
  }

  private static Map<?, ?> resolve(Map<?, ?> contract, Map<?, ?> value) {
    if (!value.containsKey("$ref")) {
      return value;
    }
    String reference = (String) value.get("$ref");
    assertThat(reference).startsWith("#/");
    Object current = contract;
    for (String segment : reference.substring(2).split("/")) {
      current = map(current).get(segment.replace("~1", "/").replace("~0", "~"));
    }
    return resolve(contract, map(current));
  }

  private static void assertSchema(
      Map<?, ?> contract, Map<?, ?> unresolved, JsonNode value, String path) {
    Map<?, ?> schema = resolve(contract, unresolved);
    Object type = schema.get("type");
    List<?> allowedTypes = type instanceof List<?> types ? types : List.of(type);
    boolean matches =
        allowedTypes.stream()
            .anyMatch(
                candidate ->
                    switch ((String) candidate) {
                      case "object" -> value.isObject();
                      case "array" -> value.isArray();
                      case "string" -> value.isString();
                      case "integer" -> value.isIntegralNumber();
                      case "number" -> value.isNumber();
                      case "boolean" -> value.isBoolean();
                      case "null" -> value.isNull();
                      default ->
                          throw new AssertionError("Unsupported OpenAPI schema type: " + candidate);
                    });
    assertThat(matches).as("%s type %s", path, type).isTrue();
    if (value.isNull()) {
      return;
    }
    if (schema.containsKey("enum")) {
      assertThat(((List<?>) schema.get("enum")).contains(value.asString()))
          .as("%s enum", path)
          .isTrue();
    }
    if (schema.containsKey("const")) {
      assertThat(value.asString()).isEqualTo(schema.get("const"));
    }
    if (value.isObject()) {
      if (schema.containsKey("required")) {
        for (Object required : (List<?>) schema.get("required")) {
          assertThat(value.has((String) required)).as("%s required %s", path, required).isTrue();
        }
      }
      if (schema.containsKey("properties")) {
        for (var property : map(schema.get("properties")).entrySet()) {
          String name = (String) property.getKey();
          if (value.has(name)) {
            assertSchema(contract, map(property.getValue()), value.get(name), path + "." + name);
          }
        }
      }
    } else if (value.isArray()) {
      int index = 0;
      for (JsonNode element : value) {
        assertSchema(contract, map(schema.get("items")), element, path + "[" + index++ + "]");
      }
    } else if (value.isString()) {
      String text = value.asString();
      if (schema.containsKey("minLength")) {
        assertThat(text.length())
            .isGreaterThanOrEqualTo(((Number) schema.get("minLength")).intValue());
      }
      if (schema.containsKey("maxLength")) {
        assertThat(text.length())
            .isLessThanOrEqualTo(((Number) schema.get("maxLength")).intValue());
      }
      if (schema.containsKey("pattern")) {
        assertThat(text).matches((String) schema.get("pattern"));
      }
      if ("uuid".equals(schema.get("format"))) {
        UUID.fromString(text);
      }
      if ("date-time".equals(schema.get("format"))) {
        Instant.parse(text);
      }
    }
  }

  @Test
  void createAcceptAndReuseFollowContractWithoutLeakingSecret(CapturedOutput output)
      throws Exception {
    MvcResult created = issue();
    String token = field(created, "token");
    UUID invitation = UUID.fromString(field(created, "id"));
    Instant expires = Instant.parse(field(created, "expiresAt"));
    assertThat(expires).isAfter(Instant.now().plus(Duration.ofHours(23)));
    assertThat(created.getResponse().getHeader("Cache-Control")).contains("no-store");
    noSecretHeaders(created, token);
    MvcResult joined = accept(recipient, token);
    assertThat(joined.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) JsonPath.read(joined.getResponse().getContentAsString(), "$.id"))
        .isEqualTo(household.toString());
    assertThat((String) JsonPath.read(joined.getResponse().getContentAsString(), "$.currency"))
        .isEqualTo("EUR");
    assertThat(joined.getResponse().getHeader("Cache-Control")).contains("no-store");
    noSecretHeaders(joined, token);
    assertThat(joined.getResponse().getContentAsString()).doesNotContain(token);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE household_id=? AND active",
                Integer.class,
                household))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                invitation))
        .isTrue();
    MvcResult conflict =
        mvc.perform(delete(REVOKE, household, invitation).cookie(owner).with(csrf()))
            .andExpect(status().isConflict())
            .andReturn();
    assertContract(REVOKE, "delete", conflict);
    assertThat(field(conflict, "code")).isEqualTo("INVITATION_CONFLICT");
    MvcResult retry = accept(outsider, token);
    assertThat(retry.getResponse().getStatus()).isEqualTo(404);
    assertThat(retry.getResponse().getContentAsString()).doesNotContain(token);
    noSecretHeaders(retry, token);
    assertThat(output.toString())
        .doesNotContain(token)
        .doesNotContain("/invitations/accept?token=");
  }

  @Test
  void acceptingWhileAlreadyAMemberReturnsDocumentedConflictWithoutConsumption() throws Exception {
    MvcResult created = issue();
    UUID id = UUID.fromString(field(created, "id"));
    MvcResult conflict = accept(owner, field(created, "token"));
    assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    assertThat(field(conflict, "code")).isEqualTo("INVITATION_CONFLICT");
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE household_id=? AND active",
                Integer.class,
                household))
        .isEqualTo(1);
  }

  @Test
  void allEndpointsRequireAuthenticationAndCsrf() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(post(CREATE, household).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(delete(REVOKE, household, id).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post(ACCEPT).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(CREATE, household).cookie(owner)).andExpect(status().isForbidden());
    mvc.perform(delete(REVOKE, household, id).cookie(owner)).andExpect(status().isForbidden());
    mvc.perform(
            post(ACCEPT).cookie(recipient).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_invitations WHERE household_id=?",
                Integer.class,
                household))
        .isZero();
  }

  @Test
  void foreignAndAbsentResourcesAreIndistinguishableAndRevocationPreventsUse() throws Exception {
    MvcResult created = issue();
    String token = field(created, "token");
    UUID id = UUID.fromString(field(created, "id"));
    UUID missingHousehold = UUID.randomUUID();
    var foreignCreate =
        mvc.perform(post(CREATE, household).cookie(outsider).with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn();
    var absentCreate =
        mvc.perform(post(CREATE, missingHousehold).cookie(outsider).with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn();
    assertContract(CREATE, "post", foreignCreate);
    assertThat(foreignCreate.getResponse().getContentAsString())
        .isEqualTo(absentCreate.getResponse().getContentAsString());
    var foreignDelete =
        mvc.perform(delete(REVOKE, household, id).cookie(outsider).with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn();
    var absentDelete =
        mvc.perform(delete(REVOKE, household, UUID.randomUUID()).cookie(owner).with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn();
    assertContract(REVOKE, "delete", foreignDelete);
    assertThat(foreignDelete.getResponse().getContentAsString())
        .isEqualTo(absentDelete.getResponse().getContentAsString());
    MvcResult deleted =
        mvc.perform(delete(REVOKE, household, id).cookie(owner).with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();
    assertContract(REVOKE, "delete", deleted);
    MvcResult revoked = accept(recipient, token);
    assertThat(revoked.getResponse().getStatus()).isEqualTo(404);
    String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    assertThat(revoked.getResponse().getContentAsString())
        .isEqualTo(accept(recipient, unknown).getResponse().getContentAsString());
    mvc.perform(delete(REVOKE, household, id).cookie(owner).with(csrf()))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL AND consumed_at IS NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                id))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM household_memberships WHERE household_id=? AND active",
                Integer.class,
                household))
        .isEqualTo(1);
  }

  @Test
  void invalidUnknownExpiredAndRevokedResponsesAreSanitizedAndGeneric(CapturedOutput output)
      throws Exception {
    MvcResult created = issue();
    String token = field(created, "token");
    UUID id = UUID.fromString(field(created, "id"));
    String unknown = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    var missing = accept(recipient, unknown);
    assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    jdbc.update(
        "UPDATE household_invitations SET created_at=now()-interval '2 days', expires_at=now()-interval '1 day' WHERE id=?",
        id);
    var expired = accept(recipient, token);
    assertThat(expired.getResponse().getStatus()).isEqualTo(404);
    assertThat(expired.getResponse().getContentAsString())
        .isEqualTo(missing.getResponse().getContentAsString());
    String secret = "malformed-secret-" + UUID.randomUUID();
    var malformed =
        mvc.perform(
                post(ACCEPT)
                    .cookie(recipient)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + secret + "\", broken"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertContract(ACCEPT, "post", malformed);
    assertThat(malformed.getResponse().getContentAsString()).doesNotContain(secret);
    mvc.perform(post(CREATE, "not-a-uuid").cookie(owner).with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(delete(REVOKE, household, "not-a-uuid").cookie(owner).with(csrf()))
        .andExpect(status().isBadRequest());
    assertThat(output.toString()).doesNotContain(token).doesNotContain(secret);
    assertThat(
            jdbc.queryForObject(
                "SELECT consumed_at IS NULL FROM household_invitations WHERE id=?",
                Boolean.class,
                id))
        .isTrue();
  }
}
