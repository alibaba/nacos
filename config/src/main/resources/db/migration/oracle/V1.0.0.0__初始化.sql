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

create table CONFIG_INFO
(
  id           NUMBER(38) not null primary key,
  data_id      VARCHAR2(255) not null,
  group_id     VARCHAR2(255),
  content      CLOB not null,
  md5          VARCHAR2(32),
  gmt_create   TIMESTAMP(6) default sysdate not null,
  gmt_modified TIMESTAMP(6) default sysdate not null,
  src_user     CLOB,
  src_ip       VARCHAR2(20),
  app_name     VARCHAR2(128),
  tenant_id    VARCHAR2(128),
  c_desc       VARCHAR2(256),
  c_use        VARCHAR2(64),
  effect       VARCHAR2(64),
  type         VARCHAR2(64),
  c_schema     CLOB,
  UNIQUE (data_id,group_id,tenant_id)
);
CREATE SEQUENCE config_info_id_seq
       INCREMENT BY 1
       START WITH 1
       NOMAXVALUE
       NOCYCLE;

CREATE TRIGGER config_info_id_tr BEFORE
insert ON CONFIG_INFO FOR EACH ROW
begin
select config_info_id_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE config_info IS 'config_info';
COMMENT ON COLUMN config_info.content IS 'content';
COMMENT ON COLUMN config_info.src_ip IS 'source ip';
COMMENT ON COLUMN config_info.data_id IS 'data_id';
COMMENT ON COLUMN config_info.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info.tenant_id IS '租户字段';
COMMENT ON COLUMN config_info.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info.id IS 'id';
COMMENT ON COLUMN config_info.src_user IS 'source user';
COMMENT ON COLUMN config_info.md5 IS 'md5';
CREATE UNIQUE INDEX uk_configinfo_datagrouptenant ON config_info (data_id,group_id,tenant_id,id);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_aggr   */
/******************************************/
create table CONFIG_INFO_AGGR
(
  id           NUMBER(38) not null primary key,
  data_id      VARCHAR2(255) not null,
  group_id     VARCHAR2(255) not null,
  datum_id     VARCHAR2(255) not null,
  content      CLOB not null,
  gmt_modified TIMESTAMP(6) not null,
  app_name     VARCHAR2(128),
  tenant_id    VARCHAR2(128),
  UNIQUE (data_id,group_id,tenant_id,datum_id)
);
CREATE SEQUENCE config_info_aggr_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER config_info_aggr_tr BEFORE
insert ON CONFIG_INFO_AGGR FOR EACH ROW
begin
select config_info_aggr_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE config_info_aggr IS '增加租户字段';
COMMENT ON COLUMN config_info_aggr.content IS '内容';
COMMENT ON COLUMN config_info_aggr.data_id IS 'data_id';
COMMENT ON COLUMN config_info_aggr.tenant_id IS '租户字段';
COMMENT ON COLUMN config_info_aggr.group_id IS 'group_id';
COMMENT ON COLUMN config_info_aggr.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_aggr.id IS 'id';
COMMENT ON COLUMN config_info_aggr.datum_id IS 'datum_id';
CREATE UNIQUE INDEX uk_configinfoaggr_dgtdi ON config_info_aggr (data_id,group_id,tenant_id,datum_id,id);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_beta   */
/******************************************/
CREATE TABLE CONFIG_INFO_BETA (
    id      NUMBER(38) not null primary key,
	data_id VARCHAR2(255) NOT NULL,
	group_id VARCHAR2(128) NOT NULL,
	app_name VARCHAR2(128),
	content  CLOB not null,
	beta_ips CLOB,
	md5 VARCHAR2(32),
	gmt_create TIMESTAMP(6) default sysdate not null,
	gmt_modified TIMESTAMP(6) default sysdate not null,
	src_user CLOB,
	src_ip VARCHAR2(20),
	tenant_id VARCHAR2(128),
	UNIQUE (data_id,group_id,tenant_id)
) ;
CREATE SEQUENCE config_info_beta_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER config_info_beta_tr BEFORE
insert ON CONFIG_INFO_BETA FOR EACH ROW
begin
select config_info_beta_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE config_info_beta IS 'config_info_beta';
COMMENT ON COLUMN config_info_beta.content IS 'content';
COMMENT ON COLUMN config_info_beta.src_ip IS 'source ip';
COMMENT ON COLUMN config_info_beta.beta_ips IS 'betaIps';
COMMENT ON COLUMN config_info_beta.app_name IS 'app_name';
COMMENT ON COLUMN config_info_beta.data_id IS 'data_id';
COMMENT ON COLUMN config_info_beta.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info_beta.tenant_id IS '租户字段';
COMMENT ON COLUMN config_info_beta.group_id IS 'group_id';
COMMENT ON COLUMN config_info_beta.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_beta.src_user IS 'source user';
COMMENT ON COLUMN config_info_beta.id IS 'id';
COMMENT ON COLUMN config_info_beta.md5 IS 'md5';
CREATE UNIQUE INDEX uk_configinfobeta_dgti ON config_info_beta (data_id,group_id,tenant_id,id);


