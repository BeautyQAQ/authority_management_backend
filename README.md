# 流沙社区管理后端

JDK 21 + Spring Boot 3.5.16 + Spring Security + JDBC/MyBatis Plus + Flyway + Redis。

## 测试与构建

```bash
./mvnw verify
```

包装脚本默认使用 `/opt/java/jdk21`，可用 `JAVA_HOME` 覆盖。首次下载并校验 Maven 3.9.11。13 项测试包含 JWT、应用上下文和 8 个完整接口场景，使用 H2 与内存认证状态替身，不连接 NAS。

## 首次启动

准备独立的空 MySQL 8 数据库和 Redis 6.2+，然后：

```bash
cp .env.example .env.local
# 填写数据库、Redis 连接，以及 JWT_SECRET
# 首次初始化：BOOTSTRAP_ADMIN=true，设置 ADMIN_USERNAME 和 ADMIN_PASSWORD
./mvnw spring-boot:run
```

Spring 从项目目录加载 `.env.local`，使用 Java properties 格式，值无需引号，环境变量优先。默认端口为 8081。

- `JWT_SECRET` 至少 64 字节，可用 `openssl rand -hex 64` 生成。
- `ADMIN_PASSWORD` 至少 8 个字符，最多 72 个 UTF-8 字节。启动时仅在用户表为空且 `BOOTSTRAP_ADMIN=true` 时创建 ID 1 管理员，密码以 BCrypt 保存。它是业务登录账号。
- 初始化成功后设置 `BOOTSTRAP_ADMIN=false`。修改环境中的密码不会重置已有账号；使用个人中心改密。
- Flyway 自动执行 `src/main/resources/db/migration` 中的版本迁移，建立表、内置管理员角色和 16 项菜单/权限。不要再导入旧 `vueadmin.sql`；它仅作历史参考。已有旧表的数据库需另行迁移，不应直接开启 baseline 或清空数据。
- 内置管理员、管理员角色及内置权限定义受保护，管理员自身通过个人中心改密。新用户需显式设置密码并分配角色。
- NAS 本地配置保存在被 Git 忽略、权限为 0600 的 `.env.local`。密码不写入文档或代码。

## API 与权限

响应统一为 `{code,msg,data}`，错误同时返回相应 HTTP 状态。前端开发代理去掉 `/api` 前缀；后端自身没有 `/api` 前缀。

| 接口 | 行为 |
| --- | --- |
| `GET /captcha` | PNG 验证码与随机 token，Redis 保存 2 分钟 |
| `POST /login` | 表单字段 username/password/token/code；验证码一次性消费，登录限流 |
| `POST /logout` | 撤销当前 Redis 会话 |
| `GET /sys/userInfo` | 当前用户信息，排除密码 |
| `GET /sys/menu/nav` | 按角色生成菜单树和 authoritys 权限列表 |
| `/sys/user/list,info/{id},save,update,delete` | 用户查询与增删改 |
| `POST /sys/user/role/{id}` | JSON 角色 ID 数组，事务替换角色 |
| `POST /sys/user/repass` | JSON `{id,password}`，管理员重置密码 |
| `POST /sys/user/updatePass` | JSON `{currentPass,password,checkPass}`，个人改密 |
| `/sys/role/list,options,info/{id},save,update,delete` | 角色管理；options 为分配角色提供选项 |
| `POST /sys/role/perm/{id}` | JSON 菜单 ID 数组，自动包含父级 |
| `/sys/menu/list,info/{id},save,update,delete/{id}` | 菜单树管理，拒绝循环父级与删除非空目录 |

查询使用 GET；变更使用 POST。用户/角色删除传 JSON ID 数组。分页参数 `current>=1`、`size=1..100`。菜单页面组件限于已经实现的 `sys/User`、`sys/Role`、`sys/Menu`。

除验证码和登录外，业务接口需要 `Authorization: Bearer <token>`。服务端每次请求检查会话、账号状态和数据库权限；仅隐藏前端按钮不能代替授权。权限分配不能超出操作者自身权限，菜单定义只允许管理员维护。禁用账号、修改账号/角色分配、重置/修改密码会使旧 token 失效；角色权限修改立即影响后续请求。

Redis 使用 `liushao:admin:` 前缀，包含验证码、登录限流计数与会话。默认 token 有效期 24 小时，可用 `JWT_EXPIRE_SECONDS` 调整。删除 Redis 会话会使对应登录失效。

## 真实开发环境冒烟验证

启动后运行：

```bash
python3 scripts/smoke_live.py http://127.0.0.1:8081
```

脚本只用于独立开发库：读取本地配置中的管理员凭据和 Redis 验证码，验证实际 HTTP/MySQL/Redis 行为，创建唯一命名的用户/角色/菜单并在 finally 中清理。改过管理员密码后，请通过环境变量 `ADMIN_PASSWORD` 提供当前密码。脚本不会输出密码或 token。

2026-10-13 已在 NAS `vueadmin` + Redis DB 12 验证登录、验证码 TTL/一次性消费、CRUD、授权/撤权、401/403、改密撤销会话与退出。社区库不参与这些测试。
