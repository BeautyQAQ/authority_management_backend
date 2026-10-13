package com.liushao.vueadmin.service;

import com.liushao.vueadmin.common.exception.ApiException;
import com.liushao.vueadmin.model.AdminRequests;
import com.liushao.vueadmin.security.AdminPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private static final String USER_FIELDS =
      "id,username,avatar,email,phone,city,created,updated,last_login,statu";
  private static final RowMapper<Map<String, Object>> ROW =
      (rs, index) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
          String[] words = rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT).split("_");
          StringBuilder key = new StringBuilder(words[0]);
          for (int j = 1; j < words.length; j++)
            key.append(Character.toUpperCase(words[j].charAt(0))).append(words[j].substring(1));
          row.put(key.toString(), rs.getObject(i));
        }
        return row;
      };

  public AdminService(JdbcTemplate db, PasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  public record Account(long id, String username, String password, int status, long version) {}

  public Account account(String username) {
    return db
        .query(
            "SELECT id,username,password,statu,auth_version FROM sys_user WHERE username=?",
            (rs, n) ->
                new Account(
                    rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getLong(5)),
            username)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Set<String> permissions(long userId) {
    Set<String> permissions = new LinkedHashSet<>();
    boolean admin = isAdmin(userId);
    if (admin) permissions.add("ROLE_ADMIN");
    for (var menu : availableMenus(userId)) {
      String values = Objects.toString(menu.get("perms"), "");
      Arrays.stream(values.split(","))
          .map(String::trim)
          .filter(p -> !p.isEmpty())
          .forEach(permissions::add);
    }
    return permissions;
  }

  private boolean isAdmin(long id) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE"
                + " ur.user_id=? AND r.code='admin' AND r.statu=1",
            Long.class,
            id)
        > 0;
  }

  private List<Map<String, Object>> availableMenus(long userId) {
    List<Map<String, Object>> all =
        db.query("SELECT * FROM sys_menu WHERE statu=1 ORDER BY order_num,id", ROW);
    Set<Long> granted =
        isAdmin(userId)
            ? new HashSet<>(all.stream().map(m -> id(m)).toList())
            : new HashSet<>(
                db.queryForList(
                    "SELECT DISTINCT rm.menu_id FROM sys_role_menu rm JOIN sys_role r ON"
                        + " r.id=rm.role_id JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
                        + " ur.user_id=? AND r.statu=1",
                    Long.class,
                    userId));
    Map<Long, Map<String, Object>> byId = new HashMap<>();
    all.forEach(m -> byId.put(id(m), m));
    return all.stream()
        .filter(
            m -> {
              if (!granted.contains(id(m))) return false;
              long parent = number(m.get("parentId"));
              Set<Long> seen = new HashSet<>();
              while (parent != 0) {
                if (!seen.add(parent) || !byId.containsKey(parent) || !granted.contains(parent))
                  return false;
                parent = number(byId.get(parent).get("parentId"));
              }
              return true;
            })
        .toList();
  }

  public Map<String, Object> navigation(long id) {
    var menus =
        availableMenus(id).stream()
            .filter(m -> number(m.get("type")) != 2)
            .map(
                m -> {
                  var copy = new LinkedHashMap<String, Object>(m);
                  copy.put("title", m.get("name"));
                  copy.put("name", "menu-" + m.get("id"));
                  return (Map<String, Object>) copy;
                })
            .toList();
    return Map.of("nav", tree(menus, 0), "authoritys", permissions(id));
  }

  private List<Map<String, Object>> tree(List<Map<String, Object>> rows, long parent) {
    return rows.stream()
        .filter(row -> number(row.get("parentId")) == parent)
        .map(
            row -> {
              var node = new LinkedHashMap<>(row);
              node.put("children", tree(rows, id(row)));
              return (Map<String, Object>) node;
            })
        .toList();
  }

  public List<Map<String, Object>> menuTree() {
    return tree(db.query("SELECT * FROM sys_menu ORDER BY order_num,id", ROW), 0);
  }

  public Map<String, Object> user(long id) {
    var user = one("SELECT " + USER_FIELDS + " FROM sys_user WHERE id=?", id);
    user.put(
        "roles",
        db.query(
            "SELECT r.* FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE"
                + " ur.user_id=?",
            ROW,
            id));
    return user;
  }

  public Map<String, Object> role(long id) {
    var role = one("SELECT * FROM sys_role WHERE id=?", id);
    role.put(
        "menuIds",
        db.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=?", Long.class, id));
    return role;
  }

  public Map<String, Object> menu(long id) {
    return one("SELECT * FROM sys_menu WHERE id=?", id);
  }

  public Map<String, Object> users(String name, int current, int size) {
    pageBounds(current, size);
    var rows =
        db.query(
            "SELECT "
                + USER_FIELDS
                + " FROM sys_user WHERE username LIKE ? ORDER BY id LIMIT ? OFFSET ?",
            ROW,
            "%" + name + "%",
            size,
            (long) (current - 1) * size);
    rows.forEach(
        row ->
            row.put(
                "roles",
                db.query(
                    "SELECT r.id,r.name,r.code FROM sys_role r JOIN sys_user_role ur ON"
                        + " ur.role_id=r.id WHERE ur.user_id=?",
                    ROW,
                    id(row))));
    return page(
        rows,
        db.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE username LIKE ?", Long.class, "%" + name + "%"),
        current,
        size);
  }

  public Map<String, Object> roles(String name, int current, int size) {
    pageBounds(current, size);
    return page(
        db.query(
            "SELECT * FROM sys_role WHERE name LIKE ? ORDER BY id LIMIT ? OFFSET ?",
            ROW,
            "%" + name + "%",
            size,
            (long) (current - 1) * size),
        db.queryForObject(
            "SELECT COUNT(*) FROM sys_role WHERE name LIKE ?", Long.class, "%" + name + "%"),
        current,
        size);
  }

  public List<Map<String, Object>> roleOptions() {
    return db.query("SELECT id,name,code,statu FROM sys_role WHERE statu=1 ORDER BY id", ROW);
  }

  @Transactional
  public long saveUser(AdminRequests.User user, boolean update) {
    if (update) {
      requireId(user.id());
      editableUser(user.id());
      db.update(
          "UPDATE sys_user SET"
              + " username=?,email=?,phone=?,statu=?,updated=CURRENT_TIMESTAMP,auth_version=auth_version+1"
              + " WHERE id=?",
          user.username(),
          user.email(),
          user.phone(),
          user.statu(),
          user.id());
      return user.id();
    }
    if (user.id() != null) throw new ApiException(400, "新增记录不能指定 ID");
    checkPassword(user.password());
    return insert(
        "INSERT INTO sys_user(username,password,email,phone,statu) VALUES(?,?,?,?,?)",
        user.username(),
        passwords.encode(user.password()),
        user.email(),
        user.phone(),
        user.statu());
  }

  @Transactional
  public void deleteUsers(List<Long> ids) {
    checkIds(ids);
    ids.forEach(this::editableUser);
    ids.forEach(id -> db.update("DELETE FROM sys_user WHERE id=?", id));
  }

  private void editableUser(long id) {
    user(id);
    if (id == 1) throw new ApiException(403, "内置管理员仅可在个人中心修改密码");
    if (isAdmin(id) && !callerAdmin()) throw new ApiException(403, "不能修改管理员账户");
  }

  @Transactional
  public void assignRoles(long userId, List<Long> ids) {
    editableUser(userId);
    checkIdsAllowEmpty(ids);
    for (long id : new LinkedHashSet<>(ids)) {
      var role = role(id);
      if (number(role.get("statu")) != 1) throw new ApiException(400, "不能分配已禁用角色");
      if ("admin".equals(role.get("code")) && !callerAdmin())
        throw new ApiException(403, "不能授予管理员角色");
      for (long menuId :
          db.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=?", Long.class, id))
        checkGrant(menuId);
    }
    db.update("DELETE FROM sys_user_role WHERE user_id=?", userId);
    new LinkedHashSet<>(ids)
        .forEach(
            id -> db.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", userId, id));
    db.update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=?", userId);
  }

  @Transactional
  public long saveRole(AdminRequests.Role role, boolean update) {
    if ("admin".equalsIgnoreCase(role.code())) throw new ApiException(403, "管理员角色为保留编码");
    if (update) {
      requireId(role.id());
      editableRole(role.id());
      db.update(
          "UPDATE sys_role SET name=?,code=?,remark=?,statu=?,updated=CURRENT_TIMESTAMP WHERE id=?",
          role.name(),
          role.code(),
          role.remark(),
          role.statu(),
          role.id());
      return role.id();
    }
    if (role.id() != null) throw new ApiException(400, "新增记录不能指定 ID");
    return insert(
        "INSERT INTO sys_role(name,code,remark,statu) VALUES(?,?,?,?)",
        role.name(),
        role.code(),
        role.remark(),
        role.statu());
  }

  private void editableRole(long id) {
    if ("admin".equals(role(id).get("code"))) throw new ApiException(403, "内置管理员角色不可修改");
    if (!callerAdmin())
      for (long menuId :
          db.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=?", Long.class, id))
        checkGrant(menuId);
  }

  @Transactional
  public void deleteRoles(List<Long> ids) {
    checkIds(ids);
    ids.forEach(this::editableRole);
    ids.forEach(id -> db.update("DELETE FROM sys_role WHERE id=?", id));
  }

  @Transactional
  public void assignMenus(long roleId, List<Long> ids) {
    editableRole(roleId);
    checkIdsAllowEmpty(ids);
    Set<Long> expanded = new LinkedHashSet<>();
    for (long id : ids) {
      long current = id;
      while (current != 0) {
        var menu = menu(current);
        checkGrant(current);
        if (!expanded.add(current)) break;
        current = number(menu.get("parentId"));
      }
    }
    db.update("DELETE FROM sys_role_menu WHERE role_id=?", roleId);
    expanded.forEach(
        id -> db.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(?,?)", roleId, id));
  }

  private void checkGrant(long menuId) {
    var menu = menu(menuId);
    if (!callerAdmin()) {
      Set<String> owned = permissions(principal().id());
      for (String permission : Objects.toString(menu.get("perms"), "").split(","))
        if (!owned.contains(permission.trim())) throw new ApiException(403, "不能授予自身未持有的权限");
    }
  }

  @Transactional
  public long saveMenu(AdminRequests.Menu menu, boolean update) {
    if (!callerAdmin()) throw new ApiException(403, "菜单定义仅允许管理员维护");
    if (update) {
      requireId(menu.id());
      editableMenu(menu.id());
    } else if (menu.id() != null) throw new ApiException(400, "新增记录不能指定 ID");
    long parent = menu.parentId();
    Set<Long> ancestors = new HashSet<>();
    while (parent != 0) {
      if (Objects.equals(menu.id(), parent) || !ancestors.add(parent))
        throw new ApiException(400, "菜单父级不能构成循环");
      var ancestor = menu(parent);
      if (number(ancestor.get("type")) == 2) throw new ApiException(400, "按钮不能作为父级菜单");
      parent = number(ancestor.get("parentId"));
    }
    if (menu.type() == 1
        && (menu.component() == null
            || !Set.of("sys/User", "sys/Role", "sys/Menu").contains(menu.component())
            || menu.path() == null
            || !menu.path().startsWith("/sys/")))
      throw new ApiException(400, "请选择已实现的管理页面和 /sys/ 路径");
    if (menu.type() == 2 && menu.parentId() == 0) throw new ApiException(400, "按钮必须属于菜单");
    if (update) {
      if (menu.type() == 2
          && db.queryForObject(
                  "SELECT COUNT(*) FROM sys_menu WHERE parent_id=?", Long.class, menu.id())
              > 0) throw new ApiException(400, "有子项的菜单不能改为按钮");
      db.update(
          "UPDATE sys_menu SET"
              + " parent_id=?,name=?,path=?,perms=?,component=?,type=?,icon=?,order_num=?,statu=?,updated=CURRENT_TIMESTAMP"
              + " WHERE id=?",
          menu.parentId(),
          menu.name(),
          menu.path(),
          menu.perms(),
          menu.component(),
          menu.type(),
          menu.icon(),
          menu.orderNum(),
          menu.statu(),
          menu.id());
      return menu.id();
    }
    return insert(
        "INSERT INTO sys_menu(parent_id,name,path,perms,component,type,icon,order_num,statu)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        menu.parentId(),
        menu.name(),
        menu.path(),
        menu.perms(),
        menu.component(),
        menu.type(),
        menu.icon(),
        menu.orderNum(),
        menu.statu());
  }

  private void editableMenu(long id) {
    menu(id);
    if (id <= 16) throw new ApiException(403, "内置权限菜单不可修改");
  }

  @Transactional
  public void deleteMenu(long id) {
    if (!callerAdmin()) throw new ApiException(403, "菜单定义仅允许管理员维护");
    editableMenu(id);
    if (db.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE parent_id=?", Long.class, id) > 0)
      throw new ApiException(409, "请先删除子菜单");
    db.update("DELETE FROM sys_menu WHERE id=?", id);
  }

  @Transactional
  public void changePassword(AdminRequests.Password input) {
    var user = account(principal().username());
    if (!passwords.matches(input.currentPass(), user.password()))
      throw new ApiException(400, "当前密码不正确");
    if (!input.password().equals(input.checkPass())) throw new ApiException(400, "两次输入密码不一致");
    updatePassword(user.id(), input.password());
  }

  @Transactional
  public void resetPassword(AdminRequests.ResetPassword input) {
    editableUser(input.id());
    updatePassword(input.id(), input.password());
  }

  private void updatePassword(long id, String password) {
    checkPassword(password);
    db.update(
        "UPDATE sys_user SET password=?,auth_version=auth_version+1,updated=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        passwords.encode(password),
        id);
  }

  public void loggedIn(long id) {
    db.update("UPDATE sys_user SET last_login=CURRENT_TIMESTAMP WHERE id=?", id);
  }

  public static AdminPrincipal principal() {
    return (AdminPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  private boolean callerAdmin() {
    return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }

  private Map<String, Object> one(String sql, Object... args) {
    return db.query(sql, ROW, args).stream()
        .findFirst()
        .orElseThrow(() -> new ApiException(404, "记录不存在"));
  }

  private static long id(Map<String, Object> row) {
    return number(row.get("id"));
  }

  private static long number(Object value) {
    return ((Number) value).longValue();
  }

  private static void requireId(Long id) {
    if (id == null || id < 1) throw new ApiException(400, "记录 ID 不正确");
  }

  private static void checkIdsAllowEmpty(List<Long> ids) {
    if (ids == null || ids.size() > 100) throw new ApiException(400, "一次最多操作 100 项");
    ids.forEach(AdminService::requireId);
  }

  private static void checkIds(List<Long> ids) {
    checkIdsAllowEmpty(ids);
    if (ids.isEmpty()) throw new ApiException(400, "请选择记录");
  }

  private static void pageBounds(int current, int size) {
    if (current < 1 || size < 1 || size > 100) throw new ApiException(400, "分页参数超出范围");
  }

  private static Map<String, Object> page(
      List<Map<String, Object>> rows, long total, int current, int size) {
    return Map.of("records", rows, "total", total, "current", current, "size", size);
  }

  public static void checkPassword(String value) {
    if (value == null || value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "密码至少 8 个字符，最多 72 字节");
  }

  private long insert(String sql, Object... args) {
    var key = new GeneratedKeyHolder();
    db.update(
        connection -> {
          var statement = connection.prepareStatement(sql, new String[] {"id"});
          for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
          return statement;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }
}
