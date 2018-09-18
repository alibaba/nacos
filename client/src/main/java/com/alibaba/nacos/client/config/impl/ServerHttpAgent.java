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
package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.config.utils.IOUtils;
import com.alibaba.nacos.client.config.utils.LogUtils;
import com.alibaba.nacos.client.identify.STSConfig;
import com.alibaba.nacos.client.logger.Logger;
import com.alibaba.nacos.client.logger.support.LoggerHelper;
import com.alibaba.nacos.client.utils.JSONUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.StringUtils;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.type.TypeReference;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Server Agent 
 * 
 * @author water.lyl
 *
 */
public class ServerHttpAgent {

	final static public Logger log = LogUtils.logger(ServerHttpAgent.class);
	/**
	 * @param path
	 *            相对于web应用根，以/开头
	 * @param headers
	 * @param paramValues
	 * @param encoding
	 * @param readTimeoutMs
	 * @return
	 * @throws IOException
	 */
	public HttpResult httpGet(String path, List<String> headers, List<String> paramValues, String encoding,
			long readTimeoutMs) throws IOException {
		final long endTime = System.currentTimeMillis() + readTimeoutMs;

		boolean isSSL = false;
		
		do {
			try {
				List<String> newHeaders = getSpasHeaders(paramValues);
				if (headers != null) {
					newHeaders.addAll(headers);
				}
				HttpResult result = HttpSimpleClient.httpGet(
						getUrl(serverListMgr.getCurrentServerAddr(), path, isSSL), newHeaders, paramValues, encoding,
						readTimeoutMs, isSSL);
				if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
						|| result.code == HttpURLConnection.HTTP_BAD_GATEWAY
						|| result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
					log.error("NACOS ConnectException", "currentServerAddr:{}. httpCode:",
							new Object[] { serverListMgr.getCurrentServerAddr(), result.code });
				} else {
					return result;
				}
			} catch (ConnectException ce) {
				log.error("NACOS ConnectException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr() });
				serverListMgr.refreshCurrentServerAddr();
			} catch (SocketTimeoutException stoe) {
				log.error("NACOS  SocketTimeoutException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				serverListMgr.refreshCurrentServerAddr();
			} catch (IOException ioe) {
				log.error("NACOS  IOException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				throw ioe;
			}
		} while (System.currentTimeMillis() <= endTime);

		log.error("NACOS-0002",
				LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0002", "环境问题", "no available server"));
		throw new ConnectException("no available server");
	}

	public HttpResult httpPost(String path, List<String> headers, List<String> paramValues, String encoding,
			long readTimeoutMs) throws IOException {
		final long endTime = System.currentTimeMillis() + readTimeoutMs;
		boolean isSSL = false;
		do {
			try {
				List<String> newHeaders = getSpasHeaders(paramValues);
				if (headers != null) {
					newHeaders.addAll(headers);
				}
				HttpResult result = HttpSimpleClient.httpPost(
						getUrl(serverListMgr.getCurrentServerAddr(), path, isSSL), newHeaders, paramValues, encoding,
						readTimeoutMs, isSSL);
				if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
						|| result.code == HttpURLConnection.HTTP_BAD_GATEWAY
						|| result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
					log.error("NACOS ConnectException", "currentServerAddr:{}. httpCode:",
							new Object[] { serverListMgr.getCurrentServerAddr(), result.code });
				} else {
					return result;
				}
			} catch (ConnectException ce) {
				log.error("NACOS ConnectException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				serverListMgr.refreshCurrentServerAddr();
			} catch (SocketTimeoutException stoe) {
				log.error("NACOS  SocketTimeoutException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				serverListMgr.refreshCurrentServerAddr();
			} catch (IOException ioe) {
				log.error("NACOS  IOException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				throw ioe;
			}

		} while (System.currentTimeMillis() <= endTime);

		log.error("NACOS-0002",
				LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0002", "环境问题", "no available server"));
		throw new ConnectException("no available server");
	}
	
	public HttpResult httpDelete(String path, List<String> headers, List<String> paramValues, String encoding,
			long readTimeoutMs) throws IOException {
		final long endTime = System.currentTimeMillis() + readTimeoutMs;
		boolean isSSL = false;
		do {
			try {
				List<String> newHeaders = getSpasHeaders(paramValues);
				if (headers != null) {
					newHeaders.addAll(headers);
				}
				HttpResult result = HttpSimpleClient.httpDelete(
						getUrl(serverListMgr.getCurrentServerAddr(), path, isSSL), newHeaders, paramValues, encoding,
						readTimeoutMs, isSSL);
				if (result.code == HttpURLConnection.HTTP_INTERNAL_ERROR
						|| result.code == HttpURLConnection.HTTP_BAD_GATEWAY
						|| result.code == HttpURLConnection.HTTP_UNAVAILABLE) {
					log.error("NACOS ConnectException", "currentServerAddr:{}. httpCode:",
							new Object[] { serverListMgr.getCurrentServerAddr(), result.code });
				} else {
					return result;
				}
			} catch (ConnectException ce) {
				log.error("NACOS ConnectException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				serverListMgr.refreshCurrentServerAddr();
			} catch (SocketTimeoutException stoe) {
				log.error("NACOS  SocketTimeoutException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				serverListMgr.refreshCurrentServerAddr();
			} catch (IOException ioe) {
				log.error("NACOS  IOException", "currentServerAddr:{}",
						new Object[] { serverListMgr.getCurrentServerAddr()});
				throw ioe;
			}
			
		} while (System.currentTimeMillis() <= endTime);
		
		log.error("NACOS-0002",
				LoggerHelper.getErrorCodeStr("NACOS", "NACOS-0002", "环境问题", "no available server"));
		throw new ConnectException("no available server");
	}

	private String getUrl(String serverAddr, String relativePath, boolean isSSL) {
		String httpPrefix = "http://";
		if (isSSL) {
			httpPrefix = "https://";
		}
		return httpPrefix + serverAddr + "/" + serverListMgr.getContentPath() + relativePath;
	}

	public static String getAppname() {
		return ParamUtil.getAppName();
	}

	public ServerHttpAgent(ServerListManager mgr) {
		serverListMgr = mgr;
	}
	
	public ServerHttpAgent(ServerListManager mgr, Properties properties) {
		serverListMgr = mgr;
		String ak = properties.getProperty(PropertyKeyConst.ACCESS_KEY);
		if (StringUtils.isBlank(ak)) {
			accessKey = SpasAdapter.getAk();
		} else {
			accessKey = ak;
		}

		String sk = properties.getProperty(PropertyKeyConst.SECRET_KEY);
		if (StringUtils.isBlank(sk)) {
			secretKey = SpasAdapter.getSk();
		} else {
			secretKey = sk;
		}
	}
	
	public ServerHttpAgent(Properties properties) throws NacosException {
		String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
		if (StringUtils.isBlank(encodeTmp)) {
			encode = Constants.ENCODE;
		} else {
			encode = encodeTmp.trim();
		}
		serverListMgr = new ServerListManager(properties);
		String ak = properties.getProperty(PropertyKeyConst.ACCESS_KEY);
		if (StringUtils.isBlank(ak)) {
			accessKey = SpasAdapter.getAk();
		} else {
			accessKey = ak;
		}
		
		String sk = properties.getProperty(PropertyKeyConst.SECRET_KEY);
		if (StringUtils.isBlank(sk)) {
			secretKey = SpasAdapter.getSk();
		} else {
			secretKey = sk;
		}
	}

	public synchronized void start() throws NacosException {
		serverListMgr.start();
	}
	
	private List<String> getSpasHeaders(List<String> paramValues) throws IOException {
		List<String> newHeaders = new ArrayList<String>();
		// STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
		if (STSConfig.getInstance().isSTSOn()) {
			STSCredential sTSCredential = getSTSCredential();
			accessKey = sTSCredential.accessKeyId;
			secretKey = sTSCredential.accessKeySecret;
			newHeaders.add("Spas-SecurityToken");
			newHeaders.add(sTSCredential.securityToken);
		}

		if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
			newHeaders.add("Spas-AccessKey");
			newHeaders.add(accessKey);
			List<String> signHeaders = SpasAdapter.getSignHeaders(paramValues, secretKey);
			if (signHeaders != null) {
				newHeaders.addAll(signHeaders);
			}
		}
		return newHeaders;
	}

	private STSCredential getSTSCredential() throws IOException {
		boolean cacheSecurityCredentials = STSConfig.getInstance().isCacheSecurityCredentials();
		if (cacheSecurityCredentials && sTSCredential != null) {
			long currentTime = System.currentTimeMillis();
			long expirationTime = sTSCredential.expiration.getTime();
			int timeToRefreshInMillisecond = STSConfig.getInstance().getTimeToRefreshInMillisecond();
			if (expirationTime - currentTime > timeToRefreshInMillisecond) {
				return sTSCredential;
			}
		}
		String stsResponse = getSTSResponse();
		STSCredential stsCredentialTmp = (STSCredential)JSONUtils.deserializeObject(stsResponse,
			new TypeReference<STSCredential>() {});
		sTSCredential = stsCredentialTmp;
		log.info("getSTSCredential", "code:{}, accessKeyId:{}, lastUpdated:{}, expiration:{}", sTSCredential.getCode(),
			sTSCredential.getAccessKeyId(), sTSCredential.getLastUpdated(), sTSCredential.getExpiration());
		return sTSCredential;
	}

	private static String getSTSResponse() throws IOException {
		String securityCredentials = STSConfig.getInstance().getSecurityCredentials();
		if (securityCredentials != null) {
			return securityCredentials;
		}
		String securityCredentialsUrl = STSConfig.getInstance().getSecurityCredentialsUrl();
		HttpURLConnection conn = null;
		int respCode;
		String response;
		try {
			conn = (HttpURLConnection)new URL(securityCredentialsUrl).openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(ParamUtil.getConnectTimeout() > 100 ? ParamUtil.getConnectTimeout() : 100);
			conn.setReadTimeout(1000);
			conn.connect();
			respCode = conn.getResponseCode();
			if (HttpURLConnection.HTTP_OK == respCode) {
				response = IOUtils.toString(conn.getInputStream(), Constants.ENCODE);
			} else {
				response = IOUtils.toString(conn.getErrorStream(), Constants.ENCODE);
			}
		} catch (IOException e) {
			log.error("500", "can not get security credentials", e);
			throw e;
		} finally {
			if (null != conn) {
				conn.disconnect();
			}
		}
		if (HttpURLConnection.HTTP_OK == respCode) {
			return response;
		}
		log.error(respCode + "", "can not get security credentials, securityCredentialsUrl:{}, response:{}",
			new Object[] {securityCredentialsUrl, response});
		throw new IOException("can not get security credentials, responseCode: " + respCode + ", response: " + response);
	}
	
	public String getName() {
		return serverListMgr.getName();
	}

	public String getNamespace() {
		return serverListMgr.getNamespace();
	}
	public String getTenant() {
		return serverListMgr.getTenant();
	}

	public String getEncode() {
		return encode;
	}

	@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
	private static class STSCredential {
		@JsonProperty(value = "AccessKeyId")
		private String accessKeyId;
		@JsonProperty(value = "AccessKeySecret")
		private String accessKeySecret;
		@JsonProperty(value = "Expiration")
		private Date expiration;
		@JsonProperty(value = "SecurityToken")
		private String securityToken;
		@JsonProperty(value = "LastUpdated")
		private Date lastUpdated;
		@JsonProperty(value = "Code")
		private String code;

		public String getAccessKeyId() {
			return accessKeyId;
		}

		public Date getExpiration() {
			return expiration;
		}

		public Date getLastUpdated() {
			return lastUpdated;
		}

		public String getCode() {
			return code;
		}

		public String toString() {
			return "STSCredential{" +
				"accessKeyId='" + accessKeyId + '\'' +
				", accessKeySecret='" + accessKeySecret + '\'' +
				", expiration=" + expiration +
				", securityToken='" + securityToken + '\'' +
				", lastUpdated=" + lastUpdated +
				", code='" + code + '\'' +
				'}';
		}
	}
	
	private String accessKey;
	private String secretKey;
	private String encode;
	private volatile STSCredential sTSCredential;
	final ServerListManager serverListMgr;

}
