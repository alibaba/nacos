package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.weak.WeakConsistencyService;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author satjd
 */
@Service
public class TreeBasedConsistencyServiceImpl implements WeakConsistencyService {
    @Autowired
    CoreService coreService;

    @Autowired
    SubscriberManager subscriberManager;

    @Autowired
    DatumStoreService datumStoreService;

    @Autowired
    SwitchDomain switchDomain;

    @Autowired
    GlobalConfig globalConfig;

    public boolean isEnabled() {
        return globalConfig.isTreeProtocolEnabled();
    }

    @Override
    public void put(String key, Record value) throws NacosException {
        try {
            coreService.signalPublish(key, value);
        } catch (Exception e) {
            Loggers.TREE.error("Tree put failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Tree put failed, key:" + key + ", value:" + value, e);
        }
    }

    public void onPut(Datum datum, TreePeer source) throws NacosException {
        try {
            coreService.onPublish(datum, source);
        } catch (Exception e) {
            Loggers.TREE.error("Tree onPut failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Tree onPut failed, datum:" + datum + ", source: " + source, e);
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        try {
            coreService.signalDelete(key);
            subscriberManager.unregisterAllListener(key);
        } catch (Exception e) {
            Loggers.TREE.error("Tree delete failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Tree delete failed, key:" + key, e);
        }
    }

    public void onRemove(Datum datum, TreePeer source) throws NacosException {
        try {
            coreService.onDelete(datum,source);
            subscriberManager.unregisterAllListener(datum.key);
        } catch (Exception e) {
            Loggers.TREE.error("Tree delete failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Tree onDelete failed, key:" + datum.key, e);
        }
    }

    @Override
    public Datum get(String key) throws NacosException {
        return datumStoreService.getDatumCache().get(key);
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        subscriberManager.registerListener(key, listener);
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        subscriberManager.unregisterListener(key, listener);
    }

    @Override
    public boolean isAvailable() {
        return coreService.isInitialized() || ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus());
    }
}
