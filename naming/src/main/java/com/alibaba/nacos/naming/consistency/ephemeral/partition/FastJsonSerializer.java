package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nkorange
 */
@Component
public class FastJsonSerializer implements Serializer {

    @Override
    public <T> byte[] serialize(Map<String, T> dataMap) {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, T> entry : dataMap.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json.toJSONString().getBytes();
    }

    @Override
    public <T> Map<String, T> deserialize(byte[] data, Class<T> clazz) {
        try {
            String dataString = new String(data, "UTF-8");
            JSONObject json = JSON.parseObject(dataString);
            Map<String, T> dataMap = new HashMap<>();
            for (String key : json.keySet()) {
                dataMap.put(key, JSON.parseObject(json.getString(key), clazz));
            }
            return dataMap;
        } catch (Exception e) {
            Loggers.SRV_LOG.error("deserialize data failed.", e);
        }
        return null;
    }
}
