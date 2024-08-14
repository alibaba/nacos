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
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.persistence.model.Page;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for handling configuration operations.
 *
 * @author zhangyukun
 */
public interface ConfigHandler {
    
    void getConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String namespaceId, String tag, String isNotify, String clientIp, boolean isV2)
            throws IOException, ServletException, NacosException;
    
    ConfigAllInfo detailConfigInfo(String dataId, String group, String namespaceId) throws NacosException;
    
    Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException;
    
    Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException;
    
    Boolean deleteConfigs(List<Long> ids, String clientIp, String srcUser);
    
    Page<ConfigInfo> searchConfigByDetails(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException;
    
    GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception;
    
    RestResult<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId, SameConfigPolicy policy,
            MultipartFile file, String srcIp, String requestIpApp) throws NacosException;
    
    RestResult<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp, String requestIpApp) throws NacosException;
}
