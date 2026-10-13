package com.liushao.vueadmin.security;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisAuthStateStore implements AuthStateStore {
  public static final String PREFIX = "liushao:admin:";
  private final StringRedisTemplate redis;

  public RedisAuthStateStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void saveCaptcha(String id, String code) {
    redis.opsForValue().set(PREFIX + "captcha:" + id, code, Duration.ofMinutes(2));
  }

  public String consumeCaptcha(String id) {
    return redis.opsForValue().getAndDelete(PREFIX + "captcha:" + id);
  }

  public void saveSession(String id, String username, Duration ttl) {
    redis.opsForValue().set(PREFIX + "session:" + id, username, ttl);
  }

  public boolean active(String id, String username) {
    return id != null && username.equals(redis.opsForValue().get(PREFIX + "session:" + id));
  }

  public void revoke(String id) {
    redis.delete(PREFIX + "session:" + id);
  }

  public boolean allowLogin(String key) {
    var script =
        new DefaultRedisScript<Long>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end;"
                + " return n",
            Long.class);
    Long count = redis.execute(script, List.of(PREFIX + "login-rate:" + key));
    return count != null && count <= 20;
  }
}
