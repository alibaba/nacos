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

const getI18N = (key, prefix = 'com.alibaba.nacos.page.serviceManagement.') => window.aliwareIntl.get(prefix + key)
export const I18N = {}
/**
 * 服务列表
 */
I18N.SERVICE_LIST = getI18N('service_list')
/**
 * 服务名称
 */
I18N.SERVICE_NAME = getI18N('service_name')
/**
 * 请输入服务名称
 */
I18N.ENTER_SERVICE_NAME = getI18N('please_enter_the_service_name')
/**
 * 查询
 */
I18N.QUERY = getI18N('query')
/**
 * 查询
 */
I18N.PUBNODEDATA = getI18N('pubnodata', '')
/**
 * 服务名
 */
I18N.COLUMN_SERVICE_NAME = getI18N('table.column.service_name')
/**
 * 集群数目
 */
I18N.COLUMN_CLUSTER_COUNT = getI18N('table.column.cluster_count')
/**
 * IP数目
 */
I18N.COLUMN_IP_COUNT = getI18N('table.column.ip_count')
/**
 * 健康程度
 */
I18N.COLUMN_HEALTH_STATUS = getI18N('table.column.health_status')
/**
 * 操作
 */
I18N.COLUMN_OPERATION = getI18N('table.column.operation')
/**
 * 详情
 */
I18N.DETAIL = getI18N('detail')

export const STATUS_COLOR_MAPPING = {
    优: 'green',
    良: 'light-green',
    中: 'orange',
    差: 'red'
}
