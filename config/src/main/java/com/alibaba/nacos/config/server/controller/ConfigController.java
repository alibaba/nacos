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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.MapUtils;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.filter.ToLeader;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.result.ResultBuilder;
import com.alibaba.nacos.config.server.result.code.ResultCodeEnum;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.utils.InetUtils;
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
import java.util.stream.Collectors;

/**
 * 软负载客户端发布数据专用控制器
 *
 * @author leiwen
 */
@RestController
@RequestMapping(Constants.CONFIG_CONTROLLER_PATH)
public class ConfigController {

	private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

	private static final String NAMESPACE_PUBLIC_KEY = "public";

	private static final String EXPORT_CONFIG_FILE_NAME = "nacos_config_export_";

	private static final String EXPORT_CONFIG_FILE_NAME_EXT = ".zip";

	private static final String EXPORT_CONFIG_FILE_NAME_DATE_FORMAT = "yyyyMMddHHmmss";

	private final ConfigServletInner inner;

	private final PersistService persistService;

	private final ConfigSubService configSubService;

	@Autowired
	public ConfigController(ConfigServletInner configServletInner,
			PersistService persistService, ConfigSubService configSubService) {
		this.inner = configServletInner;
		this.persistService = persistService;
		this.configSubService = configSubService;
	}

