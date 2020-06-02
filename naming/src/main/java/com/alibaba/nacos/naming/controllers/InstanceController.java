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
package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.NamingResponseCode;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.auth.ActionTypes;
import com.alibaba.nacos.core.auth.Secured;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.ClientInfo;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.web.CanDistro;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instance operation controller
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance")
public class InstanceController {

	@Autowired
	private SwitchDomain switchDomain;

	@Autowired
	private PushService pushService;

	@Autowired
	private ServiceManager serviceManager;

	private DataSource pushDataSource = new DataSource() {

		@Override
		public String getData(PushService.PushClient client) {

            ObjectNode result = JacksonUtils.createEmptyJsonNode();
			try {
				result = doSrvIPXT(client.getNamespaceId(), client.getServiceName(),
						client.getAgent(), client.getClusters(),
						client.getSocketAddr().getAddress().getHostAddress(), 0,
						StringUtils.EMPTY, false, StringUtils.EMPTY, StringUtils.EMPTY,
						false);
			}
			catch (Exception e) {
				Loggers.SRV_LOG.warn("PUSH-SERVICE: service is not modified", e);
			}

			// overdrive the cache millis to push mode
			result.put("cacheMillis",
					switchDomain.getPushCacheMillis(client.getServiceName()));

			return result.toString();
		}
	};

	@CanDistro
	@PostMapping
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
	public String register(HttpServletRequest request) throws Exception {

		final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		final String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);

		final Instance instance = parseInstance(request);

