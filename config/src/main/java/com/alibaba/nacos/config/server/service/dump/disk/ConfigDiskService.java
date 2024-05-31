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

package com.alibaba.nacos.config.server.service.dump.disk;

import java.io.IOException;

/**
 * config disk service.
 *
 * @author zunfei.lzf
 */
public interface ConfigDiskService {
    
    /**
     * Save configuration information to disk.
     *
     * @param dataId  dataId.
     * @param group   group.
     * @param tenant  tenant.
     * @param content content.
     * @throws IOException io exception.
     */
    void saveToDisk(String dataId, String group, String tenant, String content) throws IOException;
    
    /**
     * Save beta information to disk.
     *
     * @param dataId  dataId.
     * @param group   group.
     * @param tenant  tenant.
     * @param content content.
     * @throws IOException io exception.
     */
    void saveBetaToDisk(String dataId, String group, String tenant, String content) throws IOException;

    /**
     * Save tag information to disk.
     *
     * @param dataId  dataId.
     * @param group   group.
     * @param tenant  tenant.
     * @param tag     tag.
     * @param content content.
     * @throws IOException io exception.
     */
    void saveTagToDisk(String dataId, String group, String tenant, String tag, String content) throws IOException;
    
    /**
     * Deletes configuration files on disk.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     */
    void removeConfigInfo(String dataId, String group, String tenant);
    
    /**
     * Deletes beta configuration files on disk.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     */
    void removeConfigInfo4Beta(String dataId, String group, String tenant);
    
    /**
     * Deletes tag configuration files on disk.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param tag    tag.
     */
    void removeConfigInfo4Tag(String dataId, String group, String tenant, String tag);
    
    /**
     * Returns the content of the  cache file in server.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @return content null if not exist.
     * @throws IOException io exception.
     */
    String getContent(String dataId, String group, String tenant) throws IOException;
    
    /**
     * Returns the beta content of cache file in server.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @return content, null if not exist.
     * @throws IOException io exception.
     */
    String getBetaContent(String dataId, String group, String tenant) throws IOException;
    
    /**
     * Returns the path of the tag cache file in server.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param tag    tag.
     * @return tag content, null if not exist.
     * @throws IOException io exception.
     */
    String getTagContent(String dataId, String group, String tenant, String tag) throws IOException;
    
    /**
     * Clear all config file.
     */
    void clearAll();
    
    /**
     * Clear all beta config file.
     */
    void clearAllBeta();
    
    /**
     * Clear all tag config file.
     */
    void clearAllTag();
    
}
