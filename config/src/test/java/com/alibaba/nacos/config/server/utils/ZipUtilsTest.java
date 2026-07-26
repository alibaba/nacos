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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.Constants;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipUtilsTest {
    
    @Test
    void testConstructor() {
        assertNotNull(new ZipUtils());
    }
    
    @Test
    void testZip() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        byte[] zip = ZipUtils.zip(zipItemList);
        assertTrue(zip != null && zip.length > 0);
    }
    
    @Test
    void testZipReturnsNullWhenDuplicateEntryFails() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        zipItemList.add(new ZipUtils.ZipItem("test", "duplicate"));
        
        assertNull(ZipUtils.zip(zipItemList));
    }
    
    @Test
    void testUnzip() {
        
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        byte[] zip = ZipUtils.zip(zipItemList);
        assertTrue(zip != null && zip.length > 0);
        
        ZipUtils.UnZipResult unZipResult = ZipUtils.unzip(zip);
        List<ZipUtils.ZipItem> result = unZipResult.getZipItemList();
        assertEquals(zipItemList.size(), result.size());
        assertEquals(zipItemList.get(0).getItemName(), result.get(0).getItemName());
        assertEquals(zipItemList.get(0).getItemData(), result.get(0).getItemData());
    }
    
    @Test
    void testUnzipWithMetadata() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(Constants.CONFIG_EXPORT_METADATA));
            zos.write("{\"metadata\":[]}".getBytes());
            zos.putNextEntry(new ZipEntry("config1.json"));
            zos.write("data1".getBytes());
        }
        ZipUtils.UnZipResult result = ZipUtils.unzip(baos.toByteArray());
        assertNotNull(result.getMetaDataItem());
        assertEquals(Constants.CONFIG_EXPORT_METADATA,
            result.getMetaDataItem().getItemName());
        assertEquals(1, result.getZipItemList().size());
    }
    
    @Test
    void testUnzipWithNewMetadata() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(Constants.CONFIG_EXPORT_METADATA_NEW));
            zos.write("metadata content".getBytes());
            zos.putNextEntry(new ZipEntry("file.txt"));
            zos.write("text".getBytes());
        }
        ZipUtils.UnZipResult result = ZipUtils.unzip(baos.toByteArray());
        assertNotNull(result.getMetaDataItem());
        assertEquals(Constants.CONFIG_EXPORT_METADATA_NEW,
            result.getMetaDataItem().getItemName());
    }
    
    @Test
    void testUnzipWithDirectory() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.putNextEntry(new ZipEntry("file.txt"));
            zos.write("content".getBytes());
        }
        ZipUtils.UnZipResult result = ZipUtils.unzip(baos.toByteArray());
        assertEquals(1, result.getZipItemList().size());
    }
    
    @Test
    void testUnzipIgnoresCorruptedEntry() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        byte[] zip = ZipUtils.zip(zipItemList);
        
        ZipUtils.UnZipResult result = ZipUtils.unzip(Arrays.copyOf(zip, zip.length / 2));
        
        assertNotNull(result);
    }
    
    @Test
    void testUnzipIgnoresMalformedLocalHeaders() {
        byte[] malformedEntryData = new byte[] {
            0x50, 0x4b, 0x03, 0x04, 20, 0, 0, 0, 8, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 5, 0,
            0, 0, 5, 0, 0, 0, 4, 0, 0, 0,
            't', 'e', 's', 't', 1, 2, 3, 4, 5
        };
        byte[] truncatedHeader = new byte[] {
            0x50, 0x4b, 0x03, 0x04, 20, 0, 0, 0, 8, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 5, 0,
            0, 0, 5, 0, 0, 0, 4, 0, 0, 0
        };
        
        assertNotNull(ZipUtils.unzip(malformedEntryData));
        assertNotNull(ZipUtils.unzip(truncatedHeader));
    }
    
    @Test
    void testZipItemSetters() {
        ZipUtils.ZipItem item = new ZipUtils.ZipItem("name", "data");
        item.setItemName("newName");
        item.setItemData("newData");
        assertEquals("newName", item.getItemName());
        assertEquals("newData", item.getItemData());
    }
    
    @Test
    void testUnZipResultSetters() {
        ZipUtils.UnZipResult result = new ZipUtils.UnZipResult(
            new ArrayList<>(), null);
        assertNull(result.getMetaDataItem());
        
        List<ZipUtils.ZipItem> items = new ArrayList<>();
        items.add(new ZipUtils.ZipItem("a", "b"));
        result.setZipItemList(items);
        assertEquals(1, result.getZipItemList().size());
        
        ZipUtils.ZipItem meta = new ZipUtils.ZipItem("meta", "val");
        result.setMetaDataItem(meta);
        assertEquals("meta", result.getMetaDataItem().getItemName());
    }
}
