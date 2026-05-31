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

package com.alibaba.nacos.core.model.form.v3;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RaftCommandForm} unit test.
 */
class RaftCommandFormTest {
    
    @Test
    void validateShouldThrowWhenCommandMissing() {
        RaftCommandForm form = new RaftCommandForm();
        form.setValue("127.0.0.1:7848");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertTrue(exception.getErrMsg().contains("Raft command is required"));
    }
    
    @Test
    void validateShouldThrowWhenValueMissing() {
        RaftCommandForm form = new RaftCommandForm();
        form.setCommand("doSnapshot");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertTrue(exception.getErrMsg().contains("Raft command value is required"));
    }
    
    @Test
    void validateShouldThrowWhenBothMissing() {
        RaftCommandForm form = new RaftCommandForm();
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertTrue(exception.getErrMsg().contains("Raft command is required"));
    }
    
    @Test
    void validateShouldNotThrowWhenBothPresent() throws NacosApiException {
        RaftCommandForm form = new RaftCommandForm();
        form.setCommand("doSnapshot");
        form.setValue("127.0.0.1:7848");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldThrowWhenCommandBlank() {
        RaftCommandForm form = new RaftCommandForm();
        form.setCommand("");
        form.setValue("127.0.0.1:7848");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertTrue(exception.getErrMsg().contains("Raft command is required"));
    }
    
    @Test
    void validateShouldThrowWhenValueBlank() {
        RaftCommandForm form = new RaftCommandForm();
        form.setCommand("doSnapshot");
        form.setValue("");
        NacosApiException exception = assertThrows(NacosApiException.class, form::validate);
        assertTrue(exception.getErrMsg().contains("Raft command value is required"));
    }
    
    @Test
    void gettersAndSetters() {
        RaftCommandForm form = new RaftCommandForm();
        form.setGroupId("g1");
        form.setCommand("doSnapshot");
        form.setValue("127.0.0.1:7848");
        assertEquals("g1", form.getGroupId());
        assertEquals("doSnapshot", form.getCommand());
        assertEquals("127.0.0.1:7848", form.getValue());
    }
    
    @Test
    void toMapIncludesGroupIdWhenNotBlank() {
        RaftCommandForm form = new RaftCommandForm();
        form.setGroupId("naming_persistent_service");
        form.setCommand("doSnapshot");
        form.setValue("127.0.0.1:7848");
        Map<String, String> map = form.toMap();
        assertEquals("naming_persistent_service", map.get(JRaftConstants.GROUP_ID));
        assertEquals("doSnapshot", map.get(JRaftConstants.COMMAND_NAME));
        assertEquals("127.0.0.1:7848", map.get(JRaftConstants.COMMAND_VALUE));
        assertEquals(3, map.size());
    }
    
    @Test
    void toMapOmitsGroupIdWhenBlank() {
        RaftCommandForm form = new RaftCommandForm();
        form.setGroupId("");
        form.setCommand("doSnapshot");
        form.setValue("127.0.0.1:7848");
        Map<String, String> map = form.toMap();
        assertFalse(map.containsKey(JRaftConstants.GROUP_ID));
        assertEquals("doSnapshot", map.get(JRaftConstants.COMMAND_NAME));
        assertEquals("127.0.0.1:7848", map.get(JRaftConstants.COMMAND_VALUE));
        assertEquals(2, map.size());
    }
    
    @Test
    void toMapOmitsGroupIdWhenNull() {
        RaftCommandForm form = new RaftCommandForm();
        form.setCommand("transferLeader");
        form.setValue("127.0.0.1:7848");
        Map<String, String> map = form.toMap();
        assertNull(map.get(JRaftConstants.GROUP_ID));
        assertEquals("transferLeader", map.get(JRaftConstants.COMMAND_NAME));
        assertEquals("127.0.0.1:7848", map.get(JRaftConstants.COMMAND_VALUE));
    }
}
