package com.liushao.vueadmin.community;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.vueadmin.common.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;

class CommunityAdminBridgeTest {
  HttpServer server;
  CommunityAdminBridge bridge;
  int status;
  String response, uri, actor, key, authorization, method, body;
  static final String KEY = "test-only-community-key-0123456789";
  @BeforeEach void start() throws Exception {
    status=200; response="{\"code\":200,\"message\":\"ok\",\"data\":{\"id\":7}}";
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      uri=exchange.getRequestURI().toString(); method=exchange.getRequestMethod();
      actor=exchange.getRequestHeaders().getFirst("X-Community-Admin-Actor");
      key=exchange.getRequestHeaders().getFirst("X-Community-Admin-Key");
      authorization=exchange.getRequestHeaders().getFirst("Authorization");
      body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/json");
      if(status==302) exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/leaked");
      byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    });
    server.start(); bridge=new CommunityAdminBridge("http://127.0.0.1:"+server.getAddress().getPort(),KEY,new ObjectMapper());
  }
  @AfterEach void stop(){server.stop(0);}
  @Test void forwardsOnlyServerCredentialsAndEncodesActorAndQuery() {
    var result=bridge.forward(HttpMethod.GET,"users",Map.of("query","a&status=false + 中文"),null,"运营 管理员");
    assertThat(result.getStatusCode().value()).isEqualTo(200);
    assertThat(uri).startsWith("/internal/admin/users?query=").doesNotContain("&status=false");
    assertThat(URLDecoder.decode(actor,StandardCharsets.UTF_8)).isEqualTo("运营 管理员");
    assertThat(key).isEqualTo(KEY); assertThat(authorization).isNull();
    assertThat(result.getBody().getData().toString()).contains("\"id\":7");
  }
  @Test void preservesConflictWithoutExposingErrorPayload() {
    status=409;response="{\"code\":409,\"message\":\"标签仍被使用\",\"data\":{\"internal\":\"private\"}}";
    var result=bridge.forward(HttpMethod.DELETE,"tags/7",Map.of(),null,"admin");
    assertThat(result.getStatusCode().value()).isEqualTo(409);
    assertThat(result.getBody().getMsg()).isEqualTo("标签仍被使用");assertThat(result.getBody().getData()).isNull();
  }
  @Test void serializesTypedMutationAndKeepsFixedMethod() {
    bridge.forward(HttpMethod.PUT,"users/7/status",Map.of(),new CommunityRequests.UserStatus(false,"违规"),"admin");
    assertThat(method).isEqualTo("PUT");assertThat(uri).isEqualTo("/internal/admin/users/7/status");
    assertThat(body).contains("\"status\":false","违规");
  }
  @Test void disabledBridgeAndUnlistedRoutesCannotMakeRequests() {
    var disabled=new CommunityAdminBridge("http://127.0.0.1:"+server.getAddress().getPort(),"",new ObjectMapper());
    assertThatThrownBy(()->disabled.forward(HttpMethod.GET,"users",Map.of(),null,"admin"))
        .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(503));
    assertThatThrownBy(()->bridge.forward(HttpMethod.GET,"../actuator/env",Map.of(),null,"admin")).isInstanceOf(ApiException.class);
    assertThatThrownBy(()->bridge.forward(HttpMethod.POST,"users",Map.of(),Map.of(),"admin")).isInstanceOf(ApiException.class);
    assertThat(uri).isNull();
  }
  @Test void malformedResponsesAndRedirectsAreRejected() {
    response="<html>private stack trace</html>";
    assertThatThrownBy(()->bridge.forward(HttpMethod.GET,"audit",Map.of(),null,"admin"))
        .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(502);assertThat(e.getMessage()).doesNotContain("private");});
    status=302;
    assertThatThrownBy(()->bridge.forward(HttpMethod.GET,"audit",Map.of(),null,"admin"))
        .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(502));
  }
  @Test void upstreamAuthenticationFailureDoesNotInvalidateAdminLogin() {
    status=401;response="{\"code\":401,\"message\":\"bad key\",\"data\":null}";
    assertThatThrownBy(()->bridge.forward(HttpMethod.GET,"users",Map.of(),null,"admin"))
        .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(502));
  }
  @Test void backendFailuresAreSanitized() {
    status=500;response="{\"code\":500,\"message\":\"database password private\",\"data\":null}";
    var result=bridge.forward(HttpMethod.GET,"users",Map.of(),null,"admin");
    assertThat(result.getStatusCode().value()).isEqualTo(500);
    assertThat(result.getBody().getMsg()).doesNotContain("private","password");
  }
}
