package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import java.util.Map;

/**
 * @author nkorange
 */
public interface Serializer {

    <T> byte[] serialize(Map<String, T> data);

    <T> Map<String, T> deserialize(byte[] data, Class<T> clazz);
}
