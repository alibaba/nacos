package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MysqlTest {

    private ObjectMapper objectMapper;

    private Mysql mysql;

    @Before
    public void setUp() throws Exception {
        mysql = new Mysql();
        mysql.setUser("user");
        mysql.setPwd("pwd");
        mysql.setCmd("cmd");
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testSerialize() throws JsonProcessingException {
        String actual = objectMapper.writeValueAsString(mysql);
        assertTrue(actual.contains("\"user\":\"user\""));
        assertTrue(actual.contains("\"type\":\"MYSQL\""));
        assertTrue(actual.contains("\"pwd\":\"pwd\""));
        assertTrue(actual.contains("\"cmd\":\"cmd\""));
    }

    @Test
    public void testDeserializeWithFullInfo() throws IOException {
        String testChecker = "{\"type\":\"MYSQL\",\"type\":\"MYSQL\",\"user\":\"user\",\"pwd\":\"pwd\",\"cmd\":\"cmd\"}";
        Mysql actual = objectMapper.readValue(testChecker, Mysql.class);
        assertMysql(actual);
    }

    @Test
    public void testDeserializeWithoutFullInfo() throws IOException {
        String testChecker = "{\"type\":\"MYSQL\",\"user\":\"user\",\"pwd\":\"pwd\",\"cmd\":\"cmd\"}";
        Mysql actual = objectMapper.readValue(testChecker, Mysql.class);
        assertMysql(actual);
    }

    private void assertMysql(Mysql actual) {
        assertEquals("cmd", actual.getCmd());
        assertEquals("pwd", actual.getPwd());
        assertEquals("user", actual.getUser());
        assertEquals(Mysql.TYPE, actual.getType());
    }
}
