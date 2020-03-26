package com.alibaba.nacos.consistency;

import com.alibaba.fastjson.JSON;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ProtocolMetaDataTest {

    @Test
    public void test_protocol_meta_data() {

        Map<String, Map<String, LocalDateTime>> map = new HashMap<>();

        Map<String, LocalDateTime> data = new HashMap<>();

        data.put("test-1", LocalDateTime.now());
        data.put("test_2", LocalDateTime.now());

        map.put("global", data);

        String json = JSON.toJSONString(map);

        System.out.println(json);

        Map map1 = JSON.parseObject(json, Map.class);

        System.out.println(map1);
    }

}
