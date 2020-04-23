package com.alibaba.nacos.consistency;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.nacos.common.utils.JacksonUtils;
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
        System.out.println(json);
    }

}
