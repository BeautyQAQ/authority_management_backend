package com.liushao.vueadmin.community;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.vueadmin.common.exception.ApiException;
import com.liushao.vueadmin.common.lang.Result;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** A fixed community operations client. Never forwards a browser URL or browser credentials. */
@Service
public class CommunityAdminBridge {
  private final URI base;
  private final String key;
  private final ObjectMapper json;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
      .followRedirects(HttpClient.Redirect.NEVER).build();

  public CommunityAdminBridge(@Value("${app.community.api-url:http://localhost:8080}") String base,
      @Value("${app.community.admin-key:}") String key, ObjectMapper json) {
    URI parsed;
    try { parsed = URI.create(base); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("COMMUNITY_API_URL 必须是 HTTP(S) 服务地址"); }
    if (!("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme())) || parsed.getHost() == null
        || parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null
        || !(parsed.getPath().isEmpty() || parsed.getPath().equals("/"))) {
      throw new IllegalArgumentException("COMMUNITY_API_URL 必须是无路径、用户信息和查询参数的 HTTP(S) 服务地址");
    }
    this.base = parsed; this.key = key; this.json = json;
  }

  public ResponseEntity<Result> forward(HttpMethod method, String path, Map<String, String> query, Object body, String actor) {
    if (key.isBlank()) throw new ApiException(503, "社区运营连接尚未启用，请配置服务端连接密钥");
    if (!allowed(method, path)) throw new ApiException(400, "不支持的社区运营操作");
    if (actor == null || actor.isBlank() || actor.length() > 64) throw new ApiException(400, "后台操作者信息不正确");
    String suffix = query.isEmpty() ? "" : "?" + query.entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining("&"));
    URI uri = base.resolve("/internal/admin/" + path + suffix);
    try {
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
          .header("X-Community-Admin-Key", key)
          .header("X-Community-Admin-Actor", encode(actor))
          .header("Accept", "application/json").header("Content-Type", "application/json")
          .method(method.name(), body == null ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      byte[] bytes = response.body();
      if (bytes.length > 1_048_576) throw badResponse();
      int status = response.statusCode();
      if (status == 401 || status == 403) throw new ApiException(502, "社区运营连接认证失败，请检查服务端密钥");
      if (status < 200 || status >= 600 || (status >= 300 && status < 400)) throw badResponse();
      JsonNode envelope = json.readTree(bytes);
      if (envelope == null || !envelope.isObject() || !envelope.path("code").isIntegralNumber()
          || !envelope.path("message").isTextual()) throw badResponse();
      if (status >= 500) return ResponseEntity.status(status).body(Result.fail(status, "社区服务暂时不可用，请稍后重试", null));
      if (status >= 400) {
        String message = envelope.path("message").asText();
        if (message.isBlank() || message.length() > 300 || message.contains(key)) message = "社区操作失败，请检查输入或刷新后重试";
        return ResponseEntity.status(status).body(Result.fail(status, message, null));
      }
      if (envelope.path("code").asInt() != 200 || !envelope.has("data")) throw badResponse();
      return ResponseEntity.ok(Result.succ(envelope.get("data")));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); throw new ApiException(503, "社区服务请求已中断，请重试");
    } catch (JsonProcessingException e) {
      throw badResponse();
    } catch (IOException e) {
      throw new ApiException(503, "暂时无法连接社区服务，请稍后重试");
    } catch (IllegalArgumentException e) {
      throw new ApiException(503, "社区运营连接配置不正确");
    }
  }

  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  private static ApiException badResponse() { return new ApiException(502, "社区服务返回了无效响应"); }
  private static boolean allowed(HttpMethod method, String path) {
    if (method == HttpMethod.GET) return path.matches("(users|posts|comments|billboards|tips|promotions|tags)(/[1-9][0-9]*)?") || path.equals("audit");
    if (method == HttpMethod.POST) return path.matches("billboards|tips|promotions|tags");
    if (method == HttpMethod.PUT) return path.matches("users/[1-9][0-9]*/status|posts/[1-9][0-9]*/moderation|comments/[1-9][0-9]*/visibility|(billboards|tips|promotions|tags)/[1-9][0-9]*");
    return method == HttpMethod.DELETE && path.matches("(posts|comments|billboards|tips|promotions|tags)/[1-9][0-9]*");
  }
}
