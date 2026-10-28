# 流沙社区管理后端

JDK 21 + Spring Boot 3.5.16 + Spring Security + JDBC/MyBatis Plus + Flyway + Redis。

## 测试与构建

```bash
./mvnw verify
```

包装脚本默认使用 `/opt/java/jdk21`，可用 `JAVA_HOME` 覆盖。首次下载并校验 Maven 3.9.11。37 项测试覆盖 JWT、应用上下文、系统管理与社区权限、服务端代理、健康探针和生产配置校验，使用 H2、内存认证状态/健康替身及临时本机 HTTP 服务，不连接 NAS。

## 首次启动

准备与社区后端共用的 MySQL 8 数据库 `liushao_community` 和 Redis 6.2+，然后：

```bash
cp .env.example .env.local
# 填写数据库、Redis 连接，以及 JWT_SECRET
# 首次初始化：BOOTSTRAP_ADMIN=true，设置 ADMIN_USERNAME 和 ADMIN_PASSWORD
./mvnw spring-boot:run
```

未指定 profile 时默认启用 `local`，从项目目录加载 `.env.local`，使用 Java properties 格式，值无需引号，环境变量优先。默认端口为 8081。

- `JWT_SECRET` 至少 64 字节，可用 `openssl rand -hex 64` 生成。
- `ADMIN_PASSWORD` 至少 8 个字符，最多 72 个 UTF-8 字节。启动时仅在用户表为空且 `BOOTSTRAP_ADMIN=true` 时创建 ID 1 管理员，密码以 BCrypt 保存。它是业务登录账号。
- 初始化成功后设置 `BOOTSTRAP_ADMIN=false`。修改环境中的密码不会重置已有账号；使用个人中心改密。
- Flyway 自动执行 `src/main/resources/db/migration` 中的版本迁移，建立表、内置管理员角色、系统管理和社区运营菜单/权限。管理端历史表为 `flyway_admin_schema_history`，社区端保留 `flyway_schema_history`。两端共用数据库、分别校验和升级自己的迁移。新环境允许从版本 0 建立历史，以便社区先建表时仍执行管理端 V1。不要再导入旧 `vueadmin.sql`；已有数据库需迁移真实历史，不能跳过 V1 或清空数据。旧库合并步骤见 [数据库合并记录](docs/database-consolidation.md)。
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

上述系统管理接口查询使用 GET；变更使用 POST。用户/角色删除传 JSON ID 数组。分页参数 `current>=1`、`size=1..100`。菜单组件仅允许已实现的 `sys/*` 三个页面和 `community/*` 八个运营页面。

除验证码和登录外，业务接口需要 `Authorization: Bearer <token>`。服务端每次请求检查会话、账号状态和数据库权限；仅隐藏前端按钮不能代替授权。权限分配不能超出操作者自身权限，菜单定义只允许管理员维护。禁用账号、修改账号/角色分配、重置/修改密码会使旧 token 失效；角色权限修改立即影响后续请求。

Redis 使用 `liushao:admin:` 前缀，包含验证码、登录限流计数与会话。默认 token 有效期 24 小时，可用 `JWT_EXPIRE_SECONDS` 调整。删除 Redis 会话会使对应登录失效。

## 真实开发环境冒烟验证

启动后运行：

```bash
python3 scripts/smoke_live.py http://127.0.0.1:8081
```

脚本只用于独立开发库：读取本地配置中的管理员凭据和 Redis 验证码，验证实际 HTTP/MySQL/Redis 行为，创建唯一命名的用户/角色/菜单并在 finally 中清理。改过管理员密码后，请通过环境变量 `ADMIN_PASSWORD` 提供当前密码。脚本不会输出密码或 token。

2026-10-13 已在 NAS `vueadmin` + Redis DB 12 验证登录、验证码 TTL/一次性消费、CRUD、授权/撤权、401/403、改密撤销会话与退出。2026-10-28 管理表及迁移历史整体移入 `liushao_community`，Redis 继续使用 DB 12。


## 容器与生产配置

```bash
docker build -t liushao/admin-backend:local .
# 在 .env.prod 中填写实际 MySQL/Redis 地址、独立应用数据库账号及强密钥
docker run --rm --name liushao-admin --env-file .env.prod \
  -p 127.0.0.1:8081:8081 liushao/admin-backend:local
```

镜像以 Maven 3.9.11/JDK 21 编译，使用 Temurin JRE 21 运行，UID/GID 为 `10001:10001`。仅复制指定的可执行 JAR 到 `/app/app.jar`；构建上下文仅包含 `pom.xml` 和 `src`，本地环境配置、Git 历史及 `target` 不进入镜像。日志写 stdout/stderr，由容器运行时负责采集和轮转。JVM 参数使用 `JAVA_TOOL_OPTIONS`。

