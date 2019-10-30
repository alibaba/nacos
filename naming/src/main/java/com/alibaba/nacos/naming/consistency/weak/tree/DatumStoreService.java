package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.DatumFileStore;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author satjd
 */
@Component("datumStoreService")
public class DatumStoreService {
    @Autowired
    private ProtocolConfig protocolConfig;

    private ConcurrentMap<String, Datum> datumCache = new ConcurrentHashMap<>();


    /** datumCacheMonitor作为datumCache的monitor对象map */
    private ConcurrentMap<String, Object> datumCacheMonitor = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, Long> removedKeysTimestamp = new ConcurrentHashMap<>();

    @Autowired
    DatumFileStore datumFileStore;

    @PostConstruct
    public void init() {
        // todo : 初始化存储
    }

    public boolean datumVersionValidate(Datum d) {
        Datum datumInCache = datumCache.get(d.key);

        if (datumInCache == null) {
            Long removedTs = removedKeysTimestamp.get(d.key);
            if (removedTs != null) {
                return (d.timestamp.get() - removedTs) > protocolConfig.getTimestampDiffMax();
            }
            return true;
        }

        // 时间戳必须严格比本地高，且高于阈值，否则视为收到重复或过时消息
        long tsDiff = d.timestamp.get() - datumInCache.timestamp.get();
        return (tsDiff > protocolConfig.getTimestampDiffMax());
    }

    public Datum getTimestampMarkedDatum(Datum d) {
        d.timestamp.set(System.currentTimeMillis());
        return d;
    }

    public ConcurrentMap<String, Datum> getDatumCache() {
        return datumCache;
    }

    /** store datum to datumCache */
    public boolean putDatum(Datum datum) {
        try {
            // 不同key之间可以并发put，这里采用一个key一个object的方式来做
            datumCacheMonitor.putIfAbsent(datum.key, new Object());
            Object monitor = datumCacheMonitor.get(datum.key);
            assert monitor != null;
            synchronized (monitor) {
                if (datumCache.containsKey(datum.key)) {
                    if (!datumVersionValidate(datum)) {
                        return false;
                    }
                }
                datumCache.put(datum.key, datum);

                // 存储到磁盘
                sinkToFileSystem(datum);

                return true;
            }
        } catch (Exception e) {
            Loggers.TREE.error("Put datum error. key={},exception:{}",datum.key,e);
            return false;
        }
    }

    public boolean removeDatum(String key) {
        datumCacheMonitor.putIfAbsent(key, new Object());
        Object monitor = datumCacheMonitor.get(key);
        assert monitor != null;
        synchronized (monitor) {
            // todo 使用weakreference指向datum而不是用new Object()

            // 释放monitor在map上的ref
            datumCacheMonitor.remove(key);

            // 清理磁盘上的文件
            removeFromFileSystem(key);
            Datum d = datumCache.remove(key);
            if (d != null) {
                removedKeysTimestamp.put(key,d.timestamp.longValue());
                return true;
            }
            return false;
        }
    }

    private void sinkToFileSystem(Datum datum) throws Exception {
        // sink datum to disk
        // datumFileStore.write(datum);
    }

    private void removeFromFileSystem(String key) {
        // remove file from disk
        // datumFileStore.deleteByKey(key);
    }

}
