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

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.ALL_PATTERN;

/**
 * Remote Implementation of ConfigHandler for handling internal configuration operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class ConfigRemoteHandler implements ConfigHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public ConfigRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public Page<ConfigInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws NacosException {
        String search = dataId.contains(ALL_PATTERN) ? Constants.CONFIG_SEARCH_BLUR : Constants.CONFIG_SEARCH_ACCURATE;
        return listConfigInfo(search, pageNo, pageSize, dataId, group, namespaceId, configAdvanceInfo);
    }
    
    @Override
    public ConfigAllInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        try {
            ConfigDetailInfo configDetailInfo = clientHolder.getConfigMaintainerService()
                    .getConfig(dataId, group, namespaceId);
            return transferToConfigAllInfo(configDetailInfo);
        } catch (NacosException e) {
            if (NacosException.NOT_FOUND == e.getErrCode()) {
                return null;
            }
            throw e;
        }
    }
    
    @Override
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        return clientHolder.getConfigMaintainerService()
                .publishConfig(configForm.getDataId(), configForm.getGroup(), configForm.getNamespaceId(),
                        configForm.getContent(), configForm.getTag(), configForm.getAppName(), configForm.getSrcUser(),
                        configForm.getConfigTags(), configForm.getDesc(), configForm.getUse(), configForm.getEffect(),
                        configForm.getType(), configForm.getSchema());
    }
    
    @Override
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        return clientHolder.getConfigMaintainerService().deleteConfig(dataId, group, namespaceId, tag);
    }
    
    @Override
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) throws NacosException {
        return clientHolder.getConfigMaintainerService().deleteConfigs(ids);
    }
    
    @Override
    public Page<ConfigInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        return listConfigInfo(search, pageNo, pageSize, dataId, group, namespaceId, configAdvanceInfo);
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception {
        ConfigListenerInfo listenerInfo = clientHolder.getConfigMaintainerService()
                .getListeners(dataId, group, namespaceId, sampleTime);
        // TODO use ConfigListenerInfo after console ui modified
        GroupkeyListenserStatus result = new GroupkeyListenserStatus();
        result.setCollectStatus(200);
        result.setLisentersGroupkeyStatus(listenerInfo.getListenersStatus());
        return result;
    }
    
    @Override
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime)
            throws NacosException {
        ConfigListenerInfo listenerInfo = clientHolder.getConfigMaintainerService()
                .getAllSubClientConfigByIp(ip, all, namespaceId, sampleTime);
        // TODO use ConfigListenerInfo after console ui modified
        GroupkeyListenserStatus result = new GroupkeyListenserStatus();
        result.setCollectStatus(200);
        result.setLisentersGroupkeyStatus(listenerInfo.getListenersStatus());
        return result;
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
        List<ConfigCloneInfo> configInfos = new ArrayList<>(configBeansList.size());
        configBeansList.forEach(sameNamespaceCloneConfigBean -> {
            ConfigCloneInfo configCloneInfo = new ConfigCloneInfo();
            configCloneInfo.setConfigId(sameNamespaceCloneConfigBean.getCfgId());
            configCloneInfo.setTargetDataId(sameNamespaceCloneConfigBean.getDataId());
            configCloneInfo.setTargetGroupName(sameNamespaceCloneConfigBean.getGroup());
            configInfos.add(configCloneInfo);
        });
        return Result.success(
                clientHolder.getConfigMaintainerService().cloneConfig(namespaceId, configInfos, srcUser, policy));
    }
    
    @Override
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp, String srcUser) throws NacosException {
        return clientHolder.getConfigMaintainerService().stopBeta(dataId, group, namespaceId);
    }
    
    @Override
    public Result<ConfigInfo4Beta> queryBetaConfig(String dataId, String group, String namespaceId)
            throws NacosException {
        try {
            ConfigGrayInfo configGrayInfo = clientHolder.getConfigMaintainerService()
                    .queryBeta(dataId, group, namespaceId);
            return Result.success(transferToConfigInfo4Beta(configGrayInfo));
        } catch (NacosException e) {
            if (NacosException.NOT_FOUND == e.getErrCode()) {
                // admin api return 404, means the config is not in beta.
                return Result.success(null);
            }
            // other exception throw it.
            throw e;
        }
    }
    
    private Page<ConfigInfo> listConfigInfo(String search, int pageNo, int pageSize, String dataId, String groupName,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        String type = getInfoFromAdvanceInfo(configAdvanceInfo, ParametersField.TYPES);
        String appName = getInfoFromAdvanceInfo(configAdvanceInfo, "appName");
        String configTags = getInfoFromAdvanceInfo(configAdvanceInfo, "config_tags");
        String configDetail = getInfoFromAdvanceInfo(configAdvanceInfo, "content");
        Page<ConfigBasicInfo> configBasicInfoPage = clientHolder.getConfigMaintainerService()
                .searchConfigByDetails(dataId, groupName, namespaceId, search, configDetail, type, configTags, appName,
                        pageNo, pageSize);
        // TODO use ConfigBasicInfo after console-ui modified.
        return transferToConfigInfoPage(configBasicInfoPage);
    }
    
    private String getInfoFromAdvanceInfo(Map<String, Object> configAdvanceInfo, String key) {
        return configAdvanceInfo.containsKey(key) ? (String) configAdvanceInfo.get(key) : StringUtils.EMPTY;
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private Page<ConfigInfo> transferToConfigInfoPage(Page<ConfigBasicInfo> configBasicInfoPage) {
        Page<ConfigInfo> result = new Page<>();
        result.setTotalCount(configBasicInfoPage.getTotalCount());
        result.setPagesAvailable(configBasicInfoPage.getPagesAvailable());
        result.setPageNumber(configBasicInfoPage.getPageNumber());
        List<ConfigInfo> configInfos = new ArrayList<>(configBasicInfoPage.getPageItems().size());
        for (ConfigBasicInfo each : configBasicInfoPage.getPageItems()) {
            ConfigInfo configInfo = new ConfigInfo();
            transferToConfigInfo(configInfo, each);
            configInfos.add(configInfo);
        }
        result.setPageItems(configInfos);
        return result;
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private void transferToConfigInfo(ConfigInfo configInfo, ConfigBasicInfo basicInfo) {
        configInfo.setId(basicInfo.getId());
        configInfo.setDataId(basicInfo.getDataId());
        configInfo.setGroup(basicInfo.getGroupName());
        configInfo.setMd5(basicInfo.getMd5());
        configInfo.setType(basicInfo.getType());
        configInfo.setAppName(basicInfo.getAppName());
        configInfo.setTenant(basicInfo.getNamespaceId());
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private ConfigAllInfo transferToConfigAllInfo(ConfigDetailInfo configDetailInfo) {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        transferToConfigInfo(configAllInfo, configDetailInfo);
        configAllInfo.setCreateTime(configDetailInfo.getCreateTime());
        configAllInfo.setModifyTime(configDetailInfo.getModifyTime());
        configAllInfo.setContent(configDetailInfo.getContent());
        configAllInfo.setDesc(configDetailInfo.getDesc());
        configAllInfo.setEncryptedDataKey(configDetailInfo.getEncryptedDataKey());
        configAllInfo.setCreateUser(configDetailInfo.getCreateUser());
        configAllInfo.setCreateIp(configDetailInfo.getCreateIp());
        configAllInfo.setConfigTags(configDetailInfo.getConfigTags());
        return configAllInfo;
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private ConfigInfo4Beta transferToConfigInfo4Beta(ConfigGrayInfo configGrayInfo) {
        ConfigInfo4Beta result = new ConfigInfo4Beta();
        transferToConfigInfo(result, configGrayInfo);
        result.setBetaIps(GrayRuleManager.deserializeConfigGrayPersistInfo(configGrayInfo.getGrayRule()).getExpr());
        return result;
    }
}
