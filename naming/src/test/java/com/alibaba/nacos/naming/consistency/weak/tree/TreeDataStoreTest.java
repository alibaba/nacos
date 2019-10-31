/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.UUID;

/**
 * @author lostcharlie
 */
public class TreeDataStoreTest {
    @Test
    public void testReadDatum() throws Exception {
        String basePath = System.getProperty("user.dir") + File.separator + "tree-datum-test";
        TreeDataStore treeDataStore = new TreeDataStore();
        ReflectionTestUtils.setField(treeDataStore, "basePath", basePath);

        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, false);

        Instances value = new Instances();
        Instance instance = new Instance("192.168.0.1", 8888);
        value.getInstanceList().add(instance);
        Datum datum = new Datum();
        datum.key = key;
        datum.value = value;
        datum.timestamp.set(0L);

        treeDataStore.write(datum);
        Assert.assertTrue(new File(basePath).exists());
        Assert.assertTrue(treeDataStore.getFileName(datum.key).startsWith(basePath));
        Assert.assertTrue(new File(treeDataStore.getFileName(datum.key)).exists());

        Datum actual = treeDataStore.read(datum.key, Instances.class);
        Assert.assertNotNull(actual);
        Assert.assertEquals(datum.key, actual.key);
        Assert.assertEquals(datum.timestamp.get(), actual.timestamp.get());
        Assert.assertEquals(1, ((Instances) actual.value).getInstanceList().size());
        Assert.assertTrue(((Instances) actual.value).getInstanceList().contains(instance));

        TreeDataStoreTest.cleanUp(new File(basePath));
    }

    @Test
    public void testWriteDatum() throws Exception {
        String basePath = System.getProperty("user.dir") + File.separator + "tree-datum-test";
        TreeDataStore treeDataStore = new TreeDataStore();
        ReflectionTestUtils.setField(treeDataStore, "basePath", basePath);

        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, false);

        Instances value = new Instances();
        Instance instance = new Instance("192.168.0.1", 8888);
        value.getInstanceList().add(instance);
        Datum datum = new Datum();
        datum.key = key;
        datum.value = value;
        datum.timestamp.set(0L);

        treeDataStore.write(datum);
        Assert.assertTrue(new File(basePath).exists());
        Assert.assertTrue(treeDataStore.getFileName(datum.key).startsWith(basePath));
        Assert.assertTrue(new File(treeDataStore.getFileName(datum.key)).exists());

        TreeDataStoreTest.cleanUp(new File(basePath));
    }

    @Test
    public void testRemoveDatum() throws Exception {
        String basePath = System.getProperty("user.dir") + File.separator + "tree-datum-test";
        TreeDataStore treeDataStore = new TreeDataStore();
        ReflectionTestUtils.setField(treeDataStore, "basePath", basePath);

        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, false);

        Instances value = new Instances();
        Instance instance = new Instance("192.168.0.1", 8888);
        value.getInstanceList().add(instance);
        Datum datum = new Datum();
        datum.key = key;
        datum.value = value;
        datum.timestamp.set(0L);

        treeDataStore.write(datum);
        Assert.assertTrue(new File(basePath).exists());
        Assert.assertTrue(treeDataStore.getFileName(datum.key).startsWith(basePath));
        Assert.assertTrue(new File(treeDataStore.getFileName(datum.key)).exists());
        treeDataStore.remove(datum.key);
        Assert.assertFalse(new File(treeDataStore.getFileName(datum.key)).exists());

        TreeDataStoreTest.cleanUp(new File(basePath));
    }

    private static boolean cleanUp(File file) {
        if (!file.exists()) {
            return false;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File subFile : files) {
                boolean success = cleanUp(subFile);
                if (!success) {
                    return false;
                }
            }
        } else {// is a regular file
            boolean success = file.delete();
            if (!success) {
                return false;
            }
        }
        if (file.isDirectory()) {
            return file.delete();
        } else {
            return true;
        }
    }
}
