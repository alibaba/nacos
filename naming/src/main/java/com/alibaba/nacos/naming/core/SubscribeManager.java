package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.boot.RunningConfig;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.naming.pojo.Subscribers;
import com.alibaba.nacos.naming.push.PushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Nicholas
 */
@Service
public class SubscribeManager {

    private static final String DATA_ON_SYNC_URL = "/service/subscribers";

    @Autowired
    private PushService pushService;

    @Autowired
    private ServerListManager serverListManager;


    private List<Subscriber> getSubscribers(String serviceName, String namespaceId){
        return pushService.getClients(serviceName,namespaceId);
    }

    /**
     *
     * @param serviceName
     * @param namespaceId
     * @param aggregation
     * @return
     * @throws InterruptedException
     */
    public List<Subscriber> getSubscribers(String serviceName, String namespaceId, boolean aggregation) throws InterruptedException {
        if (aggregation){
            // size = 1 means only myself in the list, we need at least one another server alive:
            while (serverListManager.getHealthyServers().size() <= 1) {
                Thread.sleep(1000L);
                Loggers.EPHEMERAL.info("waiting server list init...");
            }

            List<Subscriber> subscriberList = new ArrayList<Subscriber>();
            // try sync data from remote server:
            for (Server server : serverListManager.getHealthyServers()) {

                Map<String, String> paramValues = new HashMap<>(128);
                paramValues.put("serviceName",serviceName);
                paramValues.put("namespaceId",namespaceId);
                paramValues.put("aggregation",String.valueOf(!aggregation));
                if (NetUtils.localServer().equals(server.getKey())) {
                    subscriberList.addAll(getSubscribers(serviceName,namespaceId));
                }

                HttpClient.HttpResult result = HttpClient.httpGet("http://" + server.getKey() + RunningConfig.getContextPath()
                    + UtilsAndCommons.NACOS_NAMING_CONTEXT + DATA_ON_SYNC_URL, new ArrayList<>(),paramValues);

                if (HttpURLConnection.HTTP_OK == result.code) {
                    Subscribers subscribers = (Subscribers) JSONObject.parseObject(result.content, Subscribers.class);
                    subscriberList.addAll(subscribers.getSubscribers());
                }
                return subscriberList.stream().filter(distinctByKey(Subscriber::toString)).collect(Collectors.toList());

            }
        } else {
            // local server
            return getSubscribers(serviceName,namespaceId);
        }
        return Collections.emptyList();
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>(128);
        return object -> seen.putIfAbsent(keyExtractor.apply(object), Boolean.TRUE) == null;
    }
}
