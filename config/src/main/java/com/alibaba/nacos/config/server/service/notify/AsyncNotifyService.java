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
import com.alibaba.nacos.config.server.service.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ServerListService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.*;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.AbstractEventListener;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher.Event;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

import static com.alibaba.nacos.common.util.SystemUtils.LOCAL_IP;

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
            ConfigDataChangeEvent evt = (ConfigDataChangeEvent)event;
            long dumpTs = evt.lastModifiedTs;
            String dataId = evt.dataId;
            String group = evt.group;
            String tenant = evt.tenant;
            String tag = evt.tag;
            List<?> ipList = serverListService.getServerList();

            // 其实这里任何类型队列都可以
            Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
            for (int i = 0; i < ipList.size(); i++) {
                queue.add(new NotifySingleTask(dataId, group, tenant, tag, dumpTs, (String)ipList.get(i), evt.isBeta));
            }
            EXCUTOR.execute(new AsyncTask(httpclient, queue));
        }
    }

    @Autowired
    public AsyncNotifyService(ServerListService serverListService) {
        this.serverListService = serverListService;
        httpclient.start();
    }

    public Executor getExecutor() {
        return EXCUTOR;
    }

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static final Executor EXCUTOR = Executors.newScheduledThreadPool(100, new NotifyThreadFactory());

    private RequestConfig requestConfig = RequestConfig.custom()
        .setConnectTimeout(PropertyUtil.getNotifyConnectTimeout())
        .setSocketTimeout(PropertyUtil.getNotifySocketTimeout()).build();

    private CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
        .setDefaultRequestConfig(requestConfig).build();

    static final Logger log = LoggerFactory.getLogger(AsyncNotifyService.class);

    private ServerListService serverListService;

    class AsyncTask implements Runnable {

        public AsyncTask(CloseableHttpAsyncClient httpclient, Queue<NotifySingleTask> queue) {
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
                if (serverListService.getServerList().contains(
                    targetIp)) {
                    // 启动健康检查且有不监控的ip则直接把放到通知队列，否则通知
                    if (serverListService.isHealthCheck()
                        && ServerListService.getServerListUnhealth().contains(targetIp)) {
                        // target ip 不健康，则放入通知列表中
                        ConfigTraceService.logNotifyEvent(task.getDataId(), task.getGroup(), task.getTenant(), null,
                            task.getLastModified(),
                            LOCAL_IP, ConfigTraceService.NOTIFY_EVENT_UNHEALTH, 0, task.target);
                        // get delay time and set fail count to the task
                        int delay = getDelayTime(task);
                        Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();
                        queue.add(task);
                        AsyncTask asyncTask = new AsyncTask(httpclient, queue);
                        ((ScheduledThreadPoolExecutor)EXCUTOR).schedule(asyncTask, delay, TimeUnit.MILLISECONDS);
                    } else {
                        HttpGet request = new HttpGet(task.url);
                        request.setHeader(NotifyService.NOTIFY_HEADER_LAST_MODIFIED,
                            String.valueOf(task.getLastModified()));
                        request.setHeader(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP, LOCAL_IP);
                        if (task.isBeta) {
                            request.setHeader("isBeta", "true");
                        }
                        httpclient.execute(request, new AyscNotifyCallBack(httpclient, task));
                    }
                }
            }
        }

        private Queue<NotifySingleTask> queue;
        private CloseableHttpAsyncClient httpclient;

    }

    class AyscNotifyCallBack implements FutureCallback<HttpResponse> {

        public AyscNotifyCallBack(CloseableHttpAsyncClient httpclient, NotifySingleTask task
        ) {
            this.task = task;
            this.httpclient = httpclient;
        }

        @Override
        public void completed(HttpResponse response) {

            long delayed = System.currentTimeMillis() - task.getLastModified();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                ConfigTraceService.logNotifyEvent(task.getDataId(),
                    task.getGroup(), task.getTenant(), null, task.getLastModified(),
                    LOCAL_IP,
                    ConfigTraceService.NOTIFY_EVENT_OK, delayed,
                    task.target);
            } else {
                log.error("[notify-error] {}, {}, to {}, result {}",
                    new Object[] {task.getDataId(), task.getGroup(),
                        task.target,
                        response.getStatusLine().getStatusCode()});
                ConfigTraceService.logNotifyEvent(task.getDataId(),
                    task.getGroup(), task.getTenant(), null, task.getLastModified(),
                    LOCAL_IP,
                    ConfigTraceService.NOTIFY_EVENT_ERROR, delayed,
                    task.target);

                //get delay time and set fail count to the task
                int delay = getDelayTime(task);

                Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();

                queue.add(task);
                AsyncTask asyncTask = new AsyncTask(httpclient, queue);

                ((ScheduledThreadPoolExecutor)EXCUTOR).schedule(asyncTask, delay, TimeUnit.MILLISECONDS);

                LogUtil.notifyLog.error(
                    "[notify-retry] target:{} dataid:{} group:{} ts:{}",
                    new Object[] {task.target, task.getDataId(),
                        task.getGroup(), task.getLastModified()});

            }
            HttpClientUtils.closeQuietly(response);
        }

        @Override
        public void failed(Exception ex) {

            long delayed = System.currentTimeMillis() - task.getLastModified();
            log.error("[notify-exception] " + task.getDataId() + ", " + task.getGroup() + ", to " + task.target + ", "
                + ex.toString());
            log.debug("[notify-exception] " + task.getDataId() + ", " + task.getGroup() + ", to " + task.target + ", "
                + ex.toString(), ex);
            ConfigTraceService.logNotifyEvent(task.getDataId(),
                task.getGroup(), task.getTenant(), null, task.getLastModified(),
                LOCAL_IP,
                ConfigTraceService.NOTIFY_EVENT_EXCEPTION, delayed,
                task.target);

            //get delay time and set fail count to the task
            int delay = getDelayTime(task);
            Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();

            queue.add(task);
            AsyncTask asyncTask = new AsyncTask(httpclient, queue);

            ((ScheduledThreadPoolExecutor)EXCUTOR).schedule(asyncTask, delay, TimeUnit.MILLISECONDS);
            LogUtil.notifyLog.error(
                "[notify-retry] target:{} dataid:{} group:{} ts:{}",
                new Object[] {task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified()});

        }

        @Override
        public void cancelled() {

            LogUtil.notifyLog.error(
                "[notify-exception] target:{} dataid:{} group:{} ts:{}",
                new Object[] {task.target, task.getGroup(),
                    task.getGroup(), task.getLastModified()},
                "CANCELED");

            //get delay time and set fail count to the task
            int delay = getDelayTime(task);
            Queue<NotifySingleTask> queue = new LinkedList<NotifySingleTask>();

            queue.add(task);
            AsyncTask asyncTask = new AsyncTask(httpclient, queue);

            ((ScheduledThreadPoolExecutor)EXCUTOR).schedule(asyncTask, delay, TimeUnit.MILLISECONDS);
            LogUtil.notifyLog.error(
                "[notify-retry] target:{} dataid:{} group:{} ts:{}",
                new Object[] {task.target, task.getDataId(),
                    task.getGroup(), task.getLastModified()});

        }

        private NotifySingleTask task;
        private CloseableHttpAsyncClient httpclient;
    }

    static class NotifySingleTask extends NotifyTask {

        private String target;
        public String url;
        private boolean isBeta;
        private static final String URL_PATTERN = "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH
            + "/dataChange"
            + "?dataId={2}&group={3}";
        private static final String URL_PATTERN_TENANT = "http://{0}{1}" + Constants.COMMUNICATION_CONTROLLER_PATH
            + "/dataChange" + "?dataId={2}&group={3}&tenant={4}";
        private int failCount;

        public NotifySingleTask(String dataId, String group, String tenant, long lastModified, String target) {
            this(dataId, group, tenant, lastModified, target, false);
        }

        public NotifySingleTask(String dataId, String group, String tenant, long lastModified, String target,
                                boolean isBeta) {
            this(dataId, group, tenant, null, lastModified, target, isBeta);
        }

        public NotifySingleTask(String dataId, String group, String tenant, String tag, long lastModified,
                                String target, boolean isBeta) {
            super(dataId, group, tenant, lastModified);
            this.target = target;
            this.isBeta = isBeta;
            try {
                dataId = URLEncoder.encode(dataId, Constants.ENCODE);
                group = URLEncoder.encode(group, Constants.ENCODE);
            } catch (UnsupportedEncodingException e) {
                log.error("URLEncoder encode error", e);
            }
            if (StringUtils.isBlank(tenant)) {
                this.url = MessageFormat.format(URL_PATTERN, target, RunningConfigUtils.getContextPath(), dataId,
                    group);
            } else {
                this.url = MessageFormat.format(URL_PATTERN_TENANT, target, RunningConfigUtils.getContextPath(), dataId,
                    group, tenant);
            }
            if (StringUtils.isNotEmpty(tag)) {
                url = url + "&tag=" + tag;
            }
            failCount = 0;
            // this.executor = executor;
        }

        public void setFailCount(int count) {
            this.failCount = count;
        }

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
            Thread thread = new Thread(r,
                "com.alibaba.nacos.AsyncNotifyServiceThread");
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * get delayTime and also set failCount to task;失败时间指数增加，以免断网场景不断重试无效任务，影响正常同步
     *
     * @param task notify task
     * @return delay
     */
    private static int getDelayTime(NotifySingleTask task) {
        int failCount = task.getFailCount();
        int delay = MINRETRYINTERVAL + failCount * failCount * INCREASESTEPS;
        if (failCount <= MAXCOUNT) {
            task.setFailCount(failCount + 1);
        }
        return delay;
    }

    private static int MINRETRYINTERVAL = 500;
    private static int INCREASESTEPS = 1000;
    private static int MAXCOUNT = 6;

}
