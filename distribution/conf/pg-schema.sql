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

-- ----------------------------
-- Table structure for config_info
-- ----------------------------
DROP TABLE IF EXISTS "config_info";
CREATE TABLE "config_info" (
  "id" bigserial NOT NULL,
  "data_id" varchar(255)  NOT NULL,
  "group_id" varchar(255) ,
  "content" text  NOT NULL,
  "md5" varchar(32) ,
  "gmt_create" timestamp(6) NOT NULL,
  "gmt_modified" timestamp(6) NOT NULL,
  "src_user" text ,
  "src_ip" varchar(20) ,
  "app_name" varchar(128) ,
  "tenant_id" varchar(128) ,
  "c_desc" varchar(256) ,
  "c_use" varchar(64) ,
  "effect" varchar(64) ,
  "type" varchar(64) ,
  "c_schema" text ,
  "encrypted_data_key" text  NOT NULL
)
;

COMMENT ON COLUMN "config_info"."id" IS 'id';
COMMENT ON COLUMN "config_info"."data_id" IS 'data_id';
COMMENT ON COLUMN "config_info"."content" IS 'content';
COMMENT ON COLUMN "config_info"."md5" IS 'md5';
COMMENT ON COLUMN "config_info"."gmt_create" IS '创建时间';
COMMENT ON COLUMN "config_info"."gmt_modified" IS '修改时间';
COMMENT ON COLUMN "config_info"."src_user" IS 'source user';
COMMENT ON COLUMN "config_info"."src_ip" IS 'source ip';
COMMENT ON COLUMN "config_info"."tenant_id" IS '租户字段';
COMMENT ON COLUMN "config_info"."encrypted_data_key" IS '秘钥';
COMMENT ON TABLE "config_info" IS 'config_info';

-- ----------------------------
-- Table structure for config_info_gray
-- ----------------------------
DROP TABLE IF EXISTS "config_info_gray";
CREATE TABLE "config_info_gray" (
  "id" bigserial NOT NULL,
  "data_id" varchar(255) NOT NULL,
  "group_id" varchar(128) NOT NULL,
  "content" text NOT NULL,
  "md5" varchar(32),
  "src_user" text,
  "src_ip" varchar(100),
  "gmt_create" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "gmt_modified" timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "app_name" varchar(128),
  "tenant_id" varchar(128) DEFAULT '',
  "gray_name" varchar(128) NOT NULL,
  "gray_rule" text NOT NULL,
  "encrypted_data_key" varchar(256) NOT NULL DEFAULT ''
);

COMMENT ON COLUMN "config_info_gray"."id" IS 'id';
COMMENT ON COLUMN "config_info_gray"."data_id" IS 'data_id';
COMMENT ON COLUMN "config_info_gray"."group_id" IS 'group_id';
COMMENT ON COLUMN "config_info_gray"."content" IS 'content';
COMMENT ON COLUMN "config_info_gray"."md5" IS 'md5';
COMMENT ON COLUMN "config_info_gray"."src_user" IS 'source user';
COMMENT ON COLUMN "config_info_gray"."src_ip" IS 'source ip';
COMMENT ON COLUMN "config_info_gray"."gmt_create" IS '创建时间';
COMMENT ON COLUMN "config_info_gray"."gmt_modified" IS '修改时间';
COMMENT ON COLUMN "config_info_gray"."app_name" IS 'app_name';
COMMENT ON COLUMN "config_info_gray"."tenant_id" IS '租户字段';
COMMENT ON COLUMN "config_info_gray"."gray_name" IS '灰度名称';
COMMENT ON COLUMN "config_info_gray"."gray_rule" IS '灰度规则';
COMMENT ON COLUMN "config_info_gray"."encrypted_data_key" IS '秘钥';
COMMENT ON TABLE "config_info_gray" IS 'config_info_gray';

