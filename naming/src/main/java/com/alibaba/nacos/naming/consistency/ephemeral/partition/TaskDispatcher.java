package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.nacos.naming.misc.GlobalExecutor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class TaskDispatcher {

    private List<BlockingQueue<String>> taskList = new ArrayList<>();

    private Map<String, String> taskMap = new ConcurrentHashMap<>();

    @Autowired
    private PartitionConfig partitionConfig;

    @Autowired
    private DataSyncer dataSyncer;

    @PostConstruct
    public void init() {
        for (int i = 0; i < partitionConfig.getTaskDispatchThreadCount(); i++) {
            taskList.add(new LinkedBlockingQueue<>(128 * 1024));
            GlobalExecutor.submit(new TaskScheduler(i));
        }
    }

    public int mapTask(String key) {
        // TODO map a task key to a particular task queue:
        return 0;
    }

    public void addTask(String key) {
        if (taskMap.containsKey(key)) {
            return;
        }
        taskMap.put(key, StringUtils.EMPTY);
        taskList.get(mapTask(key)).add(key);
    }

    public class TaskScheduler implements Runnable {

        private int index;

        private int dataSize = 0;

        private long lastExecuteTime = 0L;

        public TaskScheduler(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public void run() {

            while (true) {

                List<String> keys = new ArrayList<>();
                try {

                    String key = taskList.get(index).take();

                    if (dataSize == 0) {
                        keys = new ArrayList<>();
                    }

                    if (dataSize < partitionConfig.getBatchSyncKeyCount()) {
                        keys.add(key);
                        dataSize ++;
                    }

                    // TODO estimate lastExecuteTime
                    if (dataSize == partitionConfig.getBatchSyncKeyCount()) {

                    }

                } catch (Exception e) {

                }
            }
        }
    }
}
