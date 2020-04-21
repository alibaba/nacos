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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.event.RaftDBErrorEvent;
import com.alibaba.nacos.config.server.model.event.RaftDBErrorRecoverEvent;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.notify.listener.SmartSubscribe;
import com.alibaba.nacos.common.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.ReuseHttpRequest;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.core.utils.ReuseUploadFileHttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.AccessControlException;
import java.util.Enumeration;
import java.util.Map;

/**
 * If the embedded distributed storage is enabled, all requests are routed to the Leader
 * node for processing, and the maximum number of forwards for a single request cannot
 * exceed three
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class TransferToLeaderFilter implements Filter {

	@Autowired
	private ServerMemberManager memberManager;

	@Autowired
	private CPProtocol protocol;

	@Autowired
	private ControllerMethodsCache controllerMethodsCache;

	private volatile String leaderServer = "";
	private static final int MAX_TRANSFER_CNT = 1;

	private final RestTemplate restTemplate = new RestTemplate();

	private volatile boolean downgrading = false;

	@PostConstruct
	protected void init() {
		LogUtil.defaultLog.info("Open the request and forward it to the leader");
		listenerLeaderStatus();
		registerSubscribe();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		ReuseHttpRequest req;
		if (StringUtils.containsIgnoreCase(request.getContentType(), MediaType.MULTIPART_FORM_DATA)) {
			req = new ReuseUploadFileHttpServletRequest((HttpServletRequest) request);
		} else {
			req = new ReuseHttpServletRequest((HttpServletRequest) request);
		}
		HttpServletResponse resp = (HttpServletResponse) response;

		String urlString = req.getRequestURI();

		if (StringUtils.isNotBlank(req.getQueryString())) {
			urlString += "?" + req.getQueryString();
		}

		try {
			String path = new URI(req.getRequestURI()).getPath();
			Method method = controllerMethodsCache.getMethod(req.getMethod(), path);

			if (method == null) {
				throw new NoSuchMethodException(req.getMethod() + " " + path);
			}

			// Determine if the system degradation was triggered
			// System demotion is enabled and all requests are forwarded to the leader node

			boolean isLeader = protocol.isLeader(Constants.CONFIG_MODEL_RAFT_GROUP);

			if (downgrading && isLeader) {
				resp.sendError(HttpStatus.LOCKED.value(), "Unable to process the request at this time: System triggered degradation");
				return;
			}

			if (downgrading || (method.isAnnotationPresent(ToLeader.class) && !isLeader)) {
				if (StringUtils.isBlank(leaderServer)) {
					resp.sendError(HttpStatus.LOCKED.value(), "Unable to process the request at this time: no Leader");
					return;
				}

				String val = req.getHeader(Constants.FORWARD_LEADER);
				final int transferCnt = Integer.parseInt(StringUtils.isEmpty(val) ? "0" : val) + 1;

				// Requests can only be forwarded once if a downgrade is not triggered
				if (transferCnt > MAX_TRANSFER_CNT && !downgrading) {
					resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
							"Exceeded forwarding times:" + req.getMethod() + ":" + req.getRequestURI());
					return;
				}

				if (urlString.startsWith("/")) {
					urlString = urlString.substring(1);
				}
				final String reqUrl = req.getScheme() + "://" + leaderServer + "/" + urlString;
				HttpHeaders headers = new HttpHeaders();
				Enumeration<String> headerNames = req.getHeaderNames();
				headers.set(Constants.FORWARD_LEADER, String.valueOf(transferCnt));
				while (headerNames.hasMoreElements()) {
					String headerName = headerNames.nextElement();
					headers.set(headerName, req.getHeader(headerName));
				}
				HttpEntity<Object> httpEntity = new HttpEntity<>(req.getBody(), headers);
				ResponseEntity<String> result = restTemplate.exchange(reqUrl, HttpMethod.resolve(req.getMethod()), httpEntity, String.class);
				resp.setCharacterEncoding("UTF-8");
				resp.getWriter().write(result.getBody());
				resp.setStatus(result.getStatusCodeValue());
				return;
			}
			chain.doFilter(req, response);
		} catch (AccessControlException e) {
			resp.sendError(HttpServletResponse.SC_FORBIDDEN, "access denied: " + ExceptionUtil
					.getAllExceptionMsg(e));
			return;
		} catch (NoSuchMethodException e) {
			resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
					"no such api:" + req.getMethod() + ":" + req.getRequestURI());
			return;
		} catch (Exception e) {
			LogUtil.defaultLog.error("An exception occurred when the request was forwarded to the Leader node, {}", e);
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Server failed," + ExceptionUtil.getAllExceptionMsg(e));
			return;
		}

	}

	@Override
	public void destroy() {

	}

	private void listenerLeaderStatus() {
		protocol.protocolMetaData()
				.subscribe(Constants.CONFIG_MODEL_RAFT_GROUP,
						com.alibaba.nacos.consistency.cp.Constants.LEADER_META_DATA,
						new Observer() {
							@Override
							public void update(Observable o, Object arg) {
								final String raftLeader = String.valueOf(arg);
								boolean found = false;
								for (Map.Entry<String, Member> entry : memberManager.getServerList().entrySet()) {
									final Member member = entry.getValue();
									final String raftAddress = member.getIp() + ":" + member.getExtendVal(
											MemberMetaDataConstants.RAFT_PORT);
									if (StringUtils.equals(raftLeader, raftAddress)) {
										leaderServer = entry.getKey();
										found = true;
										break;
									}
								}
								if (!found) {
									leaderServer = "";
								}
							}
						});
	}

	private void registerSubscribe() {
		NotifyCenter.registerSubscribe(new SmartSubscribe() {

			@Override
			public void onEvent(Event event) {
				if (event instanceof RaftDBErrorRecoverEvent) {
					downgrading = false;
					return;
				}
				if (event instanceof RaftDBErrorEvent) {
					downgrading = true;
				}
			}

			@Override
			public boolean canNotify(Event event) {
				return (event instanceof RaftDBErrorEvent) || (event instanceof RaftDBErrorRecoverEvent);
			}
		});
	}
}
