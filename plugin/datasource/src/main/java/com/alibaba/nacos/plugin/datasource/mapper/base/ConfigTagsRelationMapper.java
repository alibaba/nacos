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

/**
 * The mapper of the relationship between configuration and tag.
 *
 * @author hyx
 **/

public interface ConfigTagsRelationMapper {
    /**
     * Add configuration; database atomic operation, minimum sql action, no business encapsulation.
     *
     * @param configId id
     * @param tagName  tag
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     * @return success or not.
     */
    boolean addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant);
    
    /**
     * Add configuration; database atomic operation.
     *
     * @param configId   config id
     * @param configTags tags
     * @param dataId     dataId
     * @param group      group
     * @param tenant     tenant
     * @return success or not.
     */
    boolean addConfigTagsRelation(long configId, String configTags, String dataId, String group, String tenant);
    
    /**
     * Delete tag.
     *
     * @param id id
     * @return the number of remove tags.
     */
    Integer removeTagByIdAtomic(long id);
}
