/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
 * keyword: id\content\type\usage\password\role\action
 */
CREATE TABLE config_info (
                             id bigserial NOT NULL,
                             data_id varchar(255) NOT NULL ,
                             group_id varchar(255) DEFAULT NULL,
                             content text NOT NULL,
                             md5 varchar(32) DEFAULT NULL,
                             gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             src_user text ,
                             src_ip varchar(50) DEFAULT NULL ,
                             app_name varchar(128) DEFAULT NULL,
                             tenant_id varchar(128) DEFAULT '' ,
                             c_desc varchar(256) DEFAULT NULL,
                             c_use varchar(64) DEFAULT NULL,
                             effect varchar(64) DEFAULT NULL,
                             type varchar(64) DEFAULT NULL,
                             c_schema text,
                             encrypted_data_key text,
                             PRIMARY KEY (id),
                             constraint uk_configinfo_datagrouptenant unique(data_id,group_id,tenant_id)
);
COMMENT ON TABLE config_info IS 'config_info';
COMMENT ON COLUMN config_info.id IS 'id';
COMMENT ON COLUMN config_info.data_id IS 'data_id';
COMMENT ON COLUMN config_info.content IS 'content';
COMMENT ON COLUMN config_info.md5 IS 'md5';
COMMENT ON COLUMN config_info.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info.src_user IS 'source user';
COMMENT ON COLUMN config_info.src_ip IS 'source ip';
COMMENT ON COLUMN config_info.tenant_id IS '租户字段';
COMMENT ON COLUMN config_info.encrypted_data_key IS '秘钥';

CREATE TABLE config_info_aggr (
                                  id bigserial NOT NULL,
                                  data_id varchar(255) NOT NULL,
                                  group_id varchar(255) NOT NULL,
                                  datum_id varchar(255) NOT NULL,
                                  content text NOT NULL,
                                  gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  app_name varchar(128) DEFAULT NULL,
                                  tenant_id varchar(128) DEFAULT '',
                                  PRIMARY KEY (id),
                                  constraint uk_configinfoaggr_datagrouptenantdatum unique(data_id,group_id,tenant_id,datum_id)
);
COMMENT ON TABLE config_info_aggr IS '增加租户字段';
COMMENT ON COLUMN config_info_aggr.id IS 'id';
COMMENT ON COLUMN config_info_aggr.data_id IS 'data_id';
COMMENT ON COLUMN config_info_aggr.group_id IS 'group_id';
COMMENT ON COLUMN config_info_aggr.datum_id IS 'datum_id';
COMMENT ON COLUMN config_info_aggr.content IS '内容';
COMMENT ON COLUMN config_info_aggr.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_aggr.tenant_id IS '租户字段';

CREATE TABLE config_info_beta (
                                  id bigserial NOT NULL,
                                  data_id varchar(255) NOT NULL,
                                  group_id varchar(128) NOT NULL,
                                  app_name varchar(128) DEFAULT NULL,
                                  content text NOT NULL,
                                  beta_ips varchar(1024) DEFAULT NULL,
                                  md5 varchar(32) DEFAULT NULL,
                                  gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  src_user text,
                                  src_ip varchar(50) DEFAULT NULL,
                                  tenant_id varchar(128) DEFAULT '',
                                  encrypted_data_key text,
                                  PRIMARY KEY (id),
                                  constraint uk_configinfobeta_datagrouptenant unique(data_id,group_id,tenant_id)
);
COMMENT ON TABLE config_info_beta IS 'config_info_beta';
COMMENT ON COLUMN config_info_beta.id IS 'id';
COMMENT ON COLUMN config_info_beta.data_id IS 'data_id';
COMMENT ON COLUMN config_info_beta.group_id IS 'group_id';
COMMENT ON COLUMN config_info_beta.app_name IS 'app_name';
COMMENT ON COLUMN config_info_beta.content IS 'content';
COMMENT ON COLUMN config_info_beta.beta_ips IS 'betaIps';
COMMENT ON COLUMN config_info_beta.md5 IS 'md5';
COMMENT ON COLUMN config_info_beta.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info_beta.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_beta.src_user IS 'source user';
COMMENT ON COLUMN config_info_beta.src_ip IS 'source ip';
COMMENT ON COLUMN config_info_beta.tenant_id IS '租户字段';
COMMENT ON COLUMN config_info_beta.encrypted_data_key IS '秘钥';