		serviceManager.registerInstance(namespaceId, serviceName, instance);
		return "ok";
	}

	@CanDistro
	@DeleteMapping
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
	public String deregister(HttpServletRequest request) throws Exception {
		Instance instance = getIPAddress(request);
		String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);
		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);

		Service service = serviceManager.getService(namespaceId, serviceName);
		if (service == null) {
			Loggers.SRV_LOG
					.warn("remove instance from non-exist service: {}", serviceName);
			return "ok";
		}

		serviceManager.removeInstance(namespaceId, serviceName, instance.isEphemeral(),
				instance);
		return "ok";
	}

	@CanDistro
	@PutMapping
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
	public String update(HttpServletRequest request) throws Exception {
		final String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		final String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);
		final Instance instance = parseInstance(request);

		String agent = WebUtils.getUserAgent(request);

		ClientInfo clientInfo = new ClientInfo(agent);

		if (clientInfo.type == ClientInfo.ClientType.JAVA
				&& clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
			serviceManager.updateInstance(namespaceId, serviceName, instance);
		}
		else {
			serviceManager.registerInstance(namespaceId, serviceName, instance);
		}
		return "ok";
	}

	@CanDistro
	@PatchMapping
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
	public String patch(HttpServletRequest request) throws Exception {
		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);
		String ip = WebUtils.required(request, "ip");
		String port = WebUtils.required(request, "port");
		String cluster = WebUtils
				.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
		if (StringUtils.isBlank(cluster)) {
			cluster = WebUtils
					.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
		}

		Instance instance = serviceManager
				.getInstance(namespaceId, serviceName, cluster, ip,
						Integer.parseInt(port));
		if (instance == null) {
			throw new IllegalArgumentException("instance not found");
		}

		String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);
		if (StringUtils.isNotBlank(metadata)) {
			instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
		}
		String app = WebUtils.optional(request, "app", StringUtils.EMPTY);
		if (StringUtils.isNotBlank(app)) {
			instance.setApp(app);
		}
		String weight = WebUtils.optional(request, "weight", StringUtils.EMPTY);
		if (StringUtils.isNotBlank(weight)) {
			instance.setWeight(Double.parseDouble(weight));
		}
		String healthy = WebUtils.optional(request, "healthy", StringUtils.EMPTY);
		if (StringUtils.isNotBlank(healthy)) {
			instance.setHealthy(BooleanUtils.toBoolean(healthy));
		}
		String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
		if (StringUtils.isNotBlank(enabledString)) {
			instance.setEnabled(BooleanUtils.toBoolean(enabledString));
		}
		instance.setLastBeat(System.currentTimeMillis());
		instance.validate();
		serviceManager.updateInstance(namespaceId, serviceName, instance);
		return "ok";
	}

	@GetMapping("/list")
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
	public ObjectNode list(HttpServletRequest request) throws Exception {

		String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);

		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		String agent = WebUtils.getUserAgent(request);
		String clusters = WebUtils.optional(request, "clusters", StringUtils.EMPTY);
		String clientIP = WebUtils.optional(request, "clientIP", StringUtils.EMPTY);
		Integer udpPort = Integer.parseInt(WebUtils.optional(request, "udpPort", "0"));
		String env = WebUtils.optional(request, "env", StringUtils.EMPTY);
		boolean isCheck = Boolean
				.parseBoolean(WebUtils.optional(request, "isCheck", "false"));

		String app = WebUtils.optional(request, "app", StringUtils.EMPTY);

		String tenant = WebUtils.optional(request, "tid", StringUtils.EMPTY);

		boolean healthyOnly = Boolean
				.parseBoolean(WebUtils.optional(request, "healthyOnly", "false"));

		return doSrvIPXT(namespaceId, serviceName, agent, clusters, clientIP, udpPort,
				env, isCheck, app, tenant, healthyOnly);
	}

	@GetMapping
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.READ)
	public ObjectNode detail(HttpServletRequest request) throws Exception {

		String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);
		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		String cluster = WebUtils.optional(request, CommonParams.CLUSTER_NAME,
				UtilsAndCommons.DEFAULT_CLUSTER_NAME);
		String ip = WebUtils.required(request, "ip");
		int port = Integer.parseInt(WebUtils.required(request, "port"));

		Service service = serviceManager.getService(namespaceId, serviceName);
		if (service == null) {
			throw new NacosException(NacosException.NOT_FOUND,
					"no service " + serviceName + " found!");
		}

		List<String> clusters = new ArrayList<>();
		clusters.add(cluster);

		List<Instance> ips = service.allIPs(clusters);
		if (ips == null || ips.isEmpty()) {
			throw new NacosException(NacosException.NOT_FOUND,
					"no ips found for cluster " + cluster + " in service " + serviceName);
		}

		for (Instance instance : ips) {
			if (instance.getIp().equals(ip) && instance.getPort() == port) {
			    ObjectNode result = JacksonUtils.createEmptyJsonNode();
				result.put("service", serviceName);
				result.put("ip", ip);
				result.put("port", port);
				result.put("clusterName", cluster);
				result.put("weight", instance.getWeight());
				result.put("healthy", instance.isHealthy());
				result.put("metadata", JacksonUtils.transferToJsonNode(instance.getMetadata()));
				result.put("instanceId", instance.getInstanceId());
				return result;
			}
		}

		throw new NacosException(NacosException.NOT_FOUND, "no matched ip found!");
	}

	@CanDistro
	@PutMapping("/beat")
	@Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
	public ObjectNode beat(HttpServletRequest request) throws Exception {

        ObjectNode result = JacksonUtils.createEmptyJsonNode();

		result.put("clientBeatInterval", switchDomain.getClientBeatInterval());
		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
				Constants.DEFAULT_NAMESPACE_ID);
		String clusterName = WebUtils.optional(request, CommonParams.CLUSTER_NAME,
				UtilsAndCommons.DEFAULT_CLUSTER_NAME);
		String ip = WebUtils.optional(request, "ip", StringUtils.EMPTY);
		int port = Integer.parseInt(WebUtils.optional(request, "port", "0"));
		String beat = WebUtils.optional(request, "beat", StringUtils.EMPTY);

		RsInfo clientBeat = null;
		if (StringUtils.isNotBlank(beat)) {
			clientBeat = JacksonUtils.toObj(beat, RsInfo.class);
		}

		if (clientBeat != null) {
			if (StringUtils.isNotBlank(clientBeat.getCluster())) {
				clusterName = clientBeat.getCluster();
			}
			else {
				// fix #2533
				clientBeat.setCluster(clusterName);
			}
			ip = clientBeat.getIp();
			port = clientBeat.getPort();
		}

		Loggers.SRV_LOG.debug("[CLIENT-BEAT] full arguments: beat: {}, serviceName: {}",
				clientBeat, serviceName);

		Instance instance = serviceManager
				.getInstance(namespaceId, serviceName, clusterName, ip, port);

		if (instance == null) {
			if (clientBeat == null) {
				result.put(CommonParams.CODE, NamingResponseCode.RESOURCE_NOT_FOUND);
				return result;
			}

			Loggers.SRV_LOG
					.warn("[CLIENT-BEAT] The instance has been removed for health mechanism, perform data compensation operations, beat: {}, serviceName: {}",
							clientBeat, serviceName);

			instance = new Instance();
			instance.setPort(clientBeat.getPort());
			instance.setIp(clientBeat.getIp());
			instance.setWeight(clientBeat.getWeight());
			instance.setMetadata(clientBeat.getMetadata());
			instance.setClusterName(clusterName);
			instance.setServiceName(serviceName);
			instance.setInstanceId(instance.getInstanceId());
			instance.setEphemeral(clientBeat.isEphemeral());

			serviceManager.registerInstance(namespaceId, serviceName, instance);
		}

		Service service = serviceManager.getService(namespaceId, serviceName);

		if (service == null) {
			throw new NacosException(NacosException.SERVER_ERROR,
					"service not found: " + serviceName + "@" + namespaceId);
		}
		if (clientBeat == null) {
			clientBeat = new RsInfo();
			clientBeat.setIp(ip);
			clientBeat.setPort(port);
			clientBeat.setCluster(clusterName);
		}
		service.processClientBeat(clientBeat);

		result.put(CommonParams.CODE, NamingResponseCode.OK);
		result.put("clientBeatInterval", instance.getInstanceHeartBeatInterval());
		result.put(SwitchEntry.LIGHT_BEAT_ENABLED, switchDomain.isLightBeatEnabled());
		return result;
	}

	@RequestMapping("/statuses")
	public ObjectNode listWithHealthStatus(@RequestParam String key)
			throws NacosException {

		String serviceName;
		String namespaceId;

		if (key.contains(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)) {
			namespaceId = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[0];
			serviceName = key.split(UtilsAndCommons.NAMESPACE_SERVICE_CONNECTOR)[1];
		}
		else {
			namespaceId = Constants.DEFAULT_NAMESPACE_ID;
			serviceName = key;
		}

		Service service = serviceManager.getService(namespaceId, serviceName);

		if (service == null) {
			throw new NacosException(NacosException.NOT_FOUND,
					"service: " + serviceName + " not found.");
		}

		List<Instance> ips = service.allIPs();

        ObjectNode result = JacksonUtils.createEmptyJsonNode();
		ArrayNode ipArray = JacksonUtils.createEmptyArrayNode();

		for (Instance ip : ips) {
			ipArray.add(ip.toIPAddr() + "_" + ip.isHealthy());
		}

		result.replace("ips", ipArray);
		return result;
	}

	private Instance parseInstance(HttpServletRequest request) throws Exception {

		String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
		String app = WebUtils.optional(request, "app", "DEFAULT");
		String metadata = WebUtils.optional(request, "metadata", StringUtils.EMPTY);

		Instance instance = getIPAddress(request);
		instance.setApp(app);
		instance.setServiceName(serviceName);
		// Generate simple instance id first. This value would be updated according to
		// INSTANCE_ID_GENERATOR.
		instance.setInstanceId(instance.generateInstanceId());
		instance.setLastBeat(System.currentTimeMillis());
		if (StringUtils.isNotEmpty(metadata)) {
			instance.setMetadata(UtilsAndCommons.parseMetadata(metadata));
		}

		instance.validate();

		return instance;
	}

	private Instance getIPAddress(HttpServletRequest request) {

		String ip = WebUtils.required(request, "ip");
		String port = WebUtils.required(request, "port");
		String weight = WebUtils.optional(request, "weight", "1");
		String cluster = WebUtils
				.optional(request, CommonParams.CLUSTER_NAME, StringUtils.EMPTY);
		if (StringUtils.isBlank(cluster)) {
			cluster = WebUtils
					.optional(request, "cluster", UtilsAndCommons.DEFAULT_CLUSTER_NAME);
		}
		boolean healthy = BooleanUtils
				.toBoolean(WebUtils.optional(request, "healthy", "true"));

		String enabledString = WebUtils.optional(request, "enabled", StringUtils.EMPTY);
		boolean enabled;
		if (StringUtils.isBlank(enabledString)) {
			enabled = BooleanUtils
					.toBoolean(WebUtils.optional(request, "enable", "true"));
		}
		else {
			enabled = BooleanUtils.toBoolean(enabledString);
		}

		boolean ephemeral = BooleanUtils.toBoolean(WebUtils.optional(request, "ephemeral",
				String.valueOf(switchDomain.isDefaultInstanceEphemeral())));

		Instance instance = new Instance();
		instance.setPort(Integer.parseInt(port));
		instance.setIp(ip);
		instance.setWeight(Double.parseDouble(weight));
		instance.setClusterName(cluster);
		instance.setHealthy(healthy);
		instance.setEnabled(enabled);
		instance.setEphemeral(ephemeral);

		return instance;
	}

	private void checkIfDisabled(Service service) throws Exception {
		if (!service.getEnabled()) {
			throw new Exception("service is disabled now.");
		}
	}

	public ObjectNode doSrvIPXT(String namespaceId, String serviceName, String agent,
			String clusters, String clientIP, int udpPort, String env, boolean isCheck,
			String app, String tid, boolean healthyOnly) throws Exception {

		ClientInfo clientInfo = new ClientInfo(agent);
		ObjectNode result = JacksonUtils.createEmptyJsonNode();
		Service service = serviceManager.getService(namespaceId, serviceName);

		if (service == null) {
			if (Loggers.SRV_LOG.isDebugEnabled()) {
				Loggers.SRV_LOG
						.debug("no instance to serve for service: {}", serviceName);
			}
			result.put("name", serviceName);
			result.put("clusters", clusters);
			result.replace("hosts", JacksonUtils.createEmptyArrayNode());
			return result;
		}

		checkIfDisabled(service);

		long cacheMillis = switchDomain.getDefaultCacheMillis();

		// now try to enable the push
		try {
			if (udpPort > 0 && pushService.canEnablePush(agent)) {

				pushService.addClient(namespaceId, serviceName, clusters, agent,
						new InetSocketAddress(clientIP, udpPort), pushDataSource, tid,
						app);
				cacheMillis = switchDomain.getPushCacheMillis(serviceName);
			}
		}
		catch (Exception e) {
			Loggers.SRV_LOG.error("[NACOS-API] failed to added push client {}, {}:{}",
					clientInfo, clientIP, udpPort, e);
			cacheMillis = switchDomain.getDefaultCacheMillis();
		}

		List<Instance> srvedIPs;

		srvedIPs = service.srvIPs(Arrays.asList(StringUtils.split(clusters, ",")));

		// filter ips using selector:
		if (service.getSelector() != null && StringUtils.isNotBlank(clientIP)) {
			srvedIPs = service.getSelector().select(clientIP, srvedIPs);
		}

		if (CollectionUtils.isEmpty(srvedIPs)) {

			if (Loggers.SRV_LOG.isDebugEnabled()) {
				Loggers.SRV_LOG
						.debug("no instance to serve for service: {}", serviceName);
			}

			if (clientInfo.type == ClientInfo.ClientType.JAVA
					&& clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0"))
					>= 0) {
				result.put("dom", serviceName);
			}
			else {
				result.put("dom", NamingUtils.getServiceName(serviceName));
			}

			result.put("hosts", JacksonUtils.createEmptyArrayNode());
			result.put("name", serviceName);
			result.put("cacheMillis", cacheMillis);
			result.put("lastRefTime", System.currentTimeMillis());
			result.put("checksum", service.getChecksum());
			result.put("useSpecifiedURL", false);
			result.put("clusters", clusters);
			result.put("env", env);
			result.put("metadata", JacksonUtils.transferToJsonNode(service.getMetadata()));
			return result;
		}

		Map<Boolean, List<Instance>> ipMap = new HashMap<>(2);
		ipMap.put(Boolean.TRUE, new ArrayList<>());
		ipMap.put(Boolean.FALSE, new ArrayList<>());

		for (Instance ip : srvedIPs) {
			ipMap.get(ip.isHealthy()).add(ip);
		}

		if (isCheck) {
			result.put("reachProtectThreshold", false);
		}

		double threshold = service.getProtectThreshold();

		if ((float) ipMap.get(Boolean.TRUE).size() / srvedIPs.size() <= threshold) {

			Loggers.SRV_LOG.warn("protect threshold reached, return all ips, service: {}",
					serviceName);
			if (isCheck) {
				result.put("reachProtectThreshold", true);
			}

			ipMap.get(Boolean.TRUE).addAll(ipMap.get(Boolean.FALSE));
			ipMap.get(Boolean.FALSE).clear();
		}

		if (isCheck) {
			result.put("protectThreshold", service.getProtectThreshold());
			result.put("reachLocalSiteCallThreshold", false);

			return JacksonUtils.createEmptyJsonNode();
		}

        ArrayNode hosts = JacksonUtils.createEmptyArrayNode();

		for (Map.Entry<Boolean, List<Instance>> entry : ipMap.entrySet()) {
			List<Instance> ips = entry.getValue();

			if (healthyOnly && !entry.getKey()) {
				continue;
			}

			for (Instance instance : ips) {

				// remove disabled instance:
				if (!instance.isEnabled()) {
					continue;
				}

				ObjectNode ipObj = JacksonUtils.createEmptyJsonNode();

				ipObj.put("ip", instance.getIp());
				ipObj.put("port", instance.getPort());
				// deprecated since nacos 1.0.0:
				ipObj.put("valid", entry.getKey());
				ipObj.put("healthy", entry.getKey());
				ipObj.put("marked", instance.isMarked());
				ipObj.put("instanceId", instance.getInstanceId());
				ipObj.put("metadata", JacksonUtils.transferToJsonNode(instance.getMetadata()));
				ipObj.put("enabled", instance.isEnabled());
				ipObj.put("weight", instance.getWeight());
				ipObj.put("clusterName", instance.getClusterName());
				if (clientInfo.type == ClientInfo.ClientType.JAVA
						&& clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0"))
						>= 0) {
					ipObj.put("serviceName", instance.getServiceName());
				}
				else {
					ipObj.put("serviceName",
							NamingUtils.getServiceName(instance.getServiceName()));
				}

				ipObj.put("ephemeral", instance.isEphemeral());
				hosts.add(ipObj);

			}
		}

		result.replace("hosts", hosts);
		if (clientInfo.type == ClientInfo.ClientType.JAVA
				&& clientInfo.version.compareTo(VersionUtil.parseVersion("1.0.0")) >= 0) {
			result.put("dom", serviceName);
		}
		else {
			result.put("dom", NamingUtils.getServiceName(serviceName));
		}
		result.put("name", serviceName);
		result.put("cacheMillis", cacheMillis);
		result.put("lastRefTime", System.currentTimeMillis());
		result.put("checksum", service.getChecksum());
		result.put("useSpecifiedURL", false);
		result.put("clusters", clusters);
		result.put("env", env);
		result.replace("metadata", JacksonUtils.transferToJsonNode(service.getMetadata()));
		return result;
	}
}
