package com.liushao.vueadmin.community;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.liushao.vueadmin.common.exception.GlobalExceptionHandler;
import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.config.SecurityConfig;
import com.liushao.vueadmin.security.*;
import com.liushao.vueadmin.service.AdminService;
import com.liushao.vueadmin.utils.JwtUtil;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CommunityAdminController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class,GlobalExceptionHandler.class,CommunityPermissions.class})
class CommunityAdminControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean CommunityAdminBridge bridge;
  @MockitoBean JwtUtil jwt;
  @MockitoBean AuthStateStore state;
  @MockitoBean AdminService users;
  RequestPostProcessor operator(String... permissions) {
    return authentication(new UsernamePasswordAuthenticationToken(new AdminPrincipal(9,"operator",0),null,
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
  }
  @Test void requiresLoginAndEachOperationPermission() throws Exception {
    mvc.perform(get("/community/users")).andExpect(status().isUnauthorized());
    mvc.perform(put("/community/users/7/status").with(operator("community:user:list"))
        .contentType("application/json").content("{\"status\":false,\"reason\":\"违规\"}")).andExpect(status().isForbidden());
    mvc.perform(get("/community/posts").with(operator("community:user:list"))).andExpect(status().isForbidden());
    mvc.perform(post("/community/tags").with(operator("community:billboard:create"))
        .contentType("application/json").content("{\"name\":\"java\"}")).andExpect(status().isForbidden());
    verifyNoInteractions(bridge);
  }
  @Test void actorComesFromBackendPrincipalAndQueryIsWhitelisted() throws Exception {
    when(bridge.forward(eq(HttpMethod.GET),eq("users"),anyMap(),isNull(),eq("operator")))
        .thenReturn(ResponseEntity.ok(Result.succ(Map.of("content",java.util.List.of()))));
    mvc.perform(get("/community/users").with(operator("community:user:list")).param("query","hello")
        .param("url","http://attacker.invalid").header("X-Community-Admin-Actor","forged"))
        .andExpect(status().isOk());
    verify(bridge).forward(HttpMethod.GET,"users",Map.of("query","hello","pageNo","1","size","20"),null,"operator");
  }
  @Test void rejectsInvalidPaginationMutationAndUnknownFields() throws Exception {
    mvc.perform(get("/community/users").with(operator("community:user:list")).param("size","500")).andExpect(status().isBadRequest());
    mvc.perform(put("/community/users/7/status").with(operator("community:user:status"))
        .contentType("application/json").content("{\"status\":false,\"reason\":\"  \"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/community/tags").with(operator("community:tag:create"))
        .contentType("application/json").content("{\"name\":\"java\",\"id\":1}")).andExpect(status().isBadRequest());
    mvc.perform(post("/community/promotions").with(operator("community:promotion:create"))
        .contentType("application/json").content("{\"title\":\"x\",\"link\":\"javascript:alert(1)\"}")).andExpect(status().isBadRequest());
    verifyNoInteractions(bridge);
  }
  @Test void catalogCreateAndConflictUseCorrectPermissionAndStatus() throws Exception {
    when(bridge.forward(eq(HttpMethod.POST),eq("tags"),eq(Map.of()),any(),eq("operator")))
        .thenReturn(ResponseEntity.status(409).body(Result.fail(409,"标签已存在",null)));
    mvc.perform(post("/community/tags").with(operator("community:tag:create"))
        .contentType("application/json").content("{\"name\":\"java\"}"))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.msg").value("标签已存在"));
  }
  @Test void unsupportedRoutesNeverBecomeAnArbitraryProxy() throws Exception {
    mvc.perform(get("/community/actuator/env").with(operator("community:user:list"))).andExpect(status().isNotFound());
    mvc.perform(post("/community/users").with(operator("community:user:status"))).andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(bridge);
  }
}
