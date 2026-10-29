package com.liushao.vueadmin.community;

import com.fasterxml.jackson.databind.JsonNode;
import com.liushao.vueadmin.common.exception.ApiException;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.Set;

public final class CommunityRequests {
  private CommunityRequests() {}
  public record UserStatus(@NotNull Boolean status, @NotBlank @Size(max = 255) String reason) {}
  public record Moderation(@NotBlank @Pattern(regexp = "PUBLISHED|PENDING|HIDDEN") String status,
      @NotNull Boolean top, @NotNull Boolean essence, @NotBlank @Size(max = 255) String reason,
      @NotNull @Min(0) Long version) {}
  public record Visibility(@NotNull Boolean visible, @NotBlank @Size(max = 255) String reason) {}

  public static JsonNode catalog(String resource, JsonNode body) {
    if (body == null || !body.isObject()) throw invalid();
    Set<String> fields = switch (resource) {
      case "billboards" -> Set.of("content", "visible");
      case "tips" -> Set.of("content", "author", "enabled");
      case "promotions" -> Set.of("title", "link", "description");
      case "tags" -> Set.of("name");
      default -> throw invalid();
    };
    body.fieldNames().forEachRemaining(field -> { if (!fields.contains(field)) throw invalid(); });
    switch (resource) {
      case "billboards" -> { text(body, "content", 255, true); bool(body, "visible"); }
      case "tips" -> { text(body, "content", 255, true); text(body, "author", 255, false); bool(body, "enabled"); }
      case "promotions" -> {
        text(body, "title", 255, true); text(body, "link", 255, true); text(body, "description", 255, false);
        try {
          URI uri = URI.create(body.path("link").asText());
          if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw invalid();
        } catch (IllegalArgumentException e) { throw invalid(); }
      }
      case "tags" -> text(body, "name", 32, true);
      default -> throw invalid();
    }
    return body;
  }
  private static void text(JsonNode body, String key, int max, boolean required) {
    JsonNode value = body.get(key);
    if (value == null || value.isNull()) { if (required) throw invalid(); return; }
    if (!value.isTextual() || value.asText().length() > max || (required && value.asText().isBlank())) throw invalid();
  }
  private static void bool(JsonNode body, String key) {
    if (!body.path(key).isBoolean()) throw invalid();
  }
  private static ApiException invalid() { return new ApiException(400, "内容字段不正确，请检查必填项、长度和链接格式"); }
}
