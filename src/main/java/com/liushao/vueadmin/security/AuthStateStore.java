package com.liushao.vueadmin.security;

import java.time.Duration;

public interface AuthStateStore {
  void saveCaptcha(String id, String code);

  String consumeCaptcha(String id);

  void saveSession(String id, String username, Duration ttl);

  boolean active(String id, String username);

  void revoke(String id);

  boolean allowLogin(String key);
}
