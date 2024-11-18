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
     * Save gray information to disk.
     *
     * @param dataId  dataId.
     * @param group   group.
     * @param tenant  tenant.
     * @param grayName grayName.
     * @param content content.
     * @throws IOException io exception.
     */
    void saveGrayToDisk(String dataId, String group, String tenant, String grayName, String content) throws IOException;
    
    /**
     * Deletes gray configuration files on disk.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param grayName grayName.
     */
    void removeConfigInfo4Gray(String dataId, String group, String tenant, String grayName);
    
    /**
     * Returns the content of the gray cache file in server.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     * @param grayName grayName.
     * @return gray content, null if not exist.
     * @throws IOException io exception.
     */
    String getGrayContent(String dataId, String group, String tenant, String grayName) throws IOException;
    
    /**
     * Deletes configuration files on disk.
     *
     * @param dataId dataId.
     * @param group  group.
     * @param tenant tenant.
     */
    void removeConfigInfo(String dataId, String group, String tenant);
    
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
     * Clear all config file.
     */
    void clearAll();
    
    /**
     * Clear all gray config file.
     */
    void clearAllGray();
    
}
