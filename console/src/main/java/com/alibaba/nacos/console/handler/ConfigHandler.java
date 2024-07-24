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
     * Fetch the configuration.
     *
     * @param request  the HttpServletRequest object
     * @param response the HttpServletResponse object
     * @param dataId   the data ID
     * @param group    the group
     * @param tenant   the tenant
     * @param tag      the tag
     * @throws IOException, ServletException, NacosException in case of any errors
     */
    void getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group, String tenant,
            String tag) throws IOException, ServletException, NacosException;
    
    /**
     * Publish a configuration.
     *
     * @param request          the HttpServletRequest object
     * @param response         the HttpServletResponse object
     * @param dataId           the data ID
     * @param group            the group
     * @param tenant           the tenant
     * @param content          the content
     * @param tag              the tag
     * @param appName          the app name
     * @param srcUser          the source user
     * @param configTags       the config tags
     * @param desc             the description
     * @param use              the use
     * @param effect           the effect
     * @param type             the type
     * @param schema           the schema
     * @param encryptedDataKey the encrypted data key
     * @return true if the configuration is published successfully, false otherwise
     * @throws NacosException in case of any errors
     */
    boolean publishConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String content, String tag, String appName, String srcUser, String configTags, String desc,
            String use, String effect, String type, String schema, String encryptedDataKey) throws NacosException;
    
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
