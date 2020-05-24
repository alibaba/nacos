package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpTest {

    private ObjectMapper objectMapper;

    private Http http;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        http = new Http();
    }

    @Test
    public void testGetExpectedResponseCodeWithEmpty() {
        http.setHeaders("");
        assertTrue(http.getCustomHeaders().isEmpty());
    }

    @Test
    public void testGetExpectedResponseCodeWithoutEmpty() {
        http.setHeaders("x:a|y:");
        Map<String, String> actual = http.getCustomHeaders();
        assertFalse(actual.isEmpty());
        assertEquals(1, actual.size());
        assertEquals("a", actual.get("x"));
    }

    @Test
    public void testSerialize() throws JsonProcessingException {
        http.setHeaders("x:a|y:");
        http.setPath("/x");
        String actual = objectMapper.writeValueAsString(http);
        assertTrue(actual.contains("\"path\":\"/x\""));
        assertTrue(actual.contains("\"type\":\"HTTP\""));
        assertTrue(actual.contains("\"headers\":\"x:a|y:\""));
        assertTrue(actual.contains("\"expectedResponseCode\":200"));
    }

    @Test
    public void testDeserializeWithFullInfo() throws IOException {
        String testChecker = "{\"type\":\"HTTP\",\"type\":\"HTTP\",\"path\":\"/x\",\"headers\":\"x:a|y:\",\"expectedResponseCode\":200}";
        Http actual = objectMapper.readValue(testChecker, Http.class);
        assertHttp(actual);
    }

    @Test
    public void testDeserializeWithoutFullInfo() throws IOException {
        String testChecker = "{\"type\":\"HTTP\",\"path\":\"/x\",\"headers\":\"x:a|y:\",\"expectedResponseCode\":200}";
        Http actual = objectMapper.readValue(testChecker, Http.class);
        assertHttp(actual);
    }

    private void assertHttp(Http actual) {
        assertEquals("x:a|y:", actual.getHeaders());
        assertEquals("/x", actual.getPath());
        assertEquals(200, actual.getExpectedResponseCode());
        assertEquals("x:a|y:", actual.getHeaders());
        assertEquals(Http.TYPE, actual.getType());
    }
}
