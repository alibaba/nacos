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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.common.utils.MapUtil;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.result.code.ResultCodeEnum;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
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

/**
 * Special controller for soft load client to publish data.
 *
 * @author leiwen
 */
@RestController
@RequestMapping(Constants.CONFIG_CONTROLLER_PATH)
public class ConfigController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigController.class);
    
    private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";
    
    private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";
    
    private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";
    
    @Autowired
    private ConfigServletInner inner;
    
    @Autowired
    private PersistService persistService;
    
    @Autowired
    private ConfigSubService configSubService;
    
    /**
     * Adds or updates non-aggregated data.
     *
     * @throws NacosException NacosException.
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public Boolean publishConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(value = "dataId") String dataId, @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "content") String content, @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam(value = "desc", required = false) String desc,
            @RequestParam(value = "use", required = false) String use,
            @RequestParam(value = "effect", required = false) String effect,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "schema", required = false) String schema) throws NacosException {
        
        final String srcIp = RequestUtil.getRemoteIp(request);
        final String requestIpApp = RequestUtil.getAppName(request);
        srcUser = RequestUtil.getSrcUserName(request);
        //check type
        if (!ConfigType.isValidType(type)) {
            type = ConfigType.getDefaultType().getType();
        }
        // check tenant
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", content);
        ParamUtils.checkParam(tag);
        Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(10);
        MapUtil.putIfValNoNull(configAdvanceInfo, "config_tags", configTags);
        MapUtil.putIfValNoNull(configAdvanceInfo, "desc", desc);
        MapUtil.putIfValNoNull(configAdvanceInfo, "use", use);
        MapUtil.putIfValNoNull(configAdvanceInfo, "effect", effect);
        MapUtil.putIfValNoNull(configAdvanceInfo, "type", type);
        MapUtil.putIfValNoNull(configAdvanceInfo, "schema", schema);
        ParamUtils.checkParam(configAdvanceInfo);
        
        if (AggrWhitelist.isAggrDataId(dataId)) {
            LOGGER.warn("[aggr-conflict] {} attempt to publish single data, {}, {}", RequestUtil.getRemoteIp(request),
                    dataId, group);
            throw new NacosException(NacosException.NO_RIGHT, "dataId:" + dataId + " is aggr");
        }
        
        final Timestamp time = TimeUtils.getCurrentTime();
        String betaIps = request.getHeader("betaIps");
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setType(type);
        if (StringUtils.isBlank(betaIps)) {
            if (StringUtils.isBlank(tag)) {
                persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, false);
                ConfigChangePublisher
                        .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
            } else {
                persistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, false);
                ConfigChangePublisher.notifyConfigChange(
                        new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
            }
        } else {
            // beta publish
            persistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, false);
            ConfigChangePublisher
                    .notifyConfigChange(new ConfigDataChangeEvent(true, dataId, group, tenant, time.getTime()));
        }
        ConfigTraceService
                .logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                        ConfigTraceService.PERSISTENCE_EVENT_PUB, content);
        return true;
    }
    
    /**
     * Get configure board information fail.
     *
     * @throws ServletException ServletException.
     * @throws IOException      IOException.
     * @throws NacosException   NacosException.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public void getConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag)
            throws IOException, ServletException, NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        ParamUtils.checkParam(tag);
        
        final String clientIp = RequestUtil.getRemoteIp(request);
        String isNotify = request.getHeader("notify");
        inner.doGetConfig(request, response, dataId, group, tenant, tag, isNotify, clientIp);
    }
    
    /**
     * Get the specific configuration information that the console USES.
     *
     * @throws NacosException NacosException.
     */
    @GetMapping(params = "show=all")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public ConfigAllInfo detailConfigInfo(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant)
            throws NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
        // check params
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        return persistService.findConfigAllInfo(dataId, group, tenant);
    }
    
    /**
     * Synchronously delete all pre-aggregation data under a dataId.
     *
     * @throws NacosException NacosException.
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public Boolean deleteConfig(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) throws NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", "rm");
        ParamUtils.checkParam(tag);
        String clientIp = RequestUtil.getRemoteIp(request);
        String srcUser = RequestUtil.getSrcUserName(request);
        if (StringUtils.isBlank(tag)) {
            persistService.removeConfigInfo(dataId, group, tenant, clientIp, srcUser);
        } else {
            persistService.removeConfigInfoTag(dataId, group, tenant, tag, clientIp, srcUser);
        }
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigTraceService.logPersistenceEvent(dataId, group, tenant, null, time.getTime(), clientIp,
                ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
        return true;
    }
    
    /**
     * Execute delete config operation.
     *
     * @return java.lang.Boolean
     * @author klw
     * @Description: delete configuration based on multiple config ids
     * @Date 2019/7/5 10:26
     * @Param [request, response, dataId, group, tenant, tag]
     */
    @DeleteMapping(params = "delType=ids")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public RestResult<Boolean> deleteConfigs(HttpServletRequest request, HttpServletResponse response,
            @RequestParam(value = "ids") List<Long> ids) {
        String clientIp = RequestUtil.getRemoteIp(request);
        final Timestamp time = TimeUtils.getCurrentTime();
        List<ConfigInfo> configInfoList = persistService.removeConfigInfoByIds(ids, clientIp, null);
        if (CollectionUtils.isEmpty(configInfoList)) {
            return RestResultUtils.success(true);
        }
        for (ConfigInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant(), time.getTime()));
            ConfigTraceService
                    .logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            null, time.getTime(), clientIp, ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
        }
        return RestResultUtils.success(true);
    }
    
    @GetMapping("/catalog")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public RestResult<ConfigAdvanceInfo> getConfigAdvanceInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
        ConfigAdvanceInfo configInfo = persistService.findConfigAdvanceInfo(dataId, group, tenant);
        return RestResultUtils.success(configInfo);
    }
    
    /**
     * The client listens for configuration changes.
     */
    @PostMapping("/listener")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public void listener(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);
        String probeModify = request.getParameter("Listening-Configs");
        if (StringUtils.isBlank(probeModify)) {
            LOGGER.warn("invalid probeModify is blank");
            throw new IllegalArgumentException("invalid probeModify");
        }
        
        probeModify = URLDecoder.decode(probeModify, Constants.ENCODE);
        
        Map<String, String> clientMd5Map;
        try {
            clientMd5Map = MD5Util.getClientMd5Map(probeModify);
        } catch (Throwable e) {
            throw new IllegalArgumentException("invalid probeModify");
        }
        
        // do long-polling
        inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
    }
    
    /**
     * Subscribe to configured client information.
     */
    @GetMapping("/listener")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public GroupkeyListenserStatus getListeners(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "tenant", required = false) String tenant,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime) throws Exception {
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
        SampleResult collectSampleResult = configSubService.getCollectSampleResult(dataId, group, tenant, sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        if (collectSampleResult.getLisentersGroupkeyStatus() != null) {
            gls.setLisentersGroupkeyStatus(collectSampleResult.getLisentersGroupkeyStatus());
        }
        return gls;
    }
    
    /**
     * Query the configuration information and return it in JSON format.
     */
    @GetMapping(params = "search=accurate")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public Page<ConfigInfo> searchConfig(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) {
        Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(100);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        try {
            return persistService.findConfigInfo4Page(pageNo, pageSize, dataId, group, tenant, configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Fuzzy query configuration information. Fuzzy queries based only on content are not allowed, that is, both dataId
     * and group are NULL, but content is not NULL. In this case, all configurations are returned.
     */
    @GetMapping(params = "search=blur")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public Page<ConfigInfo> fuzzySearchConfig(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group, @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "config_tags", required = false) String configTags,
            @RequestParam("pageNo") int pageNo, @RequestParam("pageSize") int pageSize) {
        Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(50);
        if (StringUtils.isNotBlank(appName)) {
            configAdvanceInfo.put("appName", appName);
        }
        if (StringUtils.isNotBlank(configTags)) {
            configAdvanceInfo.put("config_tags", configTags);
        }
        try {
            return persistService.findConfigInfoLike4Page(pageNo, pageSize, dataId, group, tenant, configAdvanceInfo);
        } catch (Exception e) {
            String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
            LOGGER.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }
    
    /**
     * Execute to remove beta operation.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return Execute to operate result.
     */
    @DeleteMapping(params = "beta=true")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public RestResult<Boolean> stopBeta(@RequestParam(value = "dataId") String dataId,
            @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
        try {
            persistService.removeConfigInfo4Beta(dataId, group, tenant);
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return RestResultUtils.failed(500, false, "remove beta data error");
        }
        ConfigChangePublisher
                .notifyConfigChange(new ConfigDataChangeEvent(true, dataId, group, tenant, System.currentTimeMillis()));
        return RestResultUtils.success("stop beta ok", true);
    }
    
    /**
     * Execute to query beta operation.
     *
     * @param dataId dataId string value.
     * @param group  group string value.
     * @param tenant tenant string value.
     * @return RestResult for ConfigInfo4Beta.
     */
    @GetMapping(params = "beta=true")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public RestResult<ConfigInfo4Beta> queryBeta(@RequestParam(value = "dataId") String dataId,
            @RequestParam(value = "group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
        try {
            ConfigInfo4Beta ci = persistService.findConfigInfo4Beta(dataId, group, tenant);
            return RestResultUtils.success("stop beta ok", ci);
        } catch (Throwable e) {
            LOGGER.error("remove beta data error", e);
            return RestResultUtils.failed("remove beta data error");
        }
    }
    
    /**
     * Execute export config operation.
     *
     * @param dataId  dataId string value.
     * @param group   group string value.
     * @param appName appName string value.
     * @param tenant  tenant string value.
     * @param ids     id list value.
     * @return ResponseEntity.
     */
    @GetMapping(params = "export=true")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public ResponseEntity<byte[]> exportConfig(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        ids.removeAll(Collections.singleton(null));
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        List<ConfigAllInfo> dataList = persistService.findAllConfigInfo4Export(dataId, group, tenant, appName, ids);
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
                    metaDataId = metaDataId.substring(0, metaDataId.lastIndexOf(".")) + "~" + metaDataId
                            .substring(metaDataId.lastIndexOf(".") + 1);
                }
                metaData.append(ci.getGroup()).append(".").append(metaDataId).append(".app=")
                        // Fixed use of "\r\n" here
                        .append(ci.getAppName()).append("\r\n");
            }
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, ci.getContent()));
        }
        if (metaData != null) {
            zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA, metaData.toString()));
        }
        
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<byte[]>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    /**
     * new version export config add metadata.yml file record config metadata.
     *
     * @param dataId  dataId string value.
     * @param group   group string value.
     * @param appName appName string value.
     * @param tenant  tenant string value.
     * @param ids     id list value.
     * @return ResponseEntity.
     */
    @GetMapping(params = "exportV2=true")
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public ResponseEntity<byte[]> exportConfigV2(@RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "ids", required = false) List<Long> ids) {
        ids.removeAll(Collections.singleton(null));
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        List<ConfigAllInfo> dataList = persistService.findAllConfigInfo4Export(dataId, group, tenant, appName, ids);
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
            String itemName = ci.getGroup() + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + ci.getDataId();
            zipItemList.add(new ZipUtils.ZipItem(itemName, ci.getContent()));
        }
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(configMetadataItems);
    
        zipItemList.add(new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW,
                YamlParserUtil.dumpObject(configMetadata)));
        HttpHeaders headers = new HttpHeaders();
        String fileName =
                EXPORT_CONFIG_FILE_NAME + DateFormatUtils.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
                        + EXPORT_CONFIG_FILE_NAME_EXT;
        headers.add("Content-Disposition", "attachment;filename=" + fileName);
        return new ResponseEntity<>(ZipUtils.zip(zipItemList), headers, HttpStatus.OK);
    }
    
    /**
     * Execute import and publish config operation.
     *
     * @param request   http servlet request .
     * @param srcUser   src user string value.
     * @param namespace namespace string value.
     * @param policy    policy model.
     * @param file      MultipartFile.
     * @return RestResult Map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "import=true")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public RestResult<Map<String, Object>> importAndPublishConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy, MultipartFile file)
            throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        
        if (Objects.isNull(file)) {
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        
        namespace = NamespaceUtil.processNamespaceParameter(namespace);
        if (StringUtils.isNotBlank(namespace) && persistService.tenantInfoCountByTenantId(namespace) <= 0) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
        }
        List<ConfigAllInfo> configInfoList = new ArrayList<>();
        List<Map<String, String>> unrecognizedList = new ArrayList<>();
        try {
            ZipUtils.UnZipResult unziped = ZipUtils.unzip(file.getBytes());
            ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
            RestResult<Map<String, Object>> errorResult;
            if (metaDataZipItem != null && Constants.CONFIG_EXPORT_METADATA_NEW.equals(metaDataZipItem.getItemName())) {
                // new export
                errorResult = parseImportDataV2(unziped, configInfoList,
                        unrecognizedList, namespace);
            } else {
                errorResult = parseImportData(unziped, configInfoList, unrecognizedList,
                        namespace);
            }
            if (errorResult != null) {
                return errorResult;
            }
        } catch (IOException e) {
            failedData.put("succCount", 0);
            LOGGER.error("parsing data failed", e);
            return RestResultUtils.buildResult(ResultCodeEnum.PARSING_DATA_FAILED, failedData);
        }
        
        if (CollectionUtils.isEmpty(configInfoList)) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        final Timestamp time = TimeUtils.getCurrentTime();
        Map<String, Object> saveResult = persistService
                .batchInsertOrUpdate(configInfoList, srcUser, srcIp, null, time, false, policy);
        for (ConfigInfo configInfo : configInfoList) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant(), time.getTime()));
            ConfigTraceService
                    .logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                            ConfigTraceService.PERSISTENCE_EVENT_PUB, configInfo.getContent());
        }
        // unrecognizedCount
        if (!unrecognizedList.isEmpty()) {
            saveResult.put("unrecognizedCount", unrecognizedList.size());
            saveResult.put("unrecognizedData", unrecognizedList);
        }
        return RestResultUtils.success("导入成功", saveResult);
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
    private RestResult<Map<String, Object>> parseImportData(ZipUtils.UnZipResult unziped,
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
                    return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
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
                    tempDataId = tempDataId.substring(0, tempDataId.lastIndexOf(".")) + "~" + tempDataId
                            .substring(tempDataId.lastIndexOf(".") + 1);
                }
                final String metaDataId = group + "." + tempDataId + ".app";
                ConfigAllInfo ci = new ConfigAllInfo();
                ci.setGroup(group);
                ci.setDataId(dataId);
                ci.setContent(item.getItemData());
                if (metaDataMap.get(metaDataId) != null) {
                    ci.setAppName(metaDataMap.get(metaDataId));
                }
                ci.setTenant(namespace);
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
    private RestResult<Map<String, Object>> parseImportDataV2(ZipUtils.UnZipResult unziped,
            List<ConfigAllInfo> configInfoList, List<Map<String, String>> unrecognizedList, String namespace) {
        ZipUtils.ZipItem metaDataItem = unziped.getMetaDataItem();
        String metaData = metaDataItem.getItemData();
        Map<String, Object> failedData = new HashMap<>(4);
        
        ConfigMetadata configMetadata = YamlParserUtil.loadObject(metaData, ConfigMetadata.class);
        if (configMetadata == null || CollectionUtils.isEmpty(configMetadata.getMetadata())) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
        }
        List<ConfigMetadata.ConfigExportItem> configExportItems = configMetadata.getMetadata();
        // check config metadata
        for (ConfigMetadata.ConfigExportItem configExportItem : configExportItems) {
            if (StringUtils.isBlank(configExportItem.getDataId()) || StringUtils.isBlank(configExportItem.getGroup())
                    || StringUtils.isBlank(configExportItem.getType())) {
                failedData.put("succCount", 0);
                return RestResultUtils.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
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
            ConfigAllInfo ci = new ConfigAllInfo();
            ci.setGroup(group);
            ci.setDataId(dataId);
            ci.setContent(content);
            ci.setType(configExportItem.getType());
            ci.setDesc(configExportItem.getDesc());
            ci.setAppName(configExportItem.getAppName());
            ci.setTenant(namespace);
            configInfoList.add(ci);
        }
        return null;
    }
    
    /**
     * Execute clone config operation.
     *
     * @param request         http servlet request .
     * @param srcUser         src user string value.
     * @param namespace       namespace string value.
     * @param configBeansList config beans list.
     * @param policy          config policy model.
     * @return RestResult for map.
     * @throws NacosException NacosException.
     */
    @PostMapping(params = "clone=true")
    @Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
    public RestResult<Map<String, Object>> cloneConfig(HttpServletRequest request,
            @RequestParam(value = "src_user", required = false) String srcUser,
            @RequestParam(value = "tenant", required = true) String namespace,
            @RequestBody(required = true) List<SameNamespaceCloneConfigBean> configBeansList,
            @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy) throws NacosException {
        Map<String, Object> failedData = new HashMap<>(4);
        if (CollectionUtils.isEmpty(configBeansList)) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NO_SELECTED_CONFIG, failedData);
        }
        configBeansList.removeAll(Collections.singleton(null));
        
        namespace = NamespaceUtil.processNamespaceParameter(namespace);
        if (StringUtils.isNotBlank(namespace) && persistService.tenantInfoCountByTenantId(namespace) <= 0) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
        }
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        Map<Long, SameNamespaceCloneConfigBean> configBeansMap = configBeansList.stream()
                .collect(Collectors.toMap(SameNamespaceCloneConfigBean::getCfgId, cfg -> {
                    idList.add(cfg.getCfgId());
                    return cfg;
                }, (k1, k2) -> k1));
        
        List<ConfigAllInfo> queryedDataList = persistService.findAllConfigInfo4Export(null, null, null, null, idList);
        
        if (queryedDataList == null || queryedDataList.isEmpty()) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        
        List<ConfigAllInfo> configInfoList4Clone = new ArrayList<>(queryedDataList.size());
        
        for (ConfigAllInfo ci : queryedDataList) {
            SameNamespaceCloneConfigBean paramBean = configBeansMap.get(ci.getId());
            ConfigAllInfo ci4save = new ConfigAllInfo();
            ci4save.setTenant(namespace);
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
            configInfoList4Clone.add(ci4save);
        }
        
        if (configInfoList4Clone.isEmpty()) {
            failedData.put("succCount", 0);
            return RestResultUtils.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
        }
        final String srcIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        final Timestamp time = TimeUtils.getCurrentTime();
        Map<String, Object> saveResult = persistService
                .batchInsertOrUpdate(configInfoList4Clone, srcUser, srcIp, null, time, false, policy);
        for (ConfigInfo configInfo : configInfoList4Clone) {
            ConfigChangePublisher.notifyConfigChange(
                    new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant(), time.getTime()));
            ConfigTraceService
                    .logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(),
                            requestIpApp, time.getTime(), InetUtils.getSelfIP(),
                            ConfigTraceService.PERSISTENCE_EVENT_PUB, configInfo.getContent());
        }
        return RestResultUtils.success("Clone Completed Successfully", saveResult);
    }
    
}
