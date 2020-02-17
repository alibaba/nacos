package com.alibaba.nacos.consistency;

import com.alibaba.fastjson.JSON;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ProtocolMetaDataTest {

    @Test
    public void test_protocol_meta_data() {
        ProtocolMetaData metaData = new ProtocolMetaData();

        Map<String, Map<String, Object>> map = new HashMap<>();

        Map<String, Object> data = new HashMap<>();

        data.put("test-1", 1);
        data.put("test_2", new Date());

        map.put("global", data);

        metaData.load(map);

        System.out.println(JSON.toJSONString(metaData));
    }

}