/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_info_tag   */
/******************************************/
CREATE TABLE CONFIG_INFO_TAG (
	id NUMBER(38) not null primary key,
	data_id VARCHAR2(255) NOT NULL,
	group_id VARCHAR2(128) NOT NULL,
	tenant_id VARCHAR2(128),
	tag_id VARCHAR2(128) NOT NULL,
	app_name VARCHAR2(128),
	content CLOB NOT NULL,
	md5 VARCHAR2(32),
	gmt_create TIMESTAMP(6) default sysdate not null,
	gmt_modified TIMESTAMP(6) default sysdate not null,
	src_user CLOB,
	src_ip VARCHAR2(20),
	UNIQUE (data_id,group_id,tenant_id,tag_id)
) ;

CREATE SEQUENCE config_info_tag_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER config_info_tag_tr BEFORE
insert ON CONFIG_INFO_TAG FOR EACH ROW
begin
select config_info_tag_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE config_info_tag IS 'config_info_tag';
COMMENT ON COLUMN config_info_tag.content IS 'content';
COMMENT ON COLUMN config_info_tag.src_ip IS 'source ip';
COMMENT ON COLUMN config_info_tag.app_name IS 'app_name';
COMMENT ON COLUMN config_info_tag.data_id IS 'data_id';
COMMENT ON COLUMN config_info_tag.gmt_create IS '创建时间';
COMMENT ON COLUMN config_info_tag.tenant_id IS 'tenant_id';
COMMENT ON COLUMN config_info_tag.group_id IS 'group_id';
COMMENT ON COLUMN config_info_tag.gmt_modified IS '修改时间';
COMMENT ON COLUMN config_info_tag.src_user IS 'source user';
COMMENT ON COLUMN config_info_tag.id IS 'id';
COMMENT ON COLUMN config_info_tag.md5 IS 'md5';
COMMENT ON COLUMN config_info_tag.tag_id IS 'tag_id';
CREATE UNIQUE INDEX uk_configinfotag_dgtti ON config_info_tag (data_id,group_id,tenant_id,tag_id,id);


