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
package com.alibaba.nacos.config.server.monitor;

import static com.alibaba.nacos.config.server.utils.LogUtil.memoryLog;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.nacos.config.server.service.ClientTrackService;
import com.alibaba.nacos.config.server.service.ConfigService;
import com.alibaba.nacos.config.server.service.TimerTaskService;
import com.alibaba.nacos.config.server.service.notify.AsyncNotifyService;

/**
 * Memory monitor
 * 
 * @author Nacos
 *
 */
@Service
public class MemoryMonitor {
    @Autowired
    public MemoryMonitor(AsyncNotifyService notifySingleService) {

        TimerTaskService.scheduleWithFixedDelay(new PrintMemoryTask(), DELAY_SECONDS,
                DELAY_SECONDS, TimeUnit.SECONDS);
        
        TimerTaskService.scheduleWithFixedDelay(new PrintGetConfigResponeTask(), DELAY_SECONDS,
                DELAY_SECONDS, TimeUnit.SECONDS);

        TimerTaskService.scheduleWithFixedDelay(new NotifyTaskQueueMonitorTask(notifySingleService), DELAY_SECONDS,
                DELAY_SECONDS, TimeUnit.SECONDS);
    }
    

    static final long DELAY_SECONDS = 10;
}

class PrintGetConfigResponeTask implements Runnable{
	@Override
	public void run() {
		memoryLog.info(ResponseMonitor.getStringForPrint());
	}
}

class PrintMemoryTask implements Runnable {
    @Override
    public void run() {
        int groupCount = ConfigService.groupCount();
        int subClientCount = ClientTrackService.subscribeClientCount();
        long subCount = ClientTrackService.subscriberCount();
        memoryLog.info("groupCount={}, subscriberClientCount={}, subscriberCount={}", groupCount, subClientCount, subCount);
    }
}


class NotifyTaskQueueMonitorTask implements Runnable {
    final private AsyncNotifyService notifySingleService;

    NotifyTaskQueueMonitorTask(AsyncNotifyService notifySingleService) {
        this.notifySingleService = notifySingleService;
    }

    @Override
    public void run() {
    	
    	 memoryLog.info("notifySingleServiceThreadPool-{}, toNotifyTaskSize={}",
                 new Object[] {((ScheduledThreadPoolExecutor)notifySingleService.getExecutor()).getClass().getName(), ((ScheduledThreadPoolExecutor)notifySingleService.getExecutor()).getQueue().size() });
    	 
//      for(Map.Entry<String, Executor> entry: notifySingleService.getExecutors().entrySet()) {
//          ThreadPoolExecutor pool = (ThreadPoolExecutor) entry.getValue();
//          String target = entry.getKey();
//          memoryLog.info("notifySingleServiceThreadPool-{}, toNotifyTaskSize={}",
//                  new Object[] { target, pool.getQueue().size() });
//      }
    }
}
