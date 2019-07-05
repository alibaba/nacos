package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.consistency.ApplyAction;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author caoyixiong
 * @Date: 2019/7/5
 * @Copyright (c) 2015, lianjia.com All Rights Reserved
 */
public class NotifierTask {
    private static final Integer NOTIFY_SUCCESS = -1;
    private static final Integer MAX_NOTIFY_COUNT = 3;

    private String datumKey;
    private ApplyAction applyAction;
    private ConcurrentHashMap<String, Integer> listenerNotifyMap = new ConcurrentHashMap<>();

    public NotifierTask(String datumKey, ApplyAction applyAction) {
        this.datumKey = datumKey;
        this.applyAction = applyAction;
    }

    public String getDatumKey() {
        return datumKey;
    }

    public void setDatumKey(String datumKey) {
        this.datumKey = datumKey;
    }

    public ApplyAction getApplyAction() {
        return applyAction;
    }

    public void setApplyAction(ApplyAction applyAction) {
        this.applyAction = applyAction;
    }

    public boolean isValidToNotify(String listenerId) {
        if (!listenerNotifyMap.containsKey(listenerId)) {
            listenerNotifyMap.put(listenerId, 0);
            return true;
        }
        if (NOTIFY_SUCCESS.equals(listenerNotifyMap.get(listenerId))) {
            return false;
        }
        if (listenerNotifyMap.get(listenerId) < MAX_NOTIFY_COUNT) {
            listenerNotifyMap.put(listenerId, listenerNotifyMap.get(listenerId) + 1);
            return true;
        }
        return false;
    }

    public void success(String listenerId) {
        listenerNotifyMap.put(listenerId, NOTIFY_SUCCESS);
    }
}
