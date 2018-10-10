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

import java.io.IOException;
import java.net.URLDecoder;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.RestResult;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.merge.MergeDatumService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.SystemConfig;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;

/**
 * 软负载客户端发布数据专用控制器
 * 
 * @author leiwen
 * 
 */
@Controller
@RequestMapping(Constants.CONFIG_CONTROLLER_PATH)
public class ConfigController {
    
	private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
	
    private final transient ConfigServletInner inner;
    
	private final transient PersistService persistService;
	
	private final transient MergeDatumService mergeService;

	private final transient ConfigSubService configSubService;

	@Autowired
	public ConfigController(ConfigServletInner configServletInner, PersistService persistService, MergeDatumService mergeService,
	                        ConfigSubService configSubService) {
		this.inner = configServletInner;
		this.persistService = persistService;
		this.mergeService = mergeService;
		this.configSubService = configSubService;
	}

	/**
	 * 增加或更新非聚合数据。
	 * 
	 * @throws NacosException
	 */
	@RequestMapping(method = RequestMethod.POST)
	@ResponseBody
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
			@RequestParam(value = "schema", required = false) String schema) throws NacosException {
		final String srcIp = RequestUtil.getRemoteIp(request);
		String requestIpApp = RequestUtil.getAppName(request);
		ParamUtils.checkParam(dataId, group, "datumId", content);
		ParamUtils.checkParam(tag);

		Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(10);
		if (configTags != null) {
			configAdvanceInfo.put("config_tags", configTags);
		}
		if (desc != null) {
			configAdvanceInfo.put("desc", desc);
		}
		if (use != null) {
			configAdvanceInfo.put("use", use);
		}
		if (effect != null) {
			configAdvanceInfo.put("effect", effect);
		}
		if (type != null) {
			configAdvanceInfo.put("type", type);
		}
		if (schema != null) {
			configAdvanceInfo.put("schema", schema);
		}
		ParamUtils.checkParam(configAdvanceInfo);

		if (AggrWhitelist.isAggrDataId(dataId)) {
			log.warn("[aggr-conflict] {} attemp to publish single data, {}, {}",
				RequestUtil.getRemoteIp(request), dataId, group);
			throw new NacosException(NacosException.NO_RIGHT, "dataId:" + dataId + " is aggr");
		}

		final Timestamp time = TimeUtils.getCurrentTime();
		String betaIps = request.getHeader("betaIps");
		ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
		if (StringUtils.isBlank(betaIps)) {
			if (StringUtils.isBlank(tag)) {
				persistService.insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, false);
				EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, dataId, group, tenant, time.getTime()));
			} else {
				persistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser, time, false);
				EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, dataId, group, tenant, tag, time.getTime()));
			}
		} else { // beta publish
			persistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser, time, false);
			EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, dataId, group, tenant, time.getTime()));
		}
		ConfigTraceService.logPersistenceEvent(dataId, group, tenant, requestIpApp, time.getTime(),
				SystemConfig.LOCAL_IP, ConfigTraceService.PERSISTENCE_EVENT_PUB, content);

		return true;
	}

	/**
	 * 取数据
	 * 
	 * @throws ServletException
	 * @throws IOException
	 * @throws NacosException
	 */
	@RequestMapping(method = RequestMethod.GET)
	public void getConfig(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam(value = "tag", required = false) String tag)
			throws IOException, ServletException, NacosException {
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
	@RequestMapping(params = "show=all", method = RequestMethod.GET)
	@ResponseBody
	public ConfigAllInfo detailConfigInfo(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant)
			throws NacosException {
		// check params
		ParamUtils.checkParam(dataId, group, "datumId", "content");
		return persistService.findConfigAllInfo(dataId, group, tenant);
	}

	/**
	 * 同步删除某个dataId下面所有的聚合前数据
	 * 
	 * @throws NacosException
	 */
	@RequestMapping(method = RequestMethod.DELETE)
	@ResponseBody
	public Boolean deleteConfig(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, //
			@RequestParam("group") String group, //
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam(value = "tag", required = false) String tag) throws NacosException {
		ParamUtils.checkParam(dataId, group, "datumId", "rm");
		ParamUtils.checkParam(tag);
		String clientIp = RequestUtil.getRemoteIp(request);
		if (StringUtils.isBlank(tag)) {
			persistService.removeAggrConfigInfo(dataId, group, tenant);
		}
		mergeService.addMergeTask(dataId, group, tenant, tag, clientIp);
		return true;
	}
	
	
	@RequestMapping(value = "/catalog", method = RequestMethod.GET)
	@ResponseBody
	public RestResult<ConfigAdvanceInfo> getConfigAdvanceInfo(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<ConfigAdvanceInfo> rr = new RestResult<ConfigAdvanceInfo>();
		ConfigAdvanceInfo configInfo = persistService.findConfigAdvanceInfo(dataId, group, tenant);
		rr.setCode(200);
		rr.setData(configInfo);
		return rr;
	}
	
	/**
	 * 比较MD5
	 */
	@RequestMapping(value = "/listener", method = RequestMethod.POST)
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
		} catch (Throwable e) {
			throw new IllegalArgumentException("invalid probeModify");
		}

		// do long-polling
		inner.doPollingConfig(request, response, clientMd5Map, probeModify.length());
	}
	
	/*
	 * 订阅改配置的客户端信息
	 */
	@RequestMapping(value = "/listener", method = RequestMethod.GET)
	@ResponseBody
	public GroupkeyListenserStatus getListeners(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId, @RequestParam("group") String group,
			@RequestParam(value = "tenant", required = false) String tenant,
			@RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime)
			throws Exception {
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
	 * 查询配置信息，返回JSON格式。
	 */
	@RequestMapping(params = "search=accurate", method = RequestMethod.GET)
	@ResponseBody
    public Page<ConfigInfo> searchConfig(HttpServletRequest request,
			HttpServletResponse response,
			@RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
			@RequestParam(value = "appName", required = false) String appName,
			@RequestParam(value = "tenant", required = false, defaultValue=StringUtils.EMPTY) String tenant, 
			@RequestParam(value = "config_tags", required = false) String configTags,
			@RequestParam("pageNo") int pageNo,
			@RequestParam("pageSize") int pageSize) {
		Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(100);
		if (StringUtils.isNotBlank(appName)) {
			configAdvanceInfo.put("appName", appName);
		}
		if (StringUtils.isNotBlank(configTags)) {
			configAdvanceInfo.put("config_tags", configTags);
		}
		try {
			return persistService.findConfigInfo4Page(pageNo, pageSize, dataId, group, tenant,
					configAdvanceInfo);
		} catch (Exception e) {
			String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
			log.error(errorMsg, e);
			throw new RuntimeException(errorMsg, e);
		}
	}
    
	/**
	 * 模糊查询配置信息。不允许只根据内容模糊查询，即dataId和group都为NULL，但content不是NULL。这种情况下，返回所有配置。
	 */
	@RequestMapping(params = "search=blur", method = RequestMethod.GET)
	@ResponseBody
	public Page<ConfigInfo> fuzzySearchConfig(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("dataId") String dataId,
			@RequestParam("group") String group,
			@RequestParam(value = "appName", required = false) String appName,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
			@RequestParam(value = "config_tags", required = false) String configTags,
			@RequestParam("pageNo") int pageNo,
			@RequestParam("pageSize") int pageSize) {
		Map<String, Object> configAdvanceInfo = new HashMap<String, Object>(50);
		if (StringUtils.isNotBlank(appName)) {
			configAdvanceInfo.put("appName", appName);
		}
		if (StringUtils.isNotBlank(configTags)) {
			configAdvanceInfo.put("config_tags", configTags);
		}
		try {
			return persistService.findConfigInfoLike4Page(pageNo, pageSize, dataId, group, tenant,
					configAdvanceInfo);
		} catch (Exception e) {
			String errorMsg = "serialize page error, dataId=" + dataId + ", group=" + group;
			log.error(errorMsg, e);
			throw new RuntimeException(errorMsg, e);
		}
	}
	
	@RequestMapping(params = "beta=true", method = RequestMethod.DELETE)
	@ResponseBody
	public RestResult<Boolean> stopBeta(HttpServletRequest request, HttpServletResponse response,
			@RequestParam(value = "dataId") String dataId, @RequestParam(value = "group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<Boolean> rr = new RestResult<Boolean>();
		try {
			persistService.removeConfigInfo4Beta(dataId, group, tenant);
		} catch (Throwable e) {
			log.error("remove beta data error", e);
			rr.setCode(500);
			rr.setData(false);
			rr.setMessage("remove beta data error");
			return rr;
		}
		EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, dataId, group, tenant, System.currentTimeMillis()));
		rr.setCode(200);
		rr.setData(true);
		rr.setMessage("stop beta ok");
		return rr;
	}

	@RequestMapping(params = "beta=true", method = RequestMethod.GET)
	@ResponseBody
	public RestResult<ConfigInfo4Beta> queryBeta(HttpServletRequest request, HttpServletResponse response,
			@RequestParam(value = "dataId") String dataId, @RequestParam(value = "group") String group,
			@RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant) {
		RestResult<ConfigInfo4Beta> rr = new RestResult<ConfigInfo4Beta>();
		try {
			ConfigInfo4Beta ci = persistService.findConfigInfo4Beta(dataId, group, tenant);
			rr.setCode(200);
			rr.setData(ci);
			rr.setMessage("stop beta ok");
			return rr;
		} catch (Throwable e) {
			log.error("remove beta data error", e);
			rr.setCode(500);
			rr.setMessage("remove beta data error");
			return rr;
		}
	}
}
