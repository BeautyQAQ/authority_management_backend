package com.liushao.vueadmin.community;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("communityPermissions")
public class CommunityPermissions {
  private static final Map<String, String> RESOURCES = Map.of(
      "billboards", "billboard", "tips", "tip", "promotions", "promotion", "tags", "tag");

  public boolean allowed(Authentication authentication, String resource, String action) {
    String name = RESOURCES.get(resource);
    if (name == null || authentication == null) return false;
    String permission = "community:" + name + ":" + action;
    return authentication.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
  }
}
