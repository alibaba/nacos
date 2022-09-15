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

public interface ConfigTagsRelationMapper {
    
    /**
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.group_id=? AND a.tenant_id=?
     * AND b.tag_name IN (...) ...;
     *
     * @param configAdvanceInfo advance info
     * @return * Query configuration information based on dataId and group.
     */
    String findConfigInfoAdvanceInfo(Map<String, Object> configAdvanceInfo);
    
    /**
     * Get the count of relations.
     * The default sql:
     * SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id=? ...
     *
     * @param configAdvanceInfo advance info
     * @return The sql of getting the count of relations.
     */
    String findConfigInfoByDataIdAndAdvanceCountRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Find config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.id WHERE a.data_id=? AND a.tenant_id AND b.tag_name IN (...) ...
     *
     * @param configAdvanceInfo advance info
     * @return The sql of finding config info.
     */
    String findConfigInfoByDataIdAndAdvanceFetchRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Get the count of config info.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE ...
     *
     * @param tagNameSize The size of tag name.
     * @return The sql of get config info.
     */
    String findConfigInfo4PageCountRows(int tagNameSize);
    
    /**
     * Find config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN
     * config_tags_relation b ON a.id=b.i ...
     *
     * @param configAdvanceInfo advance info
     * @return The sql of finding config info.
     */
    String findConfigInfo4PageFetchRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Get the count of config information by group id and tenant id and tag name.
     * The default sql:
     * SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id=? AND b.tag_name IN (...)
     *
     * @param configAdvanceInfo advance info
     * @return The sql of querying configuration information.
     */
    String findConfigInfoByGroupAndAdvanceCountRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Query configuration information.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content FROM config_info  a LEFT JOIN "
     * config_tags_relation b ON a.id=b.id WHERE a.tenant_id=? AND b.tag_name IN (...) ...
     *
     * @param configAdvanceInfo advance info
     * @return The sql of querying configuration information.
     */
    String findConfigInfoByGroupAndAdvanceFetchRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Get the count of config information by config tags relation.
     * The default sql:
     * SELECT count(*) FROM config_info  a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param configAdvanceInfo advance info
     * @return The sql of getting the count of config information.
     */
    String findConfigInfoLike4PageCountRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Query config info.
     * The default sql:
     * SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content
     * FROM config_info a LEFT JOIN config_tags_relation b ON a.id=b.id
     *
     * @param configAdvanceInfo advance info
     * @return The sql of querying config info.
     */
    String findConfigInfoLike4PageFetchRows(Map<String, Object> configAdvanceInfo);
    
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     * The default sql:
     * INSERT INTO config_tags_relation(id,tag_name,tag_type,data_id,group_id,tenant_id) VALUES(?,?,?,?,?,?)
     *
     * @return The sql of adding configuration.
     */
    String addConfigTagRelationAtomic();
    
    /**
     * Delete tag.
     * The default sql:
     * DELETE FROM config_tags_relation WHERE id=?
     *
     * @return The sql of deleting tag.
     */
    String removeTagByIdAtomic();
    
    /**
     * Query config tag list.
     * The default sql:
     * SELECT tag_name FROM config_tags_relation WHERE tenant_id = ?
     *
     * @return The sql of querying config tag list
     */
    String getConfigTagsByTenant();
    
    /**
     * Query tag list.
     * The default sql:
     * SELECT tag_name FROM config_tags_relation WHERE data_id=? AND group_id=? AND tenant_id = ?
     *
     * @return The sql of querying tag list
     */
    String selectTagByConfig();
}
