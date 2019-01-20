package com.alibaba.nacos.naming.cluster;

import com.alibaba.nacos.common.util.SystemUtils;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.members.Member;
import com.alibaba.nacos.naming.cluster.members.MemberChangeListener;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.common.util.SystemUtils.*;

/**
 * The manager to globally refresh and operate server list.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component("serverListManager")
public class ServerListManager {

    private List<MemberChangeListener> listeners = new ArrayList<>();

    private List<Member> members = new ArrayList<>();

    private List<Member> reachableMembers = new ArrayList<>();

    public void listen(MemberChangeListener listener) {
        listeners.add(listener);
    }

    @PostConstruct
    public void init() {
        GlobalExecutor.registerServerListUpdater(new ServerListUpdater());
    }

    private List<Member> refreshServerList() {

        List<Member> result = new ArrayList<>();

        if (STANDALONE_MODE) {
            Member member = new Member();
            member.setIp(NetUtils.getLocalAddress());
            member.setServePort(RunningConfig.getServerPort());
            result.add(member);
            return result;
        }

        List<String> serverList = new ArrayList<>();
        try {
            serverList = readClusterConf();
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("failed to get config: " + CLUSTER_CONF_FILE_PATH, e);
        }

        if (Loggers.DEBUG_LOG.isDebugEnabled()) {
            Loggers.DEBUG_LOG.debug("SERVER-LIST from cluster.conf: {}", result);
        }

        //use system env
        if (CollectionUtils.isEmpty(serverList)) {
            serverList = SystemUtils.getIPsBySystemEnv(UtilsAndCommons.SELF_SERVICE_CLUSTER_ENV);
            if (Loggers.DEBUG_LOG.isDebugEnabled()) {
                Loggers.DEBUG_LOG.debug("SERVER-LIST from system variable: {}", result);
            }
        }

        if (CollectionUtils.isNotEmpty(serverList)) {

            for (int i = 0; i < serverList.size(); i++) {

                String ip;
                int port;
                if (serverList.get(0).contains(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)) {

                    ip = serverList.get(i).split(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)[0];
                    port = Integer.parseInt(serverList.get(i).split(UtilsAndCommons.CLUSTER_CONF_IP_SPLITER)[1]);
                } else {
                    ip = serverList.get(i);
                    port = RunningConfig.getServerPort();
                }

                Member member = new Member();
                member.setIp(ip);
                member.setServePort(port);
                result.add(member);
            }
        }

        return result;
    }

    public boolean contains(String server) {
        for (Member member : members) {
            if (member.getKey().equals(server)) {
                return true;
            }
        }
        return false;
    }

    public List<Member> getMembers() {
        return members;
    }

    public List<Member> getReachableMembers() {
        return reachableMembers;
    }

    private void notifyListeners() {

        GlobalExecutor.submit(new Runnable() {
            @Override
            public void run() {
                for (MemberChangeListener listener : listeners) {
                    listener.onChangeMemberList(members);
                }
            }
        });
    }

    public class ServerListUpdater implements Runnable {

        @Override
        public void run() {
            try {
                List<Member> servers = refreshServerList();
                List<Member> oldServers = members;

                if (CollectionUtils.isEmpty(servers)) {
                    Loggers.RAFT.warn("refresh server list failed, ignore it.");
                    return;
                }

                boolean changed = false;

                List<Member> newServers = (List<Member>) CollectionUtils.subtract(servers, oldServers);
                if (CollectionUtils.isNotEmpty(newServers)) {
                    members.addAll(newServers);
                    changed = true;
                    Loggers.RAFT.info("server list is updated, new: {} servers: {}", newServers.size(), newServers);
                }

                List<Member> deadServers = (List<Member>) CollectionUtils.subtract(oldServers, servers);
                if (CollectionUtils.isNotEmpty(deadServers)) {
                    members.removeAll(deadServers);
                    changed = true;
                    Loggers.RAFT.info("server list is updated, dead: {}, servers: {}", deadServers.size(), deadServers);
                }

                if (changed) {
                    notifyListeners();
                }

            } catch (Exception e) {
                Loggers.RAFT.info("error while updating server list.", e);
            }
        }
    }
}
