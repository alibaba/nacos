package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Using simplified Raft protocol to maintain the consistency status of Nacos cluster.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
public class RaftConsistencyServiceImpl implements PersistentConsistencyService {

    @Autowired
    private RaftCore raftCore;

    @Override
    public void put(String key, Object value) throws NacosException {
        try {
            raftCore.signalPublish(key, (String) value);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft put failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft put failed, key:" + key + ", value:" + value);
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        try {
            raftCore.signalDelete(key);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft remove failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft remove failed, key:" + key);
        }
    }

    @Override
    public Object get(String key) throws NacosException {
        return raftCore.getDatum(key);
    }

    @Override
    public void listen(String key, DataListener listener) throws NacosException {
        raftCore.listen(key, listener);
    }

    @Override
    public void unlisten(String key, DataListener listener) throws NacosException {
        raftCore.unlisten(key, listener);
    }

    @Override
    public boolean isResponsible(String key) {
        return false;
    }

    @Override
    public String getResponsibleServer(String key) {
        return null;
    }

    public void onPut(Datum datum, RaftPeer source) throws NacosException {
        try {
            raftCore.onPublish(datum, source);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft onPut failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft onPut failed, datum:" + datum + ", source: " + source);
        }
    }

    public void onRemove(Datum datum, RaftPeer source) throws NacosException {
        try {
            raftCore.onDelete(datum, source);
        } catch (Exception e) {
            Loggers.RAFT.error("Raft onRemove failed.", e);
            throw new NacosException(NacosException.SERVER_ERROR, "Raft onRemove failed, datum:" + datum + ", source: " + source);
        }
    }
}