-- 创建索引
CREATE UNIQUE INDEX "uk_configinfogray_datagrouptenantgray" ON "config_info_gray" USING btree ("data_id", "group_id", "tenant_id", "gray_name");
CREATE INDEX "idx_dataid_gmt_modified_gray" ON "config_info_gray" USING btree ("data_id", "gmt_modified");
CREATE INDEX "idx_gmt_modified_gray" ON "config_info_gray" USING btree ("gmt_modified");

ALTER TABLE "config_info_gray" ADD CONSTRAINT "config_info_gray_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Records of config_info_gray
-- ----------------------------
BEGIN;
COMMIT;


-- ----------------------------
-- Table structure for config_tags_relation
-- ----------------------------
DROP TABLE IF EXISTS "config_tags_relation";
CREATE TABLE "config_tags_relation" (
  "id" bigint NOT NULL,
  "tag_name" varchar(128)  NOT NULL,
  "tag_type" varchar(64) ,
  "data_id" varchar(255)  NOT NULL,
  "group_id" varchar(128)  NOT NULL,
  "tenant_id" varchar(128) ,
  "nid" bigserial NOT NULL
)
;
COMMENT ON COLUMN "config_tags_relation"."id" IS 'id';
COMMENT ON COLUMN "config_tags_relation"."tag_name" IS 'tag_name';
COMMENT ON COLUMN "config_tags_relation"."tag_type" IS 'tag_type';
COMMENT ON COLUMN "config_tags_relation"."data_id" IS 'data_id';
COMMENT ON COLUMN "config_tags_relation"."group_id" IS 'group_id';
COMMENT ON COLUMN "config_tags_relation"."tenant_id" IS 'tenant_id';
COMMENT ON TABLE "config_tags_relation" IS 'config_tag_relation';

