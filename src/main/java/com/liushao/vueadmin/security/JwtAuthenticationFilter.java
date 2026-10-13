package com.liushao.vueadmin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.service.AdminService;
import com.liushao.vueadmin.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtUtil jwt;
  private final AuthStateStore sessions;
  private final AdminService users;
  private final ObjectMapper json;

  public JwtAuthenticationFilter(
      JwtUtil jwt, AuthStateStore sessions, AdminService users, ObjectMapper json) {
    this.jwt = jwt;
    this.sessions = sessions;
    this.users = users;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getServletPath().equals("/login") || request.getServletPath().equals("/captcha");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && !header.isBlank()) {
      try {
        var claims =
            jwt.getClaimByToken(header.startsWith("Bearer ") ? header.substring(7) : header);
        var user = users.account(claims.getSubject());
        Number version = claims.get("version", Number.class);
        if (user == null
            || user.status() != 1
            || version == null
            || version.longValue() != user.version()
            || !sessions.active(claims.getId(), user.username()))
          throw new IllegalArgumentException("Invalid session");
        var auth =
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(user.id(), user.username(), user.version()),
                null,
                users.permissions(user.id()).stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.setAttribute("sessionId", claims.getId());
      } catch (JwtException | IllegalArgumentException e) {
        write(response, 401, "登录已失效，请重新登录");
        return;
      } catch (Exception e) {
        write(response, 503, "认证服务暂时不可用");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private void write(HttpServletResponse response, int code, String message) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json;charset=UTF-8");
    json.writeValue(response.getWriter(), Result.fail(code, message, null));
  }
}
