package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.model.RestResult;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * JacksonUtils tests
 *
 * @author liqipeng
 * @date 2022/4/2 00:38
 */
public class JacksonUtilsTest {
    
    @Test
    public void testToJsonBytes() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("string", "你好，中国！");
        map.put("integer", 999);
        RestResult<Map<String, Object>> restResult = new RestResult();
        restResult.setData(map);
    
        byte[] bytes = JacksonUtils.toJsonBytes(restResult);
        String jsonFromBytes = ByteUtils.toString(bytes);
        String expectedJson = "{\"code\":0,\"data\":{\"string\":\"你好，中国！\",\"integer\":999}}";
        Assert.assertEquals(expectedJson, jsonFromBytes);
    
        // old `toJsonBytes` method implementation:
        //     public static byte[] toJsonBytes(Object obj) {
        //        try {
        //            return ByteUtils.toBytes(mapper.writeValueAsString(obj));
        //        } catch (JsonProcessingException e) {
        //            throw new NacosSerializationException(obj.getClass(), e);
        //        }
        //    }
        
        // here is a verification to compare with the old implementation
        byte[] bytesFromOldImplementation = ByteUtils.toBytes(JacksonUtils.toJson(restResult));
        Assert.assertEquals(expectedJson, new String(bytesFromOldImplementation, Charset.forName(Constants.ENCODE)));
    }
    
    @Test
    public void testToObjFromBytes() {
        String json = "{\"code\":0,\"data\":{\"string\":\"你好，中国！\",\"integer\":999}}";
        
        RestResult<Map<String, Object>> restResult = JacksonUtils.toObj(json, RestResult.class);
        Assert.assertEquals(0, restResult.getCode());
        Assert.assertEquals("你好，中国！", restResult.getData().get("string"));
        Assert.assertEquals(999, restResult.getData().get("integer"));
    
        restResult = JacksonUtils.toObj(json, new TypeReference<RestResult<Map<String, Object>>>() {});
        Assert.assertEquals(0, restResult.getCode());
        Assert.assertEquals("你好，中国！", restResult.getData().get("string"));
        Assert.assertEquals(999, restResult.getData().get("integer"));
    }
}
