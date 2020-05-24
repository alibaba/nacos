package com.alibaba.nacos.api.naming.pojo.healthcheck;

import java.io.IOException;

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker.None;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

/**
 * health checker factory.
 *
 * @author yangyi
 */
public class HealthCheckerFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Register new sub type of health checker to factory for serialize and deserialize.
     *
     * @param extendHealthChecker extend health checker
     */
    public static void registerSubType(AbstractHealthChecker extendHealthChecker) {
        registerSubType(extendHealthChecker.getClass(), extendHealthChecker.getType());
    }

    /**
     * Register new sub type of health checker to factory for serialize and deserialize.
     *
     * @param extendHealthCheckerClass extend health checker
     * @param typeName typeName of health checker
     */
    public static void registerSubType(Class<? extends AbstractHealthChecker> extendHealthCheckerClass, String typeName) {
        MAPPER.registerSubtypes(new NamedType(extendHealthCheckerClass, typeName));
    }

    /**
     * Create default {@link None} health checker.
     *
     * @return new none health checker
     */
    public static None createNoneHealthChecker() {
        return new None();
    }

    /**
     * Deserialize and create a instance of health checker.
     *
     * @param jsonString json string of health checker
     * @return new instance
     */
    public static AbstractHealthChecker deserialize(String jsonString) {
        try {
            return MAPPER.readValue(jsonString, AbstractHealthChecker.class);
        } catch (IOException e) {
            // TODO replace with NacosDeserializeException.
            throw new RuntimeException("Deserialize health checker from json failed", e);
        }
    }

    /**
     * Serialize a instance of health checker to json
     *
     * @param healthChecker health checker instance
     * @return son string after serializing
     */
    public static String serialize(AbstractHealthChecker healthChecker) {
        try {
            return MAPPER.writeValueAsString(healthChecker);
        } catch (JsonProcessingException e) {
            // TODO replace with NacosSerializeException.
            throw new RuntimeException("Serialize health checker to json failed", e);
        }
    }
}
