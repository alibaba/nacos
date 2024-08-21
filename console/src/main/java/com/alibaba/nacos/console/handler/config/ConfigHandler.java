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

package com.alibaba.nacos.console.handler.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.persistence.model.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for handling configuration operations.
 *
 * @author zhangyukun
 */
public interface ConfigHandler {
    
    /**
     * Retrieves the configuration based on the specified parameters.
     *
     * @param pageNo            The page number for pagination.
     * @param pageSize          The number of items per page.
     * @param dataId            The identifier of the configuration data.
     * @param group             The group to which the configuration belongs.
     * @param namespaceId       The namespace identifier.
     * @param configAdvanceInfo Additional advanced search criteria.
     * @throws IOException      If an input or output exception occurs.
     * @throws ServletException If a servlet-specific exception occurs.
     * @throws NacosException   If an error related to Nacos configuration occurs.
     */
    Page<ConfigInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws IOException, ServletException, NacosException;
    
    /**
     * Retrieves detailed information about a specific configuration.
     *
     * @param dataId      The identifier of the configuration data.
     * @param group       The group to which the configuration belongs.
     * @param namespaceId The namespace identifier.
     * @return A ConfigAllInfo object containing all details of the specified configuration.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    ConfigAllInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException;
    
    /**
     * Publishes a new configuration or updates an existing configuration.
     *
     * @param configForm        The form object containing configuration details.
     * @param configRequestInfo Additional request information related to the configuration.
     * @return A Boolean indicating whether the publish operation was successful.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException;
    
    /**
     * Deletes a specific configuration.
     *
     * @param dataId      The identifier of the configuration data to delete.
     * @param group       The group to which the configuration belongs.
     * @param namespaceId The namespace identifier.
     * @param tag         The tag associated with the configuration.
     * @param clientIp    The IP address of the client requesting the deletion.
     * @param srcUser     The source user requesting the deletion.
     * @return A Boolean indicating whether the deletion was successful.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp, String srcUser)
            throws NacosException;
    
    /**
     * Deletes multiple configurations based on their IDs.
     *
     * @param ids      A list of IDs of the configurations to delete.
     * @param clientIp The IP address of the client requesting the deletion.
     * @param srcUser  The source user requesting the deletion.
     * @return A Boolean indicating whether the deletion was successful.
     */
    Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser);
    
    /**
     * Exports the configuration based on the specified parameters.
     *
     * @param dataId      The identifier of the configuration data.
     * @param group       The group to which the configuration belongs.
     * @param namespaceId The namespace identifier.
     * @param appName     The application name associated with the configuration.
     * @param ids         A list of IDs of the configurations to export.
     * @return A ResponseEntity containing the exported configuration as a byte array.
     * @throws Exception If an unexpected error occurs during the export process.
     */
    ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName, List<Long> ids)
            throws Exception;
    
    /**
     * Exports the configuration with metadata based on the specified parameters.
     *
     * @param dataId      The identifier of the configuration data.
     * @param group       The group to which the configuration belongs.
     * @param namespaceId The namespace identifier.
     * @param appName     The application name associated with the configuration.
     * @param ids         A list of IDs of the configurations to export.
     * @return A ResponseEntity containing the exported configuration as a byte array.
     * @throws Exception If an unexpected error occurs during the export process.
     */
    ResponseEntity<byte[]> exportConfigV2(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception;
    
    /**
     * Searches for configurations based on detailed criteria.
     *
     * @param search            The search keyword.
     * @param pageNo            The page number for pagination.
     * @param pageSize          The number of items per page.
     * @param dataId            The identifier of the configuration data.
     * @param group             The group to which the configuration belongs.
     * @param namespaceId       The namespace identifier.
     * @param configAdvanceInfo Additional advanced search criteria.
     * @return A Page object containing a list of ConfigInfo that matches the search criteria.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    Page<ConfigInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException;
    
    /**
     * Retrieves the status of listeners for a specific configuration.
     *
     * @param dataId      The identifier of the configuration data.
     * @param group       The group to which the configuration belongs.
     * @param namespaceId The namespace identifier.
     * @param sampleTime  The sampling time for listener status.
     * @return A GroupkeyListenserStatus object containing the status of the listeners.
     * @throws Exception If an unexpected error occurs.
     */
    GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception;
    
    /**
     * Imports and publishes a configuration from a file.
     *
     * @param srcUser      The source user performing the import.
     * @param namespaceId  The namespace identifier.
     * @param policy       The policy for handling existing configurations.
     * @param file         The file containing the configuration to import.
     * @param srcIp        The IP address of the source.
     * @param requestIpApp The IP address of the requester.
     * @return A Result object containing the status and additional information about the operation.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId, SameConfigPolicy policy,
            MultipartFile file, String srcIp, String requestIpApp) throws NacosException;
    
    
    /**
     * Clones an existing configuration to a different namespace.
     *
     * @param srcUser         The source user performing the clone operation.
     * @param namespaceId     The namespace identifier where the configuration will be cloned to.
     * @param configBeansList A list of configurations to be cloned.
     * @param policy          The policy for handling existing configurations in the target namespace.
     * @param srcIp           The IP address of the source.
     * @param requestIpApp    The IP address of the requester.
     * @return A Result object containing the status and additional information about the operation.
     * @throws NacosException If an error related to Nacos configuration occurs.
     */
    Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException;
}
