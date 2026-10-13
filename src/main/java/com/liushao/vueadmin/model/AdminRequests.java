package com.liushao.vueadmin.model;

import jakarta.validation.constraints.*;

public final class AdminRequests {
  public record User(
      Long id,
      @NotBlank @Size(max = 64) String username,
      @Size(max = 72) String password,
      @Email @Size(max = 128) String email,
      @Size(max = 32) String phone,
      @NotNull @Min(0) @Max(1) Integer statu) {}

  public record Role(
      Long id,
      @NotBlank @Size(max = 64) String name,
      @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]{0,63}") String code,
      @Size(max = 255) String remark,
      @NotNull @Min(0) @Max(1) Integer statu) {}

  public record Menu(
      Long id,
      @NotNull @Min(0) Long parentId,
      @NotBlank @Size(max = 64) String name,
      @Size(max = 255) String path,
      @NotBlank @Size(max = 255) String perms,
      @Size(max = 255) String component,
      @NotNull @Min(0) @Max(2) Integer type,
      @Size(max = 64) String icon,
      @NotNull @Min(0) Integer orderNum,
      @NotNull @Min(0) @Max(1) Integer statu) {}

  public record Password(
      @NotBlank String currentPass,
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank String checkPass) {}

  public record ResetPassword(
      @NotNull Long id, @NotBlank @Size(min = 8, max = 72) String password) {}
}
