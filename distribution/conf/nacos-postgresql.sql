/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info   */
/******************************************/
DROP TABLE IF EXISTS "config_info";
CREATE TABLE "config_info"
(
    "id"           serial8,
    "data_id"      VARCHAR(255) NOT NULL,
    "group_id"     VARCHAR(255)          DEFAULT NULL,
    "content"      TEXT         NOT NULL,
    "md5"          VARCHAR(32)           DEFAULT NULL,
    "gmt_create"   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "src_user"     TEXT,
    "src_ip"       VARCHAR(50)           DEFAULT NULL,
    "app_name"     VARCHAR(128)          DEFAULT NULL,
    "tenant_id"    VARCHAR(128)          DEFAULT '',
    "c_desc"       VARCHAR(256)          DEFAULT NULL,
    "c_use"        VARCHAR(64)           DEFAULT NULL,
    "effect"       VARCHAR(64)           DEFAULT NULL,
    "type"         VARCHAR(64)           DEFAULT NULL,
    "c_schema"     TEXT,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_configinfo_datagrouptenant" ON "config_info" USING btree ("data_id", "group_id", "tenant_id");
/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_aggr   */
/******************************************/
DROP TABLE IF EXISTS "config_info_aggr";
CREATE TABLE "config_info_aggr"
(
    "id"           serial8,
    "data_id"      VARCHAR(255) NOT NULL,
    "group_id"     VARCHAR(255) NOT NULL,
    "datum_id"     VARCHAR(255) NOT NULL,
    "content"      TEXT         NOT NULL,
    "gmt_modified" TIMESTAMP    NOT NULL,
    "app_name"     VARCHAR(128) DEFAULT NULL,
    "tenant_id"    VARCHAR(128) DEFAULT '',
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_configinfoaggr_datagrouptenantdatum" ON "config_info_aggr" USING btree ("data_id", "group_id", "tenant_id", "datum_id");


/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_beta   */
/******************************************/
DROP TABLE IF EXISTS "config_info_beta";
CREATE TABLE "config_info_beta"
(
    "id"           serial8,
    "data_id"      VARCHAR(255) NOT NULL,
    "group_id"     VARCHAR(128) NOT NULL,
    "app_name"     VARCHAR(128)          DEFAULT NULL,
    "content"      TEXT         NOT NULL,
    "beta_ips"     VARCHAR(1024)         DEFAULT NULL,
    "md5"          VARCHAR(32)           DEFAULT NULL,
    "gmt_create"   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "src_user"     TEXT,
    "src_ip"       VARCHAR(50)           DEFAULT NULL,
    "tenant_id"    VARCHAR(128)          DEFAULT '',
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_configinfobeta_datagrouptenant" ON "config_info_beta" USING btree ("data_id", "group_id", "tenant_id");

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_tag   */
/******************************************/
DROP TABLE IF EXISTS "config_info_tag";
CREATE TABLE "config_info_tag"
(
    "id"           serial8,
    "data_id"      VARCHAR(255) NOT NULL,
    "group_id"     VARCHAR(128) NOT NULL,
    "tenant_id"    VARCHAR(128)          DEFAULT '',
    "tag_id"       VARCHAR(128) NOT NULL,
    "app_name"     VARCHAR(128)          DEFAULT NULL,
    "content"      TEXT         NOT NULL,
    "md5"          VARCHAR(32)           DEFAULT NULL,
    "gmt_create"   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "src_user"     TEXT,
    "src_ip"       VARCHAR(50)           DEFAULT NULL,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_configinfotag_datagrouptenanttag" ON "config_info_tag" USING btree ("data_id", "group_id", "tenant_id", "tag_id");

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_tags_relation   */
/******************************************/
DROP TABLE IF EXISTS "config_tags_relation";
CREATE TABLE "config_tags_relation"
(
    "id"        int8,
    "tag_name"  VARCHAR(128) NOT NULL,
    "tag_type"  VARCHAR(64)  DEFAULT NULL,
    "data_id"   VARCHAR(255) NOT NULL,
    "group_id"  VARCHAR(128) NOT NULL,
    "tenant_id" VARCHAR(128) DEFAULT '',
    "nid"       serial8,
    PRIMARY KEY ("nid")
);
CREATE UNIQUE INDEX "uk_configtagrelation_configidtag" ON "config_tags_relation" USING btree ("id", "tag_name", "tag_type");
CREATE INDEX "idx_tenant_id" ON "config_tags_relation" USING btree ("tenant_id");

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = group_capacity   */
/******************************************/
DROP TABLE IF EXISTS "group_capacity";
CREATE TABLE "group_capacity"
(
    "id"                serial8,
    "group_id"          VARCHAR(128) NOT NULL DEFAULT '',
    "quota"             INT          NOT NULL DEFAULT '0',
    "usage"             INT          NOT NULL DEFAULT '0',
    "max_size"          INT          NOT NULL DEFAULT '0',
    "max_aggr_count"    INT          NOT NULL DEFAULT '0',
    "max_aggr_size"     INT          NOT NULL DEFAULT '0',
    "max_history_count" INT          NOT NULL DEFAULT '0',
    "gmt_create"        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_group_id" ON "group_capacity" USING btree ("group_id");

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = his_config_info   */
/******************************************/
DROP TABLE IF EXISTS "his_config_info";
CREATE TABLE "his_config_info"
(
    "id"           int8         NOT NULL,
    "nid"          serial8,
    "data_id"      VARCHAR(255) NOT NULL,
    "group_id"     VARCHAR(128) NOT NULL,
    "app_name"     VARCHAR(128)          DEFAULT NULL,
    "content"      TEXT         NOT NULL,
    "md5"          VARCHAR(32)           DEFAULT NULL,
    "gmt_create"   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified" TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "src_user"     TEXT,
    "src_ip"       VARCHAR(50)           DEFAULT NULL,
    "op_type"      CHAR(10)              DEFAULT NULL,
    "tenant_id"    VARCHAR(128)          DEFAULT '',
    PRIMARY KEY ("nid")
);
CREATE INDEX "idx_gmt_create" ON "his_config_info" USING btree ("gmt_create");
CREATE INDEX "idx_gmt_modified" ON "his_config_info" USING btree ("gmt_modified");
CREATE INDEX "idx_did" ON "his_config_info" USING btree ("data_id");


/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = tenant_capacity   */
/******************************************/
DROP TABLE IF EXISTS "tenant_capacity";
CREATE TABLE "tenant_capacity"
(
    "id"                serial8,
    "tenant_id"         VARCHAR(128) NOT NULL DEFAULT '',
    "quota"             INT          NOT NULL DEFAULT '0',
    "usage"             INT          NOT NULL DEFAULT '0',
    "max_size"          INT          NOT NULL DEFAULT '0',
    "max_aggr_count"    INT          NOT NULL DEFAULT '0',
    "max_aggr_size"     INT          NOT NULL DEFAULT '0',
    "max_history_count" INT          NOT NULL DEFAULT '0',
    "gmt_create"        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "gmt_modified"      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_tenant_id" ON "tenant_capacity" USING btree ("tenant_id");

DROP TABLE IF EXISTS "tenant_info";
CREATE TABLE "tenant_info"
(
    "id"            serial8,
    "kp"            VARCHAR(128) NOT NULL,
    "tenant_id"     VARCHAR(128) DEFAULT '',
    "tenant_name"   VARCHAR(128) DEFAULT '',
    "tenant_desc"   VARCHAR(256) DEFAULT NULL,
    "create_source" VARCHAR(32)  DEFAULT NULL,
    "gmt_create"    serial8      NOT NULL,
    "gmt_modified"  serial8      NOT NULL,
    PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "uk_tenant_info_kptenantid" ON "tenant_info" USING btree ("kp", "tenant_id");
CREATE INDEX "uk_ti_tenant_id" ON "tenant_info" USING btree ("tenant_id");

DROP TABLE IF EXISTS "users";
CREATE TABLE "users"
(
    "username" VARCHAR(50)  NOT NULL,
    "password" VARCHAR(500) NOT NULL,
    "enabled"  INT2         NOT NULL,
    PRIMARY KEY ("username")
);

DROP TABLE IF EXISTS "roles";
CREATE TABLE "roles"
(
    "username" VARCHAR(50) NOT NULL,
    "role"     VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX "idx_user_role" ON "roles" USING btree ("username" ASC, "role" ASC);

DROP TABLE IF EXISTS "permissions";
CREATE TABLE "permissions"
(
    "role"     VARCHAR(50)  NOT NULL,
    "resource" VARCHAR(255) NOT NULL,
    "action"   VARCHAR(8)   NOT NULL
);
CREATE UNIQUE INDEX "uk_role_permission" ON "permissions" USING btree ("role", "resource", "action");

INSERT INTO users (username, password, enabled)
VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', 1);

INSERT INTO roles (username, role)
VALUES ('nacos', 'ROLE_ADMIN');
