/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Interface for handling configuration operations.
 *
 * @author zhangyukun
 */
public interface ConfigHandler {
    
    /**
     * Fetches the configuration.
     *
     * @param request  the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @param dataId   the data ID
     * @param group    the group
     * @param tenant   the tenant
     * @param tag      the tag
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     * @throws NacosException   if a Nacos error occurs
     */
    void getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group, String tenant,
            String tag) throws IOException, ServletException, NacosException;
    
    /**
     * Publish a configuration.
     *
     * @return true if the configuration is published successfully, false otherwise
     * @throws NacosException in case of any errors
     */
    boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo, String encryptedDataKey) throws NacosException;
    
    /**
     * Delete a configuration.
     *
     * @param request  the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @param dataId   the data ID
     * @param group    the group
     * @param tenant   the tenant
     * @param tag      the tag
     * @return true if the configuration is deleted successfully, false otherwise
     * @throws NacosException in case of any errors
     */
    boolean deleteConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag) throws NacosException;
    
    /**
     * Fetch detailed configuration information.
     *
     * @param dataId the data ID
     * @param group  the group
     * @param tenant the tenant
     * @return the detailed configuration information
     * @throws NacosException in case of any errors
     */
    ConfigAllInfo detailConfigInfo(String dataId, String group, String tenant) throws NacosException;
}
