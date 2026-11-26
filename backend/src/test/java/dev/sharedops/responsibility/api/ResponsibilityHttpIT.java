package dev.sharedops.responsibility.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import dev.sharedops.responsibility.application.ResponsibilityResponses;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
class ResponsibilityHttpIT {
  @Autowired MockMvc mvc;
  @Autowired SessionRepository<? extends Session> sessions;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper jsonMapper;
  @MockitoSpyBean ResponsibilityResponses responses;
  UUID household = UUID.randomUUID();
  UUID account = UUID.randomUUID();
  UUID member = UUID.randomUUID();
  UUID partner;
  UUID other;
  UUID otherHouse;
  Authentication auth;
  String path;

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        account,
        account + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Home', 'EUR', 'Europe/Berlin')",
        household);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        member,
        account,
        household);
    var principal = new AccountPrincipal(account, account + "@example.com", "hash", 0);
    auth =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    path = "/api/v1/households/" + household + "/responsibilities";
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM tasks WHERE household_id=?", household);
    jdbc.update("DELETE FROM responsibility_owner_transfers WHERE household_id=?", household);
    jdbc.update("DELETE FROM responsibilities WHERE household_id=?", household);
    jdbc.update("DELETE FROM household_memberships WHERE household_id=?", household);
    jdbc.update("DELETE FROM households WHERE id=?", household);
    jdbc.update("DELETE FROM accounts WHERE id=?", account);
    if (partner != null) {
      jdbc.update("DELETE FROM accounts WHERE id=?", partner);
    }
    if (otherHouse != null) {
      jdbc.update("DELETE FROM household_memberships WHERE household_id=?", otherHouse);
      jdbc.update("DELETE FROM households WHERE id=?", otherHouse);
      jdbc.update("DELETE FROM accounts WHERE id=?", other);
    }
  }

  private <S extends Session> Cookie sessionCookie(
      SessionRepository<S> repository, Authentication identity) {
    MockHttpSession policy = new MockHttpSession();
    SessionPolicy.initialize(policy, false, Instant.now());
    S session = repository.createSession();
    var names = policy.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      session.setAttribute(name, policy.getAttribute(name));
    }
    session.setMaxInactiveInterval(Duration.ofSeconds(policy.getMaxInactiveInterval()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(identity);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    repository.save(session);
    return new Cookie(
        "SESSION",
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
  }

  private Cookie cookie(Authentication identity) {
    return sessionCookie(sessions, identity);
  }

  String create() throws Exception {
    var result =
        mvc.perform(
                post(path)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Clean\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
                            + account
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  @Test
  void zeroBytesAreRejectedBySchemaAndHttpWithoutChangingCommittedState() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    MvcResult initial =
        mvc.perform(
                post(path)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Clean\",\"description\":\"Keep this description\","
                            + "\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
                            + account
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(initial.getResponse().getContentAsString(), "$.id");
    UUID linkedTask = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
        linkedTask,
        household,
        UUID.fromString(id));
    List<Map<String, Object>> beforeResponsibilities =
        jdbc.queryForList(
            "SELECT * FROM responsibilities WHERE household_id=? ORDER BY id", household);
    List<Map<String, Object>> beforeTransfers =
        jdbc.queryForList(
            "SELECT * FROM responsibility_owner_transfers WHERE household_id=? ORDER BY id",
            household);
    List<Map<String, Object>> beforeTasks =
        jdbc.queryForList("SELECT * FROM tasks WHERE household_id=? ORDER BY id", household);
    assertThat(beforeResponsibilities)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("description")).isEqualTo("Keep this description");
              assertThat(row.get("owner_membership_id")).isEqualTo(member);
              assertThat(row.get("status")).isEqualTo("ACTIVE");
            });
    String ordinaryControl = "{\"version\":0,\"title\":\"Valid\",\"description\":\"x\\u0001y\"}";
    assertRequestSchema(
        contract, template + "/{responsibilityId}/details", "put", ordinaryControl, true);
    for (String field : List.of("title", "description")) {
      String title = field.equals("title") ? "x\\u0000y" : "Valid";
      String description = field.equals("description") ? "x\\u0000y" : "Valid";
      String createJson =
          "{\"title\":\""
              + title
              + "\",\"description\":\""
              + description
              + "\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
              + account
              + "\"}";
      String editJson =
          "{\"version\":0,\"title\":\"" + title + "\",\"description\":\"" + description + "\"}";
      assertRequestSchema(contract, template, "post", createJson, false);
      assertRequestSchema(
          contract, template + "/{responsibilityId}/details", "put", editJson, false);
      MvcResult rejectedCreate =
          mvc.perform(
                  post(path)
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(createJson))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertThat(rejectedCreate.getResponse().getContentType())
          .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      assertThat(
              (String) JsonPath.read(rejectedCreate.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("VALIDATION_ERROR");
      assertThat(
              jdbc.queryForList(
                  "SELECT * FROM responsibilities WHERE household_id=? ORDER BY id", household))
          .isEqualTo(beforeResponsibilities);
      assertThat(
              jdbc.queryForList(
                  "SELECT * FROM responsibility_owner_transfers WHERE household_id=? ORDER BY id",
                  household))
          .isEqualTo(beforeTransfers);
      assertThat(
              jdbc.queryForList("SELECT * FROM tasks WHERE household_id=? ORDER BY id", household))
          .isEqualTo(beforeTasks);

      MvcResult rejectedEdit =
          mvc.perform(
                  put(path + "/" + id + "/details")
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(editJson))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertThat(rejectedEdit.getResponse().getContentType())
          .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      assertThat((String) JsonPath.read(rejectedEdit.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("VALIDATION_ERROR");
      assertThat(
              jdbc.queryForList(
                  "SELECT * FROM responsibilities WHERE household_id=? ORDER BY id", household))
          .isEqualTo(beforeResponsibilities);
      assertThat(
              jdbc.queryForList(
                  "SELECT * FROM responsibility_owner_transfers WHERE household_id=? ORDER BY id",
                  household))
          .isEqualTo(beforeTransfers);
      assertThat(
              jdbc.queryForList("SELECT * FROM tasks WHERE household_id=? ORDER BY id", household))
          .isEqualTo(beforeTasks);
    }
  }

  @Test
  void ordinaryControlCharactersAreAcceptedAndPersistedOnCreateAndDetails() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    String createJson =
        "{\"title\":\"Before\\u0001title\",\"description\":\"Before\\u0001description\","
            + "\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
            + account
            + "\"}";
    assertRequestSchema(contract, template, "post", createJson, true);
    MvcResult created =
        mvc.perform(
                post(path)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson))
            .andExpect(status().isCreated())
            .andReturn();
    assertResponseSchema(contract, template, "post", created);
    JsonNode createdBody = jsonMapper.readTree(created.getResponse().getContentAsString());
    UUID id = UUID.fromString(createdBody.get("id").asString());
    String titleBefore = "Before" + (char) 1 + "title";
    String descriptionBefore = "Before" + (char) 1 + "description";
    assertThat(createdBody.get("title").asString()).isEqualTo(titleBefore);
    assertThat(createdBody.get("description").asString()).isEqualTo(descriptionBefore);
    assertThat(createdBody.get("version").asLong()).isZero();
    assertThat(
            jdbc.queryForObject("SELECT title FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo(titleBefore);
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo(descriptionBefore);
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isZero();

    String detailsJson =
        "{\"version\":0,\"title\":\"After\\u0001title\","
            + "\"description\":\"After\\u0001description\"}";
    String detailsTemplate = template + "/{responsibilityId}/details";
    assertRequestSchema(contract, detailsTemplate, "put", detailsJson, true);
    MvcResult updated =
        mvc.perform(
                put(path + "/" + id + "/details")
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(detailsJson))
            .andExpect(status().isOk())
            .andReturn();
    assertResponseSchema(contract, detailsTemplate, "put", updated);
    JsonNode updatedBody = jsonMapper.readTree(updated.getResponse().getContentAsString());
    String titleAfter = "After" + (char) 1 + "title";
    String descriptionAfter = "After" + (char) 1 + "description";
    assertThat(updatedBody.get("title").asString()).isEqualTo(titleAfter);
    assertThat(updatedBody.get("description").asString()).isEqualTo(descriptionAfter);
    assertThat(updatedBody.get("version").asLong()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT title FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo(titleAfter);
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo(descriptionAfter);
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isEqualTo(1L);
  }

  @Test
  void internalProjectionFailureIsNotInvalidTransitionAndRollsBackMutation() throws Exception {
    String id = create();
    doThrow(new IllegalStateException("projection failed")).when(responses).project(any());
    assertThatThrownBy(
            () ->
                mvc.perform(
                    put(path + "/" + id + "/details")
                        .cookie(cookie(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"Changed\"}")))
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM responsibilities WHERE id = ?",
                String.class,
                UUID.fromString(id)))
        .isEqualTo("Clean");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id = ?",
                Long.class,
                UUID.fromString(id)))
        .isZero();
  }

  @Test
  void lifecycleAndVersionChecksPreserveStateOnInvalidRequests() throws Exception {
    String id = create();
    String item = path + "/" + id;
    mvc.perform(
            put(item + "/details")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"title\":\"Changed\",\"description\":\"\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post(item + "/archive")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1}"))
        .andExpect(status().isOk());
    mvc.perform(
            post(item + "/restore")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"ownerAccountId\":null}"))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM responsibilities WHERE id=?",
                String.class,
                UUID.fromString(id)))
        .isEqualTo("ARCHIVED");
    mvc.perform(
            post(item + "/restore")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
        .andExpect(status().isOk());
    mvc.perform(
            post(item + "/pause")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"))
        .andExpect(status().isConflict());
    mvc.perform(get(path).param("view", "current").cookie(cookie(auth))).andExpect(status().isOk());
  }

  @Test
  void malformedAndUnknownFieldsAreRejectedWithoutWrites() throws Exception {
    String id = create();
    String item = path + "/" + id;
    for (String body :
        new String[] {
          "{}",
          "{\"version\":null}",
          "{\"version\":\"0\"}",
          "{\"version\":0.0}",
          "{\"version\":-1}",
          "{\"version\":9223372036854775808}",
          "{\"version\":0,\"status\":\"COMPLETED\"}"
        }) {
      mvc.perform(
              post(item + "/pause")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id=?", Long.class, UUID.fromString(id)))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM responsibilities WHERE id=?",
                String.class,
                UUID.fromString(id)))
        .isEqualTo("ACTIVE");
    mvc.perform(get(path).cookie(cookie(auth))).andExpect(status().isBadRequest());
    mvc.perform(
            post(item + "/pause")
                .cookie(cookie(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unicodeBlankTitleIsRejectedAndNonBlankTitleIsPreserved() throws Exception {
    String blank =
        jsonMapper.writeValueAsString(
            Map.of(
                "title",
                "\u1680\u2003\u3000",
                "mode",
                "INDIVIDUAL",
                "ownerAccountId",
                account.toString()));
    mvc.perform(
            post(path)
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(blank))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibilities WHERE household_id=?",
                Long.class,
                household))
        .isZero();
    String validTitle = "\u2003x\u2003";
    String valid =
        jsonMapper.writeValueAsString(
            Map.of(
                "title", validTitle, "mode", "INDIVIDUAL", "ownerAccountId", account.toString()));
    mvc.perform(
            post(path)
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isCreated());
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM responsibilities WHERE household_id=?", String.class, household))
        .isEqualTo(validTitle);
  }

  @Test
  void titleSchemaAndHttpAgreeOnJavaWhitespaceForCreateAndDetails() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    String detailsTemplate = template + "/{responsibilityId}/details";
    String[] titles = {"\u001c", "\u2003", " ", "\u00a0", "\u001cx", "plain"};
    for (String title : titles) {
      boolean valid = !title.isBlank();
      String createBody =
          jsonMapper.writeValueAsString(
              Map.of("title", title, "mode", "INDIVIDUAL", "ownerAccountId", account.toString()));
      assertRequestSchema(contract, template, "post", createBody, valid);
      long before = jdbc.queryForObject("SELECT count(*) FROM responsibilities", Long.class);
      MvcResult created =
          mvc.perform(
                  post(path)
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(createBody))
              .andExpect(valid ? status().isCreated() : status().isBadRequest())
              .andReturn();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM responsibilities", Long.class))
          .isEqualTo(before + (valid ? 1 : 0));
      if (!valid) {
        continue;
      }
      String id =
          jsonMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();
      for (String replacement : titles) {
        boolean replacementValid = !replacement.isBlank();
        long version =
            jdbc.queryForObject(
                "SELECT version FROM responsibilities WHERE id=?", Long.class, UUID.fromString(id));
        String updateBody =
            jsonMapper.writeValueAsString(Map.of("title", replacement, "version", version));
        assertRequestSchema(contract, detailsTemplate, "put", updateBody, replacementValid);
        String beforeTitle =
            jdbc.queryForObject(
                "SELECT title FROM responsibilities WHERE id=?", String.class, UUID.fromString(id));
        mvc.perform(
                put(path + "/" + id + "/details")
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateBody))
            .andExpect(replacementValid ? status().isOk() : status().isBadRequest());
        assertThat(
                jdbc.queryForObject(
                    "SELECT title FROM responsibilities WHERE id=?",
                    String.class,
                    UUID.fromString(id)))
            .isEqualTo(replacementValid ? replacement : beforeTitle);
        if (!replacementValid) {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT version FROM responsibilities WHERE id=?",
                      Long.class,
                      UUID.fromString(id)))
              .isEqualTo(version);
        }
      }
    }
  }

  @Test
  void creationRejectsInvalidFieldsWithoutPersistingRows() throws Exception {
    String owner = "\"" + account + "\"";
    String[] bodies = {
      "{}",
      "{\"title\":null,\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":" + owner + "}",
      "{\"title\":4,\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"  \",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"A\",\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"A\",\"mode\":null,\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"A\",\"mode\":true,\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"A\",\"mode\":\"SHARED\",\"ownerAccountId\":" + owner + "}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\"}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":null}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":12}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\"bad\"}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":"
          + owner
          + ",\"description\":true}",
      "{\"title\":\"A\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":" + owner + ",\"version\":0}"
    };
    for (String body : bodies) {
      mvc.perform(
              post(path)
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibilities WHERE household_id=?",
                  Long.class,
                  household))
          .isZero();
    }
  }

  @Test
  void invalidUpdateBodiesAndUnknownFieldsNeverMutate() throws Exception {
    UUID id = UUID.fromString(create());
    String item = path + "/" + id;
    String[] details = {
      "{}",
      "{\"version\":0}",
      "{\"version\":0,\"title\":null}",
      "{\"version\":0,\"title\":true}",
      "{\"version\":0,\"title\":\"X\",\"description\":5}",
      "{\"version\":0,\"title\":\"X\",\"mode\":\"SHARED\"}"
    };
    for (String body : details) {
      mvc.perform(
              put(item + "/details")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    for (String body :
        new String[] {
          "{\"version\":0}",
          "{\"version\":0,\"ownerAccountId\":null}",
          "{\"version\":0,\"ownerAccountId\":true}",
          "{\"version\":0,\"ownerAccountId\":\"bad\"}",
          "{\"version\":0,\"ownerAccountId\":\"" + account + "\",\"extra\":1}"
        }) {
      mvc.perform(
              post(item + "/transfer")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    for (String action : new String[] {"pause", "resume", "archive", "restore"}) {
      mvc.perform(
              post(item + "/" + action)
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":0,\"extra\":1}"))
          .andExpect(status().isBadRequest());
    }
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT title FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo("Clean");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                Long.class,
                id))
        .isZero();
  }

  @Test
  void foreignAndMissingResourcesAreIndistinguishable() throws Exception {
    String id = create();
    other = UUID.randomUUID();
    otherHouse = UUID.randomUUID();
    UUID otherMember = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        other,
        other + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Other', 'EUR', 'Europe/Berlin')",
        otherHouse);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        otherMember,
        other,
        otherHouse);
    var principal = new AccountPrincipal(other, other + "@example.com", "hash", 0);
    var foreign =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    String absent = path + "/" + UUID.randomUUID();
    var a =
        mvc.perform(get(path + "/" + id).cookie(cookie(foreign)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    var b =
        mvc.perform(get(absent).cookie(cookie(foreign)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertEquivalentProblems(a, path + "/" + id, b, absent);

    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    String itemTemplate = template + "/{responsibilityId}";
    Map<?, ?> paths = map(contract.get("paths"));
    for (String operationPath : new String[] {template, itemTemplate}) {
      Map<?, ?> operation = map(map(paths.get(operationPath)).get("get"));
      Map<?, ?> documented = map(operation.get("responses"));
      assertThat(documented.containsKey("401")).isTrue();
      assertThat(documented.containsKey("404")).isTrue();
      assertThat(documented.containsKey("403")).isFalse();
    }
    MvcResult foreignItem =
        mvc.perform(get(path + "/" + id).cookie(cookie(foreign)))
            .andExpect(status().isNotFound())
            .andReturn();
    MvcResult foreignList =
        mvc.perform(get(path).param("view", "current").cookie(cookie(foreign)))
            .andExpect(status().isNotFound())
            .andReturn();
    MvcResult absentList =
        mvc.perform(
                get("/api/v1/households/" + UUID.randomUUID() + "/responsibilities")
                    .param("view", "current")
                    .cookie(cookie(foreign)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertEquivalentProblems(
        foreignList.getResponse(),
        path,
        absentList.getResponse(),
        absentList.getRequest().getRequestURI());

    UUID foreignId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO responsibilities (id, household_id, title, mode, status, owner_membership_id, version) VALUES (?, ?, 'Foreign', 'INDIVIDUAL', 'ACTIVE', ?, 0)",
        foreignId,
        otherHouse,
        otherMember);
    jdbc.update(
        "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
        UUID.randomUUID(),
        household,
        UUID.fromString(id));
    String sameUri = path + "/" + foreignId;
    var beforeForeign = responsibilityState();
    var foreignResponse =
        mvc.perform(get(sameUri).cookie(cookie(auth)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertThat(responsibilityState()).isEqualTo(beforeForeign);
    jdbc.update("DELETE FROM responsibilities WHERE id=?", foreignId);
    var beforeMissing = responsibilityState();
    var missingResponse =
        mvc.perform(get(sameUri).cookie(cookie(auth)))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse();
    assertThat(responsibilityState()).isEqualTo(beforeMissing);
    assertThat(foreignResponse.getContentAsString())
        .isEqualTo(missingResponse.getContentAsString());
    assertResponseSchema(contract, itemTemplate, "get", foreignItem);
    assertResponseSchema(contract, template, "get", foreignList);
    assertResponseSchema(contract, template, "get", absentList);
  }

  private java.util.List<java.util.List<java.util.Map<String, Object>>> responsibilityState() {
    return java.util.List.of(
        jdbc.queryForList("SELECT * FROM responsibilities ORDER BY id"),
        jdbc.queryForList("SELECT * FROM responsibility_owner_transfers ORDER BY id"),
        jdbc.queryForList("SELECT * FROM tasks ORDER BY id"));
  }

  private void assertEquivalentProblems(
      org.springframework.mock.web.MockHttpServletResponse first,
      String firstUri,
      org.springframework.mock.web.MockHttpServletResponse second,
      String secondUri)
      throws Exception {
    assertThat(first.getStatus()).isEqualTo(second.getStatus());
    assertThat(first.getContentType()).isEqualTo(second.getContentType());
    assertThat((String) JsonPath.read(first.getContentAsString(), "$.instance"))
        .isEqualTo(firstUri);
    assertThat((String) JsonPath.read(second.getContentAsString(), "$.instance"))
        .isEqualTo(secondUri);
    var firstBody =
        jsonMapper.readTree(
            JsonPath.parse(first.getContentAsString()).delete("$.instance").jsonString());
    var secondBody =
        jsonMapper.readTree(
            JsonPath.parse(second.getContentAsString()).delete("$.instance").jsonString());
    assertThat(firstBody).isEqualTo(secondBody);
  }

  @Test
  void everyMutationRejectsMissingNullAndWrongVersionShapes() throws Exception {
    UUID id = UUID.fromString(create());
    String item = path + "/" + id;
    for (String version :
        new String[] {
          "",
          "\"version\":null",
          "\"version\":true",
          "\"version\":\"0\"",
          "\"version\":0.0",
          "\"version\":-1",
          "\"version\":9223372036854775808"
        }) {
      String prefix = version.isEmpty() ? "" : version + ",";
      mvc.perform(
              put(item + "/details")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{" + prefix + "\"title\":\"X\"}"))
          .andExpect(status().isBadRequest());
      mvc.perform(
              post(item + "/transfer")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{" + prefix + "\"ownerAccountId\":\"" + account + "\"}"))
          .andExpect(status().isBadRequest());
      for (String action : new String[] {"pause", "resume", "archive", "restore"}) {
        String body = version.isEmpty() ? "{}" : "{" + version + "}";
        mvc.perform(
                post(item + "/" + action)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isBadRequest());
      }
    }
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                Long.class,
                id))
        .isZero();
  }

  @Test
  void restoreRejectsNullMalformedAndWrongTypeOwnerWithoutReopening() throws Exception {
    UUID id = UUID.fromString(create());
    String item = path + "/" + id;
    mvc.perform(
            post(item + "/archive")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isOk());
    for (String owner : new String[] {"null", "true", "5", "\"\"", "\"bad\""}) {
      mvc.perform(
              post(item + "/restore")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"version\":1,\"ownerAccountId\":" + owner + "}"))
          .andExpect(status().isBadRequest());
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM responsibilities WHERE id=?", String.class, id))
          .isEqualTo("ARCHIVED");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id=?", Long.class, id))
          .isEqualTo(1L);
    }
  }

  @Test
  void descriptionNullOmissionAndEmptyAreDistinctAtHttpBoundary() throws Exception {
    UUID id = UUID.fromString(create());
    String item = path + "/" + id;
    mvc.perform(
            put(item + "/details")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"title\":\"Clean\",\"description\":\"\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM responsibilities WHERE id=?", String.class, id))
        .isEmpty();
    mvc.perform(
            put(item + "/details")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"title\":\"Clean\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT description FROM responsibilities WHERE id=?", String.class, id))
        .isNull();
    mvc.perform(
            put(item + "/details")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"title\":\"Clean\",\"description\":null}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isEqualTo(2L);
  }

  @Test
  void archivedInactiveHistoricalOwnerIsProjectedAndRestoreRequiresValidReplacement()
      throws Exception {
    partner = UUID.randomUUID();
    UUID partnerMembership = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        partner,
        partner + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,2)",
        partnerMembership,
        partner,
        household);
    MvcResult created =
        mvc.perform(
                post(path)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Partner task\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
                            + partner
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    UUID id = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    other = UUID.randomUUID();
    otherHouse = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        other,
        other + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?, 'Foreign', 'EUR', 'Europe/Berlin')",
        otherHouse);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        UUID.randomUUID(),
        other,
        otherHouse);
    UUID linkedTask = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO tasks (id,household_id,responsibility_id,title,priority,status,version,deadline_kind) VALUES (?,?,?,'Task','NORMAL','OPEN',0,'NONE')",
        linkedTask,
        household,
        id);
    String item = path + "/" + id;
    mvc.perform(
            post(item + "/archive")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0}"))
        .andExpect(status().isOk());
    jdbc.update(
        "UPDATE household_memberships SET active=false, slot=NULL WHERE id=?", partnerMembership);
    MvcResult archived =
        mvc.perform(get(item).cookie(cookie(auth))).andExpect(status().isOk()).andReturn();
    assertThat(
            (String) JsonPath.read(archived.getResponse().getContentAsString(), "$.ownerAccountId"))
        .isEqualTo(partner.toString());
    assertThat((String) JsonPath.read(archived.getResponse().getContentAsString(), "$.status"))
        .isEqualTo("ARCHIVED");
    for (String body :
        List.of(
            "{\"version\":1}",
            "{\"version\":1,\"ownerAccountId\":\"" + partner + "\"}",
            "{\"version\":1,\"ownerAccountId\":\"" + UUID.randomUUID() + "\"}",
            "{\"version\":1,\"ownerAccountId\":\"" + other + "\"}")) {
      mvc.perform(
              post(item + "/restore")
                  .cookie(cookie(auth))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM responsibilities WHERE id=?", String.class, id))
          .isEqualTo("ARCHIVED");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id=?", Long.class, id))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "SELECT owner_membership_id FROM responsibilities WHERE id=?", UUID.class, id))
          .isEqualTo(partnerMembership);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                  Long.class,
                  id))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT responsibility_id FROM tasks WHERE id=?", UUID.class, linkedTask))
          .isEqualTo(id);
    }
    mvc.perform(
            post(item + "/restore")
                .cookie(cookie(auth))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"ownerAccountId\":\"" + account + "\"}"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT owner_membership_id FROM responsibilities WHERE id=?", UUID.class, id))
        .isEqualTo(member);
    assertThat(
            jdbc.queryForObject("SELECT status FROM responsibilities WHERE id=?", String.class, id))
        .isEqualTo("ACTIVE");
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT responsibility_id FROM tasks WHERE id=?", UUID.class, linkedTask))
        .isEqualTo(id);
  }

  @Test
  void runtimeResponsesAndRejectedRequestsMatchOpenApiSchemas() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    String valid =
        "{\"title\":\"Clean\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\"" + account + "\"}";
    assertRequestSchema(contract, template, "post", valid, true);
    assertRequestSchema(
        contract,
        template,
        "post",
        "{\"title\":\"   \",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\"" + account + "\"}",
        false);
    assertRequestSchema(
        contract,
        template,
        "post",
        "{\"title\":\"Valid\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\"1-1-1-1-1\"}",
        false);
    MvcResult created =
        mvc.perform(
                post(path)
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(valid))
            .andExpect(status().isCreated())
            .andReturn();
    assertResponseSchema(contract, template, "post", created);
    UUID id = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));
    String itemTemplate = template + "/{responsibilityId}";
    MvcResult getResult =
        mvc.perform(get(path + "/" + id).cookie(cookie(auth)))
            .andExpect(status().isOk())
            .andReturn();
    assertResponseSchema(contract, itemTemplate, "get", getResult);
    MvcResult list =
        mvc.perform(get(path).param("view", "current").cookie(cookie(auth)))
            .andExpect(status().isOk())
            .andReturn();
    assertResponseSchema(contract, template, "get", list);
    String mutationTemplate = itemTemplate + "/pause";
    for (String invalid : List.of("{}", "{\"version\":\"0\"}", "{\"version\":0,\"extra\":true}")) {
      assertRequestSchema(contract, mutationTemplate, "post", invalid, false);
      MvcResult rejected =
          mvc.perform(
                  post(path + "/" + id + "/pause")
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(invalid))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertResponseSchema(contract, mutationTemplate, "post", rejected);
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id=?", Long.class, id))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM responsibilities WHERE id=?", String.class, id))
          .isEqualTo("ACTIVE");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                  Long.class,
                  id))
          .isZero();
    }
    String validPause = "{\"version\":0}";
    assertRequestSchema(contract, mutationTemplate, "post", validPause, true);
    MvcResult paused =
        mvc.perform(
                post(path + "/" + id + "/pause")
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validPause))
            .andExpect(status().isOk())
            .andReturn();
    assertResponseSchema(contract, mutationTemplate, "post", paused);

    String item = path + "/" + id;
    String[] actions = {"resume", "archive", "restore"};
    long[] versions = {1, 3, 4};
    MvcResult details =
        mvc.perform(
                put(item + "/details")
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":2,\"title\":\"Updated\"}"))
            .andExpect(status().isConflict())
            .andReturn();
    assertResponseSchema(contract, itemTemplate + "/details", "put", details);
    for (int i = 0; i < actions.length; i++) {
      if (i == 1) {
        String body = "{\"version\":2,\"title\":\"Updated\"}";
        assertRequestSchema(contract, itemTemplate + "/details", "put", body, true);
        MvcResult updated =
            mvc.perform(
                    put(item + "/details")
                        .cookie(cookie(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseSchema(contract, itemTemplate + "/details", "put", updated);
        String transfer = "{\"version\":3,\"ownerAccountId\":\"" + account + "\"}";
        assertRequestSchema(contract, itemTemplate + "/transfer", "post", transfer, true);
        MvcResult transferred =
            mvc.perform(
                    post(item + "/transfer")
                        .cookie(cookie(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseSchema(contract, itemTemplate + "/transfer", "post", transferred);
      }
      String route = itemTemplate + "/" + actions[i];
      String body = "{\"version\":" + versions[i] + "}";
      assertRequestSchema(contract, route, "post", body, true);
      MvcResult response =
          mvc.perform(
                  post(item + "/" + actions[i])
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isOk())
              .andReturn();
      assertResponseSchema(contract, route, "post", response);
      if (actions[i].equals("archive")) {
        MvcResult archived =
            mvc.perform(get(path).param("view", "archived").cookie(cookie(auth)))
                .andExpect(status().isOk())
                .andReturn();
        assertResponseSchema(contract, template, "get", archived);
      }
    }
    String malformed = "{\"version\":5,\"ownerAccountId\":\"1-1-1-1-1\"}";
    assertRequestSchema(contract, itemTemplate + "/transfer", "post", malformed, false);
    MvcResult badOwner =
        mvc.perform(
                post(item + "/transfer")
                    .cookie(cookie(auth))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformed))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertResponseSchema(contract, itemTemplate + "/transfer", "post", badOwner);
    assertThat(
            jdbc.queryForObject("SELECT version FROM responsibilities WHERE id=?", Long.class, id))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                Long.class,
                id))
        .isZero();
  }

  @Test
  void documentedMutationErrorsMatchRuntimeSchemas() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,?, 'INDIVIDUAL','ACTIVE',?,0)",
        id,
        household,
        "Existing",
        member);
    String[] suffixes = {"", "/details", "/transfer", "/pause", "/resume", "/archive", "/restore"};
    for (String suffix : suffixes) {
      boolean create = suffix.isEmpty();
      String itemTemplate = create ? template : template + "/{responsibilityId}" + suffix;
      String url = create ? path : path + "/" + id + suffix;
      String missingUrl =
          create
              ? "/api/v1/households/" + UUID.randomUUID() + "/responsibilities"
              : path + "/" + UUID.randomUUID() + suffix;
      String method = suffix.equals("/details") ? "put" : "post";
      String valid =
          create
              ? "{\"title\":\"Create\",\"mode\":\"INDIVIDUAL\",\"ownerAccountId\":\""
                  + account
                  + "\"}"
              : suffix.equals("/details")
                  ? "{\"version\":0,\"title\":\"Updated\"}"
                  : suffix.equals("/transfer")
                      ? "{\"version\":0,\"ownerAccountId\":\"" + account + "\"}"
                      : "{\"version\":0}";
      String stale = valid.replace("\"version\":0", "\"version\":99");
      MvcResult invalid =
          mvc.perform(
                  request(method, url)
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{}"))
              .andExpect(status().isBadRequest())
              .andReturn();
      assertResponseSchema(contract, itemTemplate, method, invalid);
      MvcResult unauth =
          mvc.perform(
                  request(method, url)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(valid))
              .andExpect(status().isUnauthorized())
              .andReturn();
      assertResponseSchema(contract, itemTemplate, method, unauth);
      MvcResult forbidden =
          mvc.perform(
                  request(method, url)
                      .cookie(cookie(auth))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(valid))
              .andExpect(status().isForbidden())
              .andReturn();
      assertResponseSchema(contract, itemTemplate, method, forbidden);
      MvcResult absent =
          mvc.perform(
                  request(method, missingUrl)
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(valid))
              .andExpect(status().isNotFound())
              .andReturn();
      assertResponseSchema(contract, itemTemplate, method, absent);
      MvcResult unsupported =
          mvc.perform(
                  request(method, url)
                      .cookie(cookie(auth))
                      .with(csrf())
                      .contentType(MediaType.TEXT_PLAIN)
                      .content(valid))
              .andExpect(status().isUnsupportedMediaType())
              .andReturn();
      assertResponseSchema(contract, itemTemplate, method, unsupported);
      if (!create) {
        MvcResult conflict =
            mvc.perform(
                    request(method, url)
                        .cookie(cookie(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stale))
                .andExpect(status().isConflict())
                .andReturn();
        assertResponseSchema(contract, itemTemplate, method, conflict);
      }
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id=?", Long.class, id))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM responsibility_owner_transfers WHERE responsibility_id=?",
                  Long.class,
                  id))
          .isZero();
    }
  }

  @Test
  void documentedReadErrorsMatchRuntimeSchemas() throws Exception {
    Map<?, ?> contract;
    try (var input = Files.newInputStream(Path.of("../docs/api/openapi.yml"))) {
      contract = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
    }
    String template = "/api/v1/households/{householdId}/responsibilities";
    String item = template + "/{responsibilityId}";
    MvcResult badList =
        mvc.perform(get(path).param("view", "unknown").cookie(cookie(auth)))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertResponseSchema(contract, template, "get", badList);
    MvcResult badId =
        mvc.perform(get(path + "/invalid-uuid").cookie(cookie(auth)))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertResponseSchema(contract, item, "get", badId);
    MvcResult noListAuth =
        mvc.perform(get(path).param("view", "current"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertResponseSchema(contract, template, "get", noListAuth);
    MvcResult noItemAuth =
        mvc.perform(get(path + "/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertResponseSchema(contract, item, "get", noItemAuth);
    MvcResult missingList =
        mvc.perform(
                get("/api/v1/households/" + UUID.randomUUID() + "/responsibilities")
                    .param("view", "current")
                    .cookie(cookie(auth)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertResponseSchema(contract, template, "get", missingList);
    MvcResult missingItem =
        mvc.perform(get(path + "/" + UUID.randomUUID()).cookie(cookie(auth)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertResponseSchema(contract, item, "get", missingItem);
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
      String method, String url) {
    return method.equals("put") ? put(url) : post(url);
  }

  private void assertRequestSchema(
      Map<?, ?> contract, String path, String method, String json, boolean expected) {
    Map<?, ?> op = map(map(map(contract.get("paths")).get(path)).get(method));
    Map<?, ?> body = map(map(map(op.get("requestBody")).get("content")).get("application/json"));
    boolean matches = schemaMatches(contract, map(body.get("schema")), jsonMapper.readTree(json));
    assertThat(matches)
        .as("request conforms to %s %s schema: %s", method, path, json)
        .isEqualTo(expected);
  }

  private void assertResponseSchema(
      Map<?, ?> contract, String path, String method, MvcResult result) throws Exception {
    Map<?, ?> op = map(map(map(contract.get("paths")).get(path)).get(method));
    Map<?, ?> responses = map(op.get("responses"));
    String code = Integer.toString(result.getResponse().getStatus());
    assertThat(responses.containsKey(code)).isTrue();
    Map<?, ?> response = resolve(contract, map(responses.get(code)));
    String contentType =
        MediaType.parseMediaType(result.getResponse().getContentType()).getType()
            + "/"
            + MediaType.parseMediaType(result.getResponse().getContentType()).getSubtype();
    Map<?, ?> content = map(response.get("content"));
    assertThat(content.containsKey(contentType)).isTrue();
    Map<?, ?> schema = map(map(content.get(contentType)).get("schema"));
    assertThat(
            schemaMatches(
                contract, schema, jsonMapper.readTree(result.getResponse().getContentAsString())))
        .as("response conforms to %s %s %s schema", method, path, code)
        .isTrue();
  }

  private static Map<?, ?> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<?, ?>) value;
  }

  private static Map<?, ?> resolve(Map<?, ?> contract, Map<?, ?> schema) {
    if (!schema.containsKey("$ref")) {
      return schema;
    }
    Object current = contract;
    String reference = (String) schema.get("$ref");
    assertThat(reference).startsWith("#/");
    for (String segment : reference.substring(2).split("/")) {
      current = map(current).get(segment.replace("~1", "/").replace("~0", "~"));
    }
    return resolve(contract, map(current));
  }

  private static boolean schemaMatches(Map<?, ?> contract, Map<?, ?> raw, JsonNode value) {
    Map<?, ?> schema = resolve(contract, raw);
    Object type = schema.get("type");
    if (type == null) {
      return true;
    }
    List<?> types = type instanceof List<?> list ? list : List.of(type);
    boolean matches =
        types.stream()
            .anyMatch(
                t ->
                    switch ((String) t) {
                      case "object" -> value.isObject();
                      case "array" -> value.isArray();
                      case "string" -> value.isString();
                      case "integer" -> value.isIntegralNumber();
                      case "boolean" -> value.isBoolean();
                      case "null" -> value.isNull();
                      case "number" -> value.isNumber();
                      default -> throw new AssertionError("Unsupported type: " + t);
                    });
    if (!matches) {
      return false;
    }
    if (value.isNull()) {
      return true;
    }
    if (schema.containsKey("enum") && !((List<?>) schema.get("enum")).contains(value.asString())) {
      return false;
    }
    if (value.isObject()) {
      if (schema.containsKey("required")) {
        for (Object field : (List<?>) schema.get("required")) {
          if (!value.has((String) field)) {
            return false;
          }
        }
      }
      Map<?, ?> properties =
          schema.containsKey("properties") ? map(schema.get("properties")) : Map.of();
      if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
        for (String name : value.propertyNames()) {
          if (!properties.containsKey(name)) {
            return false;
          }
        }
      }
      for (var entry : properties.entrySet()) {
        String name = (String) entry.getKey();
        if (value.has(name) && !schemaMatches(contract, map(entry.getValue()), value.get(name))) {
          return false;
        }
      }
    } else if (value.isArray()) {
      for (JsonNode item : value) {
        if (!schemaMatches(contract, map(schema.get("items")), item)) {
          return false;
        }
      }
    } else if (value.isString()) {
      String text = value.asString();
      if (schema.containsKey("pattern")
          && !java.util.regex.Pattern.compile(schema.get("pattern").toString())
              .matcher(text)
              .find()) {
        return false;
      }
      if ("uuid".equals(schema.get("format"))) {
        try {
          if (!UUID.fromString(text).toString().equalsIgnoreCase(text)) {
            return false;
          }
        } catch (IllegalArgumentException e) {
          return false;
        }
      }
    } else if (value.isIntegralNumber()) {
      var number = value.bigIntegerValue();
      if (schema.containsKey("minimum")
          && number.compareTo(new java.math.BigInteger(schema.get("minimum").toString())) < 0) {
        return false;
      }
      if (schema.containsKey("maximum")
          && number.compareTo(new java.math.BigInteger(schema.get("maximum").toString())) > 0) {
        return false;
      }
    }
    return true;
  }
}
