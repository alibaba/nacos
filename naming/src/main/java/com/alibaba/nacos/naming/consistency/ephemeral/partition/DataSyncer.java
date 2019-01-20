package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.members.Member;
import com.alibaba.nacos.naming.cluster.members.MemberChangeListener;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class DataSyncer implements MemberChangeListener {

    @Autowired
    private DataStore dataStore;

    @Autowired
    private Serializer serializer;

    @Autowired
    private ServerListManager serverListManager;

    private List<Member> servers;

    public DataSyncer() {
        serverListManager.listen(this);
    }

    public void submit(SyncTask task) {

        GlobalExecutor.submitDataSync(new Runnable() {
            @Override
            public void run() {

                try {
                    if (servers == null || servers.isEmpty()) {
                        Loggers.SRV_LOG.warn("try to sync data but server list is empty.");
                        return;
                    }

                    List<String> keys = task.getKeys();
                    Map<String, List<Instance>> instancMap = dataStore.batchGet(keys);
                    byte[] data = serializer.serialize(instancMap);

                    if (StringUtils.isBlank(task.getTargetServer())) {
                        for (Member server : servers) {
                            long timestamp = System.currentTimeMillis();
                            boolean success = NamingProxy.syncData(data, server.getKey());
                            if (!success) {
                                SyncTask syncTask = new SyncTask();
                                syncTask.setKeys(task.getKeys());
                                syncTask.setRetryCount(task.getRetryCount() + 1);
                                syncTask.setLastExecuteTime(timestamp);
                                syncTask.setTargetServer(server.getKey());
                                submit(syncTask);
                            }
                        }
                    } else {
                        long timestamp = System.currentTimeMillis();
                        boolean success = NamingProxy.syncData(data, task.getTargetServer());
                        if (!success) {
                            SyncTask syncTask = new SyncTask();
                            syncTask.setKeys(task.getKeys());
                            syncTask.setRetryCount(task.getRetryCount() + 1);
                            syncTask.setLastExecuteTime(timestamp);
                            syncTask.setTargetServer(task.getTargetServer());
                            submit(syncTask);
                        }
                    }

                } catch (Exception e) {
                    Loggers.SRV_LOG.error("sync data failed.", e);
                }
            }
        });
    }


    @Override
    public void onChangeMemberList(List<Member> latestMembers) {

    }

    @Override
    public void onChangeReachableMemberList(List<Member> latestReachableMembers) {
        servers = latestReachableMembers;
    }
}
