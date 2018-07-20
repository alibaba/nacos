CREATE TABLE `app_list` (
 `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
 `app_name` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'app_name',
 `is_dynamic_collect_disabled` BIT(1) DEFAULT 0,
 `last_sub_info_collected_time` datetime DEFAULT '1970-01-01 08:00:00.0',
 `sub_info_lock_owner` varchar(128) COLLATE utf8_bin COMMENT 'lock owner',
 `sub_info_lock_time` datetime DEFAULT '1970-01-01 08:00:00.0',
 PRIMARY KEY (`id`),
 UNIQUE KEY `uk_appname` (`app_name`)
) ENGINE=InnoDB AUTO_INCREMENT=65535 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='application list';

CREATE TABLE `app_configdata_relation_subs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `app_name` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'app_name',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `gmt_modified` datetime DEFAULT '2010-05-05 00:00:00' COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_sub_config_datagroup` (`app_name`,`data_id`,`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=565666 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='app_configdata_relation_subs';	


CREATE TABLE `app_configdata_relation_pubs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `app_name` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'app_name',
  `data_id` varchar(255) COLLATE utf8_bin NOT NULL COMMENT 'data_id',
  `group_id` varchar(128) COLLATE utf8_bin NOT NULL COMMENT 'group_id',
  `gmt_modified` datetime DEFAULT '2010-05-05 00:00:00' COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_pub_config_datagroup` (`app_name`,`data_id`,`group_id`)
) ENGINE=InnoDB AUTO_INCREMENT=565666 DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='app_configdata_relation_pubs';	