	/**
	 * 增加或更新非聚合数据。
	 *
	 * @throws NacosException
	 */
	@PostMapping
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public Boolean publishConfig(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam("content") String content,
			@RequestParam(value = "tag", required = false) String tag,
			@RequestParam(value = "appName", required = false) String appName,
			@RequestParam(value = "src_user", required = false) String srcUser,
			@RequestParam(value = "config_tags", required = false) String configTags,
			@RequestParam(value = "desc", required = false) String desc,
			@RequestParam(value = "use", required = false) String use,
			@RequestParam(value = "effect", required = false) String effect,
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "schema", required = false) String schema)
			throws NacosException {

		final String srcIp = RequestUtil.getRemoteIp(request);
		String requestIpApp = RequestUtil.getAppName(request);
        // check tenant
        ParamUtils.checkTenant(tenant);
		ParamUtils.checkParam(dataId, group, "datumId", content);
		ParamUtils.checkParam(tag);
		Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(10);
		MapUtils.putIfValNoNull(configAdvanceInfo, "config_tags", configTags);
		MapUtils.putIfValNoNull(configAdvanceInfo, "desc", desc);
		MapUtils.putIfValNoNull(configAdvanceInfo, "use", use);
		MapUtils.putIfValNoNull(configAdvanceInfo, "effect", effect);
		MapUtils.putIfValNoNull(configAdvanceInfo, "type", type);
		MapUtils.putIfValNoNull(configAdvanceInfo, "schema", schema);
		ParamUtils.checkParam(configAdvanceInfo);

		if (AggrWhitelist.isAggrDataId(dataId)) {
			log.warn("[aggr-conflict] {} attemp to publish single data, {}, {}",
					RequestUtil.getRemoteIp(request), dataId, group);
			throw new NacosException(NacosException.NO_RIGHT,
					"dataId:" + dataId + " is aggr");
		}

		final Timestamp time = TimeUtils.getCurrentTime();
		String betaIps = request.getHeader("betaIps");
		ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
		configInfo.setType(type);
		if (StringUtils.isBlank(betaIps)) {
			if (StringUtils.isBlank(tag)) {
				persistService.insertOrUpdate(srcIp, srcUser, configInfo, time,
						configAdvanceInfo, true);
			}
			else {
				persistService
						.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, true);
			}
		}
		else {
			// beta publish
			persistService
					.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, true);
		}
		ConfigTraceService.logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(),
				InetUtils.getSelfIp(), ConfigTraceService.PERSISTENCE_EVENT_PUB, content);
		return true;
	}

	/**
	 * 取数据
	 *
	 * @throws ServletException
	 * @throws IOException
	 * @throws NacosException
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
	    tenant = processTenant(tenant);
		// check params
		ParamUtils.checkParam(dataId, group, "datumId", "content");
		ParamUtils.checkParam(tag);

		final String clientIp = RequestUtil.getRemoteIp(request);
		inner.doGetConfig(request, response, dataId, group, tenant, tag, clientIp);
	}

	/**
	 * 取数据
	 *
	 * @throws NacosException
	 */
	@GetMapping(params = "show=all")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public ConfigAllInfo detailConfigInfo(HttpServletRequest request,
			HttpServletResponse response, @RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant)
			throws NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
        // check params
		ParamUtils.checkParam(dataId, group, "datumId", "content");
		return persistService.findConfigAllInfo(dataId, group, tenant);
	}

	/**
	 * 同步删除某个dataId下面所有的聚合前数据
	 *
	 * @throws NacosException
	 */
	@DeleteMapping
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public Boolean deleteConfig(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, //
			@RequestParam("group") String group, //
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam(value = "tag", required = false) String tag)
			throws NacosException {
        // check tenant
        ParamUtils.checkTenant(tenant);
	    ParamUtils.checkParam(dataId, group, "datumId", "rm");
		ParamUtils.checkParam(tag);
		String clientIp = RequestUtil.getRemoteIp(request);
		if (StringUtils.isBlank(tag)) {
			persistService.removeConfigInfo(dataId, group, tenant, clientIp, null);
		}
		else {
			persistService
					.removeConfigInfoTag(dataId, group, tenant, tag, clientIp, null);
		}
		final Timestamp time = TimeUtils.getCurrentTime();
		ConfigTraceService
				.logPersistenceEvent(dataId, group, tenant, null, time.getTime(),
						clientIp, ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
		return true;
	}

	/**
	 * @return java.lang.Boolean
	 * @author klw
	 * @Description: delete configuration based on multiple config ids
	 * @Date 2019/7/5 10:26
	 * @Param [request, response, dataId, group, tenant, tag]
	 */
	@DeleteMapping(params = "delType=ids")
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public RestResult<Boolean> deleteConfigs(HttpServletRequest request,
			HttpServletResponse response, @RequestParam(value = "ids") List<Long> ids) {
		String clientIp = RequestUtil.getRemoteIp(request);
		final Timestamp time = TimeUtils.getCurrentTime();
		List<ConfigInfo> configInfoList = persistService
				.removeConfigInfoByIds(ids, clientIp, null);
		if (!CollectionUtils.isEmpty(configInfoList)) {
			for (ConfigInfo configInfo : configInfoList) {
				ConfigTraceService.logPersistenceEvent(configInfo.getDataId(),
						configInfo.getGroup(), configInfo.getTenant(), null,
						time.getTime(), clientIp,
						ConfigTraceService.PERSISTENCE_EVENT_REMOVE, null);
			}
		}
		return ResultBuilder.buildSuccessResult(true);
	}

	@GetMapping("/catalog")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public RestResult<ConfigAdvanceInfo> getConfigAdvanceInfo(
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<ConfigAdvanceInfo> rr = new RestResult<ConfigAdvanceInfo>();
		ConfigAdvanceInfo configInfo = persistService
				.findConfigAdvanceInfo(dataId, group, tenant);
		rr.setCode(200);
		rr.setData(configInfo);
		return rr;
	}

	/**
	 * 比较MD5
	 */
	@PostMapping("/listener")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public void listener(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		request.setAttribute("org.apache.catalina.ASYNC_SUPPORTED", true);
		String probeModify = request.getParameter("Listening-Configs");
		if (StringUtils.isBlank(probeModify)) {
			throw new IllegalArgumentException("invalid probeModify");
		}

		probeModify = URLDecoder.decode(probeModify, Constants.ENCODE);

		Map<String, String> clientMd5Map;
		try {
			clientMd5Map = MD5Util.getClientMd5Map(probeModify);
		}
		catch (Throwable e) {
			throw new IllegalArgumentException("invalid probeModify");
		}

		// do long-polling
		inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
	}

	/**
	 * 订阅改配置的客户端信息
	 */
	@GetMapping("/listener")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public GroupkeyListenserStatus getListeners(@RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false) String tenant,
			@RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime)
			throws Exception {
		group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
		SampleResult collectSampleResult = configSubService
				.getCollectSampleResult(dataId, group, tenant, sampleTime);
		GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
		gls.setCollectStatus(200);
		if (collectSampleResult.getLisentersGroupkeyStatus() != null) {
			gls.setLisentersGroupkeyStatus(
					collectSampleResult.getLisentersGroupkeyStatus());
		}
		return gls;
	}

	/**
	 * 查询配置信息，返回JSON格式。
	 */
	@GetMapping(params = "search=accurate")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public Page<ConfigInfo> searchConfig(@RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
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
			return persistService
					.findConfigInfo4Page(pageNo, pageSize, dataId, group, tenant,
							configAdvanceInfo);
		}
		catch (Exception e) {
			String errorMsg =
					"serialize page error, dataId=" + dataId + ", group=" + group;
			log.error(errorMsg, e);
			throw new RuntimeException(errorMsg, e);
		}
	}

	/**
	 * 模糊查询配置信息。不允许只根据内容模糊查询，即dataId和group都为NULL，但content不是NULL。这种情况下，返回所有配置。
	 */
	@GetMapping(params = "search=blur")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public Page<ConfigInfo> fuzzySearchConfig(@RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
			@RequestParam(value = "appName", required = false) String appName,
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
			return persistService
					.findConfigInfoLike4Page(pageNo, pageSize, dataId, group, tenant,
							configAdvanceInfo);
		}
		catch (Exception e) {
			String errorMsg =
					"serialize page error, dataId=" + dataId + ", group=" + group;
			log.error(errorMsg, e);
			throw new RuntimeException(errorMsg, e);
		}
	}

	@DeleteMapping(params = "beta=true")
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public RestResult<Boolean> stopBeta(@RequestParam(value = "dataId") String dataId,
			@RequestParam(value = "group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<Boolean> rr = new RestResult<Boolean>();
		try {
			persistService.removeConfigInfo4Beta(dataId, group, tenant);
		}
		catch (Exception e) {
			log.error("remove beta data error", e);
			rr.setCode(500);
			rr.setData(false);
			rr.setMessage("remove beta data error");
			return rr;
		}
		rr.setCode(200);
		rr.setData(true);
		rr.setMessage("stop beta ok");
		return rr;
	}

	@GetMapping(params = "beta=true")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public RestResult<ConfigInfo4Beta> queryBeta(
			@RequestParam(value = "dataId") String dataId,
			@RequestParam(value = "group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<ConfigInfo4Beta> rr = new RestResult<ConfigInfo4Beta>();
		try {
			ConfigInfo4Beta ci = persistService
					.findConfigInfo4Beta(dataId, group, tenant);
			rr.setCode(200);
			rr.setData(ci);
			rr.setMessage("stop beta ok");
			return rr;
		}
		catch (Exception e) {
			log.error("remove beta data error", e);
			rr.setCode(500);
			rr.setMessage("remove beta data error");
			return rr;
		}
	}

	@GetMapping(params = "export=true")
	@Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
	public ResponseEntity<byte[]> exportConfig(
			@RequestParam(value = "dataId", required = false) String dataId,
			@RequestParam(value = "group", required = false) String group,
			@RequestParam(value = "appName", required = false) String appName,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam(value = "ids", required = false) List<Long> ids) {
		ids.removeAll(Collections.singleton(null));
		tenant = processTenant(tenant);
		List<ConfigAllInfo> dataList = persistService
				.findAllConfigInfo4Export(dataId, group, tenant, appName, ids);
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
					metaDataId =
							metaDataId.substring(0, metaDataId.lastIndexOf(".")) + "~"
									+ metaDataId
									.substring(metaDataId.lastIndexOf(".") + 1);
				}
				metaData.append(ci.getGroup()).append(".").append(metaDataId)
						.append(".app=")
						// Fixed use of "\r\n" here
						.append(ci.getAppName()).append("\r\n");
			}
			String itemName = ci.getGroup() + "/" + ci.getDataId();
			zipItemList.add(new ZipUtils.ZipItem(itemName, ci.getContent()));
		}
		if (metaData != null) {
			zipItemList.add(new ZipUtils.ZipItem(".meta.yml", metaData.toString()));
		}

		HttpHeaders headers = new HttpHeaders();
		String fileName = EXPORT_CONFIG_FILE_NAME + DateFormatUtils
				.format(new Date(), EXPORT_CONFIG_FILE_NAME_DATE_FORMAT)
				+ EXPORT_CONFIG_FILE_NAME_EXT;
		headers.add("Content-Disposition", "attachment;filename=" + fileName);
		return new ResponseEntity<byte[]>(ZipUtils.zip(zipItemList), headers,
				HttpStatus.OK);
	}

	@PostMapping(params = "import=true")
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public RestResult<Map<String, Object>> importAndPublishConfig(
			HttpServletRequest request,
			@RequestParam(value = "src_user", required = false) String srcUser,
			@RequestParam(value = "namespace", required = false) String namespace,
			@RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy,
			MultipartFile file) throws NacosException {
		Map<String, Object> failedData = new HashMap<>(4);

		if (Objects.isNull(file)) {
			return ResultBuilder.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
		}

		if (StringUtils.isNotBlank(namespace)) {
			if (persistService.tenantInfoCountByTenantId(namespace) <= 0) {
				failedData.put("succCount", 0);
				return ResultBuilder
						.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
			}
		}
		List<ConfigAllInfo> configInfoList = null;
		try {
			ZipUtils.UnZipResult unziped = ZipUtils.unzip(file.getBytes());
			ZipUtils.ZipItem metaDataZipItem = unziped.getMetaDataItem();
			Map<String, String> metaDataMap = new HashMap<>(16);
			if (metaDataZipItem != null) {
				String metaDataStr = metaDataZipItem.getItemData();
				String[] metaDataArr = metaDataStr.split("\r\n");
				for (String metaDataItem : metaDataArr) {
					String[] metaDataItemArr = metaDataItem.split("=");
					if (metaDataItemArr.length != 2) {
						failedData.put("succCount", 0);
						return ResultBuilder
								.buildResult(ResultCodeEnum.METADATA_ILLEGAL, failedData);
					}
					metaDataMap.put(metaDataItemArr[0], metaDataItemArr[1]);
				}
			}
			List<ZipUtils.ZipItem> itemList = unziped.getZipItemList();
			if (itemList != null && !itemList.isEmpty()) {
				configInfoList = new ArrayList<>(itemList.size());
				for (ZipUtils.ZipItem item : itemList) {
					String[] groupAdnDataId = item.getItemName().split("/");
					if (!item.getItemName().contains("/") || groupAdnDataId.length != 2) {
						failedData.put("succCount", 0);
						return ResultBuilder
								.buildResult(ResultCodeEnum.DATA_VALIDATION_FAILED,
										failedData);
					}
					String group = groupAdnDataId[0];
					String dataId = groupAdnDataId[1];
					String tempDataId = dataId;
					if (tempDataId.contains(".")) {
						tempDataId =
								tempDataId.substring(0, tempDataId.lastIndexOf(".")) + "~"
										+ tempDataId
										.substring(tempDataId.lastIndexOf(".") + 1);
					}
					String metaDataId = group + "." + tempDataId + ".app";
					ConfigAllInfo ci = new ConfigAllInfo();
					ci.setTenant(namespace);
					ci.setGroup(group);
					ci.setDataId(dataId);
					ci.setContent(item.getItemData());
					if (metaDataMap.get(metaDataId) != null) {
						ci.setAppName(metaDataMap.get(metaDataId));
					}
					configInfoList.add(ci);
				}
			}
		}
		catch (IOException e) {
			failedData.put("succCount", 0);
			log.error("parsing data failed", e);
			return ResultBuilder
					.buildResult(ResultCodeEnum.PARSING_DATA_FAILED, failedData);
		}
		if (configInfoList == null || configInfoList.isEmpty()) {
			failedData.put("succCount", 0);
			return ResultBuilder.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
		}
		final String srcIp = RequestUtil.getRemoteIp(request);
		String requestIpApp = RequestUtil.getAppName(request);
		final Timestamp time = TimeUtils.getCurrentTime();
		Map<String, Object> saveResult = persistService
				.batchInsertOrUpdate(configInfoList, srcUser, srcIp, null, time, false,
						policy);
		for (ConfigInfo configInfo : configInfoList) {
			EventDispatcher.fireEvent(
					new ConfigDataChangeEvent(false, configInfo.getDataId(),
							configInfo.getGroup(), configInfo.getTenant(),
							time.getTime()));
			ConfigTraceService
					.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
							configInfo.getTenant(), requestIpApp, time.getTime(),
							InetUtils.getSelfIp(), ConfigTraceService.PERSISTENCE_EVENT_PUB,
							configInfo.getContent());
		}
		return ResultBuilder.buildSuccessResult("导入成功", saveResult);
	}

	@PostMapping(params = "clone=true")
	@ToLeader
	@Secured(action = ActionTypes.WRITE, parser = ConfigResourceParser.class)
	public RestResult<Map<String, Object>> cloneConfig(HttpServletRequest request,
			@RequestParam(value = "src_user", required = false) String srcUser,
			@RequestParam(value = "tenant", required = true) String namespace,
			@RequestBody(required = true) List<SameNamespaceCloneConfigBean> configBeansList,
			@RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy)
			throws NacosException {
		Map<String, Object> failedData = new HashMap<>(4);
		if (CollectionUtils.isEmpty(configBeansList)) {
			failedData.put("succCount", 0);
			return ResultBuilder
					.buildResult(ResultCodeEnum.NO_SELECTED_CONFIG, failedData);
		}
		configBeansList.removeAll(Collections.singleton(null));

		if (NAMESPACE_PUBLIC_KEY.equalsIgnoreCase(namespace)) {
			namespace = "";
		}
		else if (persistService.tenantInfoCountByTenantId(namespace) <= 0) {
			failedData.put("succCount", 0);
			return ResultBuilder
					.buildResult(ResultCodeEnum.NAMESPACE_NOT_EXIST, failedData);
		}

		List<Long> idList = new ArrayList<>(configBeansList.size());
		Map<Long, SameNamespaceCloneConfigBean> configBeansMap = configBeansList.stream()
				.collect(Collectors.toMap(SameNamespaceCloneConfigBean::getCfgId, cfg -> {
					idList.add(cfg.getCfgId());
					return cfg;
				}, (k1, k2) -> k1));

		List<ConfigAllInfo> queryedDataList = persistService
				.findAllConfigInfo4Export(null, null, null, null, idList);

		if (queryedDataList == null || queryedDataList.isEmpty()) {
			failedData.put("succCount", 0);
			return ResultBuilder.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
		}

		List<ConfigAllInfo> configInfoList4Clone = new ArrayList<>(
				queryedDataList.size());

		for (ConfigAllInfo ci : queryedDataList) {
			SameNamespaceCloneConfigBean prarmBean = configBeansMap.get(ci.getId());
			ConfigAllInfo ci4save = new ConfigAllInfo();
			ci4save.setTenant(namespace);
			ci4save.setType(ci.getType());
			ci4save.setGroup(
					(prarmBean != null && StringUtils.isNotBlank(prarmBean.getGroup())) ?
							prarmBean.getGroup() :
							ci.getGroup());
			ci4save.setDataId(
					(prarmBean != null && StringUtils.isNotBlank(prarmBean.getDataId())) ?
							prarmBean.getDataId() :
							ci.getDataId());
			ci4save.setContent(ci.getContent());
			if (StringUtils.isNotBlank(ci.getAppName())) {
				ci4save.setAppName(ci.getAppName());
			}
			configInfoList4Clone.add(ci4save);
		}

		if (configInfoList4Clone.isEmpty()) {
			failedData.put("succCount", 0);
			return ResultBuilder.buildResult(ResultCodeEnum.DATA_EMPTY, failedData);
		}
		final String srcIp = RequestUtil.getRemoteIp(request);
		String requestIpApp = RequestUtil.getAppName(request);
		final Timestamp time = TimeUtils.getCurrentTime();
		Map<String, Object> saveResult = persistService
				.batchInsertOrUpdate(configInfoList4Clone, srcUser, srcIp, null, time,
						false, policy);
		for (ConfigInfo configInfo : configInfoList4Clone) {
			EventDispatcher.fireEvent(
					new ConfigDataChangeEvent(false, configInfo.getDataId(),
							configInfo.getGroup(), configInfo.getTenant(),
							time.getTime()));
			ConfigTraceService
					.logPersistenceEvent(configInfo.getDataId(), configInfo.getGroup(),
							configInfo.getTenant(), requestIpApp, time.getTime(),
							InetUtils.getSelfIp(), ConfigTraceService.PERSISTENCE_EVENT_PUB,
							configInfo.getContent());
		}
		return ResultBuilder.buildSuccessResult("克隆成功", saveResult);
	}

	private String processTenant(String tenant) {
		if (StringUtils.isEmpty(tenant) || NAMESPACE_PUBLIC_KEY
				.equalsIgnoreCase(tenant)) {
			return "";
		}
		return tenant;
	}

}
