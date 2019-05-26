package com.alibaba.nacos.naming.pojo;

import java.io.Serializable;
import java.util.List;

public class Subscribers implements Serializable {

    private List<Subscriber> subscribers;

    public List<Subscriber> getSubscribers() {
        return subscribers;
    }

    public void setSubscribers(List<Subscriber> subscribers) {
        this.subscribers = subscribers;
    }
}
