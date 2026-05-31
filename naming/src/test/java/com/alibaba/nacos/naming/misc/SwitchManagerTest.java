/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.misc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchReadResponse;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchWriteRequest;
import com.alibaba.nacos.naming.consistency.persistent.impl.OldDataOperation;
import com.alibaba.nacos.naming.pojo.Record;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwitchManagerTest {
    
    private SwitchManager switchManager;
    
    private SwitchDomain switchDomain;
    
    @Mock
    private ProtocolManager protocolManager;
    
    @Mock
    private CPProtocol cpProtocol;
    
    private Serializer serializer;
    
    @TempDir
    private Path tempDir;
    
    @BeforeEach
    void setUp() throws Exception {
        switchDomain = new SwitchDomain();
        serializer = SerializeFactory.getSerializer("JSON");
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        switchManager = new SwitchManager(switchDomain, protocolManager);
        Files.createDirectories(tempDir.resolve("data"));
        ReflectionTestUtils.setField(switchManager, "dataFile",
            tempDir.resolve("data").resolve(KeyBuilder.getSwitchDomainKey()).toFile());
    }
    
    @Test
    void testUpdatePushVersionWithValidJavaValue() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "java:2.0.0", true);
        assertEquals("2.0.0", switchDomain.getPushVersionOfJava());
    }
    
    @Test
    void testUpdatePushVersionWithValidPythonValue() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "python:3.1.0", true);
        assertEquals("3.1.0", switchDomain.getPushVersionOfPython());
    }
    
    @Test
    void testUpdatePushVersionWithOtherSupportedClients() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "c:1.0.13", true);
        switchManager.update(SwitchEntry.PUSH_VERSION, "go:1.2.3", true);
        switchManager.update(SwitchEntry.PUSH_VERSION, "csharp:2.3.4", true);
        
        assertEquals("1.0.13", switchDomain.getPushVersionOfC());
        assertEquals("1.2.3", switchDomain.getPushVersionOfGo());
        assertEquals("2.3.4", switchDomain.getPushVersionOfCsharp());
    }
    
    @Test
    void testUpdatePushVersionWithMultipleColons() throws Exception {
        switchManager.update(SwitchEntry.PUSH_VERSION, "java:1.0.0:extra", true);
        assertEquals("1.0.0", switchDomain.getPushVersionOfJava());
    }
    
    @Test
    void testUpdatePushVersionWithoutDelimiter() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "invalidvalue", true));
        assertEquals("illegal format, must be 'type:version', but got: invalidvalue",
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithEmptyValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "", true));
        assertEquals("illegal format, must be 'type:version', but got: ", exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithTrailingColon() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "java:", true));
        assertEquals("illegal format, must be 'type:version', but got: java:",
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithInvalidVersionFormat() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "java:abc", true));
        assertEquals("illegal version, must match: " + UtilsAndCommons.VERSION_STRING_SYNTAX,
            exception.getMessage());
    }
    
    @Test
    void testUpdatePushVersionWithUnsupportedClientType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_VERSION, "ruby:1.0.0", true));
        assertEquals("unsupported client type: ruby", exception.getMessage());
    }
    
    @Test
    void testUpdateEnableStandaloneToFalseAppliesValue() throws Exception {
        assertTrue(switchDomain.isEnableStandalone());
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "false", true);
        assertFalse(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateEnableStandaloneToTrueAppliesValue() throws Exception {
        switchDomain.setEnableStandalone(false);
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "true", true);
        assertTrue(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateEnableStandaloneWithEmptyValueIsNoOp() throws Exception {
        assertTrue(switchDomain.isEnableStandalone());
        switchManager.update(SwitchEntry.ENABLE_STANDALONE, "", true);
        assertTrue(switchDomain.isEnableStandalone());
    }
    
    @Test
    void testUpdateNumericAndBooleanSwitches() throws Exception {
        switchManager.update(SwitchEntry.DISTRO_THRESHOLD, "0.9", true);
        switchManager.update(SwitchEntry.CLIENT_BEAT_INTERVAL, "12345", true);
        switchManager.update(SwitchEntry.PUSH_CACHE_MILLIS, "10000", true);
        switchManager.update(SwitchEntry.DEFAULT_CACHE_MILLIS, "1000", true);
        switchManager.update(SwitchEntry.DISTRO, "false", true);
        switchManager.update(SwitchEntry.PUSH_ENABLED, "false", true);
        switchManager.update(SwitchEntry.SERVICE_STATUS_SYNC_PERIOD, "5000", true);
        switchManager.update(SwitchEntry.SERVER_STATUS_SYNC_PERIOD, "15000", true);
        switchManager.update(SwitchEntry.HEALTH_CHECK_TIMES, "7", true);
        switchManager.update(SwitchEntry.DISABLE_ADD_IP, "true", true);
        switchManager.update(SwitchEntry.SEND_BEAT_ONLY, "true", true);
        switchManager.update(SwitchEntry.DEFAULT_INSTANCE_EPHEMERAL, "false", true);
        switchManager.update(SwitchEntry.DISTRO_SERVER_EXPIRED_MILLIS, "30000", true);
        switchManager.update(SwitchEntry.LIGHT_BEAT_ENABLED, "false", true);
        switchManager.update(SwitchEntry.AUTO_CHANGE_HEALTH_CHECK_ENABLED, "false", true);
        
        assertEquals(0.9F, switchDomain.getDistroThreshold());
        assertEquals(12345L, switchDomain.getClientBeatInterval());
        assertEquals(10000L, switchDomain.getDefaultPushCacheMillis());
        assertEquals(1000L, switchDomain.getDefaultCacheMillis());
        assertFalse(switchDomain.isDistroEnabled());
        assertFalse(switchDomain.isPushEnabled());
        assertEquals(5000L, switchDomain.getServiceStatusSynchronizationPeriodMillis());
        assertEquals(15000L, switchDomain.getServerStatusSynchronizationPeriodMillis());
        assertEquals(7, switchDomain.getCheckTimes());
        assertTrue(switchDomain.isDisableAddIP());
        assertTrue(switchDomain.isSendBeatOnly());
        assertFalse(switchDomain.isDefaultInstanceEphemeral());
        assertEquals(30000L, switchDomain.getDistroServerExpiredMillis());
        assertFalse(switchDomain.isLightBeatEnabled());
        assertFalse(switchDomain.isAutoChangeHealthCheckEnabled());
    }
    
    @Test
    void testUpdateNumericSwitchesRejectTooSmallValues() {
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.DISTRO_THRESHOLD, "0", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.PUSH_CACHE_MILLIS, "9999", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.DEFAULT_CACHE_MILLIS, "999", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.SERVICE_STATUS_SYNC_PERIOD, "4999", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.SERVER_STATUS_SYNC_PERIOD, "999", true));
    }
    
    @Test
    void testUpdateCollectionAndStatusSwitches() throws Exception {
        switchManager.update(SwitchEntry.MASTERS, "1.1.1.1:8848,2.2.2.2:8848", true);
        switchManager.update(SwitchEntry.LIMITED_URL_MAP, "/nacos:403,/health:200", true);
        switchManager.update(SwitchEntry.OVERRIDDEN_SERVER_STATUS, "UP", true);
        
        assertEquals(2, switchDomain.getMasters().size());
        assertEquals(Integer.valueOf(403), switchDomain.getLimitedUrlMap().get("/nacos"));
        assertEquals(Integer.valueOf(200), switchDomain.getLimitedUrlMap().get("/health"));
        assertEquals("UP", switchDomain.getOverriddenServerStatus());
        
        switchManager.update(SwitchEntry.OVERRIDDEN_SERVER_STATUS, Constants.NULL_STRING, true);
        assertEquals("", switchDomain.getOverriddenServerStatus());
    }
    
    @Test
    void testUpdateLimitedUrlMapRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.LIMITED_URL_MAP, "/nacos", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.LIMITED_URL_MAP, ":200", true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.LIMITED_URL_MAP, "/nacos:0", true));
    }
    
    @Test
    void testUpdateHealthParams() throws Exception {
        SwitchDomain.HttpHealthParams httpHealthParams = new SwitchDomain.HttpHealthParams();
        httpHealthParams.setMin(600);
        httpHealthParams.setMax(4000);
        httpHealthParams.setFactor(0.5F);
        SwitchDomain.TcpHealthParams tcpHealthParams = new SwitchDomain.TcpHealthParams();
        tcpHealthParams.setMin(700);
        tcpHealthParams.setMax(5000);
        tcpHealthParams.setFactor(0.6F);
        SwitchDomain.MysqlHealthParams mysqlHealthParams = new SwitchDomain.MysqlHealthParams();
        mysqlHealthParams.setMin(2500);
        
        switchManager.update(SwitchEntry.HTTP_HEALTH_PARAMS, JacksonUtils.toJson(httpHealthParams),
            true);
        switchManager.update(SwitchEntry.TCP_HEALTH_PARAMS, JacksonUtils.toJson(tcpHealthParams),
            true);
        switchManager.update(SwitchEntry.MYSQL_HEALTH_PARAMS,
            JacksonUtils.toJson(mysqlHealthParams),
            true);
        
        assertEquals(600, switchDomain.getHttpHealthParams().getMin());
        assertEquals(5000, switchDomain.getTcpHealthParams().getMax());
        assertEquals(2500, switchDomain.getMysqlHealthParams().getMin());
    }
    
    @Test
    void testUpdateHealthParamsRejectsInvalidValues() {
        SwitchDomain.HttpHealthParams tooSmallMin = new SwitchDomain.HttpHealthParams();
        tooSmallMin.setMin(499);
        SwitchDomain.HttpHealthParams tooSmallMax = new SwitchDomain.HttpHealthParams();
        tooSmallMax.setMax(2999);
        SwitchDomain.HttpHealthParams malformedFactor = new SwitchDomain.HttpHealthParams();
        malformedFactor.setFactor(1.1F);
        
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.HTTP_HEALTH_PARAMS,
                JacksonUtils.toJson(tooSmallMin), true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.HTTP_HEALTH_PARAMS,
                JacksonUtils.toJson(tooSmallMax), true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.HTTP_HEALTH_PARAMS,
                JacksonUtils.toJson(malformedFactor), true));
        assertThrows(IllegalArgumentException.class,
            () -> switchManager.update(SwitchEntry.HTTP_HEALTH_PARAMS, "{", true));
    }
    
    @Test
    void testUpdateWithConsistencySubmitsWriteRequest() throws Exception {
        switchManager.update(SwitchEntry.CLIENT_BEAT_INTERVAL, "6000", false);
        
        verify(cpProtocol).write(any(WriteRequest.class));
    }
    
    @Test
    void testUpdateWithConsistencyThrowsNacosExceptionWhenWriteFails() throws Exception {
        when(cpProtocol.write(any(WriteRequest.class))).thenThrow(new RuntimeException("failed"));
        
        assertThrows(NacosException.class,
            () -> switchManager.update(SwitchEntry.CLIENT_BEAT_INTERVAL, "6000", false));
    }
    
    @Test
    void testUpdateHealthCheckEnabledWithLegacyCheckEntry() throws Exception {
        assertTrue(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.CHECK, "false", true);
        assertFalse(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.CHECK, "true", true);
        assertTrue(switchDomain.isHealthCheckEnabled());
    }
    
    @Test
    void testUpdateHealthCheckEnabledWithExposedFieldName() throws Exception {
        assertTrue(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.HEALTH_CHECK_ENABLED, "false", true);
        assertFalse(switchDomain.isHealthCheckEnabled());
        switchManager.update(SwitchEntry.HEALTH_CHECK_ENABLED, "true", true);
        assertTrue(switchDomain.isHealthCheckEnabled());
    }
    
    @Test
    void testHealthCheckEnabledEntryConstantValue() {
        assertEquals("healthCheckEnabled", SwitchEntry.HEALTH_CHECK_ENABLED);
    }
    
    @Test
    void testSwitchDomainDirectAccessors() {
        switchDomain.update(new SwitchDomain());
        
        Map<String, Integer> weights = new HashMap<>();
        weights.put("service", 7);
        switchDomain.setAdWeightMap(weights);
        assertEquals(7, switchDomain.getAdWeight("service"));
        
        switchDomain.setDefaultPushCacheMillis(1234L);
        assertEquals(1234L, switchDomain.getPushCacheMillis("service"));
        
        switchDomain.setHealthCheckEnabled(false);
        assertFalse(switchDomain.isHealthCheckEnabled("service"));
        
        Set<String> whiteList = new HashSet<>();
        whiteList.add("service");
        switchDomain.setHealthCheckWhiteList(whiteList);
        assertTrue(switchDomain.isHealthCheckEnabled("service"));
        
        assertTrue(switchDomain.toString().contains("\"defaultPushCacheMillis\":1234"));
    }
    
    @Test
    void testOnRequestReturnsSwitchDomainData() {
        ReadRequest request = ReadRequest.newBuilder().setData(ByteString.copyFrom(
            serializer.serialize(Collections.singletonList(ByteUtils.toBytes(KeyBuilder
                .getSwitchDomainKey())))))
            .build();
        
        Response response = switchManager.onRequest(request);
        BatchReadResponse batchResponse =
            serializer.deserialize(response.getData().toByteArray(), BatchReadResponse.class);
        
        assertTrue(response.getSuccess());
        assertEquals(1, batchResponse.getKeys().size());
    }
    
    @Test
    void testOnRequestReturnsErrorForInvalidPayload() {
        ReadRequest request =
            ReadRequest.newBuilder().setData(ByteString.copyFromUtf8("invalid")).build();
        
        Response response = switchManager.onRequest(request);
        
        assertFalse(response.getSuccess());
    }
    
    @Test
    void testOnRequestRejectsNonSwitchDomainKey() {
        ReadRequest request = ReadRequest.newBuilder().setData(
            ByteString.copyFrom(serializer.serialize(Collections.singletonList(ByteUtils
                .toBytes("other")))))
            .build();
        
        Response response = switchManager.onRequest(request);
        
        assertFalse(response.getSuccess());
        assertEquals("not switch domain key", response.getErrMsg());
    }
    
    @Test
    void testOnRequestAcceptsMultipleKeysAsLegacyBehavior() {
        ReadRequest request = ReadRequest.newBuilder().setData(ByteString.copyFrom(
            serializer.serialize(Arrays.asList(ByteUtils.toBytes(KeyBuilder.getSwitchDomainKey()),
                ByteUtils.toBytes("other")))))
            .build();
        
        Response response = switchManager.onRequest(request);
        
        assertTrue(response.getSuccess());
    }
    
    @Test
    void testOnApplyUpdatesSwitchDomain() {
        SwitchDomain newSwitchDomain = new SwitchDomain();
        newSwitchDomain.setClientBeatInterval(9000L);
        BatchWriteRequest batchWriteRequest = new BatchWriteRequest();
        batchWriteRequest.append(ByteUtils.toBytes(KeyBuilder.getSwitchDomainKey()),
            serializer.serialize(Datum.createDatum(KeyBuilder.getSwitchDomainKey(),
                newSwitchDomain)));
        WriteRequest request = WriteRequest.newBuilder().setOperation(OldDataOperation.Write
            .getDesc()).setData(ByteString.copyFrom(serializer.serialize(batchWriteRequest)))
            .build();
        
        Response response = switchManager.onApply(request);
        
        assertTrue(response.getSuccess());
        assertEquals(9000L, switchDomain.getClientBeatInterval());
    }
    
    @Test
    void testOnApplyRejectsNonSwitchDomainKey() {
        BatchWriteRequest batchWriteRequest = new BatchWriteRequest();
        batchWriteRequest.append(ByteUtils.toBytes("other"), new byte[] {1});
        WriteRequest request = WriteRequest.newBuilder().setOperation(OldDataOperation.Write
            .getDesc()).setData(ByteString.copyFrom(serializer.serialize(batchWriteRequest)))
            .build();
        
        Response response = switchManager.onApply(request);
        
        assertFalse(response.getSuccess());
        assertEquals("not switch domain key", response.getErrMsg());
    }
    
    @Test
    void testOnApplyRejectsNonSwitchDomainDatum() {
        BatchWriteRequest batchWriteRequest = new BatchWriteRequest();
        batchWriteRequest.append(ByteUtils.toBytes(KeyBuilder.getSwitchDomainKey()),
            serializer.serialize(Datum.createDatum(KeyBuilder.getSwitchDomainKey(), null)));
        WriteRequest request = WriteRequest.newBuilder().setOperation(OldDataOperation.Write
            .getDesc()).setData(ByteString.copyFrom(serializer.serialize(batchWriteRequest)))
            .build();
        
        Response response = switchManager.onApply(request);
        
        assertFalse(response.getSuccess());
        assertEquals("datum is not switch domain", response.getErrMsg());
    }
    
    @Test
    void testSnapshotAccessorsAndOperations() throws Exception {
        assertSame(switchDomain, switchManager.getSwitchDomain());
        assertEquals(1, switchManager.loadSnapshotOperate().size());
        
        switchManager.loadSnapshot(tempDir.resolve("missing").toString());
        
        Path snapshotDir = Files.createDirectory(tempDir.resolve("snapshot"));
        SwitchDomain snapshotDomain = new SwitchDomain();
        snapshotDomain.setClientBeatInterval(16000L);
        Files.write(snapshotDir.resolve(KeyBuilder.getSwitchDomainKey()),
            serializer.serialize(Datum.createDatum(KeyBuilder.getSwitchDomainKey(),
                snapshotDomain)));
        
        switchManager.loadSnapshot(snapshotDir.toString());
        assertEquals(16000L, switchDomain.getClientBeatInterval());
        
        Path backupDir = tempDir.resolve("backup");
        switchManager.dumpSnapshot(backupDir.toString());
        assertTrue(Files.exists(backupDir.resolve(KeyBuilder.getSwitchDomainKey())));
    }
    
    private static class MockRecord implements Record {
        
        private static final long serialVersionUID = 1L;
        
        @Override
        public String getChecksum() {
            return "mock";
        }
    }
}
