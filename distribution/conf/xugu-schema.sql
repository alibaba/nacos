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
/*   表名称 = config_info                  */
/******************************************/
CREATE TABLE `config_info`
(
    `id`                 bigint AUTO_INCREMENT NOT NULL  COMMENT 'id',
    `data_id`            varchar(255)  NOT NULL COMMENT 'data_id',
    `group_id`           varchar(128)           DEFAULT NULL COMMENT 'group_id',
    `content`            varchar      NOT NULL COMMENT 'content',
    `md5`                varchar(32)            DEFAULT NULL COMMENT 'md5',
    `gmt_create`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user`           varchar COMMENT 'source user',
    `src_ip`             varchar(50)            DEFAULT NULL COMMENT 'source ip',
    `app_name`           varchar(128)           DEFAULT NULL COMMENT 'app_name',
    `tenant_id`          varchar(128)           DEFAULT '' COMMENT '租户字段',
    `c_desc`             varchar(256)           DEFAULT NULL COMMENT 'configuration description',
    `c_use`              varchar(64)            DEFAULT NULL COMMENT 'configuration usage',
    `effect`             varchar(64)            DEFAULT NULL COMMENT '配置生效的描述',
    `type`               varchar(64)            DEFAULT NULL COMMENT '配置的类型',
    `c_schema`           varchar COMMENT '配置的模式',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_configinfo_datagrouptenant` UNIQUE  (`data_id`,`group_id`,`tenant_id`)
)COMMENT 'config_info';


/******************************************/
/*   表名称 = config_info  since 2.5.0                */
/******************************************/
CREATE TABLE `config_info_gray`
(
    `id`                 bigint AUTO_INCREMENT NOT NULL  COMMENT 'id',
    `data_id`            varchar(255) NOT NULL COMMENT 'data_id',
    `group_id`           varchar(128) NOT NULL COMMENT 'group_id',
    `content`            clob     NOT NULL COMMENT 'content',
    `md5`                varchar(32)           DEFAULT NULL COMMENT 'md5',
    `src_user`           clob COMMENT 'src_user',
    `src_ip`             varchar(100)          DEFAULT NULL COMMENT 'src_ip',
    `gmt_create`         datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'gmt_create',
    `gmt_modified`       datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'gmt_modified',
    `app_name`           varchar(128)          DEFAULT NULL COMMENT 'app_name',
    `tenant_id`          varchar(128)          DEFAULT '' COMMENT 'tenant_id',
    `gray_name`          varchar(128) NOT NULL COMMENT 'gray_name',
    `gray_rule`          clob         NOT NULL COMMENT 'gray_rule',
    `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT 'encrypted_data_key',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_configinfogray_datagrouptenantgray` UNIQUE (`data_id`,`group_id`,`tenant_id`,`gray_name`)
) COMMENT 'config_info_gray';

CREATE INDEX `idx_dataid_gmt_modified` ON `config_info_gray` (`data_id`,`gmt_modified`);

CREATE INDEX `idx_gmt_modified` ON `config_info_gray` (`gmt_modified`);

/******************************************/
/*   表名称 = config_tags_relation         */
/******************************************/
CREATE TABLE `config_tags_relation`
(
    `id`        bigint NOT NULL COMMENT 'id',
    `tag_name`  varchar(128) NOT NULL COMMENT 'tag_name',
    `tag_type`  varchar(64)  DEFAULT NULL COMMENT 'tag_type',
    `data_id`   varchar(255) NOT NULL COMMENT 'data_id',
    `group_id`  varchar(128) NOT NULL COMMENT 'group_id',
    `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
    `nid`       bigint AUTO_INCREMENT NOT NULL  COMMENT 'nid, 自增长标识',
    PRIMARY KEY (`nid`),
    CONSTRAINT `uk_configtagrelation_configidtag` UNIQUE(`id`,`tag_name`,`tag_type`)
) COMMENT 'config_tag_relation';

create index `idx_tenant_id` on `config_tags_relation` (`tenant_id`);

/******************************************/
/*   表名称 = group_capacity               */
/******************************************/
CREATE TABLE `group_capacity`
(
    `id`                bigint  AUTO_INCREMENT NOT NULL  COMMENT '主键ID',
    `group_id`          varchar(128) NOT NULL DEFAULT '' COMMENT 'Group ID，空字符表示整个集群',
    `quota`             int NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
    `usage`             int NOT NULL DEFAULT '0' COMMENT '使用量',
    `max_size`          int NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
    `max_aggr_count`    int NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数，，0表示使用默认值',
    `max_aggr_size`     int NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
    `max_history_count` int NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_group_id` UNIQUE  (`group_id`)
)COMMENT '集群、各Group容量信息表';