-- ----------------------------
-- Records of config_tags_relation
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for group_capacity
-- ----------------------------
DROP TABLE IF EXISTS "group_capacity";
CREATE TABLE "group_capacity" (
  "id" bigserial NOT NULL,
  "group_id" varchar(128)  NOT NULL,
  "quota" int4 NOT NULL,
  "usage" int4 NOT NULL,
  "max_size" int4 NOT NULL,
  "max_aggr_count" int4 NOT NULL,
  "max_aggr_size" int4 NOT NULL,
  "max_history_count" int4 NOT NULL,
  "gmt_create" timestamp(6) NOT NULL,
  "gmt_modified" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "group_capacity"."id" IS '主键ID';
COMMENT ON COLUMN "group_capacity"."group_id" IS 'Group ID，空字符表示整个集群';
COMMENT ON COLUMN "group_capacity"."quota" IS '配额，0表示使用默认值';
COMMENT ON COLUMN "group_capacity"."usage" IS '使用量';
COMMENT ON COLUMN "group_capacity"."max_size" IS '单个配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN "group_capacity"."max_aggr_count" IS '聚合子配置最大个数，，0表示使用默认值';
COMMENT ON COLUMN "group_capacity"."max_aggr_size" IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN "group_capacity"."max_history_count" IS '最大变更历史数量';
COMMENT ON COLUMN "group_capacity"."gmt_create" IS '创建时间';
COMMENT ON COLUMN "group_capacity"."gmt_modified" IS '修改时间';
COMMENT ON TABLE "group_capacity" IS '集群、各Group容量信息表';

-- ----------------------------
-- Records of group_capacity
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for his_config_info
-- ----------------------------
DROP TABLE IF EXISTS "his_config_info";
CREATE TABLE "his_config_info" (
  "id" int8 NOT NULL,
  "nid" bigserial NOT NULL,
  "data_id" varchar(255)  NOT NULL,
  "group_id" varchar(128)  NOT NULL,
  "app_name" varchar(128) ,
  "content" text  NOT NULL,
  "md5" varchar(32) ,
  "gmt_create" timestamp(6) NOT NULL  DEFAULT '2010-05-05 00:00:00',
  "gmt_modified" timestamp(6) NOT NULL,
  "src_user" text ,
  "src_ip" varchar(20) ,
  "op_type" char(10) ,
  "tenant_id" varchar(128) ,
  "encrypted_data_key" text  NOT NULL,
  "publish_type" varchar(50) DEFAULT 'formal',
  "gray_name" varchar(50),
  "ext_info" text
)
;
COMMENT ON COLUMN "his_config_info"."app_name" IS 'app_name';
COMMENT ON COLUMN "his_config_info"."tenant_id" IS '租户字段';
COMMENT ON COLUMN "his_config_info"."encrypted_data_key" IS '秘钥';
COMMENT ON COLUMN "his_config_info"."publish_type" IS 'publish type gray or formal';
COMMENT ON COLUMN "his_config_info"."gray_name" IS 'gray name';
COMMENT ON COLUMN "his_config_info"."ext_info" IS 'ext info';
COMMENT ON TABLE "his_config_info" IS '多租户改造';


-- ----------------------------
-- Table structure for permissions
-- ----------------------------
DROP TABLE IF EXISTS "permissions";
CREATE TABLE "permissions" (
  "role" varchar(50)  NOT NULL,
  "resource" varchar(512)  NOT NULL,
  "action" varchar(8)  NOT NULL
)
;

-- ----------------------------
-- Records of permissions
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for roles
-- ----------------------------
DROP TABLE IF EXISTS "roles";
CREATE TABLE "roles" (
  "username" varchar(50)  NOT NULL,
  "role" varchar(50)  NOT NULL
)
;

-- ----------------------------
-- Records of roles
-- ----------------------------
BEGIN;
INSERT INTO "roles" VALUES ('nacos', 'ROLE_ADMIN');
COMMIT;

-- ----------------------------
-- Table structure for tenant_capacity
-- ----------------------------
DROP TABLE IF EXISTS "tenant_capacity";
CREATE TABLE "tenant_capacity" (
  "id" bigserial NOT NULL,
  "tenant_id" varchar(128)  NOT NULL,
  "quota" int4 NOT NULL,
  "usage" int4 NOT NULL,
  "max_size" int4 NOT NULL,
  "max_aggr_count" int4 NOT NULL,
  "max_aggr_size" int4 NOT NULL,
  "max_history_count" int4 NOT NULL,
  "gmt_create" timestamp(6) NOT NULL,
  "gmt_modified" timestamp(6) NOT NULL
)
;
COMMENT ON COLUMN "tenant_capacity"."id" IS '主键ID';
COMMENT ON COLUMN "tenant_capacity"."tenant_id" IS 'Tenant ID';
COMMENT ON COLUMN "tenant_capacity"."quota" IS '配额，0表示使用默认值';
COMMENT ON COLUMN "tenant_capacity"."usage" IS '使用量';
COMMENT ON COLUMN "tenant_capacity"."max_size" IS '单个配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN "tenant_capacity"."max_aggr_count" IS '聚合子配置最大个数';
COMMENT ON COLUMN "tenant_capacity"."max_aggr_size" IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN "tenant_capacity"."max_history_count" IS '最大变更历史数量';
COMMENT ON COLUMN "tenant_capacity"."gmt_create" IS '创建时间';
COMMENT ON COLUMN "tenant_capacity"."gmt_modified" IS '修改时间';
COMMENT ON TABLE "tenant_capacity" IS '租户容量信息表';

-- ----------------------------
-- Records of tenant_capacity
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for tenant_info
-- ----------------------------
DROP TABLE IF EXISTS "tenant_info";
CREATE TABLE "tenant_info" (
  "id" bigserial NOT NULL,
  "kp" varchar(128)  NOT NULL,
  "tenant_id" varchar(128) ,
  "tenant_name" varchar(128) ,
  "tenant_desc" varchar(256) ,
  "create_source" varchar(32) ,
  "gmt_create" int8 NOT NULL,
  "gmt_modified" int8 NOT NULL
)
;
COMMENT ON COLUMN "tenant_info"."id" IS 'id';
COMMENT ON COLUMN "tenant_info"."kp" IS 'kp';
COMMENT ON COLUMN "tenant_info"."tenant_id" IS 'tenant_id';
COMMENT ON COLUMN "tenant_info"."tenant_name" IS 'tenant_name';
COMMENT ON COLUMN "tenant_info"."tenant_desc" IS 'tenant_desc';
COMMENT ON COLUMN "tenant_info"."create_source" IS 'create_source';
COMMENT ON COLUMN "tenant_info"."gmt_create" IS '创建时间';
COMMENT ON COLUMN "tenant_info"."gmt_modified" IS '修改时间';
COMMENT ON TABLE "tenant_info" IS 'tenant_info';

-- ----------------------------
-- Records of tenant_info
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS "users";
CREATE TABLE "users" (
  "username" varchar(50)  NOT NULL,
  "password" varchar(500)  NOT NULL,
  "enabled" boolean NOT NULL
)
;

-- ----------------------------
-- Records of users
-- ----------------------------
BEGIN;
COMMIT;

-- ----------------------------
-- Indexes structure for table config_info
-- ----------------------------
CREATE UNIQUE INDEX "uk_configinfo_datagrouptenant" ON "config_info" ("data_id","group_id","tenant_id");

-- ----------------------------
-- Primary Key structure for table config_info
-- ----------------------------
ALTER TABLE "config_info" ADD CONSTRAINT "config_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table config_tags_relation
-- ----------------------------
CREATE INDEX "idx_tenant_id" ON "config_tags_relation" USING btree (
  "tenant_id"
);
CREATE UNIQUE INDEX "uk_configtagrelation_configidtag" ON "config_tags_relation" USING btree (
  "id",
  "tag_name",
  "tag_type"
);

-- ----------------------------
-- Primary Key structure for table config_tags_relation
-- ----------------------------
ALTER TABLE "config_tags_relation" ADD CONSTRAINT "config_tags_relation_pkey" PRIMARY KEY ("nid");

-- ----------------------------
-- Indexes structure for table group_capacity
-- ----------------------------
CREATE UNIQUE INDEX "uk_group_id" ON "group_capacity" USING btree (
  "group_id"
);

-- ----------------------------
-- Primary Key structure for table group_capacity
-- ----------------------------
ALTER TABLE "group_capacity" ADD CONSTRAINT "group_capacity_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table his_config_info
-- ----------------------------
CREATE INDEX "idx_did" ON "his_config_info" USING btree (
  "data_id"
);
CREATE INDEX "idx_gmt_create" ON "his_config_info" USING btree (
  "gmt_create"
);
CREATE INDEX "idx_gmt_modified" ON "his_config_info" USING btree (
  "gmt_modified"
);

-- ----------------------------
-- Primary Key structure for table his_config_info
-- ----------------------------
ALTER TABLE "his_config_info" ADD CONSTRAINT "his_config_info_pkey" PRIMARY KEY ("nid");

-- ----------------------------
-- Indexes structure for table permissions
-- ----------------------------
CREATE UNIQUE INDEX "uk_role_permission" ON "permissions" USING btree (
  "role",
  "resource",
  "action"
);

-- ----------------------------
-- Indexes structure for table roles
-- ----------------------------
CREATE UNIQUE INDEX "uk_username_role" ON "roles" USING btree (
  "username",
  "role"
);

-- ----------------------------
-- Indexes structure for table tenant_capacity
-- ----------------------------
CREATE UNIQUE INDEX "uk_tenant_id" ON "tenant_capacity" USING btree (
  "tenant_id"
);

-- ----------------------------
-- Primary Key structure for table tenant_capacity
-- ----------------------------
ALTER TABLE "tenant_capacity" ADD CONSTRAINT "tenant_capacity_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table tenant_info
-- ----------------------------
CREATE UNIQUE INDEX "uk_tenant_info_kptenantid" ON "tenant_info" USING btree (
  "kp",
  "tenant_id"
);
