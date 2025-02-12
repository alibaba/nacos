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

package com.alibaba.nacos.console.handler.impl.inner.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.DateFormatUtils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.utils.InetUtils;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ConfigHandler for handling internal configuration operations.
 *
 * @author zhangyukun
 */
@Service
@EnabledInnerHandler
public class ConfigInnerHandler implements ConfigHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigInnerHandler.class);
    
    private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";
    
    private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";
    
    private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigServletInner inner;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    private final ConfigSubService configSubService;
    
    private NamespacePersistService namespacePersistService;
    
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    public ConfigInnerHandler(ConfigServletInner inner, ConfigOperationService configOperationService,
            ConfigInfoPersistService configInfoPersistService, ConfigDetailService configDetailService,
            ConfigSubService configSubService, NamespacePersistService namespacePersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.inner = inner;
        this.configOperationService = configOperationService;
        this.configInfoPersistService = configInfoPersistService;
        this.configDetailService = configDetailService;
        this.configSubService = configSubService;
        this.namespacePersistService = namespacePersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
    }
    
    @Override
    public Page<ConfigInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws IOException, ServletException, NacosException {
        return configInfoPersistService.findConfigInfoLike4Page(pageNo, pageSize, dataId, group, namespaceId,
                configAdvanceInfo);
    }
    
    @Override
    public ConfigAllInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, group, namespaceId);
        // decrypted
        if (Objects.nonNull(configAllInfo)) {
            String encryptedDataKey = configAllInfo.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    configAllInfo.getContent());
            configAllInfo.setContent(pair.getSecond());
        }
        return configAllInfo;
    }
    
    @Override
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        String encryptedDataKeyFinal = configForm.getEncryptedDataKey();
        if (StringUtils.isBlank(encryptedDataKeyFinal)) {
            // encrypted
            Pair<String, String> pair = EncryptionHandler.encryptHandler(configForm.getDataId(),
                    configForm.getContent());
            configForm.setContent(pair.getSecond());
            encryptedDataKeyFinal = pair.getFirst();
        }
        return configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal);
    }
    
    @Override
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        return configOperationService.deleteConfig(dataId, group, namespaceId, tag, clientIp, srcUser);
    }
    
    @Override
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) {
        final Timestamp time = TimeUtils.getCurrentTime();
        List<ConfigAllInfo> configInfoList = configInfoPersistService.removeConfigInfoByIds(ids, clientIp, srcUser);
        if (CollectionUtils.isEmpty(configInfoList)) {
            return true;
        }
        for (ConfigInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            time.getTime()));
            
            ConfigTraceService.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), null, time.getTime(), clientIp, ConfigTraceService.PERSISTENCE_EVENT,
                    ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        }
        return true;
    }
    
    @Override
    public Page<ConfigInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        try {
            return configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId, group, namespaceId,
                    configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw e;
        }
    }
    
    @Override
    public GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception {
        SampleResult collectSampleResult = configSubService.getCollectSampleResult(dataId, group, namespaceId,
                sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        if (collectSampleResult.getLisentersGroupkeyStatus() != null) {
            gls.setLisentersGroupkeyStatus(collectSampleResult.getLisentersGroupkeyStatus());
        }
        return gls;
    }
    
    @Override
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId,
            int sampleTime) {
        SampleResult collectSampleResult = configSubService.getCollectSampleResultByIp(ip, sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        Map<String, String> configMd5Status = new HashMap<>(100);
        
        if (collectSampleResult.getLisentersGroupkeyStatus() == null) {
            return gls;
        }
        
        Map<String, String> status = collectSampleResult.getLisentersGroupkeyStatus();
        for (Map.Entry<String, String> config : status.entrySet()) {
            if (!StringUtils.isBlank(namespaceId) && config.getKey().contains(namespaceId)) {
                configMd5Status.put(config.getKey(), config.getValue());
                continue;
            }
            if (all) {
                configMd5Status.put(config.getKey(), config.getValue());
            } else {
                String[] configKeys = GroupKey2.parseKey(config.getKey());
                if (StringUtils.isBlank(configKeys[2])) {
                    configMd5Status.put(config.getKey(), config.getValue());
                }
            }
        }
        gls.setLisentersGroupkeyStatus(configMd5Status);
        return gls;
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        List<ConfigAllInfo> dataList = configInfoPersistService.findAllConfigInfo4Export(dataId, group, namespaceId,
                appName, ids);
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        StringBuilder metaData = null;
        for (ConfigInfo ci : dataList) {
            if (StringUtils.isNotBlank(ci.getAppName())) {
                // Handle appName
                if (metaData == null) {
                    metaData = new StringBuilder();
                }
                String metaDataId = ci.getDataId();
                if (metaDataId.contains(".")) {
                    metaDataId = metaDataId.substring(0, metaDataId.lastIndexOf(".")) + "~" + metaDataId.substring(
                            metaDataId.lastIndexOf(".") + 1);
                }
                metaData.append(ci.getGroup()).append('.').append(metaDataId).append(".app=")
                        // Fixed use of "\r\n" here
                        .append(ci.getAppName()).append("\r\n");
            }
            Pair<String, String> pair = EncryptionHandler.decryptHandler(ci.getDataId(), ci.getEncryptedDataKey(),
                    ci.getContent());
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, pair.getSecond()));
        }
        if (metaData != null) {
            zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA, metaData.toString()));
        }
        
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfigV2(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        List<ConfigAllInfo> dataList = configInfoPersistService.findAllConfigInfo4Export(dataId, group, namespaceId,
                appName, ids);
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        List<ConfigMetadata.ConfigExportItem> configMetadataItems = new ArrayList<>();
        for (ConfigAllInfo ci : dataList) {
            ConfigMetadata.ConfigExportItem configMetadataItem = new ConfigMetadata.ConfigExportItem();
            configMetadataItem.setAppName(ci.getAppName());
            configMetadataItem.setDataId(ci.getDataId());
            configMetadataItem.setDesc(ci.getDesc());
            configMetadataItem.setGroup(ci.getGroup());
            configMetadataItem.setType(ci.getType());
            configMetadataItems.add(configMetadataItem);
            Pair<String, String> pair = EncryptionHandler.decryptHandler(ci.getDataId(), ci.getEncryptedDataKey(),
                    ci.getContent());
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, pair.getSecond()));
        }
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(configMetadataItems);
        zipItemList.add(
                new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, YamlParserUtil.dumpObject(configMetadata)));
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    @Override
    public Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId,
            SameConfigPolicy policy, MultipartFile file, String srcIp, String requestIpApp) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (Objects.isNull(file)) {
            return Result.failure(ErrorCode.DATA_EMPTY, failedData);
        }
        if (StringUtils.isNotBlank(namespaceId)
                && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        List<Map<String, String>> unrecognizedList = new ArrayList<>();
        try {
            ZipUtils.UnZipResult unziped = ZipUtils.unzip(file.getBytes());
            ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
            Result<Map<String, Object>> errorResult;
            if (metaDataZipItem != null && Constants.CONFIG_EXPORT_METADATA_NEW.equals(metaDataZipItem.getItemName())) {
                // new export
                errorResult = parseImportDataV2(srcUser, unziped, configInfoList, unrecognizedList, namespaceId);
            } else {
                errorResult = parseImportData(srcUser, unziped, configInfoList, unrecognizedList, namespaceId);
            }
            if (errorResult != null) {
                return errorResult;
            }
        } catch (IOException e) {
            failedData.put("succCount", 0);
            LOGGER.error("parsing data failed", e);
            return Result.failure(ErrorCode.PARSING_DATA_FAILED, failedData);
        }
        
        if (CollectionUtils.isEmpty(configInfoList)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.DATA_EMPTY, failedData);
        }
        final Timestamp time = TimeUtils.getCurrentTime();
        Map<String, Object> saveResult = configInfoPersistService.batchInsertOrUpdate(configInfoList, srcUser, srcIp,
                null, policy);
        for (ConfigInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            time.getTime()));
            ConfigTraceService.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                    ConfigTraceService.PERSISTENCE_EVENT, ConfigTraceService.PERSISTENCE_TYPE_PUB,
                    configInfo.getContent());
        }
        // unrecognizedCount
        if (!unrecognizedList.isEmpty()) {
            saveResult.put("unrecognizedCount", unrecognizedList.size());
            saveResult.put("unrecognizedData", unrecognizedList);
        }
        return Result.success(saveResult);
    }
    
    /**
     * old import config.
     *
     * @param unziped          export file.
     * @param configInfoList   parse file result.
     * @param unrecognizedList unrecognized file.
     * @param namespace        import namespace.
     * @return error result.
     */
    private Result<Map<String, Object>> parseImportData(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespace) {
        ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
        
        Map<String, String> metaDataMap = new HashMap<>(16);
        if (metaDataZipItem != null) {
            // compatible all file separator
            String metaDataStr = metaDataZipItem.getItemData().replaceAll("[\r\n]+", "|");
            String[] metaDataArr = metaDataStr.split("\\|");
            Map<String, Object> failedData = new HashMap<>(4);
            for (String metaDataItem : metaDataArr) {
                String[] metaDataItemArr = metaDataItem.split("=");
                if (metaDataItemArr.length != 2) {
                    failedData.put("succCount", 0);
                    return Result.failure(ErrorCode.METADATA_ILLEGAL, failedData);
                }
                metaDataMap.put(metaDataItemArr[0], metaDataItemArr[1]);
            }
        }
        
        List<ZipUtils.ZipItem> itemList = unziped.getZipItemList();
        if (itemList != null && !itemList.isEmpty()) {
            for (ZipUtils.ZipItem item : itemList) {
                String[] groupAdnDataId = item.getItemName().split(Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR);
                if (groupAdnDataId.length != 2) {
                    Map<String, String> unrecognizedItem = new HashMap<>(2);
                    unrecognizedItem.put("itemName", item.getItemName());
                    unrecognizedList.add(unrecognizedItem);
                    continue;
                }
                String group = groupAdnDataId[0];
                String dataId = groupAdnDataId[1];
                String tempDataId = dataId;
                if (tempDataId.contains(".")) {
                    tempDataId = tempDataId.substring(0, tempDataId.lastIndexOf(".")) + "~" + tempDataId.substring(
                            tempDataId.lastIndexOf(".") + 1);
                }
                final String metaDataId = group + "." + tempDataId + ".app";
                
                //encrypted
                String content = item.getItemData();
                Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
                content = pair.getSecond();
                
                ConfigAllInfo ci = new ConfigAllInfo();
                ci.setGroup(group);
                ci.setDataId(dataId);
                ci.setContent(content);
                if (metaDataMap.get(metaDataId) != null) {
                    ci.setAppName(metaDataMap.get(metaDataId));
                }
                ci.setTenant(namespace);
                ci.setEncryptedDataKey(pair.getFirst());
                ci.setCreateUser(srcUser);
                configInfoList.add(ci);
            }
        }
        return null;
    }
    
    /**
     * new version import config add .metadata.yml file.
     *
     * @param unziped          export file.
     * @param configInfoList   parse file result.
     * @param unrecognizedList unrecognized file.
     * @param namespace        import namespace.
     * @return error result.
     */
    private Result<Map<String, Object>> parseImportDataV2(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespace) {
        ZipUtils.ZipItem metaDataItem = unziped.getMetaDataItem();
        String metaData = metaDataItem.getItemData();
        Map<String, Object> failedData = new HashMap<>(4);
        
        ConfigMetadata configMetadata = YamlParserUtil.loadObject(metaData, ConfigMetadata.class);
        if (configMetadata == null || CollectionUtils.isEmpty(configMetadata.getMetadata())) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.METADATA_ILLEGAL, failedData);
        }
        List<ConfigMetadata.ConfigExportItem> configExportItems = configMetadata.getMetadata();
        // check config metadata
        for (ConfigMetadata.ConfigExportItem configExportItem : configExportItems) {
            if (StringUtils.isBlank(configExportItem.getDataId()) || StringUtils.isBlank(configExportItem.getGroup())
                    || StringUtils.isBlank(configExportItem.getType())) {
                failedData.put("succCount", 0);
                return Result.failure(ErrorCode.METADATA_ILLEGAL, failedData);
            }
        }
        
        List<ZipUtils.ZipItem> zipItemList = unziped.getZipItemList();
        Set<String> metaDataKeys = configExportItems.stream()
                .map(metaItem -> GroupKey.getKey(metaItem.getDataId(), metaItem.getGroup()))
                .collect(Collectors.toSet());
        
        Map<String, String> configContentMap = new HashMap<>(zipItemList.size());
        int itemNameLength = 2;
        zipItemList.forEach(item -> {
            String itemName = item.getItemName();
            String[] groupAdnDataId = itemName.split(Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR);
            if (groupAdnDataId.length != itemNameLength) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", item.getItemName());
                unrecognizedList.add(unrecognizedItem);
                return;
            }
            
            String group = groupAdnDataId[0];
            String dataId = groupAdnDataId[1];
            String key = GroupKey.getKey(dataId, group);
            // metadata does not contain config file
            if (!metaDataKeys.contains(key)) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", "未在元数据中找到: " + item.getItemName());
                unrecognizedList.add(unrecognizedItem);
                return;
            }
            String itemData = item.getItemData();
            configContentMap.put(key, itemData);
        });
        
        for (ConfigMetadata.ConfigExportItem configExportItem : configExportItems) {
            String dataId = configExportItem.getDataId();
            String group = configExportItem.getGroup();
            String content = configContentMap.get(GroupKey.getKey(dataId, group));
            // config file not in metadata
            if (content == null) {
                Map<String, String> unrecognizedItem = new HashMap<>(2);
                unrecognizedItem.put("itemName", "未在文件中找到: " + group + "/" + dataId);
                unrecognizedList.add(unrecognizedItem);
                continue;
            }
            // encrypted
            Pair<String, String> pair = EncryptionHandler.encryptHandler(dataId, content);
            content = pair.getSecond();
            
            ConfigAllInfo ci = new ConfigAllInfo();
            ci.setGroup(group);
            ci.setDataId(dataId);
            ci.setContent(content);
            ci.setType(configExportItem.getType());
            ci.setDesc(configExportItem.getDesc());
            ci.setAppName(configExportItem.getAppName());
            ci.setTenant(namespace);
            ci.setEncryptedDataKey(pair.getFirst());
            ci.setCreateUser(srcUser);
            configInfoList.add(ci);
        }
        return null;
    }
    
    @Override
    public Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(configBeansList)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NO_SELECTED_CONFIG, failedData);
        }
        if (StringUtils.isNotBlank(namespaceId)
                && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        Map<Long, SameNamespaceCloneConfigBean> configBeansMap = configBeansList.stream()
                .collect(Collectors.toMap(SameNamespaceCloneConfigBean::getCfgId, cfg -> {
                    idList.add(cfg.getCfgId());
                    return cfg;
                }, (k1, k2) -> k1));
        
        List<ConfigAllInfo> queryedDataList = configInfoPersistService.findAllConfigInfo4Export(null, null, null, null,
                idList);
        
        if (queryedDataList == null || queryedDataList.isEmpty()) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.DATA_EMPTY, failedData);
        }
        
        List<ConfigAllInfo> configInfoList4Clone = new ArrayList<>(queryedDataList.size());
        
        for (ConfigAllInfo ci : queryedDataList) {
            SameNamespaceCloneConfigBean paramBean = configBeansMap.get(ci.getId());
            ConfigAllInfo ci4save = new ConfigAllInfo();
            ci4save.setTenant(namespaceId);
            ci4save.setType(ci.getType());
            ci4save.setGroup((paramBean != null && StringUtils.isNotBlank(paramBean.getGroup())) ? paramBean.getGroup()
                    : ci.getGroup());
            ci4save.setDataId(
                    (paramBean != null && StringUtils.isNotBlank(paramBean.getDataId())) ? paramBean.getDataId()
                            : ci.getDataId());
            ci4save.setContent(ci.getContent());
            if (StringUtils.isNotBlank(ci.getAppName())) {
                ci4save.setAppName(ci.getAppName());
            }
            ci4save.setDesc(ci.getDesc());
            ci4save.setEncryptedDataKey(
                    ci.getEncryptedDataKey() == null ? StringUtils.EMPTY : ci.getEncryptedDataKey());
            configInfoList4Clone.add(ci4save);
        }
        
        final Timestamp time = TimeUtils.getCurrentTime();
        Map<String, Object> saveResult = configInfoPersistService.batchInsertOrUpdate(configInfoList4Clone, srcUser,
                srcIp, null, policy);
        for (ConfigInfo configInfo : configInfoList4Clone) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            time.getTime()));
            ConfigTraceService.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                    ConfigTraceService.PERSISTENCE_EVENT, ConfigTraceService.PERSISTENCE_TYPE_PUB,
                    configInfo.getContent());
        }
        return Result.success(saveResult);
    }
    
    @Override
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp, String srcUser) {
        try {
            configInfoGrayPersistService.removeConfigInfoGray(dataId, group, namespaceId, BetaGrayRule.TYPE_BETA,
                    remoteIp, srcUser);
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return false;
        }
        ConfigTraceService.logPersistenceEvent(dataId, group, namespaceId, requestIpApp, System.currentTimeMillis(),
                remoteIp, ConfigTraceService.PERSISTENCE_EVENT_BETA, ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        if (PropertyUtil.isGrayCompatibleModel()) {
            configInfoBetaPersistService.removeConfigInfo4Beta(dataId, group, namespaceId);
        }
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(dataId, group, namespaceId, BetaGrayRule.TYPE_BETA,
                        System.currentTimeMillis()));
        return true;
        
    }
    
    @Override
    public Result<ConfigInfo4Beta> queryBetaConfig(String dataId, String group, String namespaceId) {
        try {
            ConfigInfo4Beta ci = configInfoBetaPersistService.findConfigInfo4Beta(dataId, group, namespaceId);
            
            if (Objects.nonNull(ci)) {
                String encryptedDataKey = ci.getEncryptedDataKey();
                Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey, ci.getContent());
                ci.setContent(pair.getSecond());
            }
            return Result.success(ci);
        } catch (Throwable e) {
            LOGGER.error("query beta data error", e);
            return Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), null);
        }
    }
    
}