/******************************************/
/*   表名称 = his_config_info              */
/******************************************/
CREATE TABLE `his_config_info`
(
    `id`                 bigint  NOT NULL COMMENT 'id',
    `nid`                bigint  AUTO_INCREMENT NOT NULL  COMMENT 'nid, 自增标识',
    `data_id`            varchar(255)  NOT NULL COMMENT 'data_id',
    `group_id`           varchar(128)  NOT NULL COMMENT 'group_id',
    `app_name`           varchar(128)           DEFAULT NULL COMMENT 'app_name',
    `content`            clob      NOT NULL COMMENT 'content',
    `md5`                varchar(32)            DEFAULT NULL COMMENT 'md5',
    `gmt_create`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user`           clob COMMENT 'source user',
    `src_ip`             varchar(50)            DEFAULT NULL COMMENT 'source ip',
    `op_type`            char(10)               DEFAULT NULL COMMENT 'operation type',
    `tenant_id`          varchar(128)           DEFAULT '' COMMENT '租户字段',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '密钥',
    `publish_type`       varchar(50)            DEFAULT 'formal' COMMENT 'publish type gray or formal',
    `gray_name`          varchar(50)            DEFAULT NULL COMMENT 'gray name',
    `ext_info`           clob               DEFAULT NULL COMMENT 'ext info',
    PRIMARY KEY (`nid`)
)COMMENT '多租户改造';

create index  `idx_gmt_create`  on `his_config_info` (`gmt_create`);
create index  `idx_gmt_modified`  on `his_config_info` (`gmt_modified`);
create index  `idx_did`  on `his_config_info` (`data_id`);


/******************************************/
/*   表名称 = tenant_capacity              */
/******************************************/
CREATE TABLE `tenant_capacity`
(
    `id`                bigint AUTO_INCREMENT NOT NULL  COMMENT '主键ID',
    `tenant_id`         varchar(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
    `quota`             int NOT NULL DEFAULT '0' COMMENT '配额，0表示使用默认值',
    `usage`             int NOT NULL DEFAULT '0' COMMENT '使用量',
    `max_size`          int NOT NULL DEFAULT '0' COMMENT '单个配置大小上限，单位为字节，0表示使用默认值',
    `max_aggr_count`    int NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
    `max_aggr_size`     int NOT NULL DEFAULT '0' COMMENT '单个聚合数据的子配置大小上限，单位为字节，0表示使用默认值',
    `max_history_count` int NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tenant_id` UNIQUE   (`tenant_id`)
)COMMENT '租户容量信息表';


CREATE TABLE `tenant_info`
(
    `id`            bigint AUTO_INCREMENT NOT NULL  COMMENT 'id',
    `kp`            varchar(128) NOT NULL COMMENT 'kp',
    `tenant_id`     varchar(128) default '' COMMENT 'tenant_id',
    `tenant_name`   varchar(128) default '' COMMENT 'tenant_name',
    `tenant_desc`   varchar(256) DEFAULT NULL COMMENT 'tenant_desc',
    `create_source` varchar(32)  DEFAULT NULL COMMENT 'create_source',
    `gmt_create`    bigint NOT NULL COMMENT '创建时间',
    `gmt_modified`  bigint NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tenant_info_kptenantid` UNIQUE (`kp`,`tenant_id`)
) COMMENT 'tenant_info';

create index `idx_tenant_id` on `tenant_info` (`tenant_id`);


CREATE TABLE `users`
(
    `username` varchar(50)  NOT NULL PRIMARY KEY COMMENT 'username',
    `password` varchar(500) NOT NULL COMMENT 'password',
    `enabled`  boolean      NOT NULL COMMENT 'enabled'
);

CREATE TABLE `roles`
(
    `username` varchar(50) NOT NULL COMMENT 'username',
    `role`     varchar(50) NOT NULL COMMENT 'role'
);
CREATE UNIQUE INDEX `idx_user_role` on `roles` (`username` ASC, `role` ASC)  indextype is btree;

CREATE TABLE `permissions`
(
    `role`     varchar(50)  NOT NULL COMMENT 'role',
    `resource` varchar(128) NOT NULL COMMENT 'resource',
    `action`   varchar(8)   NOT NULL COMMENT 'action'
);
CREATE UNIQUE INDEX `uk_role_permission` on `permissions` (`role`,`resource`,`action`)   indextype is btree;

