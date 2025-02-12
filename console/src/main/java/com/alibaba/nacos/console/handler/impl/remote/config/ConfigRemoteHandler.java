/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.api.model.Page;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Remote Implementation of ConfigHandler for handling internal configuration operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class ConfigRemoteHandler implements ConfigHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigRemoteHandler.class);
    
    public ConfigRemoteHandler() {
    }
    
    @Override
    public Page<ConfigInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws IOException, ServletException, NacosException {
        // TODO get from nacos servers
        return new Page<>();
    }
    
    @Override
    public ConfigAllInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        // TODO get from nacos servers
        return new ConfigAllInfo();
    }
    
    @Override
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        // TODO get from nacos servers
        return true;
    }
    
    @Override
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        // TODO get from nacos servers
        return true;
    }
    
    @Override
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) {
        // TODO get from nacos servers
        return true;
    }
    
    @Override
    public Page<ConfigInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        // TODO get from nacos servers
        return new Page<>();
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception {
        // TODO get from nacos servers
        return new GroupkeyListenserStatus();
    }
    
    @Override
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId,
            int sampleTime) {
        // TODO get from nacos servers
        return new GroupkeyListenserStatus();
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        // TODO get from nacos servers
        return new ResponseEntity<>(null, null, HttpStatus.OK);
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfigV2(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        // TODO get from nacos servers
        return new ResponseEntity<>(null, null, HttpStatus.OK);
    }
    
    @Override
    public Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId,
            SameConfigPolicy policy, MultipartFile file, String srcIp, String requestIpApp) throws NacosException {
        // TODO get from nacos servers
        return Result.success();
    }
    
    @Override
    public Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException {
        // TODO get from nacos servers
        return Result.success();
    }
    
    @Override
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp, String srcUser) {
        // TODO get from nacos servers
        return true;
        
    }
    
    @Override
    public Result<ConfigInfo4Beta> queryBetaConfig(String dataId, String group, String namespaceId) {
        // TODO get from nacos servers
        return Result.success();
    }
    
}
