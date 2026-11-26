package dev.sharedops.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.sharedops.TestcontainersConfiguration;
import dev.sharedops.config.security.SessionPolicy;
import dev.sharedops.household.infrastructure.security.AccountPrincipal;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TaskApiIT {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper jsonMapper;
  @Autowired SessionRepository<? extends Session> sessions;
  UUID household = UUID.randomUUID(),
      account = UUID.randomUUID(),
      member = UUID.randomUUID(),
      responsibility = UUID.randomUUID();
  String path;
  Cookie cookie;

  @BeforeEach
  void setup() {
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        account,
        account + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?,'Home','EUR','Europe/Berlin')",
        household);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        member,
        account,
        household);
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,'Context','INDIVIDUAL','ACTIVE',?,0)",
        responsibility,
        household,
        member);
    path = "/api/v1/households/" + household + "/tasks";
    var principal = new AccountPrincipal(account, account + "@example.com", "hash", 0);
    cookie =
        session(
            sessions,
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  private <S extends Session> Cookie session(
      SessionRepository<S> repo, org.springframework.security.core.Authentication auth) {
    MockHttpSession policy = new MockHttpSession();
    SessionPolicy.initialize(policy, false, Instant.now());
    S session = repo.createSession();
    var names = policy.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      session.setAttribute(name, policy.getAttribute(name));
    }
    session.setMaxInactiveInterval(Duration.ofSeconds(policy.getMaxInactiveInterval()));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    repo.save(session);
    return new Cookie(
        "SESSION",
        Base64.getEncoder().encodeToString(session.getId().getBytes(StandardCharsets.UTF_8)));
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM task_edit_facts WHERE household_id=?", household);
    jdbc.update("DELETE FROM tasks WHERE household_id=?", household);
    jdbc.update("DELETE FROM responsibilities WHERE household_id=?", household);
    jdbc.update("DELETE FROM household_memberships WHERE household_id=?", household);
    jdbc.update("DELETE FROM households WHERE id=?", household);
    jdbc.update("DELETE FROM accounts WHERE id=?", account);
  }

  private MvcResult create(String json) throws Exception {
    return mvc.perform(
            post(path)
                .cookie(cookie)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  private MvcResult edit(String id, String json) throws Exception {
    return mvc.perform(
            patch(path + "/" + id)
                .cookie(cookie)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  @Test
  void createsReadsAndEditsWithPresenceAndPrecision() throws Exception {
    MvcResult created = create("{\"title\":\"Laundry\"}");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    String body = created.getResponse().getContentAsString();
    String id = JsonPath.read(body, "$.id");
    assertThat(created.getResponse().getHeader("Location")).isEqualTo(path + "/" + id);
    assertThat((String) JsonPath.read(body, "$.priority")).isEqualTo("NORMAL");
    assertThat((String) JsonPath.read(body, "$.deadline.kind")).isEqualTo("NONE");
    assertThat((Object) JsonPath.read(body, "$.assigneeAccountId")).isNull();
    mvc.perform(get(path + "/" + id).cookie(cookie)).andExpect(status().isOk());
    MvcResult updated =
        edit(
            id,
            "{\"version\":0,\"description\":\"work\",\"assigneeAccountId\":\""
                + account
                + "\",\"deadline\":{\"kind\":\"DATE_TIME\",\"instant\":\"2025-01-01T00:00:00.123456789Z\"}}");
    assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    assertThat(
            (String)
                JsonPath.read(updated.getResponse().getContentAsString(), "$.deadline.instant"))
        .isEqualTo("2025-01-01T00:00:00.123456789Z");
    assertThat(edit(id, "{\"version\":0,\"title\":\"stale\"}").getResponse().getStatus())
        .isEqualTo(409);
    MvcResult cleared =
        edit(
            id,
            "{\"version\":1,\"description\":null,\"assigneeAccountId\":null,\"deadline\":{\"kind\":\"NONE\"}}");
    assertThat(cleared.getResponse().getStatus()).isEqualTo(200);
    assertThat((Object) JsonPath.read(cleared.getResponse().getContentAsString(), "$.description"))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM task_edit_facts WHERE household_id=?",
                Integer.class,
                household))
        .isEqualTo(2);
  }

  @Test
  void rejectsTrailingJsonOnCreateAndEditWithoutCommittedChanges() throws Exception {
    MvcResult created = create("{\"title\":\"original\"}");
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    for (String suffix : new String[] {" {}", " null", " true", " trailing"}) {
      MvcResult rejectedCreate = create("{\"title\":\"extra\"}" + suffix);
      assertThat(rejectedCreate.getResponse().getStatus()).isEqualTo(400);
      assertThat(
              (String) JsonPath.read(rejectedCreate.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("INVALID_VALUE");
      MvcResult rejectedEdit = edit(id, "{\"version\":0,\"title\":\"changed\"}" + suffix);
      assertThat(rejectedEdit.getResponse().getStatus()).isEqualTo(400);
      assertThat((String) JsonPath.read(rejectedEdit.getResponse().getContentAsString(), "$.code"))
          .isEqualTo("INVALID_VALUE");
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE household_id=?", Integer.class, household))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM tasks WHERE id=?", String.class, UUID.fromString(id)))
        .isEqualTo("original");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM tasks WHERE id=?", Long.class, UUID.fromString(id)))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM task_edit_facts WHERE household_id=?",
                Integer.class,
                household))
        .isZero();
  }

  @Test
  void linkedDefaultsOwnerAndRejectsBadRequestsWithoutWrites() throws Exception {
    MvcResult linked =
        create("{\"title\":\"Linked\",\"responsibilityId\":\"" + responsibility + "\"}");
    assertThat(linked.getResponse().getStatus()).isEqualTo(201);
    assertThat(
            (String)
                JsonPath.read(linked.getResponse().getContentAsString(), "$.assigneeAccountId"))
        .isEqualTo(account.toString());
    MvcResult unassigned =
        create(
            "{\"title\":\"Linked\",\"responsibilityId\":\""
                + responsibility
                + "\",\"assigneeAccountId\":null}");
    assertThat(unassigned.getResponse().getStatus()).isEqualTo(201);
    assertThat(
            (Object)
                JsonPath.read(unassigned.getResponse().getContentAsString(), "$.assigneeAccountId"))
        .isNull();
    for (String json :
        new String[] {
          "{\"title\":\"x\",\"title\":\"y\"}",
          "{\"title\":\"x\",\"priority\":null}",
          "{\"title\":\"x\",\"unknown\":1}",
          "{\"title\":\"x\",\"deadline\":{\"kind\":\"DATE_TIME\",\"localDateTime\":\"2025-03-30T02:30:00\"}}",
          "{\"title\":\"x\",\"assigneeAccountId\":\"" + UUID.randomUUID() + "\"}"
        }) {
      assertThat(create(json).getResponse().getStatus()).isEqualTo(400);
    }
    jdbc.update("UPDATE responsibilities SET status='PAUSED' WHERE id=?", responsibility);
    assertThat(
            create("{\"title\":\"x\",\"responsibilityId\":\"" + responsibility + "\"}")
                .getResponse()
                .getStatus())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tasks WHERE household_id=?", Integer.class, household))
        .isEqualTo(2);
  }

  private void assertEquivalentNotFound(MvcResult foreign, MvcResult missing) throws Exception {
    assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
    assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    var foreignBody = (ObjectNode) jsonMapper.readTree(foreign.getResponse().getContentAsString());
    var missingBody = (ObjectNode) jsonMapper.readTree(missing.getResponse().getContentAsString());
    // The request URI is expected to differ; every other problem field must match.
    foreignBody.remove("instance");
    missingBody.remove("instance");
    assertThat(foreignBody.path("code").asString()).isEqualTo("NOT_FOUND");
    assertThat(foreignBody).isEqualTo(missingBody);
  }

  @Test
  void foreignAndMissingResponsibilitiesAreIndistinguishableAndDoNotWrite() throws Exception {
    UUID otherHouse = UUID.randomUUID(), otherAccount = UUID.randomUUID();
    UUID otherMember = UUID.randomUUID(), foreignResponsibility = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO accounts (id,email,password_hash) VALUES (?,?,?)",
        otherAccount,
        otherAccount + "@example.com",
        "hash");
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?,'Other','EUR','Europe/Berlin')",
        otherHouse);
    jdbc.update(
        "INSERT INTO household_memberships (id,account_id,household_id,active,slot) VALUES (?,?,?,true,1)",
        otherMember,
        otherAccount,
        otherHouse);
    jdbc.update(
        "INSERT INTO responsibilities (id,household_id,title,mode,status,owner_membership_id,version) VALUES (?,?,'Foreign','INDIVIDUAL','ACTIVE',?,0)",
        foreignResponsibility,
        otherHouse,
        otherMember);
    try {
      MvcResult foreign =
          create("{\"title\":\"x\",\"responsibilityId\":\"" + foreignResponsibility + "\"}");
      MvcResult missing =
          create("{\"title\":\"x\",\"responsibilityId\":\"" + UUID.randomUUID() + "\"}");
      assertEquivalentNotFound(foreign, missing);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM tasks WHERE household_id IN (?,?)",
                  Integer.class,
                  household,
                  otherHouse))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM task_edit_facts WHERE household_id IN (?,?)",
                  Integer.class,
                  household,
                  otherHouse))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT status FROM responsibilities WHERE id=?",
                  String.class,
                  foreignResponsibility))
          .isEqualTo("ACTIVE");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM responsibilities WHERE id=?",
                  Long.class,
                  foreignResponsibility))
          .isZero();
    } finally {
      jdbc.update("DELETE FROM tasks WHERE household_id=?", otherHouse);
      jdbc.update("DELETE FROM responsibilities WHERE household_id=?", otherHouse);
      jdbc.update("DELETE FROM household_memberships WHERE household_id=?", otherHouse);
      jdbc.update("DELETE FROM households WHERE id=?", otherHouse);
      jdbc.update("DELETE FROM accounts WHERE id=?", otherAccount);
    }
  }

  @Test
  void inaccessibleTaskMatchesMissingAndTerminalEditsLeaveNoFact() throws Exception {
    UUID otherHouse = UUID.randomUUID(), foreignTask = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO households (id,name,currency,timezone) VALUES (?,'Other','EUR','Europe/Berlin')",
        otherHouse);
    jdbc.update(
        "INSERT INTO tasks (id,household_id,title,priority,status,version,deadline_kind) VALUES (?,?,'Foreign','NORMAL','OPEN',0,'NONE')",
        foreignTask,
        otherHouse);
    try {
      MvcResult foreign = mvc.perform(get(path + "/" + foreignTask).cookie(cookie)).andReturn();
      MvcResult missing =
          mvc.perform(get(path + "/" + UUID.randomUUID()).cookie(cookie)).andReturn();
      assertEquivalentNotFound(foreign, missing);
      MvcResult foreignEdit = edit(foreignTask.toString(), "{\"version\":0,\"title\":\"changed\"}");
      MvcResult missingEdit =
          edit(UUID.randomUUID().toString(), "{\"version\":0,\"title\":\"changed\"}");
      assertEquivalentNotFound(foreignEdit, missingEdit);
      assertThat(
              jdbc.queryForObject("SELECT title FROM tasks WHERE id=?", String.class, foreignTask))
          .isEqualTo("Foreign");
      assertThat(
              jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Long.class, foreignTask))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM tasks WHERE household_id=?", Integer.class, household))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM task_edit_facts WHERE household_id IN (?,?)",
                  Integer.class,
                  household,
                  otherHouse))
          .isZero();
      MvcResult created = create("{\"title\":\"terminal\"}");
      String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
      jdbc.update("UPDATE tasks SET status='COMPLETED' WHERE id=?", UUID.fromString(id));
      assertThat(edit(id, "{\"version\":0,\"title\":\"changed\"}").getResponse().getStatus())
          .isEqualTo(409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM task_edit_facts WHERE household_id=?",
                  Integer.class,
                  household))
          .isZero();
    } finally {
      jdbc.update("DELETE FROM tasks WHERE household_id=?", otherHouse);
      jdbc.update("DELETE FROM households WHERE id=?", otherHouse);
    }
  }
}