/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = config_tags_relation   */
/******************************************/
CREATE TABLE CONFIG_TAGS_RELATION (
	id  NUMBER(38) not null,
	tag_name VARCHAR2(128) NOT NULL,
	tag_type VARCHAR2(64),
	data_id VARCHAR2(255) NOT NULL,
	group_id VARCHAR2(128) NOT NULL,
	tenant_id VARCHAR2(128),
	nid NUMBER(38) not null primary key,
	UNIQUE (id,tag_name,tag_type)
) ;
CREATE SEQUENCE config_info_relation_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER config_tags_relation_tr BEFORE
insert ON CONFIG_TAGS_RELATION FOR EACH ROW
begin
select config_info_relation_seq.nextval into:New.nid from dual;
end;
/
COMMENT ON TABLE config_tags_relation IS 'config_tag_relation';
COMMENT ON COLUMN config_tags_relation.data_id IS 'data_id';
COMMENT ON COLUMN config_tags_relation.tenant_id IS 'tenant_id';
COMMENT ON COLUMN config_tags_relation.group_id IS 'group_id';
COMMENT ON COLUMN config_tags_relation.tag_name IS 'tag_name';
COMMENT ON COLUMN config_tags_relation.tag_type IS 'tag_type';
COMMENT ON COLUMN config_tags_relation.id IS 'id';
CREATE INDEX idx_ctr_tenant_id ON config_tags_relation (tenant_id);
CREATE UNIQUE INDEX uk_configtagrelation_rttn ON config_tags_relation (id,tag_name,tag_type,nid);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = group_capacity   */
/******************************************/
CREATE TABLE GROUP_CAPACITY (
	id NUMBER(38) not null primary key,
	group_id VARCHAR2(128) NOT NULL,
	quota NUMBER(10) DEFAULT 0 NOT NULL,
	usage NUMBER(10) DEFAULT 0 NOT NULL,
	max_size NUMBER(10) DEFAULT 0 NOT NULL,
	max_aggr_count NUMBER(10) DEFAULT 0 NOT NULL,
	max_aggr_size NUMBER(10) DEFAULT 0 NOT NULL,
	max_history_count NUMBER(10) DEFAULT 0 NOT NULL,
	gmt_create TIMESTAMP(6) default sysdate not null,
	gmt_modified TIMESTAMP(6) default sysdate not null
) ;

CREATE SEQUENCE group_capacity_id_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER group_capacity_tr BEFORE
insert ON GROUP_CAPACITY FOR EACH ROW
begin
select group_capacity_id_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE group_capacity IS '集群、各Group容量信息表';
COMMENT ON COLUMN group_capacity.max_aggr_size IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN group_capacity.quota IS '配额，0表示使用默认值';
COMMENT ON COLUMN group_capacity.usage IS '使用量';
COMMENT ON COLUMN group_capacity.max_aggr_count IS '聚合子配置最大个数，，0表示使用默认值';
COMMENT ON COLUMN group_capacity.gmt_create IS '创建时间';
COMMENT ON COLUMN group_capacity.group_id IS 'Group ID，空字符表示整个集群';
COMMENT ON COLUMN group_capacity.gmt_modified IS '修改时间';
COMMENT ON COLUMN group_capacity.id IS '主键ID';
COMMENT ON COLUMN group_capacity.max_history_count IS '最大变更历史数量';
COMMENT ON COLUMN group_capacity.max_size IS '单个配置大小上限，单位为字节，0表示使用默认值';
CREATE UNIQUE INDEX uk_group_capacity_groupid ON group_capacity (group_id);

/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = his_config_info   */
/******************************************/
CREATE TABLE HIS_CONFIG_INFO (
	id  NUMBER(38) not null,
	nid  NUMBER(38) not null primary key,
	data_id VARCHAR2(255) NOT NULL,
	group_id VARCHAR2(128) NOT NULL,
	app_name VARCHAR2(128),
	content CLOB not null,
	md5 VARCHAR2(32),
	gmt_create TIMESTAMP(6) default sysdate not null,
	gmt_modified TIMESTAMP(6) default sysdate not null,
	src_user CLOB,
	src_ip VARCHAR2(20),
	op_type char(10),
	tenant_id VARCHAR2(128)
) ;

CREATE SEQUENCE his_config_info_nid_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER his_config_info_tr BEFORE
insert ON HIS_CONFIG_INFO FOR EACH ROW
begin
select his_config_info_nid_seq.nextval into:New.nid from dual;
end;
/
COMMENT ON TABLE his_config_info IS '多租户改造';
COMMENT ON COLUMN his_config_info.app_name IS 'app_name';
COMMENT ON COLUMN his_config_info.tenant_id IS '租户字段';
CREATE INDEX idx_his_config_info_did ON his_config_info (data_id);
CREATE INDEX idx_his_config_info_gc ON his_config_info (gmt_create);
CREATE INDEX idxhis_config_info_gm ON his_config_info (gmt_modified);


