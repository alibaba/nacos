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
package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.monitor.MetricsMonitor;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.AbstractEventListener;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.Event;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.core.utils.InetUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Async notify service
 *
 * @author Nacos
 */
@Service
public class AsyncNotifyService extends AbstractEventListener {

	@Override
	public List<Class<? extends Event>> interest() {
		List<Class<? extends Event>> types = new ArrayList<Class<? extends Event>>();
		// 触发配置变更同步通知
		types.add(ConfigDataChangeEvent.class);
		return types;
	}

	@Override
	public void onEvent(Event event) {

		// 并发产生 ConfigDataChangeEvent
		if (event instanceof ConfigDataChangeEvent) {
			ConfigDataChangeEvent evt = (ConfigDataChangeEvent) event;
			long dumpTs = evt.lastModifiedTs;
			String dataId = evt.dataId;
			String group = evt.group;
			String tenant = evt.tenant;
			String tag = evt.tag;
			Collection<Member> ipList = memberManager.allMembers();

			// 其实这里任何类型队列都可以
			Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
			for (Member member : ipList) {
				queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs,
						member.getAddress(), evt.isBeta));
			}
			EXECUTOR.execute(new AsyncTask(httpclient, queue));
		}
	}

	@Autowired
	public AsyncNotifyService(ServerMemberManager memberManager) {
		this.memberManager = memberManager;
		httpclient.start();
	}

	public Executor getExecutor() {
		return EXECUTOR;
	}

	@SuppressWarnings("PMD.ThreadPoolCreationRule")
	private static final Executor EXECUTOR = Executors
			.newScheduledThreadPool(100, new NotifyThreadFactory());

	private RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(PropertyUtil.getNotifyConnectTimeout())
			.setSocketTimeout(PropertyUtil.getNotifySocketTimeout()).build();

	private CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
			.setDefaultRequestConfig(requestConfig).build();

	private static final Logger log = LoggerFactory.getLogger(AsyncNotifyService.class);

	private ServerMemberManager memberManager;

	class AsyncTask implements Runnable {

		public AsyncTask(CloseableHttpAsyncClient httpclient,
				Queue<NotifySingleTask> queue) {
			this.httpclient = httpclient;
			this.queue = queue;
		}

		@Override
		public void run() {
			executeAsyncInvoke();
		}

		private void executeAsyncInvoke() {
			while (!queue.isEmpty()) {
				NotifySingleTask task = queue.poll();
				String targetIp = task.getTargetIP();
				if (memberManager.hasMember(targetIp)) {
					// 启动健康检查且有不监控的ip则直接把放到通知队列，否则通知
					boolean unHealthNeedDelay = memberManager.isUnHealth(targetIp);
					if (unHealthNeedDelay) {
						// target ip 不健康，则放入通知列表中
						ConfigTraceService
								.logNotifyEvent(task.getDataId(), task.getGroup(),
										task.getTenant(), null, task.getLastModified(),
										InetUtils.getSelfIp(),
										ConfigTraceService.NOTIFY_EVENT_UNHEALTH, 0,
										task.target);
						// get delay time and set fail count to the task
						asyncTaskExecute(task);
					}
					else {
						HttpGet request = new HttpGet(task.url);
						request.setHeader(NotifyService.NOTIFY_HEADER_LAST_MODIFIED,
								String.valueOf(task.getLastModified()));
						request.setHeader(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP,
								InetUtils.getSelfIp());
						if (task.isBeta) {
							request.setHeader("isBeta", "true");
						}
						httpclient.execute(request,
								new AsyncNotifyCallBack(httpclient, task));
					}
				}
			}
		}

		private Queue<NotifySingleTask> queue;
		private CloseableHttpAsyncClient httpclient;

	}

	private void asyncTaskExecute(NotifySingleTask task) {
		int delay = getDelayTime(task);
		Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
		queue.add(task);
		AsyncTask asyncTask = new AsyncTask(httpclient, queue);
		((ScheduledThreadPoolExecutor) EXECUTOR)
				.schedule(asyncTask, delay, TimeUnit.MILLISECONDS);
	}

	class AsyncNotifyCallBack implements FutureCallback<HttpResponse> {

		public AsyncNotifyCallBack(CloseableHttpAsyncClient httpClient,
				NotifySingleTask task) {
			this.task = task;
			this.httpClient = httpClient;
		}

		@Override
		public void completed(HttpResponse response) {

			long delayed = System.currentTimeMillis() - task.getLastModified();

			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(),
						task.getTenant(), null, task.getLastModified(),
						InetUtils.getSelfIp(), ConfigTraceService.NOTIFY_EVENT_OK,
						delayed, task.target);
			}
			else {
				log.error("[notify-error] target:{} dataId:{} group:{} ts:{} code:{}",
						task.target, task.getDataId(), task.getGroup(),
						task.getLastModified(), response.getStatusLine().getStatusCode());
				ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(),
						task.getTenant(), null, task.getLastModified(),
						InetUtils.getSelfIp(), ConfigTraceService.NOTIFY_EVENT_ERROR,
						delayed, task.target);

				//get delay time and set fail count to the task
				asyncTaskExecute(task);

				LogUtil.notifyLog
						.error("[notify-retry] target:{} dataId:{} group:{} ts:{}",
								task.target, task.getDataId(), task.getGroup(),
								task.getLastModified());

				MetricsMonitor.getConfigNotifyException().increment();
			}
			HttpClientUtils.closeQuietly(response);
		}

		@Override
		public void failed(Exception ex) {

			long delayed = System.currentTimeMillis() - task.getLastModified();
			log.error("[notify-exception] target:{} dataId:{} group:{} ts:{} ex:{}",
					task.target, task.getDataId(), task.getGroup(),
					task.getLastModified(), ex.toString());
			ConfigTraceService
					.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(),
							null, task.getLastModified(), InetUtils.getSelfIp(),
							ConfigTraceService.NOTIFY_EVENT_EXCEPTION, delayed,
							task.target);

			//get delay time and set fail count to the task
			asyncTaskExecute(task);
			LogUtil.notifyLog.error("[notify-retry] target:{} dataId:{} group:{} ts:{}",
					task.target, task.getDataId(), task.getGroup(),
					task.getLastModified());

			MetricsMonitor.getConfigNotifyException().increment();
		}

		@Override
		public void cancelled() {

			LogUtil.notifyLog
					.error("[notify-exception] target:{} dataId:{} group:{} ts:{} method:{}",
							task.target, task.getDataId(), task.getGroup(),
							task.getLastModified(), "CANCELED");

			//get delay time and set fail count to the task
			asyncTaskExecute(task);
			LogUtil.notifyLog.error("[notify-retry] target:{} dataId:{} group:{} ts:{}",
					task.target, task.getDataId(), task.getGroup(),
					task.getLastModified());

			MetricsMonitor.getConfigNotifyException().increment();
		}

		private NotifySingleTask task;
		private CloseableHttpAsyncClient httpClient;
	}

	static class NotifySingleTask extends NotifyTask {

		private String target;
		public String url;
		private boolean isBeta;
		private static final String URL_PATTERN =
				"http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange"
						+ "?dataId={2}&group={3}";
		private static final String URL_PATTERN_TENANT =
				"http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH + "/dataChange"
						+ "?dataId={2}&group={3}&tenant={4}";
		private int failCount;

		public NotifySingleTask(String dataId, String group, String tenant,
				long lastModified, String target) {
			this(dataId, group, tenant, lastModified, target, false);
		}

		public NotifySingleTask(String dataId, String group, String tenant,
				long lastModified, String target, boolean isBeta) {
			this(dataId, group, tenant, null, lastModified, target, isBeta);
		}

		public NotifySingleTask(String dataId, String group, String tenant, String tag,
				long lastModified, String target, boolean isBeta) {
			super(dataId, group, tenant, lastModified);
			this.target = target;
			this.isBeta = isBeta;
			try {
				dataId = URLEncoder.encode(dataId, Constants.ENCODE);
				group = URLEncoder.encode(group, Constants.ENCODE);
			}
			catch (UnsupportedEncodingException e) {
				log.error("URLEncoder encode error", e);
			}
			if (StringUtils.isBlank(tenant)) {
				this.url = MessageFormat
						.format(URL_PATTERN, target, ApplicationUtils.getContextPath(),
								dataId, group);
			}
			else {
				this.url = MessageFormat.format(URL_PATTERN_TENANT, target,
						ApplicationUtils.getContextPath(), dataId, group, tenant);
			}
			if (StringUtils.isNotEmpty(tag)) {
				url = url + "&tag=" + tag;
			}
			failCount = 0;
			// this.executor = executor;
		}

		@Override
		public void setFailCount(int count) {
			this.failCount = count;
		}

		@Override
		public int getFailCount() {
			return failCount;
		}

		public String getTargetIP() {
			return target;
		}

	}

	static class NotifyThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, "com.alibaba.nacos.AsyncNotifyServiceThread");
			thread.setDaemon(true);
			return thread;
		}
	}

	/**
	 * get delayTime and also set failCount to task;
	 * The failure time index increases, so as not to retry invalid tasks in the offline
	 * scene, which affects the normal synchronization
	 *
	 * @param task notify task
	 * @return delay
	 */
	private static int getDelayTime(NotifySingleTask task) {
		int failCount = task.getFailCount();
		int delay = MIN_RETRY_INTERVAL + failCount * failCount * INCREASE_STEPS;
		if (failCount <= MAX_COUNT) {
			task.setFailCount(failCount + 1);
		}
		return delay;
	}

	private static int MIN_RETRY_INTERVAL = 500;
	private static int INCREASE_STEPS = 1000;
	private static int MAX_COUNT = 6;

}