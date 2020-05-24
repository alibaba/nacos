package com.alibaba.nacos.api.naming.pojo.healthcheck;

import org.junit.Before;
import org.junit.Test;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

public class AbstractHealthCheckerTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerSubtypes(new NamedType(TestChecker.class, TestChecker.TYPE));
    }

    @Test
    public void testSerialize() throws JsonProcessingException {
        TestChecker testChecker = new TestChecker();
        testChecker.setTestValue("");
        String actual = objectMapper.writeValueAsString(testChecker);
        assertTrue(actual.contains("\"testValue\":\"\""));
        assertTrue(actual.contains("\"type\":\"TEST\""));
    }

    @Test
    public void testDeserializeWithFullInfo() throws IOException {
        String testChecker = "{\"type\":\"TEST\",\"type\":\"TEST\",\"testValue\":\"\"}";
        TestChecker actual = objectMapper.readValue(testChecker, TestChecker.class);
        assertEquals("", actual.getTestValue());
        assertEquals(TestChecker.TYPE, actual.getType());
    }

    @Test
    public void testDeserializeWithoutFullInfo() throws IOException {
        String testChecker = "{\"type\":\"TEST\",\"testValue\":\"\"}";
        TestChecker actual = objectMapper.readValue(testChecker, TestChecker.class);
        assertEquals("", actual.getTestValue());
        assertEquals(TestChecker.TYPE, actual.getType());
    }
}