/******************************************/
/*   数据库全名 = nacos_config   */
/*   表名称 = tenant_capacity   */
/******************************************/
CREATE TABLE TENANT_CAPACITY (
	id NUMBER(38) not null primary key,
	tenant_id VARCHAR2(128) NOT NULL,
	quota NUMBER(10) DEFAULT 0 NOT NULL,
	usage NUMBER(10) DEFAULT 0 NOT NULL,
	max_size NUMBER(10) DEFAULT 0 NOT NULL,
	max_aggr_count NUMBER(10) DEFAULT 0 NOT NULL,
	max_aggr_size NUMBER(10) DEFAULT 0 NOT NULL,
	max_history_count NUMBER(10) DEFAULT 0 NOT NULL,
	gmt_create TIMESTAMP(6) default sysdate not null,
	gmt_modified TIMESTAMP(6) default sysdate not null
) ;

CREATE SEQUENCE tenant_capacity_id_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER tenant_capacity_tr BEFORE
insert ON TENANT_CAPACITY FOR EACH ROW
begin
select tenant_capacity_id_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE tenant_capacity IS '租户容量信息表';
COMMENT ON COLUMN tenant_capacity.max_aggr_size IS '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值';
COMMENT ON COLUMN tenant_capacity.quota IS '配额，0表示使用默认值';
COMMENT ON COLUMN tenant_capacity.usage IS '使用量';
COMMENT ON COLUMN tenant_capacity.max_aggr_count IS '聚合子配置最大个数';
COMMENT ON COLUMN tenant_capacity.gmt_create IS '创建时间';
COMMENT ON COLUMN tenant_capacity.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN tenant_capacity.gmt_modified IS '修改时间';
COMMENT ON COLUMN tenant_capacity.id IS '主键ID';
COMMENT ON COLUMN tenant_capacity.max_history_count IS '最大变更历史数量';
COMMENT ON COLUMN tenant_capacity.max_size IS '单个配置大小上限，单位为字节，0表示使用默认值';
CREATE UNIQUE INDEX uk_tenant_capacity_tenantid ON tenant_capacity (tenant_id);

CREATE TABLE TENANT_INFO (
	id  NUMBER(38) not null primary key,
	kp VARCHAR2(128) NOT NULL,
	tenant_id VARCHAR2(128),
	tenant_name VARCHAR2(128),
	tenant_desc VARCHAR2(256),
	create_source VARCHAR2(32),
	gmt_create NUMBER(38) not null,
	gmt_modified NUMBER(38) not null
) ;

CREATE SEQUENCE tenant_info_id_seq
 INCREMENT BY 1
     START WITH 1
     NOMAXVALUE
     NOCYCLE ;

CREATE TRIGGER tenant_info_tr BEFORE
insert ON TENANT_INFO FOR EACH ROW
begin
select tenant_info_id_seq.nextval into:New.id from dual;
end;
/
COMMENT ON TABLE tenant_info IS 'tenant_info';
COMMENT ON COLUMN tenant_info.tenant_name IS 'tenant_name';
COMMENT ON COLUMN tenant_info.create_source IS 'create_source';
COMMENT ON COLUMN tenant_info.tenant_desc IS 'tenant_desc';
COMMENT ON COLUMN tenant_info.gmt_create IS '创建时间';
COMMENT ON COLUMN tenant_info.tenant_id IS 'tenant_id';
COMMENT ON COLUMN tenant_info.kp IS 'kp';
COMMENT ON COLUMN tenant_info.gmt_modified IS '修改时间';
COMMENT ON COLUMN tenant_info.id IS 'id';
CREATE INDEX idx_tenant_id ON tenant_info (tenant_id);
CREATE UNIQUE INDEX uk_tenant_info_kptenantid ON tenant_info (kp,tenant_id,id);


CREATE TABLE users (
	username VARCHAR2(50) NOT NULL primary key,
	password VARCHAR2(500) NOT NULL,
	enabled NUMBER(10) NOT NULL
) ;

CREATE TABLE roles (
	username VARCHAR2(50) NOT NULL,
	role VARCHAR2(50) NOT NULL
) ;
CREATE UNIQUE INDEX idx_user_role ON roles (username,role);

CREATE TABLE permissions (
	role VARCHAR2(50) NOT NULL,
	"resource" VARCHAR2(255) NOT NULL,
	action VARCHAR2(8) NOT NULL
) ;
CREATE UNIQUE INDEX uk_role_permission ON permissions (role,"resource",action);


INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', 1);

INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');

