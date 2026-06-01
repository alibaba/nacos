/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.model.form;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingFormTest {
    
    @Test
    void testClientServiceFormValidateFillsDefaults() throws NacosApiException {
        ClientServiceForm form = new ClientServiceForm();
        form.setServiceName("service");
        form.setIp("1.1.1.1");
        form.setPort(8848);
        
        form.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertEquals("service", form.getServiceName());
        assertEquals("1.1.1.1", form.getIp());
        assertEquals(8848, form.getPort());
    }
    
    @Test
    void testClientServiceFormValidateThrowsWhenServiceNameBlank() {
        ClientServiceForm form = new ClientServiceForm();
        
        assertThrows(NacosApiException.class, form::validate);
    }
    
    @Test
    void testServiceListFormValidateFillsBlankNamespace() throws NacosApiException {
        ServiceListForm form = new ServiceListForm();
        form.setNamespaceId("");
        form.setGroupNameParam("group");
        form.setServiceNameParam("service");
        form.setIgnoreEmptyService(true);
        form.setWithInstances(true);
        
        form.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals("group", form.getGroupNameParam());
        assertEquals("service", form.getServiceNameParam());
        assertTrue(form.isIgnoreEmptyService());
        assertTrue(form.isWithInstances());
    }
    
    @Test
    void testUpdateClusterFormValidateRequiredParametersAndDefaults() throws NacosApiException {
        assertThrows(NacosApiException.class, new UpdateClusterForm()::validate);
        assertThrows(NacosApiException.class, () -> {
            UpdateClusterForm form = new UpdateClusterForm();
            form.setServiceName("service");
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            UpdateClusterForm form = new UpdateClusterForm();
            form.setServiceName("service");
            form.setClusterName("cluster");
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            UpdateClusterForm form = createUpdateClusterForm();
            form.setUseInstancePort4Check(null);
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            UpdateClusterForm form = createUpdateClusterForm();
            form.setHealthChecker("");
            form.validate();
        });
        
        UpdateClusterForm form = createUpdateClusterForm();
        form.setNamespaceId("");
        form.setGroupName("");
        form.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertEquals("", form.getMetadata());
        assertEquals("service", form.getServiceName());
        assertEquals("cluster", form.getClusterName());
        assertEquals(80, form.getCheckPort());
        assertTrue(form.isUseInstancePort4Check());
        assertEquals("{}", form.getHealthChecker());
        
        UpdateClusterForm metadataForm = createUpdateClusterForm();
        metadataForm.setMetadata("metadata");
        metadataForm.validate();
        
        assertEquals("metadata", metadataForm.getMetadata());
    }
    
    @Test
    void testUpdateSwitchFormValidateAndObjectMethods() throws NacosApiException {
        assertThrows(NacosApiException.class, new UpdateSwitchForm()::validate);
        assertThrows(NacosApiException.class, () -> {
            UpdateSwitchForm form = new UpdateSwitchForm();
            form.setEntry("entry");
            form.validate();
        });
        
        UpdateSwitchForm form = createUpdateSwitchForm("entry", "value", true);
        UpdateSwitchForm same = createUpdateSwitchForm("entry", "value", true);
        UpdateSwitchForm different = createUpdateSwitchForm("entry", "other", true);
        form.validate();
        
        assertTrue(form.getDebug());
        assertEquals("entry", form.getEntry());
        assertEquals("value", form.getValue());
        assertEquals(form, form);
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
        assertTrue(form.toString().contains("entry='entry'"));
    }
    
    @Test
    void testServiceFormValidateAndObjectMethods() throws NacosApiException {
        assertThrows(NacosApiException.class, new ServiceForm()::validate);
        
        ServiceForm form = createServiceForm("service");
        ServiceForm same = createServiceForm("service");
        ServiceForm different = createServiceForm("other");
        form.validate();
        same.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertFalse(form.getEphemeral());
        assertEquals(0.0F, form.getProtectThreshold());
        assertEquals("", form.getMetadata());
        assertEquals("", form.getSelector());
        assertEquals(form, form);
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
        assertTrue(form.toString().contains("serviceName='service'"));
    }
    
    @Test
    void testInstanceFormValidateAndObjectMethods() throws NacosApiException {
        assertThrows(NacosApiException.class, new InstanceForm()::validate);
        assertThrows(NacosApiException.class, () -> {
            InstanceForm form = createInstanceForm();
            form.setIp("");
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            InstanceForm form = createInstanceForm();
            form.setPort(null);
            form.validate();
        });
        
        InstanceForm form = createInstanceForm();
        InstanceForm same = createInstanceForm();
        InstanceForm different = createInstanceForm();
        different.setEphemeral(false);
        form.validate();
        same.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, form.getClusterName());
        assertTrue(form.getHealthy());
        assertEquals(1.0, form.getWeight());
        assertTrue(form.getEnabled());
        assertEquals(form, form);
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
        assertTrue(form.toString().contains("serviceName='service'"));
    }
    
    @Test
    void testUpdateHealthFormValidateAndObjectMethods() throws NacosApiException {
        assertThrows(NacosApiException.class, new UpdateHealthForm()::validate);
        assertThrows(NacosApiException.class, () -> {
            UpdateHealthForm form = createUpdateHealthForm();
            form.setServiceName("");
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            UpdateHealthForm form = createUpdateHealthForm();
            form.setIp("");
            form.validate();
        });
        assertThrows(NacosApiException.class, () -> {
            UpdateHealthForm form = createUpdateHealthForm();
            form.setPort(null);
            form.validate();
        });
        
        UpdateHealthForm form = createUpdateHealthForm();
        UpdateHealthForm same = createUpdateHealthForm();
        UpdateHealthForm different = createUpdateHealthForm();
        different.setHealthy(false);
        form.validate();
        same.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, form.getClusterName());
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
        assertTrue(form.toString().contains("healthy=true"));
    }
    
    @Test
    void testInstanceMetadataBatchOperationFormValidateAndObjectMethods()
        throws NacosApiException {
        assertThrows(NacosApiException.class, new InstanceMetadataBatchOperationForm()::validate);
        assertThrows(NacosApiException.class, () -> {
            InstanceMetadataBatchOperationForm form = createMetadataBatchForm();
            form.setMetadata("");
            form.validate();
        });
        
        InstanceMetadataBatchOperationForm form = createMetadataBatchForm();
        InstanceMetadataBatchOperationForm same = createMetadataBatchForm();
        InstanceMetadataBatchOperationForm different = createMetadataBatchForm();
        different.setInstances("[{}]");
        form.validate();
        same.validate();
        
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, form.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, form.getGroupName());
        assertEquals("", form.getConsistencyType());
        assertEquals("", form.getInstances());
        assertEquals(form, form);
        assertEquals(form, same);
        assertEquals(form.hashCode(), same.hashCode());
        assertNotEquals(form, different);
        assertNotEquals(form, null);
        assertNotEquals(form, new Object());
        assertTrue(form.toString().contains("metadata='metadata'"));
    }
    
    private UpdateClusterForm createUpdateClusterForm() {
        UpdateClusterForm form = new UpdateClusterForm();
        form.setServiceName("service");
        form.setClusterName("cluster");
        form.setCheckPort(80);
        form.setUseInstancePort4Check(true);
        form.setHealthChecker("{}");
        return form;
    }
    
    private UpdateSwitchForm createUpdateSwitchForm(String entry, String value, boolean debug) {
        UpdateSwitchForm form = new UpdateSwitchForm();
        form.setEntry(entry);
        form.setValue(value);
        form.setDebug(debug);
        return form;
    }
    
    private ServiceForm createServiceForm(String serviceName) {
        ServiceForm form = new ServiceForm();
        form.setServiceName(serviceName);
        return form;
    }
    
    private InstanceForm createInstanceForm() {
        InstanceForm form = new InstanceForm();
        form.setServiceName("service");
        form.setIp("1.1.1.1");
        form.setPort(8848);
        form.setMetadata("metadata");
        form.setEphemeral(true);
        return form;
    }
    
    private UpdateHealthForm createUpdateHealthForm() {
        UpdateHealthForm form = new UpdateHealthForm();
        form.setHealthy(true);
        form.setServiceName("service");
        form.setIp("1.1.1.1");
        form.setPort(8848);
        return form;
    }
    
    private InstanceMetadataBatchOperationForm createMetadataBatchForm() {
        InstanceMetadataBatchOperationForm form = new InstanceMetadataBatchOperationForm();
        form.setServiceName("service");
        form.setMetadata("metadata");
        return form;
    }
}
