package com.alibaba.nacos.consistency;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.Assert;
import org.junit.Test;

public class ProtocolMetaDataTest {

    @Test
    public void test_protocol_meta_data() throws Exception {
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("test-1", LocalDateTime.now());
        data.put("test_2", LocalDateTime.now());
        map.put("global", data);

        ProtocolMetaData metaData = new ProtocolMetaData();

        metaData.load(map);

        String json = JacksonUtils.toJson(metaData);
        AtomicInteger count = new AtomicInteger(0);

        CountDownLatch latch = new CountDownLatch(2);

        metaData.subscribe("global", "test-1", (o, arg) -> {
            System.out.println(arg);
            count.incrementAndGet();
            latch.countDown();
        });

        System.out.println(json);

        map = new HashMap<>();
        data = new HashMap<>();
        data.put("test-1", LocalDateTime.now());
        data.put("test_2", LocalDateTime.now());
        map.put("global", data);

        metaData.load(map);

        json = JacksonUtils.toJson(metaData);
        System.out.println(json);

        latch.await(10_000L, TimeUnit.MILLISECONDS);

        Assert.assertEquals(2, count.get());

    }

}
