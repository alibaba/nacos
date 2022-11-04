/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper;

import java.util.Map;

/**
 * The config with tags mapper.
 *
 * @author hyx
 **/

public interface ConfigTagsRelationMapper extends Mapper {
    
    
    /**
     * Get the count of relations.
     * The default sql:
     * SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id=? ...
     *
     * @param params The map of dataId and tenantId.
     * @param tagSize the tags name size.
     * @return The sql of getting the count of relations.
     */
    String findConfigInfoByDataIdAndAdvanceCountRows(Map<String, String> params, int tagSize);
    
    /**
     * Find config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id AND b.tag_name IN (...) ...
     *
     * @param params The map of appName.
     * @param tagSize the tags name size.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of finding config info.
     */
    String findConfigInfoByDataIdAndAdvanceFetchRows(Map<String, String> params, int tagSize, int startRow, int pageSize);
    
    /**
     * Get the count of config info.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param params The map of params, the key is the parameter name(dataId, groupId, tenantId, appName, startTime, endTime, content),
     *                the value is the key's value.
     * @param tagSize the tags name size.
     * @return The sql of get config info.
     */
    String findConfigInfo4PageCountRows(final Map<String, String> params, int tagSize);
    
    /**
     * Find config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.i ...
     *
     * @param params The keys and values are dataId and group.
     * @param tagSize the tags name size.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of finding config info.
     */
    String findConfigInfo4PageFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize);
    
    /**
     * Get the count of config information by group id and tenant id and tag name.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? AND b.tag_name IN (...)
     *
     * @param params The keys and values are dataId and group.
     * @param tagSize the tags name size.
     * @return The sql of querying configuration information.
     */
    String findConfigInfoByGroupAndAdvanceCountRows(final Map<String, String> params, int tagSize);
    
    /**
     * Query configuration information.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
     * config_tags_relation b ON a.id=b.id WHERE a.tenant_id=? AND b.tag_name IN (...) ...
     *
     * @param params the keys and values are dataId and group.
     * @param tagSize the tags name size.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying configuration information.
     */
    String findConfigInfoByGroupAndAdvanceFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize);
    
    /**
     * Get the count of config information by config tags relation.
     * The default sql:
     * SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param params the keys and values are dataId and group.
     * @param tagSize the tags name size.
     * @return The sql of getting the count of config information.
     */
    String findConfigInfoLike4PageCountRows(final Map<String, String> params, int tagSize);
    
    /**
     * Query config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content
     * FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param params the keys and values are dataId and group.
     * @param tagSize the tags name size.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying config info.
     */
    String findConfigInfoLike4PageFetchRows(final Map<String, String> params, int tagSize, int startRow, int pageSize);
    
    /**
     * The number of config.
     * The default sql:
     * SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id=?
     *
     * @param params The map of appName.
     * @param tagSize The size of tags.
     * @return The number of config.
     */
    String findConfigInfoByAdvanceCountRows(Map<String, String> params, int tagSize);
    
    /**
     * Query configuration information.
     * The default sql:
     * SELECT count(*) FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.tenant_id=?
     *
     * @param params The map of appName.
     * @param tagSize The size of tags.
     * @param startRow The start index.
     * @param pageSize The size of page.
     * @return The sql of querying configuration information.
     */
    String findConfigInfoByAdvanceFetchRows(Map<String, String> params, int tagSize, int startRow, int pageSize);
    
    /**
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.group_id=? AND a.tenant_id=?
     * AND b.tag_name IN (...) ...;
     *
     * @param params The map of appName.
     * @param tagSize the tags name size.
     * @return * Query configuration information based on dataId and group.
     */
    String findConfigInfoAdvanceInfo(Map<String, String> params, int tagSize);
}