镜像默认启用 `prod`；直接运行 JAR 时设置 `SPRING_PROFILES_ACTIVE=prod`。生产启动前校验 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`JWT_SECRET` 必须显式配置且非空，错误仅列出缺失变量名。JWT 密钥仍要求至少 64 字节。生产 profile 不读取 `.env.local`；`.env.prod` 由 Docker 的 `--env-file` 加载，支持的格式是 `KEY=value`，不要放入仓库。`BOOTSTRAP_ADMIN` 默认关闭；仅空库首次初始化时显式开启并设置 `ADMIN_USERNAME`、`ADMIN_PASSWORD`，初始化后关闭。

`REDIS_PORT` 默认 `6379`，`REDIS_DATABASE` 默认 `0`，按部署分配独立数据库编号；`SERVER_PORT` 默认 `8081`，修改时同步调整宿主端口映射。停止服务时支持 30 秒优雅退出，容器停止宽限期应至少 35 秒。生产启用 Tomcat 原生转发头处理；反向代理需覆盖客户端转发头，后端仅在可信内网监听，通过代理恢复真实 IP 供登录限流使用。

默认 JDBC URL 使用 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`，连接池也启用 `forceConnectionTimeZoneToSession`，确保驱动与 MySQL 当前连接的时区一致。已有 `serverTimezone=Asia/Shanghai` URL 也会同步会话时区，避免数据库自动时间与 Java 写入时间产生 8 小时差异；不要在 URL 中显式关闭该选项。只调整当前连接，不修改 MySQL 全局时区。参见 [Connector/J 时间配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。

## 健康检查与 CI

[Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html) 只开放健康检查，响应仅含状态及探针分组名称，不暴露配置、连接信息或组件细节：

- `GET /actuator/health/liveness`：进程存活，不依赖数据库或 Redis。
- `GET /actuator/health/readiness`：应用就绪且 MySQL、Redis 可用；依赖故障返回 HTTP 503。
- `GET /actuator/health`：总体健康状态。以上端点无需登录，与业务 API 使用同一端口。

Docker 每 30 秒检查 readiness，启动宽限期 90 秒。普通 `./mvnw verify` 使用 `test` profile，不加载开发 `.env.local`，健康检查测试用 Redis 健康替身验证就绪失败及细节隐藏，不连接 NAS。

独立仓库的 `.github/workflows/ci.yml` 在 push、PR 或手动触发时运行 Java 21 `./mvnw verify` 和镜像构建，不需要数据库服务或仓库密钥，不推送镜像、不部署。

## 社区运营后台

管理账号 `sys_user` 与社区注册账号 `ums_user` 是两个独立账号体系，表均位于 `liushao_community`。新增的「社区运营」目录管理社区账号、帖子审核/下架/置顶/精华、评论可见性与删除、公告、提示语、推广链接、标签和操作审计。社区业务操作继续通过社区后端 API 执行授权、事务和审计逻辑。

服务端配置 `COMMUNITY_API_URL`（默认 `http://localhost:8080`）及与社区后端一致的 `COMMUNITY_ADMIN_KEY`；空密钥关闭运营连接并返回 503。密钥仅由管理后端发送，不能配置为前端 Vite 变量。代理仅允许预定义的 `/community/users|posts|comments|billboards|tips|promotions|tags|audit` 路由和 HTTP 方法，调用社区 `/internal/admin/**`。操作者来自当前后台登录身份，用户名使用 UTF-8 URL 编码传入 `X-Community-Admin-Actor`，社区后端解码后记录审计。

Flyway V2 新增独立菜单和逐操作的 `community:*` 权限，并标记内置菜单为不可修改。管理员自动具备新权限；为运营人员分配菜单/操作权限时正常执行现有权限范围检查，内置保护不影响角色授权。只读、审核、删除及各内容资源的新增/编辑/删除权限分别控制。用户状态、审核与删除需要填写原因；操作记录只能查询。V1 保持不变。

列表查询使用 `pageNo`（从 1 起）、`size`（1–50）与 `query`，帖子支持 `status=PUBLISHED|PENDING|HIDDEN`，评论支持 `postId`。社区分页数据保留 `{content,totalElements,totalPages,number,size}`，代理将外层 `message` 转为管理端 `msg`。400/404/409 等业务状态原样保留，社区内部认证失败映射 502，避免让后台用户被错误退出；连接失败返回 503，远端异常详情不暴露给浏览器。

新增测试覆盖固定路由、查询编码、中文操作者、密钥隔离、错误状态/重定向、逐操作 RBAC、输入约束，以及内置社区权限可授予但不可编辑。测试仅使用 H2、模拟认证和临时本机 HTTP 服务。

## 工作区资料

数据库合并 SQL 位于 `scripts/sql/`；共享部署、维护与恢复入口见 [社区后端部署目录](../liushao_community_backend/deploy/README.md)，当前 NAS 连接约定见 [基础设施说明](../liushao_community_backend/docs/INFRASTRUCTURE.md)。
