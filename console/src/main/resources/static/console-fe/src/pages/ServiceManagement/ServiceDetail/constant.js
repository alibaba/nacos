/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { aliwareIntl } from '../../../globalLib';

const getI18N = (key, prefix = 'com.alibaba.nacos.page.serviceDetail.') =>
  aliwareIntl.get(prefix + key);
export const I18N = {};
/**
 * 服务列表
 */
I18N.SERVICE_DETAILS = getI18N('service_details');
/**
 * 编辑服务
 */
I18N.EDIT_SERVICE = getI18N('edit_service');
/**
 * 返回
 */
I18N.BACK = getI18N('back');
/**
 * 服务名
 */
I18N.SERVICE_NAME = getI18N('service_name');
/**
 * 保护阀值
 */
I18N.PROTECT_THRESHOLD = getI18N('protect_threshold');
/**
 * 健康检查模式
 */
I18N.HEALTH_CHECK_PATTERN = getI18N('health_check_pattern');
/**
 * 健康检查模式 - 服务端
 */
I18N.HEALTH_CHECK_PATTERN_SERVICE = getI18N('health_check_pattern.service');
/**
 * 健康检查模式 - 客户端
 */
I18N.HEALTH_CHECK_PATTERN_CLIENT = getI18N('health_check_pattern.client');
/**
 * 健康检查模式 - 禁止
 */
I18N.HEALTH_CHECK_PATTERN_NONE = getI18N('health_check_pattern.none');
/**
 * 元数据
 */
I18N.METADATA = getI18N('metadata');
/**
 * 更新服务
 */
I18N.UPDATE_SERVICE = getI18N('update_service');
/**
 * 创建服务
 */
I18N.CREATE_SERVICE = getI18N('create_service');
/**
 * 集群
 */
I18N.CLUSTER = getI18N('cluster');
/**
 * 端口
 */
I18N.PORT = getI18N('port');
/**
 * 权重
 */
I18N.WEIGHT = getI18N('weight');
/**
 * 健康状态
 */
I18N.HEALTHY = getI18N('healthy');
/**
 * 操作
 */
I18N.OPERATION = getI18N('operation');
/**
 * 编辑
 */
I18N.EDITOR = getI18N('editor');
/**
 * 上线
 */
I18N.ONLINE = getI18N('online');
/**
 * 下线
 */
I18N.OFFLINE = getI18N('offline');
/**
 * 集群配置
 */
I18N.EDIT_CLUSTER = getI18N('edit_cluster');
/**
 * 检查类型
 */
I18N.CHECK_TYPE = getI18N('check_type');
/**
 * 检查端口
 */
I18N.CHECK_PORT = getI18N('check_port');
/**
 * 使用IP端口检查
 */
I18N.USE_IP_PORT_CHECK = getI18N('use_ip_port_check');
/**
 * 检查路径
 */
I18N.CHECK_PATH = getI18N('check_path');
/**
 * 检查头
 */
I18N.CHECK_HEADERS = getI18N('check_headers');
/**
 * 更新集群
 */
I18N.UPDATE_CLUSTER = getI18N('update_cluster');
/**
 * 编辑实例
 */
I18N.UPDATE_INSTANCE = getI18N('update_instance');
/**
 * 是否上线
 */
I18N.WHETHER_ONLINE = getI18N('whether_online');

export const DIALOG_FORM_LAYOUT = {
  labelCol: { fixedSpan: 12 },
  wrapperCol: { span: 12 },
};

export const HEALTHY_COLOR_MAPPING = {
  true: 'green',
  false: 'red',
};