CREATE TABLE config_info_tag (
                                 id bigserial NOT NULL,
                                 data_id varchar(255) NOT NULL,
                                 group_id varchar(128) NOT NULL,
                                 tenant_id varchar(128) DEFAULT '',
                                 tag_id varchar(128) NOT NULL,
                                 app_name varchar(128) DEFAULT NULL,
                                 content text NOT NULL,
                                 md5 varchar(32) DEFAULT NULL,
                                 gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 src_user text,
                                 src_ip varchar(50) DEFAULT NULL,
                                 PRIMARY KEY (id),
                                 constraint uk_configinfotag_datagrouptenanttag unique(data_id,group_id,tenant_id,tag_id)
);
COMMENT ON TABLE config_info_tag IS 'config_info_tag';
COMMENT ON COLUMN config_info_tag.id IS 'id';
COMMENT ON COLUMN config_info_tag.data_id IS 'data_id';
COMMENT ON COLUMN config_info_tag.group_id IS 'group_id';
COMMENT ON COLUMN config_info_tag.tenant_id IS 'tenant_id';
COMMENT ON COLUMN config_info_tag.tag_id IS 'tag_id';
COMMENT ON COLUMN config_info_tag.app_name IS 'app_name';
COMMENT ON COLUMN config_info_tag.content IS 'content';
COMMENT ON COLUMN config_info_tag.md5 IS 'md5';
COMMENT ON COLUMN config_info_tag.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info_tag.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_tag.src_user IS 'source user';
COMMENT ON COLUMN config_info_tag.src_ip IS 'source ip';

CREATE TABLE config_tags_relation (
                                      id bigint NOT NULL,
                                      tag_name varchar(128) NOT NULL ,
                                      tag_type varchar(64) DEFAULT NULL,
                                      data_id varchar(255) NOT NULL,
                                      group_id varchar(128) NOT NULL,
                                      tenant_id varchar(128) DEFAULT '',
                                      nid bigserial NOT NULL,
                                      PRIMARY KEY (nid),
                                      constraint uk_configtagrelation_configidtag unique(id,tag_name,tag_type)
);
CREATE INDEX idx_tenant_id ON config_tags_relation (tenant_id);
COMMENT ON TABLE config_tags_relation IS 'config_tags_relation';
COMMENT ON COLUMN config_tags_relation.id IS 'id';
COMMENT ON COLUMN config_tags_relation.tag_name IS 'tag_name';
COMMENT ON COLUMN config_tags_relation.tag_type IS 'tag_type';
COMMENT ON COLUMN config_tags_relation.data_id IS 'data_id';
COMMENT ON COLUMN config_tags_relation.group_id IS 'group_id';
COMMENT ON COLUMN config_tags_relation.tenant_id IS 'tenant_id';

CREATE TABLE group_capacity (
                                id bigserial NOT NULL ,
                                group_id varchar(128) NOT NULL DEFAULT '' ,
                                quota int4 NOT NULL DEFAULT '0',
                                usage int4 NOT NULL DEFAULT '0' ,
                                max_size int4 NOT NULL DEFAULT '0',
                                max_aggr_count int4 NOT NULL DEFAULT '0',
                                max_aggr_size int4 NOT NULL DEFAULT '0',
                                max_history_count int4 NOT NULL DEFAULT '0',
                                gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (id),
                                constraint uk_group_id unique(group_id)
);
COMMENT ON TABLE group_capacity IS '集群、各Group容量信息表';
COMMENT ON COLUMN group_capacity.id IS '主键ID';
COMMENT ON COLUMN group_capacity.group_id IS 'Group ID，空字符表示整个集群';
COMMENT ON COLUMN group_capacity.quota IS '配额，0表示使用默认值';
COMMENT ON COLUMN group_capacity.usage IS '使用量';
COMMENT ON COLUMN group_capacity.max_size IS '单个配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN group_capacity.max_aggr_count IS '聚合子配置最大个数，，0表示使用默认值';
COMMENT ON COLUMN group_capacity.max_aggr_size IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN group_capacity.max_history_count IS '最大变更历史数量';
COMMENT ON COLUMN group_capacity.gmt_create IS '创建时间';
COMMENT ON COLUMN group_capacity.gmt_modified IS '修改时间';

