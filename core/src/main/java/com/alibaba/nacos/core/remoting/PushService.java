package com.alibaba.nacos.core.remoting;

public interface PushService {
    void push(String clientId, String dataId, byte[] data);
}
