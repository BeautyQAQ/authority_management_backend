package com.liushao.vueadmin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liushao.vueadmin.common.lang.Result;
import com.liushao.vueadmin.security.*;
import com.liushao.vueadmin.service.AdminService;
import com.liushao.vueadmin.utils.JwtUtil;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService unusedDefaultUserService() {
    return name -> {
      throw new UsernameNotFoundException("Use JWT authentication");
    };
  }

  @Bean
  SecurityFilterChain apiSecurity(
      HttpSecurity http, JwtUtil jwt, AuthStateStore state, AdminService users, ObjectMapper json)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/captcha", "/login", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, e) -> {
                          response.setStatus(401);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(response.getWriter(), Result.fail(401, "请先登录", null));
                        })
                    .accessDeniedHandler(
                        (request, response, e) -> {
                          response.setStatus(403);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(response.getWriter(), Result.fail(403, "没有操作权限", null));
                        }))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwt, state, users, json),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
