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
}
