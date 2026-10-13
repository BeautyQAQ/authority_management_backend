package com.liushao.vueadmin.controller;

import com.google.code.kaptcha.Producer;
import com.liushao.vueadmin.common.exception.ApiException;
import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.security.AuthStateStore;
import com.liushao.vueadmin.service.AdminService;
import com.liushao.vueadmin.utils.JwtUtil;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final Producer captcha;
  private final AuthStateStore state;
  private final AdminService users;
  private final JwtUtil jwt;
  private final PasswordEncoder passwords;
  private final String dummyHash;

  public AuthController(
      Producer captcha,
      AuthStateStore state,
      AdminService users,
      JwtUtil jwt,
      PasswordEncoder passwords) {
    this.captcha = captcha;
    this.state = state;
    this.users = users;
    this.jwt = jwt;
    this.passwords = passwords;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @GetMapping("/captcha")
  public Result captcha(HttpServletResponse response) throws IOException {
    response.setHeader("Cache-Control", "no-store");
    String id = UUID.randomUUID().toString(), code = captcha.createText();
    state.saveCaptcha(id, code);
    var output = new ByteArrayOutputStream();
    ImageIO.write(captcha.createImage(code), "png", output);
    return Result.succ(
        Map.of(
            "token",
            id,
            "captchaImg",
            "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray())));
  }

  @PostMapping("/login")
  public Result login(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam String token,
      @RequestParam String code,
      HttpServletRequest request,
      HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    if (username.isBlank()
        || username.length() > 64
        || password.length() > 72
        || token.length() > 64
        || code.length() > 10) throw new ApiException(400, "登录参数不正确");
    if (!state.allowLogin("ip:" + request.getRemoteAddr()) || !state.allowLogin("user:" + username))
      throw new ApiException(429, "尝试过于频繁，请一分钟后重试");
    String expected = state.consumeCaptcha(token);
    if (expected == null || !expected.equalsIgnoreCase(code))
      throw new ApiException(400, "验证码错误或已过期");
    var user = users.account(username);
    boolean matches = passwords.matches(password, user == null ? dummyHash : user.password());
    if (user == null || !matches || user.status() != 1) throw new ApiException(401, "账号或密码不正确");
    String signed = jwt.generateToken(user.username(), user.version());
    var claims = jwt.getClaimByToken(signed);
    state.saveSession(
        claims.getId(),
        user.username(),
        Duration.between(java.time.Instant.now(), claims.getExpiration().toInstant()));
    users.loggedIn(user.id());
    response.setHeader("Authorization", "Bearer " + signed);
    return Result.succ(Map.of("token", "Bearer " + signed));
  }

  @PostMapping("/logout")
  public Result logout(HttpServletRequest request) {
    state.revoke((String) request.getAttribute("sessionId"));
    return Result.succ(null);
  }

  @GetMapping("/sys/userInfo")
  public Result userInfo() {
    return Result.succ(users.user(AdminService.principal().id()));
  }
}
