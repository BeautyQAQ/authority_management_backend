# 管理库合并到社区库

2026-10-28，在 NAS `nas-mysql`（MySQL 8.1.0）上，通过 dbx 将 `vueadmin` 的全部 6 张表迁移到 `liushao_community`。迁移采用同一条 `RENAME TABLE`，保留所有记录、索引、检查约束、外键及自增值。旧数据库保留为空库。

| 原表 | 迁移后 |
| --- | --- |
| `vueadmin.sys_user` | `liushao_community.sys_user` |
| `vueadmin.sys_role` | `liushao_community.sys_role` |
| `vueadmin.sys_menu` | `liushao_community.sys_menu` |
| `vueadmin.sys_user_role` | `liushao_community.sys_user_role` |
| `vueadmin.sys_role_menu` | `liushao_community.sys_role_menu` |
| `vueadmin.flyway_schema_history` | `liushao_community.flyway_admin_schema_history` |

社区原有 `flyway_schema_history` 继续记录社区迁移，管理端通过 `spring.flyway.table` 使用独立历史。合并前两端 NAS 历史均为 V1；本次移表保留现有版本，不额外执行 NAS 上待应用的 V2–V4。应用下次启动仍按各自历史正常升级。

同日后续工作已启动两端应用，当前管理端升级到 V2、社区端升级到 V5，并完成旧社区数据转换。下面的 14 张表与 V1 核验结果记录移表当时的状态；当前共享库为 25 张表，详细数据和验证见 [历史社区迁移记录](../../liushao_community_backend/docs/legacy-community-migration.md)。

管理端的默认配置、`.env.example`、私密 `.env.local` 及 Compose 连接串统一使用 `liushao_community`。社区连接串已指向目标库。新共享库支持两端任一启动顺序：缺少本端历史时从版本 0 建立历史，完整执行 V1，避免跳过初始化。两个登录账号体系、Redis DB 12/13 和社区运营 API 继续分别使用。

## 备份与执行

本次迁移前完整 SQL 备份位于本项目私密目录：

`.local/backups/database-consolidation/20261028T040928Z/before.sql.gz`

同目录的 `SHA256SUMS` 可核验备份，`metadata.json` 记录备份来源和时间。目录权限为 `0700`，备份为 `0600`；备份不进入 Git。

在其他已有双库环境重做合并时：

1. 停止应用写入，完整备份两个数据库。
2. 核对目标没有上述 5 张业务表和 `flyway_admin_schema_history`，检查跨库外键、触发器、例程和事件。该 SQL 适用于本次核验的同实例 InnoDB 表。
3. 使用 dbx 执行 [原子迁移 SQL](../scripts/sql/migrate-vueadmin-to-community.sql)。任一目标表已存在时，整条语句失败。
4. 使用独立应用用户的部署需要数据库管理员执行：`GRANT ALL PRIVILEGES ON liushao_community.* TO 'admin_app'@'%';`。NAS 本地配置使用既有账号，其目标库访问已具备权限。新环境初始化脚本会为两个应用用户授予共享库权限。
5. 更新管理端连接串和历史表配置，核对逐表行数、`CHECKSUM TABLE`、自增值及外键引用，再启动与该配置匹配的应用。

## 回退

回退需要停止写入，并确保旧库同名表不存在。下面语句仅撤销表的位置和历史表名称；它保留执行时的最新数据，不撤销合并后应用可能执行的新版本迁移。

```sql
RENAME TABLE
  liushao_community.sys_user TO vueadmin.sys_user,
  liushao_community.sys_role TO vueadmin.sys_role,
  liushao_community.sys_menu TO vueadmin.sys_menu,
  liushao_community.sys_user_role TO vueadmin.sys_user_role,
  liushao_community.sys_role_menu TO vueadmin.sys_role_menu,
  liushao_community.flyway_admin_schema_history TO vueadmin.flyway_schema_history;
```

随后恢复管理端旧连接串及默认历史表配置。若需要恢复迁移前数据，应先在独立环境导入备份并核验，处理备份之后的新写入。

联合备份脚本已改为导出一个共享库及上传卷，格式版本为 2。旧版本双库包仍可校验，恢复步骤见 [部署说明](../../liushao_community_backend/deploy/README.md)。

## 验证结果

- dbx 核验迁移前后 14 张表的精确行数与数据校验值一致；104 个字段定义、38 个索引项、7 条外键、4 个检查约束及全部自增值保留。管理端关系表无孤立记录，外键不再引用 `vueadmin`。
- 使用两端更新后的 `.env.local` 通过真实 MySQL JDBC 读取各自全部表，并用 Flyway 校验已应用迁移的校验和；两端仍为 V1，待执行版本保持不变。
- H2 使用两套真实 SQL，分别验证管理端先启动、社区端先启动，两端均完整执行 V1，并验证重复执行无新增迁移。
- 管理端 37 项测试通过，社区端 68 项通过、1 项需要独立 MySQL 时区配置的测试跳过，备份校验 27 项通过；Compose 配置与脚本语法检查通过。
- 在两个独立本机 Compose 项目中完成 9 组真实备份与恢复检查，包括两张历史表、中文数据与图片、两个应用账号访问共享库及系统库权限限制、旧会话撤销和失败恢复。全部通过，测试资源已清理，NAS 业务数据未用于此演练。

完整的脱敏核验摘要见 [验证报告](database-consolidation-validation.json)。迁移前后结构快照与备份保存在上述 `.local/backups/database-consolidation/` 目录；一次性验证依赖和运行日志不作为项目资料保留。
