package com.alibaba.nacos.client.naming.net;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.SubscribeInfo;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.beat.BeatInfo;

public interface NamingClient {

    void subscribeService(String namespaceId, String serviceName, String groupName, String clusters) throws NacosException;

    void unsubscribeService(String namespaceId, String serviceName, String groupName, String clusters) throws NacosException;

    void registerInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException;

    void deregisterInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException;

    void updateInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException;

    Service queryService(String namespaceId, String serviceName, String groupName) throws NacosException;

    void createService(String namespaceId, Service service, AbstractSelector selector) throws NacosException;

    boolean deleteService(String namespaceId, String serviceName, String groupName) throws NacosException;

    void updateService(String namespaceId, Service service, AbstractSelector selector) throws NacosException;

    String queryList(String namespaceId, String serviceName, String groupName, String clusters, SubscribeInfo subscribeInfo, boolean healthyOnly) throws NacosException;

    JSONObject sendBeat(String namespaceId, BeatInfo beatInfo, boolean lightBeatEnabled) throws NacosException;

    boolean serverHealthy();

    ListView<String> getServiceList(String namespaceId, int pageNo, int pageSize, String groupName) throws NacosException;

    ListView<String> getServiceList(String namespaceId, int pageNo, int pageSize, String groupName, AbstractSelector selector) throws NacosException;
}
