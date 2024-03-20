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

-- Table: config_info
CREATE TABLE config_info (
  id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
  data_id varchar(255) NOT NULL,
  group_id varchar(128) NULL,
  content nvarchar(max) NOT NULL,
  md5 varchar(32) NULL,
  gmt_create datetime NOT NULL DEFAULT GETDATE(),
  gmt_modified datetime NOT NULL DEFAULT GETDATE(),
  src_user nvarchar(max),
  src_ip varchar(50) NULL,
  app_name varchar(128) NULL,
  tenant_id varchar(128) DEFAULT '',
  c_desc varchar(256) NULL,
  c_use varchar(64) NULL,
  effect varchar(64) NULL,
  type varchar(64) NULL,
  c_schema nvarchar(max),
  encrypted_data_key nvarchar(max) NOT NULL,
  UNIQUE (data_id, group_id, tenant_id)
);

-- Table: config_info_aggr
CREATE TABLE config_info_aggr (
  id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
  data_id varchar(255) NOT NULL,
  group_id varchar(128) NOT NULL,
  datum_id varchar(255) NOT NULL,
  content nvarchar(max) NOT NULL,
  gmt_modified datetime NOT NULL,
  app_name varchar(128) NULL,
  tenant_id varchar(128) DEFAULT '',
  UNIQUE (data_id, group_id, tenant_id, datum_id)
);

-- Table: config_info_beta
CREATE TABLE config_info_beta (
    id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    app_name varchar(128) NULL,
    content nvarchar(max) NOT NULL,
    beta_ips varchar(1024) NULL,
    md5 varchar(32) NULL,
    gmt_create datetime NOT NULL DEFAULT GETDATE(),
    gmt_modified datetime NOT NULL DEFAULT GETDATE(),
    src_user nvarchar(max),
    src_ip varchar(50) NULL,
    tenant_id varchar(128) DEFAULT '',
    encrypted_data_key nvarchar(max) NOT NULL,
    UNIQUE (data_id, group_id, tenant_id)
);

-- Table: config_info_tag
CREATE TABLE config_info_tag (
    id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    tag_id varchar(128) NOT NULL,
    app_name varchar(128) NULL,
    content nvarchar(max) NOT NULL,
    md5 varchar(32) NULL,
    gmt_create datetime NOT NULL DEFAULT GETDATE(),
    gmt_modified datetime NOT NULL DEFAULT GETDATE(),
    src_user nvarchar(max),
    src_ip varchar(50) NULL,
    UNIQUE (data_id, group_id, tenant_id, tag_id)
);

-- Table: config_tags_relation
CREATE TABLE config_tags_relation (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    tag_name varchar(128) NOT NULL,
    tag_type varchar(64) NULL,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    nid bigint NOT NULL,
    UNIQUE (id, tag_name, tag_type),
    INDEX idx_tenant_id (tenant_id)
);

-- Table: group_capacity
CREATE TABLE group_capacity (
    id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
    group_id varchar(128) NOT NULL DEFAULT '',
    quota int NOT NULL DEFAULT 0 CHECK (quota >= 0),
    usage int NOT NULL DEFAULT 0 CHECK (usage >= 0),
    max_size int NOT NULL DEFAULT 0 CHECK (max_size >= 0),
    max_aggr_count int NOT NULL DEFAULT 0 CHECK (max_aggr_count >= 0),
    max_aggr_size int NOT NULL DEFAULT 0 CHECK (max_aggr_size >= 0),
    max_history_count int NOT NULL DEFAULT 0 CHECK (max_history_count >= 0),
    gmt_create datetime NOT NULL DEFAULT GETDATE(),
    gmt_modified datetime NOT NULL DEFAULT GETDATE(),
    UNIQUE (group_id)
);

-- Table: his_config_info
CREATE TABLE his_config_info (
    id bigint NOT NULL ,
    nid bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
    data_id varchar(255) NOT NULL,
    group_id varchar(128) NOT NULL,
    app_name varchar(128) NULL,
    content nvarchar(max) NOT NULL,
    md5 varchar(32) NULL,
    gmt_create datetime NOT NULL DEFAULT GETDATE(),
    gmt_modified datetime NOT NULL DEFAULT GETDATE(),
    src_user nvarchar(max),
    src_ip varchar(50) NULL,
    op_type char(10) NULL,
    tenant_id varchar(128) DEFAULT '',
    encrypted_data_key nvarchar(max) NOT NULL,
    INDEX idx_gmt_create (gmt_create),
    INDEX idx_gmt_modified (gmt_modified),
    INDEX idx_did (data_id)
);

-- Table: tenant_capacity
CREATE TABLE tenant_capacity (
    id bigint NOT NULL IDENTITY(1,1) PRIMARY KEY,
    tenant_id varchar(128) NOT NULL DEFAULT '',
    quota int NOT NULL DEFAULT 0 CHECK (quota >= 0),
    usage int NOT NULL DEFAULT 0 CHECK (usage >= 0),
    max_size int NOT NULL DEFAULT 0 CHECK (max_size >= 0),
    max_aggr_count int NOT NULL DEFAULT 0 CHECK (max_aggr_count >= 0),
    max_aggr_size int NOT NULL DEFAULT 0 CHECK (max_aggr_size >= 0),
    max_history_count int NOT NULL DEFAULT 0 CHECK (max_history_count >= 0),
    gmt_create datetime NOT NULL DEFAULT GETDATE(),
    gmt_modified datetime NOT NULL DEFAULT GETDATE(),
    UNIQUE (tenant_id)
);

-- Table: tenant_info
CREATE TABLE tenant_info (
    id bigint NOT NULL idENTITY(1,1) PRIMARY KEY,
    kp varchar(128) NOT NULL,
    tenant_id varchar(128) DEFAULT '',
    tenant_name varchar(128) DEFAULT '',
    tenant_desc varchar(256) NULL,
    create_source varchar(32) NULL,
    gmt_create bigint NOT NULL,
    gmt_modified bigint NOT NULL,
    UNIQUE (kp,tenant_id),
    INDEX idx_tenant_id (tenant_id)
);

CREATE TABLE users (
    username varchar(50) NOT NULL PRIMARY KEY,
    password varchar(500) NOT NULL,
    enabled bit NOT NULL
);

CREATE TABLE roles (
    username varchar(50) NOT NULL,
    role varchar(50) NOT NULL,
    CONSTRAINT idx_user_role PRIMARY KEY (username, role)
);

CREATE TABLE permissions (
    role varchar(50) NOT NULL,
    resource varchar(255) NOT NULL,
    action varchar(8) NOT NULL,
    CONSTRAINT uk_role_permission PRIMARY KEY (role, resource, action)
);

INSERT INTO users (username, [password], [enabled]) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', 1);

INSERT INTO roles (username, [role]) VALUES ('nacos', 'ROLE_ADMIN');

GO

-- 实现两个字段的自增
CREATE TRIGGER trg_config_tags_relation
    ON config_tags_relation
    AFTER INSERT
    AS
BEGIN
    UPDATE config_tags_relation
    SET nid = id
    WHERE id IN (SELECT id FROM inserted)
END
