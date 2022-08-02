/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.base;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;

import java.sql.Timestamp;
import java.util.List;

/**
 * The config info tags.
 *
 * @author hyx
 **/

public interface ConfigInfoTagMapper extends BaseMapper<ConfigInfo4Tag> {
    
    /**
     * Update tag configuration information.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @return success or not.
     */
    boolean updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time);
    
    /**
     * insert or update tag config cas.
     *
     * @param configInfo config info
     * @param tag        tag
     * @param srcIp      remote ip
     * @param srcUser    user
     * @param time       time
     * @return success or not.
     */
    boolean insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time);
    
    /**
     * Query tag configuration information based on dataId and group.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @param tag    tag
     * @return {@link ConfigInfo4Tag}
     */
    ConfigInfoTagWrapper findConfigInfo4Tag(final String dataId, final String group, final String tenant, final String tag);
    
    /**
     * Query config tag list.
     *
     * @param tenant tenant
     * @return config tag list
     */
    List<String> getConfigTagsByTenant(String tenant);
    
    /**
     * Query tag list.
     *
     * @param dataId data id
     * @param group  group
     * @param tenant tenant
     * @return tag list
     */
    List<String> selectTagByConfig(String dataId, String group, String tenant);
    
    /**
     * Delete configuration; database atomic operation, minimum SQL action, no business encapsulation.
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param tag     tag
     * @param srcIp   remote ip
     * @param srcUser user
     * @return remove tag.
     */
    boolean removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser);
}
