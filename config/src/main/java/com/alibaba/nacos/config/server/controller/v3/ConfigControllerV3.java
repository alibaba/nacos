/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.DateFormatUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.utils.InetUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.utils.RequestUtil.getRemoteIp;

/**
 * Configuration management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConfigControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigControllerV3.class);
    
    private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";
    
    private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";
    
    private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigSubService configSubService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigDetailService configDetailService;
    
    private final ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private final ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    private final NamespacePersistService namespacePersistService;
    
    public ConfigControllerV3(ConfigOperationService configOperationService, ConfigSubService configSubService,
            ConfigInfoPersistService configInfoPersistService, ConfigDetailService configDetailService,
            ConfigInfoGrayPersistService configInfoGrayPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService,
            NamespacePersistService namespacePersistService) {
        this.configOperationService = configOperationService;
        this.configSubService = configSubService;
        this.configInfoPersistService = configInfoPersistService;
        this.configDetailService = configDetailService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.namespacePersistService = namespacePersistService;
    }
    
    /**
     * Query configuration.
     */
    @GetMapping
    @TpsControl(pointName = "ConfigQuery")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigAllInfo> getConfig(ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        // check namespaceId
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        // check params
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, groupName, namespaceId);
        
        // decrypted
        if (Objects.nonNull(configAllInfo)) {
            String encryptedDataKey = configAllInfo.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    configAllInfo.getContent());
            configAllInfo.setContent(pair.getSecond());
        }
        
        return Result.success(configAllInfo);
    }
    
    /**
     * Publish configuration.
     */
    @PostMapping
    @TpsControl(pointName = "ConfigPublish")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> publishConfig(HttpServletRequest request, ConfigFormV3 configForm) throws NacosException {
        // check required field
        configForm.validateWithContent();
        configForm.setNamespaceId(NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId()));
        
        // check param
        ParamUtils.checkParam(configForm.getDataId(), configForm.getGroup(), "datumId", configForm.getContent());
        ParamUtils.checkParamV2(configForm.getTag());
        
        if (StringUtils.isBlank(configForm.getSrcUser())) {
            configForm.setSrcUser(RequestUtil.getSrcUserName(request));
        }
        
        if (!ConfigType.isValidType(configForm.getType())) {
            configForm.setType(ConfigType.getDefaultType().getType());
        }
        
        String encryptedDataKeyFinal = configForm.getEncryptedDataKey();
        if (StringUtils.isBlank(encryptedDataKeyFinal)) {
            // encrypted
            Pair<String, String> pair = EncryptionHandler.encryptHandler(configForm.getDataId(),
                    configForm.getContent());
            configForm.setContent(pair.getSecond());
            encryptedDataKeyFinal = pair.getFirst();
        }
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        configRequestInfo.setBetaIps(request.getHeader("betaIps"));
        configRequestInfo.setCasMd5(request.getHeader("casMd5"));
        
        return Result.success(
                configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal));
    }
    
    /**
     * Delete configuration.
     */
    @DeleteMapping
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> deleteConfig(HttpServletRequest request, ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        // check namespaceId
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String tag = configForm.getTag();
        ParamUtils.checkParamV2(tag);
        
        String clientIp = getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        
        return Result.success(
                configOperationService.deleteConfig(configForm.getDataId(), configForm.getGroupName(), namespaceId, tag,
                        clientIp, srcUser));
    }
    
    /**
     * Batch delete configuration by ids.
     */
    @DeleteMapping("/batch")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> deleteConfigs(HttpServletRequest request, @RequestParam(value = "ids") List<Long> ids) {
        String clientIp = getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        final Timestamp time = TimeUtils.getCurrentTime();
        
        List<ConfigAllInfo> configInfoList = configInfoPersistService.removeConfigInfoByIds(ids, clientIp, srcUser);
        if (CollectionUtils.isEmpty(configInfoList)) {
            return Result.success(true);
        }
        for (ConfigAllInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            time.getTime()));
            ConfigTraceService.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), null, time.getTime(), clientIp, ConfigTraceService.PERSISTENCE_EVENT,
                    ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        }
        
        return Result.success(true);
    }
    
    /**
     * Get extra configuration information.
     */
    @GetMapping("/extInfo")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigAdvanceInfo> getConfigAdvanceInfo(ConfigFormV3 configForm) throws NacosApiException {
        configForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        ConfigAdvanceInfo configInfo = configInfoPersistService.findConfigAdvanceInfo(configForm.getDataId(),
                configForm.getGroupName(), namespaceId);
        
        return Result.success(configInfo);
    }
    
    /**
     * Subscribe to configured client information.
     */
    @GetMapping("/listener")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<GroupkeyListenserStatus> getListeners(ConfigFormV3 configForm,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime) throws Exception {
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        SampleResult collectSampleResult = configSubService.getCollectSampleResult(configForm.getDataId(),
                configForm.getGroupName(), namespaceId, sampleTime);
        
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        if (collectSampleResult.getLisentersGroupkeyStatus() != null) {
            gls.setLisentersGroupkeyStatus(collectSampleResult.getLisentersGroupkeyStatus());
        }
        
        return Result.success(gls);
    }
    
    /**
     * Search config by config detail.
     */
    @GetMapping("/searchDetail")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Page<ConfigInfo>> searchConfigByDetails(ConfigFormV3 configForm, PageForm pageForm,
            String configDetail, @RequestParam(defaultValue = "blur") String search) throws NacosApiException {
        configForm.blurSearchValidate();
        pageForm.validate();
        Map<String, Object> configAdvanceInfo = new HashMap<>(100);
        if (StringUtils.isNotBlank(configForm.getAppName())) {
            configAdvanceInfo.put("appName", configForm.getAppName());
        }
        if (StringUtils.isNotBlank(configForm.getConfigTags())) {
            configAdvanceInfo.put("config_tags", configForm.getConfigTags());
        }
        if (StringUtils.isNotBlank(configForm.getType())) {
            configAdvanceInfo.put(ParametersField.TYPES, configForm.getType());
        }
        if (StringUtils.isNotBlank(configDetail)) {
            configAdvanceInfo.put("content", configDetail);
        }
        int pageNo = pageForm.getPageNo();
        int pageSize = pageForm.getPageSize();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        
        return Result.success(
                configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId, groupName, namespaceId,
                        configAdvanceInfo));
    }
    
    /**
     * Execute to remove beta operation.
     */
    @DeleteMapping("/beta")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> stopBeta(HttpServletRequest httpServletRequest, ConfigFormV3 configForm)
            throws NacosApiException {
        configForm.validate();
        String remoteIp = getRemoteIp(httpServletRequest);
        String requestIpApp = RequestUtil.getAppName(httpServletRequest);
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        try {
            configInfoGrayPersistService.removeConfigInfoGray(dataId, groupName, namespaceId, BetaGrayRule.TYPE_BETA,
                    remoteIp, RequestUtil.getSrcUserName(httpServletRequest));
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "remove beta data error", false);
        }
        
        ConfigTraceService.logPersistenceEvent(dataId, groupName, namespaceId, requestIpApp, System.currentTimeMillis(),
                remoteIp, ConfigTraceService.PERSISTENCE_EVENT_BETA, ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        if (PropertyUtil.isGrayCompatibleModel()) {
            configInfoBetaPersistService.removeConfigInfo4Beta(dataId, groupName, namespaceId);
        }
        ConfigChangePublisher.notifyConfigChange(
                new ConfigDataChangeEvent(dataId, groupName, namespaceId, BetaGrayRule.TYPE_BETA,
                        System.currentTimeMillis()));
        
        return Result.success(true);
    }
    
    /**
     * Execute to query beta operation.
     */
    @GetMapping("/beta")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigInfo4Beta> queryBeta(ConfigFormV3 configForm) throws NacosApiException {
        configForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        ConfigInfo4Beta configInfo4Beta = null;
        ConfigInfoGrayWrapper beta4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, groupName,
                namespaceId, "beta");
        if (Objects.nonNull(beta4Gray)) {
            String encryptedDataKey = beta4Gray.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    beta4Gray.getContent());
            beta4Gray.setContent(pair.getSecond());
            configInfo4Beta = new ConfigInfo4Beta();
            BeanUtils.copyProperties(beta4Gray, configInfo4Beta);
            configInfo4Beta.setBetaIps(
                    GrayRuleManager.deserializeConfigGrayPersistInfo(beta4Gray.getGrayRule()).getExpr());
        }
        
        return Result.success(configInfo4Beta);
    }
    
    /**
     * Execute import and publish config operation.
     */
    @PostMapping("/import")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Object>> importAndPublishConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy, MultipartFile file)
            throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (Objects.isNull(file)) {
            return Result.failure(ErrorCode.DATA_EMPTY, failedData);
        }
        
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        if (StringUtils.isNotBlank(namespaceId)
                && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_NOT_EXIST, failedData);
        }
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
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
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
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
     */
    private Result<Map<String, Object>> parseImportData(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespaceId) {
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
                ci.setTenant(namespaceId);
                ci.setEncryptedDataKey(pair.getFirst());
                ci.setCreateUser(srcUser);
                configInfoList.add(ci);
            }
        }
        
        return null;
    }
    
    /**
     * Import config add .metadata.yml file.
     */
    private Result<Map<String, Object>> parseImportDataV2(String srcUser, ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespaceId) {
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
                unrecognizedItem.put("itemName", "Item not found in metadata: " + item.getItemName());
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
                unrecognizedItem.put("itemName", "Item not found in file: " + group + "/" + dataId);
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
            ci.setTenant(namespaceId);
            ci.setEncryptedDataKey(pair.getFirst());
            ci.setCreateUser(srcUser);
            configInfoList.add(ci);
        }
        
        return null;
    }
    
    /**
     * Export config add metadata.yml file record config metadata.
     */
    @GetMapping("/export")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ResponseEntity<byte[]>> exportConfig(ConfigFormV3 configForm,
            @RequestParam(value = "ids", required = false) List<Long> ids) throws NacosApiException {
        configForm.blurSearchValidate();
        ids.removeAll(Collections.singleton(null));
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        List<ConfigAllInfo> dataList = configInfoPersistService.findAllConfigInfo4Export(configForm.getDataId(),
                configForm.getGroupName(), namespaceId, configForm.getAppName(), ids);
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
        
        return Result.success(new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK));
    }
    
    /**
     * Execute clone config operation.
     */
    @PostMapping("/clone")
    @Secured(resource = Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.WRITE,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespaceId") String namespaceId,
            @RequestBody List<SameNamespaceCloneConfigBean> configBeansList,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(configBeansList)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NO_SELECTED_CONFIG, failedData);
        }
        configBeansList.removeAll(Collections.singleton(null));
        
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        if (StringUtils.isNotBlank(namespaceId)
                && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_ALREADY_EXIST, failedData);
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
        if (StringUtils.isBlank(srcUser)) {
            srcUser = RequestUtil.getSrcUserName(request);
        }
        final String srcIp = getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
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
}