CREATE TABLE his_config_info (
                                 id bigint NOT NULL,
                                 nid bigserial NOT NULL,
                                 data_id varchar(255) NOT NULL,
                                 group_id varchar(128) NOT NULL,
                                 app_name varchar(128) DEFAULT NULL ,
                                 content text NOT NULL,
                                 md5 varchar(32) DEFAULT NULL,
                                 gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 src_user text,
                                 src_ip varchar(50) DEFAULT NULL,
                                 op_type char(10) DEFAULT NULL,
                                 tenant_id varchar(128) DEFAULT '',
                                 encrypted_data_key text,
                                 PRIMARY KEY (nid)
);
CREATE INDEX idx_gmt_create ON his_config_info (gmt_create);
CREATE INDEX idx_gmt_modified ON his_config_info (gmt_modified);
CREATE INDEX idx_did ON his_config_info (data_id);
COMMENT ON TABLE his_config_info IS '多租户改造表';
COMMENT ON COLUMN his_config_info.app_name IS 'app_name';
COMMENT ON COLUMN his_config_info.tenant_id IS '租户字段';
COMMENT ON COLUMN his_config_info.encrypted_data_key IS '秘钥';


CREATE TABLE tenant_capacity (
                                 id bigserial NOT NULL,
                                 tenant_id varchar(128) NOT NULL DEFAULT '',
                                 quota int4 NOT NULL DEFAULT '0',
                                 usage int4 NOT NULL DEFAULT '0',
                                 max_size int4 NOT NULL DEFAULT '0',
                                 max_aggr_count int4 NOT NULL DEFAULT '0',
                                 max_aggr_size int4 NOT NULL DEFAULT '0' ,
                                 max_history_count int4 NOT NULL DEFAULT '0',
                                 gmt_create timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 gmt_modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ,
                                 PRIMARY KEY (id),
                                 constraint uk_tenant_id unique(tenant_id)
);
COMMENT ON TABLE tenant_capacity IS '租户容量信息表';
COMMENT ON COLUMN tenant_capacity.id IS '主键ID';
COMMENT ON COLUMN tenant_capacity.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN tenant_capacity.quota IS '配额，0表示使用默认值';
COMMENT ON COLUMN tenant_capacity.usage IS '使用量';
COMMENT ON COLUMN tenant_capacity.max_size IS '单个配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN tenant_capacity.max_aggr_count IS '聚合子配置最大个数';
COMMENT ON COLUMN tenant_capacity.max_aggr_size IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN tenant_capacity.max_history_count IS '最大变更历史数量';
COMMENT ON COLUMN tenant_capacity.gmt_create IS '创建时间';
COMMENT ON COLUMN tenant_capacity.gmt_modified IS '修改时间';


CREATE TABLE tenant_info (
                             id bigserial NOT NULL ,
                             kp varchar(128) NOT NULL,
                             tenant_id varchar(128) default '',
                             tenant_name varchar(128) default '',
                             tenant_desc varchar(256) DEFAULT NULL ,
                             create_source varchar(32) DEFAULT NULL ,
                             gmt_create int8 NOT NULL,
                             gmt_modified int8 NOT NULL,
                             PRIMARY KEY (id),
                             constraint uk_tenant_info_kptenantid unique(kp,tenant_id)
);
CREATE INDEX idx_tenant_info_tenant_id ON tenant_info (tenant_id);
COMMENT ON TABLE tenant_info IS '租户信息表';
COMMENT ON COLUMN tenant_info.id IS 'id';
COMMENT ON COLUMN tenant_info.kp IS 'kp';
COMMENT ON COLUMN tenant_info.tenant_id IS 'tenant_id';
COMMENT ON COLUMN tenant_info.tenant_name IS 'tenant_name';
COMMENT ON COLUMN tenant_info.tenant_desc IS 'tenant_desc';
COMMENT ON COLUMN tenant_info.create_source IS 'create_source';
COMMENT ON COLUMN tenant_info.gmt_create IS '创建时间';
COMMENT ON COLUMN tenant_info.gmt_modified IS '修改时间';

CREATE TABLE users (
                       username varchar(50) NOT NULL PRIMARY KEY,
                       password varchar(500) NOT NULL,
                       enabled boolean NOT NULL
);

CREATE TABLE roles (
                       username varchar(50) NOT NULL,
                       role varchar(50) NOT NULL
);
CREATE INDEX idx_user_role ON roles (username, role);

CREATE TABLE permissions (
                             role varchar(50) NOT NULL,
                             resources varchar(255) NOT NULL,
                             action varchar(8) NOT NULL
);
CREATE INDEX uk_role_permission ON permissions (role,resources,action);

CREATE OR REPLACE FUNCTION working_version_num()
RETURNS varchar AS $$
BEGIN
RETURN '9.2.4';
END;
$$ LANGUAGE plpgsql;

INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);

INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');