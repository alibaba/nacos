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
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigCloneInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.DateFormatUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.constant.ParametersField;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.paramcheck.ConfigBlurSearchHttpParamExtractor;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigMigrateService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.listener.ConfigListenerStateDelegate;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.ResponseUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.model.form.AggregationForm;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import com.alibaba.nacos.sys.utils.InetUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping(Constants.CONFIG_ADMIN_V3_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ConfigControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigControllerV3.class);
    
    private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";
    
    private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";
    
    private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigDetailService configDetailService;
    
    private final ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private final ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    private final NamespacePersistService namespacePersistService;
    
    private final ConfigListenerStateDelegate configListenerStateDelegate;
    
    private final ConfigMigrateService configMigrateService;

    /**
     * Flag to indicate if the table `config_info_beta` exists, which means the old version of table schema is used.
     */
    private boolean oldTableVersion;
    
    public ConfigControllerV3(ConfigOperationService configOperationService,
            ConfigInfoPersistService configInfoPersistService, ConfigDetailService configDetailService,
            ConfigInfoGrayPersistService configInfoGrayPersistService,
            ConfigInfoBetaPersistService configInfoBetaPersistService, NamespacePersistService namespacePersistService,
            ConfigListenerStateDelegate configListenerStateDelegate, ConfigMigrateService configMigrateService) {
        this.configOperationService = configOperationService;
        this.configInfoPersistService = configInfoPersistService;
        this.configDetailService = configDetailService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
        this.configInfoBetaPersistService = configInfoBetaPersistService;
        this.namespacePersistService = namespacePersistService;
        this.configListenerStateDelegate = configListenerStateDelegate;
        this.configMigrateService = configMigrateService;
        this.oldTableVersion = namespacePersistService.isExistTable("config_info_beta");
    }
    
    /**
     * Query configuration.
     */
    @GetMapping
    @TpsControl(pointName = "ConfigQuery")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigDetailInfo> getConfig(ConfigFormV3 configForm) throws NacosException {
        configForm.validate();
        // check namespaceId
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        // check params
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, groupName, namespaceId);
        if (Objects.isNull(configAllInfo)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Config not exist, please publish Config first.");
        }
        // decrypted
        String encryptedDataKey = configAllInfo.getEncryptedDataKey();
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configAllInfo.getContent());
        configAllInfo.setContent(pair.getSecond());
        ConfigDetailInfo result = ResponseUtil.transferToConfigDetailInfo(configAllInfo);
        return Result.success(result);
    }
    
    /**
     * Publish configuration.
     */
    @PostMapping
    @TpsControl(pointName = "ConfigPublish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> publishConfig(HttpServletRequest request, ConfigFormV3 configForm) throws NacosException {
        // check required field
        configForm.validateWithContent();
        final boolean namespaceTransferred = NamespaceUtil.isNeedTransferNamespace(configForm.getNamespaceId());
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
        configRequestInfo.setNamespaceTransferred(namespaceTransferred);
        
        return Result.success(
                configOperationService.publishConfig(configForm, configRequestInfo, encryptedDataKeyFinal));
    }
    
    /**
     * Publish config metadata result.
     *
     * @param request    the request
     * @param configForm the config form
     * @return the result
     * @throws NacosException the nacos exception
     */
    @PostMapping("/metadata")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> publishConfigMetadata(HttpServletRequest request, ConfigFormV3 configForm)
            throws NacosException {
        configForm.validate();
        String configTags = configForm.getConfigTags();
        String description = configForm.getDesc();
        String dataId = configForm.getDataId();
        String group = configForm.getGroup();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        configInfoPersistService.updateConfigInfoMetadata(dataId, group, namespaceId, configTags, description);
        configMigrateService.updateConfigMetadataMigrate(dataId, group, namespaceId, configTags, description);
        return Result.success(true);
    }
    
    /**
     * Delete configuration.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
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
                        clientIp, srcUser, Constants.HTTP));
    }
    
    /**
     * Batch delete configuration by ids.
     */
    @DeleteMapping("/batch")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Boolean> deleteConfigs(HttpServletRequest request, @RequestParam(value = "ids") List<Long> ids) {
        String clientIp = getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        try {
            for (Long id : ids) {
                ConfigInfo configInfo = configInfoPersistService.findConfigInfo(id);
                if (configInfo == null) {
                    LOGGER.warn("[deleteConfigs] configInfo is null, id: {}", id);
                    continue;
                }
                configOperationService.deleteConfig(configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant(), null, clientIp, srcUser, Constants.HTTP);
            }
            return Result.success(true);
        } catch (Exception e) {
            LOGGER.error("delete configs based on the IDs list error, IDs: {}", ids, e);
            return Result.failure(ErrorCode.SERVER_ERROR);
        }
    }
    
    /**
     * Subscribe to configured client information.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigListenerInfo> getListeners(ConfigFormV3 configForm, AggregationForm aggregationForm)
            throws Exception {
        configForm.validate();
        aggregationForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        return Result.success(
                configListenerStateDelegate.getListenerState(configForm.getDataId(), configForm.getGroupName(),
                        namespaceId, aggregationForm.isAggregation()));
    }
    
    /**
     * List or Search config by config condition.
     *
     * <p>
     * This API will entry the request into an queue to cache and do query limit. If API called with frequently or
     * datasource is high performance and slow RT, The API will return {@code 503}. Can use
     * `nacos.config.search.max_capacity` and `nacos.config.search.max_thread` to upper the limit of query. And use
     * `nacos.config.search.wait_timeout` to control the waiting time of query.
     * </p>
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    @ExtractorManager.Extractor(httpExtractor = ConfigBlurSearchHttpParamExtractor.class)
    public Result<Page<ConfigBasicInfo>> list(ConfigFormV3 configForm, PageForm pageForm, String configDetail,
            @RequestParam(defaultValue = "blur") String search) throws NacosApiException {
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
        
        Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, dataId,
                groupName, namespaceId, configAdvanceInfo);
        Page<ConfigBasicInfo> result = new Page<>();
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable(configInfoPage.getPagesAvailable());
        result.setPageNumber(configInfoPage.getPageNumber());
        result.setPageItems(configInfoPage.getPageItems().stream().map(ResponseUtil::transferToConfigBasicInfo)
                .collect(Collectors.toList()));
        return Result.success(result);
    }
    
    /**
     * Execute to remove beta operation.
     */
    @DeleteMapping("/beta")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
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
            configMigrateService.removeConfigInfoGrayMigrate(dataId, groupName, namespaceId, BetaGrayRule.TYPE_BETA,
                    remoteIp, RequestUtil.getSrcUserName(httpServletRequest));
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "remove beta data error", false);
        }
        
        ConfigTraceService.logPersistenceEvent(dataId, groupName, namespaceId, requestIpApp, System.currentTimeMillis(),
                remoteIp, ConfigTraceService.PERSISTENCE_EVENT_BETA, ConfigTraceService.PERSISTENCE_TYPE_REMOVE, null);
        if (PropertyUtil.isGrayCompatibleModel() && oldTableVersion) {
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
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigGrayInfo> queryBeta(ConfigFormV3 configForm) throws NacosApiException {
        configForm.validate();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        ConfigInfoGrayWrapper beta4Gray = configInfoGrayPersistService.findConfigInfo4Gray(dataId, groupName,
                namespaceId, "beta");
        if (Objects.nonNull(beta4Gray)) {
            String encryptedDataKey = beta4Gray.getEncryptedDataKey();
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                    beta4Gray.getContent());
            beta4Gray.setContent(pair.getSecond());
            ConfigGrayInfo result = ResponseUtil.transferToConfigGrayInfo(beta4Gray);
            return Result.success(result);
        } else {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Config is not in beta.");
        }
    }
    
    /**
     * Execute import and publish config operation.
     */
    @PostMapping("/import")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
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
        if (StringUtils.isNotBlank(namespaceId) && !NamespaceUtil.isDefaultNamespaceId(namespaceId)
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
            Result<Map<String, Object>> errorResult = parseImportDataV2(srcUser, unziped, configInfoList,
                    unrecognizedList, namespaceId);
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
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public ResponseEntity<byte[]> exportConfig(ConfigFormV3 configForm,
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
        
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    /**
     * Execute clone config operation.
     */
    @PostMapping("/clone")
    @Secured(action = ActionTypes.WRITE, signType = SignType.CONFIG, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespaceId") String namespaceId, @RequestBody List<ConfigCloneInfo> cloneInfos,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(cloneInfos)) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NO_SELECTED_CONFIG, failedData);
        }
        cloneInfos.removeAll(Collections.singleton(null));
        
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        if (StringUtils.isNotBlank(namespaceId) && !NamespaceUtil.isDefaultNamespaceId(namespaceId)
                && namespacePersistService.tenantInfoCountByTenantId(namespaceId) <= 0) {
            failedData.put("succCount", 0);
            return Result.failure(ErrorCode.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<Long> idList = new ArrayList<>(cloneInfos.size());
        Map<Long, ConfigCloneInfo> configBeansMap = cloneInfos.stream()
                .collect(Collectors.toMap(ConfigCloneInfo::getConfigId, cfg -> {
                    idList.add(cfg.getConfigId());
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
            ConfigCloneInfo paramBean = configBeansMap.get(ci.getId());
            ConfigAllInfo ci4save = new ConfigAllInfo();
            ci4save.setTenant(namespaceId);
            ci4save.setType(ci.getType());
            ci4save.setGroup((paramBean != null && StringUtils.isNotBlank(paramBean.getTargetGroupName()))
                    ? paramBean.getTargetGroupName() : ci.getGroup());
            ci4save.setDataId((paramBean != null && StringUtils.isNotBlank(paramBean.getTargetDataId()))
                    ? paramBean.getTargetDataId() : ci.getDataId());
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