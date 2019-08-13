package com.alibaba.nacos.naming.pojo;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Nicholas
 */
public class SubscriberTest {

    @Test
    public void subscriberBeanTest() {
        Subscriber subscriber = new Subscriber("127.0.0.1:8080", "agent", "app", "127.0.0.1", "public", "test");
        subscriber.setAddrStr("127.0.0.1:8080");
        subscriber.setIp("127.0.0.1");
        subscriber.setApp("app");
        subscriber.setAgent("agent");
        subscriber.setNamespaceId("public");
        subscriber.setServiceName("test");

        subscriber.getAddrStr();
        subscriber.getIp();
        subscriber.getAgent();
        subscriber.getApp();
        subscriber.getNamespaceId();
        subscriber.getServiceName();

        Subscribers subscribers = new Subscribers();
        List<Subscriber> subscriberList = new ArrayList<>();
        subscriberList.add(subscriber);
        subscribers.setSubscribers(subscriberList);
        subscribers.getSubscribers();

        Assert.assertNotNull(subscriberList);
        Assert.assertEquals(1, subscriberList.size());
        Assert.assertEquals("127.0.0.1:8080", subscriberList.get(0).getAddrStr());
        Assert.assertEquals("127.0.0.1", subscriberList.get(0).getIp());
        Assert.assertEquals("app", subscriberList.get(0).getApp());
        Assert.assertEquals("agent", subscriberList.get(0).getAgent());
        Assert.assertEquals("public", subscriberList.get(0).getNamespaceId());
        Assert.assertEquals("test", subscriberList.get(0).getServiceName());
    }
}
