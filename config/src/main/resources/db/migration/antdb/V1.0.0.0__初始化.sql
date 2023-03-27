CREATE TABLE config_info (
	id bigserial NOT NULL,
	data_id varchar(255) NOT NULL,
	group_id varchar(255),
	content text NOT NULL,
	md5 varchar(32),
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	src_user text,
	src_ip varchar(20),
	app_name varchar(128),
	tenant_id varchar(128),
	c_desc varchar(256),
	c_use varchar(64),
	effect varchar(64),
	type varchar(64),
	c_schema text,
	PRIMARY KEY (id),
	UNIQUE (data_id,group_id,tenant_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE config_info_aggr (
	id bigserial NOT NULL,
	data_id varchar(255) NOT NULL,
	group_id varchar(255) NOT NULL,
	datum_id varchar(255) NOT NULL,
	content text NOT NULL,
	gmt_modified timestamp without time zone NOT NULL,
	app_name varchar(128),
	tenant_id varchar(128),
	PRIMARY KEY (id),
	UNIQUE (data_id,group_id,tenant_id,datum_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE config_info_beta (
	id bigserial NOT NULL,
	data_id varchar(255) NOT NULL,
	group_id varchar(128) NOT NULL,
	app_name varchar(128),
	content text NOT NULL,
	beta_ips varchar(1024),
	md5 varchar(32),
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	src_user text,
	src_ip varchar(20),
	tenant_id varchar(128),
	PRIMARY KEY (id),
	UNIQUE (data_id,group_id,tenant_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE config_info_tag (
	id bigserial NOT NULL,
	data_id varchar(255) NOT NULL,
	group_id varchar(128) NOT NULL,
	tenant_id varchar(128),
	tag_id varchar(128) NOT NULL,
	app_name varchar(128),
	content text NOT NULL,
	md5 varchar(32),
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	src_user text,
	src_ip varchar(20),
	PRIMARY KEY (id),
	UNIQUE (data_id,group_id,tenant_id,tag_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE config_tags_relation (
	id bigint NOT NULL,
	tag_name varchar(128) NOT NULL,
	tag_type varchar(64),
	data_id varchar(255) NOT NULL,
	group_id varchar(128) NOT NULL,
	tenant_id varchar(128),
	nid bigserial,
	PRIMARY KEY (nid),
	UNIQUE (id,tag_name,tag_type)
) DISTRIBUTE BY REPLICATION;
CREATE INDEX idx_ctr_tenant_id ON config_tags_relation (tenant_id);

CREATE TABLE group_capacity (
	id bigserial NOT NULL,
	group_id varchar(128) NOT NULL,
	quota integer NOT NULL DEFAULT 0,
	usage integer NOT NULL DEFAULT 0,
	max_size integer NOT NULL DEFAULT 0,
	max_aggr_count integer NOT NULL DEFAULT 0,
	max_aggr_size integer NOT NULL DEFAULT 0,
	max_history_count integer NOT NULL DEFAULT 0,
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	UNIQUE (group_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE his_config_info (
	id bigint NOT NULL,
	nid bigserial,
	data_id varchar(255) NOT NULL,
	group_id varchar(128) NOT NULL,
	app_name varchar(128),
	content text NOT NULL,
	md5 varchar(32),
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	src_user text,
	src_ip varchar(20),
	op_type char(10),
	tenant_id varchar(128),
	PRIMARY KEY (nid)
) DISTRIBUTE BY REPLICATION;
CREATE INDEX idx_did ON his_config_info (data_id);
CREATE INDEX idx_gmt_create ON his_config_info (gmt_create);
CREATE INDEX idx_gmt_modified ON his_config_info (gmt_modified);

CREATE TABLE permissions (
	role varchar(50) NOT NULL,
	resource varchar(255) NOT NULL,
	action varchar(8) NOT NULL,
	UNIQUE (role,resource,action)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE roles (
	username varchar(50) NOT NULL,
	role varchar(50) NOT NULL,
	UNIQUE (username,role)
) DISTRIBUTE BY REPLICATION;
CREATE UNIQUE INDEX idx_user_role ON roles (username,role);

CREATE TABLE tenant_capacity (
	id bigserial NOT NULL,
	tenant_id varchar(128) NOT NULL,
	quota integer NOT NULL DEFAULT 0,
	usage integer NOT NULL DEFAULT 0,
	max_size integer NOT NULL DEFAULT 0,
	max_aggr_count integer NOT NULL DEFAULT 0,
	max_aggr_size integer NOT NULL DEFAULT 0,
	max_history_count integer NOT NULL DEFAULT 0,
	gmt_create timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	gmt_modified timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (id),
	UNIQUE (tenant_id)
) DISTRIBUTE BY REPLICATION;

CREATE TABLE tenant_info (
	id bigserial NOT NULL,
	kp varchar(128) NOT NULL,
	tenant_id varchar(128),
	tenant_name varchar(128),
	tenant_desc varchar(256),
	create_source varchar(32),
	gmt_create bigint NOT NULL,
	gmt_modified bigint NOT NULL,
	PRIMARY KEY (id),
	UNIQUE (kp,tenant_id)
) DISTRIBUTE BY REPLICATION;
CREATE INDEX idx_tenant_id ON tenant_info (tenant_id);

CREATE TABLE users (
	username varchar(50) NOT NULL,
	password varchar(500) NOT NULL,
	enabled smallint NOT NULL,
	PRIMARY KEY (username)
) DISTRIBUTE BY REPLICATION;

INSERT INTO users (username, password, enabled) VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', 1);
INSERT INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');

