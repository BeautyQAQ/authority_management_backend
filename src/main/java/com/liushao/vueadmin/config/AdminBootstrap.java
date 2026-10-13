package com.liushao.vueadmin.config;

import com.liushao.vueadmin.service.AdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final String username, password;

  public AdminBootstrap(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${app.bootstrap.username:admin}") String username,
      @Value("${app.bootstrap.password:}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class) > 0) return;
    AdminService.checkPassword(password);
    db.update(
        "INSERT INTO sys_user(id,username,password,statu) VALUES(1,?,?,1)",
        username,
        encoder.encode(password));
    db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(1,1)");
  }
}
