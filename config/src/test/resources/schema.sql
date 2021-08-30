-- config info 配置信息
CREATE TABLE config_info
(
  id           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  data_id      varchar(255) NOT NULL COMMENT 'data_id',
  group_id     varchar(255) DEFAULT NULL,
  content      longtext     NOT NULL COMMENT 'content',
  md5          varchar(32)  DEFAULT NULL COMMENT 'md5',
  gmt_create   datetime     NOT NULL COMMENT '创建时间',
  gmt_modified datetime     NOT NULL COMMENT '修改时间',
  src_user     text COMMENT 'source user',
  src_ip       varchar(50)  DEFAULT NULL COMMENT 'source ip',
  app_name     varchar(128) DEFAULT NULL,
  tenant_id    varchar(128) DEFAULT '' COMMENT '租户字段',
  c_desc       varchar(256) DEFAULT NULL,
  c_use        varchar(64)  DEFAULT NULL,
  effect       varchar(64)  DEFAULT NULL,
  type         varchar(64)  DEFAULT NULL,
  c_schema     text,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfo_datagrouptenant (data_id, group_id, tenant_id)
);

-- 集合配置信息
CREATE TABLE config_info_aggr
(
  id           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  data_id      varchar(255) NOT NULL COMMENT 'data_id',
  group_id     varchar(255) NOT NULL COMMENT 'group_id',
  datum_id     varchar(255) NOT NULL COMMENT 'datum_id',
  content      longtext     NOT NULL COMMENT '内容',
  gmt_modified datetime     NOT NULL COMMENT '修改时间',
  app_name     varchar(128) DEFAULT NULL,
  tenant_id    varchar(128) DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfoaggr_datagrouptenantdatum (data_id, group_id, tenant_id, datum_id)
);

-- Beta配置信息
CREATE TABLE config_info_beta
(
  id           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  data_id      varchar(255) NOT NULL COMMENT 'data_id',
  group_id     varchar(128) NOT NULL COMMENT 'group_id',
  app_name     varchar(128)  DEFAULT NULL COMMENT 'app_name',
  content      longtext     NOT NULL COMMENT 'content',
  beta_ips     varchar(1024) DEFAULT NULL COMMENT 'betaIps',
  md5          varchar(32)   DEFAULT NULL COMMENT 'md5',
  gmt_create   datetime     NOT NULL COMMENT '创建时间',
  gmt_modified datetime     NOT NULL COMMENT '修改时间',
  src_user     text COMMENT 'source user',
  src_ip       varchar(50)   DEFAULT NULL COMMENT 'source ip',
  tenant_id    varchar(128)  DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfobeta_datagrouptenant (data_id, group_id, tenant_id)
);

-- 标签配置信息
CREATE TABLE config_info_tag
(
  id           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  data_id      varchar(255) NOT NULL COMMENT 'data_id',
  group_id     varchar(128) NOT NULL COMMENT 'group_id',
  tenant_id    varchar(128) DEFAULT '' COMMENT 'tenant_id',
  tag_id       varchar(128) NOT NULL COMMENT 'tag_id',
  app_name     varchar(128) DEFAULT NULL COMMENT 'app_name',
  content      longtext     NOT NULL COMMENT 'content',
  md5          varchar(32)  DEFAULT NULL COMMENT 'md5',
  gmt_create   datetime     NOT NULL COMMENT '创建时间',
  gmt_modified datetime     NOT NULL COMMENT '修改时间',
  src_user     text COMMENT 'source user',
  src_ip       varchar(50)  DEFAULT NULL COMMENT 'source ip',
  PRIMARY KEY (id),
  UNIQUE KEY uk_configinfotag_datagrouptenanttag (data_id, group_id, tenant_id, tag_id)
);

-- 配置信息和标签关系
CREATE TABLE config_tags_relation
(
  id        bigint(20)   NOT NULL COMMENT 'id',
  tag_name  varchar(128) NOT NULL COMMENT 'tag_name',
  tag_type  varchar(64)  DEFAULT NULL COMMENT 'tag_type',
  data_id   varchar(255) NOT NULL COMMENT 'data_id',
  group_id  varchar(128) NOT NULL COMMENT 'group_id',
  tenant_id varchar(128) DEFAULT '' COMMENT 'tenant_id',
  nid       bigint(20)   NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (nid),
  UNIQUE KEY uk_configtagrelation_configidtag (id, tag_name, tag_type),
  KEY idx_tenant_id (tenant_id)
);

-- 分组容量
CREATE TABLE group_capacity
(
  id                bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  group_id          varchar(128)        NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
  quota             int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
  usage             int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '使用量',
  max_size          int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
  max_aggr_count    int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，，0表示使用默认值',
  max_aggr_size     int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
  max_history_count int(10) unsigned    NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
  gmt_create        datetime            NOT NULL COMMENT '创建时间',
  gmt_modified      datetime            NOT NULL COMMENT '修改时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_id (group_id)
);

-- 配置历史信息
CREATE TABLE his_config_info
(
  id           bigint(64) unsigned NOT NULL,
  nid          bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  data_id      varchar(255)        NOT NULL,
  group_id     varchar(128)        NOT NULL,
  app_name     varchar(128) DEFAULT NULL COMMENT 'app_name',
  content      longtext            NOT NULL,
  md5          varchar(32)  DEFAULT NULL,
  gmt_create   datetime,
  gmt_modified datetime,
  src_user     text,
  src_ip       varchar(50)  DEFAULT NULL,
  op_type      char(10)     DEFAULT NULL,
  tenant_id    varchar(128) DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (nid),
  KEY idx_gmt_create (gmt_create),
  KEY idx_gmt_modified (gmt_modified),
  KEY idx_did (data_id)
);

