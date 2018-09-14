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
package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.impl.ServerHttpAgent;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.LogUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.config.utils.TenantUtil;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerHelper;
import com.alibaba.nacos.client.utils.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Config Impl
 * @author Nacos
 *
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosConfigService implements ConfigService {

	final static public Logger log = LogUtils.logger(NacosConfigService.class);
	public final long POST_TIMEOUT = 3000L;
	/**
	 * http agent
	 */
	private ServerHttpAgent agent;
	/**
	 * longpulling
	 */
	private ClientWorker worker;
	private String namespace;
	private String encode;
	private ConfigFilterChainManager configFilterChainManager = new ConfigFilterChainManager();

	public NacosConfigService(Properties properties) throws NacosException {
		String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
		if (StringUtils.isBlank(encodeTmp)) {
			encode = Constants.ENCODE;
		} else {
			encode = encodeTmp.trim();
		}
		String namespaceTmp = properties.getProperty(PropertyKeyConst.NAMESPACE);
		if (StringUtils.isBlank(namespaceTmp)) {
			namespace = TenantUtil.getUserTenant();
			properties.put(PropertyKeyConst.NAMESPACE, namespace);
		} else {
			namespace = namespaceTmp;
			properties.put(PropertyKeyConst.NAMESPACE, namespace);
		}
		agent = new ServerHttpAgent(properties);
		agent.start();
		worker = new ClientWorker(agent, configFilterChainManager);
	}

	@Override
	public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
		return getConfigInner(namespace, dataId, group, timeoutMs);
	}

	@Override
	public void addListener(String dataId, String group, Listener listener) throws NacosException {
		worker.addTenantListeners(dataId, group, Arrays.asList(listener));
	}

	@Override
	public boolean publishConfig(String dataId, String group, String content) throws NacosException {
		return publishConfigInner(namespace, dataId, group, null, null, null, content);
	}

	@Override
	public boolean removeConfig(String dataId, String group) throws NacosException {
		return removeConfigInner(namespace, dataId, group, null);
	}

	@Override
	public void removeListener(String dataId, String group, Listener listener) {
		worker.removeTenantListener(dataId, group, listener);
	}

	private String getConfigInner(String tenant, String dataId, String group, long timeoutMs) throws NacosException {
		group = null2defaultGroup(group);
		ParamUtils.checkKeyParam(dataId, group);
		ConfigResponse cr = new ConfigResponse();

		cr.setDataId(dataId);
		cr.setTenant(tenant);
		cr.setGroup(group);

		// 优先使用本地配置
		String content = LocalConfigInfoProcessor.getFailover(agent.getName(), dataId, group, tenant);
		if (content != null) {
			log.warn(agent.getName(), "[get-config] get failover ok, dataId={}, group={}, tenant={}, config={}", dataId,
					group, tenant, ContentUtils.truncateContent(content));
			cr.setContent(content);
			configFilterChainManager.doFilter(null, cr);
			content = cr.getContent();
			return content;
		}

		try {
			content = worker.getServerConfig(dataId, group, tenant, timeoutMs);
			cr.setContent(content);
			configFilterChainManager.doFilter(null, cr);
			content = cr.getContent();
			return content;
		} catch (NacosException ioe) {
			if (NacosException.NO_RIGHT == ioe.getErrCode()) {
				throw ioe;
			}
			log.warn("NACOS-0003",
					LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0003", "环境问题", "get from server error"));
			log.warn(agent.getName(), "[get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
					dataId, group, tenant, ioe.toString());
		}

		log.warn(agent.getName(), "[get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}", dataId,
				group, tenant, ContentUtils.truncateContent(content));
		content = LocalConfigInfoProcessor.getSnapshot(agent.getName(), dataId, group, tenant);
		cr.setContent(content);
		configFilterChainManager.doFilter(null, cr);
		content = cr.getContent();
		return content;
	}

	private String null2defaultGroup(String group) {
		return (null == group) ? Constants.DEFAULT_GROUP : group.trim();
	}

	private boolean removeConfigInner(String tenant, String dataId, String group, String tag) throws NacosException {
		group = null2defaultGroup(group);
		ParamUtils.checkKeyParam(dataId, group);
		String url = Constants.CONFIG_CONTROLLER_PATH;
		List<String> params = new ArrayList<String>();
		params.add("dataId");
		params.add(dataId);
		params.add("group");
		params.add(group);
		if (StringUtils.isNotEmpty(tenant)) {
			params.add("tenant");
			params.add(tenant);
		}
		if (StringUtils.isNotEmpty(tag)) {
			params.add("tag");
			params.add(tag);
		}
		HttpResult result = null;
		try {
			result = agent.httpDelete(url, null, params, encode, POST_TIMEOUT);
		} catch (IOException ioe) {
			log.warn("[remove] error, " + dataId + ", " + group + ", " + tenant + ", msg: " + ioe.toString());
			return false;
		}

		if (HttpURLConnection.HTTP_OK == result.code) {
			log.info(agent.getName(), "[remove] ok, dataId={}, group={}, tenant={}", dataId, group, tenant);
			return true;
		} else if (HttpURLConnection.HTTP_FORBIDDEN == result.code) {
			log.warn(agent.getName(), "[remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", dataId, group,
					tenant, result.code, result.content);
			throw new NacosException(result.code, result.content);
		} else {
			log.warn(agent.getName(), "[remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", dataId, group,
					tenant, result.code, result.content);
			return false;
		}
	}

	private boolean publishConfigInner(String tenant, String dataId, String group, String tag, String appName,
			String betaIps, String content) throws NacosException {
		group = null2defaultGroup(group);
		ParamUtils.checkParam(dataId, group, content);

		ConfigRequest cr = new ConfigRequest();
		cr.setDataId(dataId);
		cr.setTenant(tenant);
		cr.setGroup(group);
		cr.setContent(content);
		configFilterChainManager.doFilter(cr, null);
		content = cr.getContent();

		String url = Constants.CONFIG_CONTROLLER_PATH;
		List<String> params = new ArrayList<String>();
		params.add("dataId");
		params.add(dataId);
		params.add("group");
		params.add(group);
		params.add("content");
		params.add(content);
		if (StringUtils.isNotEmpty(tenant)) {
			params.add("tenant");
			params.add(tenant);
		}
		if (StringUtils.isNotEmpty(appName)) {
			params.add("appName");
			params.add(appName);
		}
		if (StringUtils.isNotEmpty(tag)) {
			params.add("tag");
			params.add(tag);
		}

		List<String> headers = new ArrayList<String>();
		if (StringUtils.isNotEmpty(betaIps)) {
			headers.add("betaIps");
			headers.add(betaIps);
		}

		HttpResult result = null;
		try {
			result = agent.httpPost(url, headers, params, encode, POST_TIMEOUT);
		} catch (IOException ioe) {
			log.warn("NACOS-0006",
					LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0006", "环境问题", "[publish-single] exception"));
			log.warn(agent.getName(), "[publish-single] exception, dataId={}, group={}, msg={}", dataId, group,
					ioe.toString());
			return false;
		}

		if (HttpURLConnection.HTTP_OK == result.code) {
			log.info(agent.getName(), "[publish-single] ok, dataId={}, group={}, tenant={}, config={}", dataId, group,
					tenant, ContentUtils.truncateContent(content));
			return true;
		} else if (HttpURLConnection.HTTP_FORBIDDEN == result.code) {
			log.warn(agent.getName(), "[publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}", dataId,
					group, tenant, result.code, result.content);
			throw new NacosException(result.code, result.content);
		} else {
			log.warn(agent.getName(), "[publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}", dataId,
					group, tenant, result.code, result.content);
			return false;
		}

	}

	@Override
	public String getServerStatus() {
		if (worker.isHealthServer()) {
			return "UP";
		} else {
			return "DOWN";
		}
	}

}
