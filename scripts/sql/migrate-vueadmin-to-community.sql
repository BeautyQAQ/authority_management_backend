-- Run once after backing up both databases and stopping application writes.
-- All source tables are InnoDB. One RENAME preserves their data, indexes,
-- foreign keys and AUTO_INCREMENT values atomically on the same MySQL server.
-- Existing destination tables cause the whole statement to fail.
RENAME TABLE
  vueadmin.sys_user TO liushao_community.sys_user,
  vueadmin.sys_role TO liushao_community.sys_role,
  vueadmin.sys_menu TO liushao_community.sys_menu,
  vueadmin.sys_user_role TO liushao_community.sys_user_role,
  vueadmin.sys_role_menu TO liushao_community.sys_role_menu,
  vueadmin.flyway_schema_history TO liushao_community.flyway_admin_schema_history;
