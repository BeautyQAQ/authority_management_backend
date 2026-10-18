package com.liushao.vueadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.vueadmin.security.AuthStateStore;
import com.liushao.vueadmin.utils.JwtUtil;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TestRedisHealthIndicator redisHealth;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtUtil jwt;
  @MockitoBean AuthStateStore state;
  final Map<String, String> captchas = new HashMap<>(), sessions = new HashMap<>();
  String admin;

  @BeforeEach
  void setup() {
    redisHealth.current = Health.up().build();
    db.update(
        "INSERT INTO sys_user(id,username,password,statu) VALUES(1,'admin',?,1)",
        passwords.encode("Admin-test-123"));
    db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(1,1)");
    doAnswer(
            a -> {
              captchas.put(a.getArgument(0), a.getArgument(1));
              return null;
            })
        .when(state)
        .saveCaptcha(anyString(), anyString());
    when(state.consumeCaptcha(anyString())).thenAnswer(a -> captchas.remove(a.getArgument(0)));
    doAnswer(
            a -> {
              sessions.put(a.getArgument(0), a.getArgument(1));
              return null;
            })
        .when(state)
        .saveSession(anyString(), anyString(), any(Duration.class));
    when(state.active(anyString(), anyString()))
        .thenAnswer(a -> Objects.equals(sessions.get(a.getArgument(0)), a.getArgument(1)));
    doAnswer(
            a -> {
              sessions.remove(a.getArgument(0));
              return null;
            })
        .when(state)
        .revoke(anyString());
    when(state.allowLogin(anyString())).thenReturn(true);
    admin = token("admin");
  }

  String token(String username) {
    long version =
        db.queryForObject(
            "SELECT auth_version FROM sys_user WHERE username=?", Long.class, username);
    String token = jwt.generateToken(username, version);
    sessions.put(jwt.getClaimByToken(token).getId(), username);
    return "Bearer " + token;
  }

  ResultActions postJson(String path, String token, Object body) throws Exception {
    return mvc.perform(
        post(path)
            .header("Authorization", token)
            .contentType("application/json")
            .content(json.writeValueAsBytes(body)));
  }

  long created(String path, Object body) throws Exception {
    return json.readTree(
            postJson(path, admin, body)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("data")
        .asLong();
  }

  Map<String, Object> user(String username) {
    return Map.of(
        "username",
        username,
        "password",
        "Member-test-123",
        "email",
        username + "@example.com",
        "statu",
        1);
  }

  Map<String, Object> role(String name) {
    return Map.of("name", name, "code", name, "statu", 1);
  }

  Map<String, Object> menu(String name, long parent) {
    return Map.of(
        "name",
        name,
        "parentId",
        parent,
        "type",
        0,
        "perms",
        "custom:" + name,
        "orderNum",
        1,
        "statu",
        1);
  }


  @Test
  void healthProbesArePublicAndDoNotRevealInfrastructure() throws Exception {
    for (String endpoint : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
      mvc.perform(get(endpoint))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"))
          .andExpect(jsonPath("$.components").doesNotExist())
          .andExpect(jsonPath("$.details").doesNotExist());
    }
    mvc.perform(get("/actuator/env").header("Authorization", admin)).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/configprops").header("Authorization", admin)).andExpect(status().isNotFound());
  }

  @Test
  void readinessFailsWhenRedisIsDownButLivenessStaysUp() throws Exception {
    redisHealth.current = Health.down().withDetail("private-host", "should-not-be-published").build();
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void loginConsumesCaptchaAndLogoutRevokesSession() throws Exception {
    var captchaResult =
        mvc.perform(get("/captcha"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                jsonPath("$.data.captchaImg")
                    .value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
            .andReturn();
    String id =
        json.readTree(captchaResult.getResponse().getContentAsString())
            .path("data")
            .path("token")
            .asText();
    String code = captchas.get(id);
    var login =
        mvc.perform(
                post("/login")
                    .param("username", "admin")
                    .param("password", "Admin-test-123")
                    .param("token", id)
                    .param("code", code.toLowerCase()))
            .andExpect(status().isOk())
            .andExpect(header().exists("Authorization"))
            .andReturn();
    String signed = login.getResponse().getHeader("Authorization");
    mvc.perform(
            post("/login")
                .param("username", "admin")
                .param("password", "Admin-test-123")
                .param("token", id)
                .param("code", code))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/sys/userInfo").header("Authorization", signed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.password").doesNotExist());
    postJson("/logout", signed, Map.of()).andExpect(status().isOk());
    mvc.perform(get("/sys/userInfo").header("Authorization", signed))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsBadLoginAndRateLimits() throws Exception {
    captchas.put("a", "12345");
    mvc.perform(
            post("/login")
                .param("username", "admin")
                .param("password", "wrong")
                .param("token", "a")
                .param("code", "12345"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Authorization"));
    mvc.perform(
            post("/login")
                .param("username", "admin")
                .param("password", "Admin-test-123")
                .param("token", "expired")
                .param("code", "12345"))
        .andExpect(status().isBadRequest());
    when(state.allowLogin(anyString())).thenReturn(false);
    mvc.perform(
            post("/login")
                .param("username", "admin")
                .param("password", "Admin-test-123")
                .param("token", "a")
                .param("code", "12345"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void requiresAuthenticationAndReturnsSanitizedPagedUsers() throws Exception {
    mvc.perform(get("/sys/user/list")).andExpect(status().isUnauthorized());
    mvc.perform(get("/sys/user/list").header("Authorization", "Bearer garbage"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/sys/user/list").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[0].password").doesNotExist())
        .andExpect(jsonPath("$.data.records[0].roles[0].code").value("admin"));
    mvc.perform(get("/sys/user/list?size=101").header("Authorization", admin))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/sys/user/list?username=' OR 1=1 --").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));
  }

  @Test
  void userRolePermissionsApplyImmediatelyAndAssignmentsAreAtomic() throws Exception {
    long userId = created("/sys/user/save", user("reader"));
    long roleId = created("/sys/role/save", role("reader"));
    postJson("/sys/role/perm/" + roleId, admin, List.of(2)).andExpect(status().isOk());
    postJson("/sys/user/role/" + userId, admin, List.of(roleId)).andExpect(status().isOk());
    String reader = token("reader");
    mvc.perform(get("/sys/menu/nav").header("Authorization", reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nav[0].children[0].component").value("sys/User"));
    mvc.perform(get("/sys/user/list").header("Authorization", reader)).andExpect(status().isOk());
    postJson("/sys/user/save", reader, user("forbidden")).andExpect(status().isForbidden());
    postJson("/sys/user/role/" + userId, admin, List.of(roleId, 999999))
        .andExpect(status().isNotFound());
    assertThat(
            db.queryForList(
                "SELECT role_id FROM sys_user_role WHERE user_id=?", Long.class, userId))
        .containsExactly(roleId);
    postJson("/sys/role/perm/" + roleId, admin, List.of()).andExpect(status().isOk());
    mvc.perform(get("/sys/user/list").header("Authorization", reader))
        .andExpect(status().isForbidden());
    postJson("/sys/user/delete", admin, List.of(userId)).andExpect(status().isOk());
    mvc.perform(get("/sys/userInfo").header("Authorization", reader))
        .andExpect(status().isUnauthorized());
    postJson("/sys/role/delete", admin, List.of(roleId)).andExpect(status().isOk());
  }

  @Test
  void cannotEscalatePermissionsOrModifyBuiltins() throws Exception {
    long uid = created("/sys/user/save", user("manager"));
    long rid = created("/sys/role/save", role("manager"));
    long target = created("/sys/role/save", role("target"));
    postJson("/sys/role/perm/" + rid, admin, List.of(8, 11, 13)).andExpect(status().isOk());
    postJson("/sys/user/role/" + uid, admin, List.of(rid)).andExpect(status().isOk());
    String manager = token("manager");
    postJson("/sys/user/role/" + uid, manager, List.of(1)).andExpect(status().isForbidden());
    postJson("/sys/role/perm/" + target, manager, List.of(7)).andExpect(status().isForbidden());
    postJson("/sys/role/perm/" + target, admin, List.of(7)).andExpect(status().isOk());
    var update = new HashMap<>(role("target"));
    update.put("id", target);
    postJson("/sys/role/update", manager, update).andExpect(status().isForbidden());
    postJson("/sys/user/delete", admin, List.of(1)).andExpect(status().isForbidden());
    postJson("/sys/role/delete", admin, List.of(1)).andExpect(status().isForbidden());
    postJson("/sys/menu/delete/1", admin, Map.of()).andExpect(status().isForbidden());
  }

  @Test
  void communityPermissionsCanBeGrantedWithoutChangingProtectedMenus() throws Exception {
    long listId = db.queryForObject("SELECT id FROM sys_menu WHERE perms='community:user:list'", Long.class);
    long statusId = db.queryForObject("SELECT id FROM sys_menu WHERE perms='community:user:status'", Long.class);
    long uid = created("/sys/user/save", user("community_operator"));
    long rid = created("/sys/role/save", role("community_operator"));
    postJson("/sys/role/perm/" + rid, admin, List.of(listId)).andExpect(status().isOk());
    postJson("/sys/user/role/" + uid, admin, List.of(rid)).andExpect(status().isOk());
    String reader = token("community_operator");
    mvc.perform(get("/sys/menu/nav").header("Authorization", reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nav[0].title").value("社区运营"))
        .andExpect(jsonPath("$.data.nav[0].children[0].component").value("community/Users"))
        .andExpect(jsonPath("$.data.authoritys", org.hamcrest.Matchers.hasItem("community:user:list")));
    mvc.perform(put("/community/users/7/status").header("Authorization", reader)
        .contentType("application/json").content("{\"status\":false,\"reason\":\"违规\"}"))
        .andExpect(status().isForbidden());
    postJson("/sys/role/perm/" + rid, admin, List.of(statusId)).andExpect(status().isOk());
    mvc.perform(get("/sys/menu/nav").header("Authorization", reader))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authoritys", org.hamcrest.Matchers.hasItem("community:user:status")))
        .andExpect(jsonPath("$.data.authoritys", org.hamcrest.Matchers.hasItem("community:user:list")));
    postJson("/sys/menu/delete/" + statusId, admin, Map.of()).andExpect(status().isForbidden());
    var edit = new HashMap<>(menu("renamed_builtin", 0));
    edit.put("id", statusId);
    postJson("/sys/menu/update", admin, edit).andExpect(status().isForbidden());
  }

  @Test
  void validatesMenuCyclesAndDeletesChildrenFirst() throws Exception {
    long parent = created("/sys/menu/save", menu("parent", 0));
    long child = created("/sys/menu/save", menu("child", parent));
    var cycle = new HashMap<>(menu("parent", child));
    cycle.put("id", parent);
    postJson("/sys/menu/update", admin, cycle).andExpect(status().isBadRequest());
    postJson("/sys/menu/delete/" + parent, admin, Map.of()).andExpect(status().isConflict());
    var invalid = new HashMap<>(menu("invalid", 0));
    invalid.put("type", 1);
    postJson("/sys/menu/save", admin, invalid).andExpect(status().isBadRequest());
    postJson("/sys/menu/delete/" + child, admin, Map.of()).andExpect(status().isOk());
    postJson("/sys/menu/delete/" + parent, admin, Map.of()).andExpect(status().isOk());
  }

  @Test
  void passwordChangesInvalidateExistingTokens() throws Exception {
    long uid = created("/sys/user/save", user("member"));
    String member = token("member");
    postJson(
            "/sys/user/updatePass",
            member,
            Map.of("currentPass", "wrong", "password", "New-pass-123", "checkPass", "New-pass-123"))
        .andExpect(status().isBadRequest());
    postJson(
            "/sys/user/updatePass",
            member,
            Map.of(
                "currentPass",
                "Member-test-123",
                "password",
                "New-pass-123",
                "checkPass",
                "New-pass-123"))
        .andExpect(status().isOk());
    mvc.perform(get("/sys/userInfo").header("Authorization", member))
        .andExpect(status().isUnauthorized());
    assertThat(
            passwords.matches(
                "New-pass-123",
                db.queryForObject("SELECT password FROM sys_user WHERE id=?", String.class, uid)))
        .isTrue();
    String changed = token("member");
    postJson("/sys/user/repass", admin, Map.of("id", uid, "password", "Reset-pass-123"))
        .andExpect(status().isOk());
    mvc.perform(get("/sys/userInfo").header("Authorization", changed))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void disabledUsersAndInvalidInputAreRejected() throws Exception {
    long uid = created("/sys/user/save", user("disabled"));
    String signed = token("disabled");
    var input = new HashMap<>(user("disabled"));
    input.put("id", uid);
    input.put("statu", 0);
    postJson("/sys/user/update", admin, input).andExpect(status().isOk());
    mvc.perform(get("/sys/userInfo").header("Authorization", signed))
        .andExpect(status().isUnauthorized());
    postJson("/sys/user/save", admin, user("disabled")).andExpect(status().isConflict());
    postJson("/sys/user/delete", admin, Arrays.asList((Long) null))
        .andExpect(status().isBadRequest());
    postJson("/sys/user/save", admin, Map.of("username", "short", "password", "short", "statu", 1))
        .andExpect(status().isBadRequest());
  }
  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class HealthTestConfiguration {
    @org.springframework.context.annotation.Bean
    TestRedisHealthIndicator redisHealthIndicator() {
      return new TestRedisHealthIndicator();
    }
  }

  static class TestRedisHealthIndicator implements HealthIndicator {
    Health current = Health.up().build();

    @Override
    public Health health() {
      return current;
    }
  }
}
