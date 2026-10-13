CREATE TABLE sys_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(64) NOT NULL UNIQUE,
 password VARCHAR(100) NOT NULL, avatar VARCHAR(255), email VARCHAR(128), phone VARCHAR(32), city VARCHAR(64),
 created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated TIMESTAMP, last_login TIMESTAMP,
 statu INT NOT NULL DEFAULT 1, auth_version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_user_status CHECK (statu IN (0,1))
);
CREATE TABLE sys_role (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL UNIQUE, code VARCHAR(64) NOT NULL UNIQUE,
 remark VARCHAR(255), created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated TIMESTAMP,
 statu INT NOT NULL DEFAULT 1, CONSTRAINT ck_role_status CHECK (statu IN (0,1))
);
CREATE TABLE sys_menu (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(64) NOT NULL UNIQUE, path VARCHAR(255), perms VARCHAR(255), component VARCHAR(255),
 type INT NOT NULL, icon VARCHAR(64), order_num INT NOT NULL DEFAULT 1,
 created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated TIMESTAMP, statu INT NOT NULL DEFAULT 1,
 CONSTRAINT ck_menu_status CHECK (statu IN (0,1)), CONSTRAINT ck_menu_type CHECK (type IN (0,1,2))
);
CREATE TABLE sys_user_role (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
 CONSTRAINT uq_user_role UNIQUE (user_id,role_id),
 FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
 FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
);
CREATE TABLE sys_role_menu (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
 CONSTRAINT uq_role_menu UNIQUE (role_id,menu_id),
 FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE,
 FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE
);
CREATE INDEX idx_menu_parent ON sys_menu(parent_id);
INSERT INTO sys_role (id,name,code,remark,statu) VALUES (1,'超级管理员','admin','系统内置管理角色',1);
INSERT INTO sys_menu (id,parent_id,name,path,perms,component,type,order_num) VALUES
 (1,0,'系统管理','','sys:manage','',0,1),
 (2,1,'用户管理','/sys/users','sys:user:list','sys/User',1,1),
 (3,1,'角色管理','/sys/roles','sys:role:list','sys/Role',1,2),
 (4,1,'菜单管理','/sys/menus','sys:menu:list','sys/Menu',1,3),
 (5,2,'新增用户','','sys:user:save','',2,1),
 (6,2,'修改用户','','sys:user:update','',2,2),
 (7,2,'删除用户','','sys:user:delete','',2,3),
 (8,2,'分配角色','','sys:user:role','',2,4),
 (9,2,'重置密码','','sys:user:repass','',2,5),
 (10,3,'新增角色','','sys:role:save','',2,1),
 (11,3,'修改角色','','sys:role:update','',2,2),
 (12,3,'删除角色','','sys:role:delete','',2,3),
 (13,3,'分配权限','','sys:role:perm','',2,4),
 (14,4,'新增菜单','','sys:menu:save','',2,1),
 (15,4,'修改菜单','','sys:menu:update','',2,2),
 (16,4,'删除菜单','','sys:menu:delete','',2,3);
INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,id FROM sys_menu;
