# 流沙社区管理后端

JDK 21 + Spring Boot 3.5.16 + MyBatis Plus 3.5.17 + Spring Security + Redis。

## 测试与构建

```bash
./mvnw verify
```

Linux/macOS 包装脚本默认使用 `/opt/java/jdk21`，可用 `JAVA_HOME` 覆盖。第一次执行将下载 Maven 3.9.11 到用户缓存，校验 SHA-512；需要 curl、tar、sha512sum。测试使用 H2，不连接实际 MySQL/Redis/Nacos。所有测试资源只在测试 classpath 中。

## 本地启动

准备 MySQL 并将本项目的 `vueadmin.sql` 导入独立开发库。SQL 含 DROP TABLE 语句，请勿直接导入已有数据的库。管理项目还需要 Redis。

```bash
cp .env.example .env.local
# 编辑 .env.local，填入 DB_PASSWORD 和 JWT_SECRET 等配置
./mvnw spring-boot:run
```

Spring 自动读取当前项目目录的 `.env.local`（Java properties 格式，值无需加引号），环境变量优先。`JWT_SECRET` 必须至少 64 字节，可用 `openssl rand -hex 64` 生成；每个后端使用独立密钥。另需设置 ADMIN_PASSWORD，这是 Spring Security 默认用户密码，尚不是业务管理员登录。 默认端口 8081。

默认数据库地址为 localhost，移除了对历史远程服务器的自动连接。代码生成器显式执行时从环境变量读取数据库配置，输出到 target/generated-code。

## 迁移说明

JWT 库升级到 0.12.6，旧硬编码密钥已移除，旧 token 需要重新登录。JPA/Servlet/Validation 使用 Jakarta 命名空间。数据库结构未自动迁移；默认关闭自动 DDL。

现有用户/角色/菜单控制器仍是历史生成空壳，真实认证、权限和 CRUD 待第二阶段实现。默认 Spring Security 继续保护接口，没有为通过测试放开所有接口。

详见工作区根目录 `MODERNIZATION.md`。
