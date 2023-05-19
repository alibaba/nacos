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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * ZipUtils for import and export.
 *
 * @author klw
 * @date 2019/5/14 16:59
 */
public class ZipUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipUtils.class);
    
    public static class ZipItem {
        
        private String itemName;
        
        private String itemData;
        
        public ZipItem(String itemName, String itemData) {
            this.itemName = itemName;
            this.itemData = itemData;
        }
        
        public String getItemName() {
            return itemName;
        }
        
        public void setItemName(String itemName) {
            this.itemName = itemName;
        }
        
        public String getItemData() {
            return itemData;
        }
        
        public void setItemData(String itemData) {
            this.itemData = itemData;
        }
    }
    
    public static class UnZipResult {
        
        private List<ZipItem> zipItemList;
        
        private ZipItem metaDataItem;
        
        public UnZipResult(List<ZipItem> zipItemList, ZipItem metaDataItem) {
            this.zipItemList = zipItemList;
            this.metaDataItem = metaDataItem;
        }
        
        public List<ZipItem> getZipItemList() {
            return zipItemList;
        }
        
        public void setZipItemList(List<ZipItem> zipItemList) {
            this.zipItemList = zipItemList;
        }
        
        public ZipItem getMetaDataItem() {
            return metaDataItem;
        }
        
        public void setMetaDataItem(ZipItem metaDataItem) {
            this.metaDataItem = metaDataItem;
        }
    }
    
    /**
     * zip method.
     */
    public static byte[] zip(List<ZipItem> source) {
        byte[] result = null;
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream(); ZipOutputStream zipOut = new ZipOutputStream(
                byteOut)) {
            for (ZipItem item : source) {
                zipOut.putNextEntry(new ZipEntry(item.getItemName()));
                zipOut.write(item.getItemData().getBytes(StandardCharsets.UTF_8));
            }
            zipOut.flush();
            zipOut.finish();
            result = byteOut.toByteArray();
        } catch (IOException e) {
            LOGGER.error("an error occurred while compressing data.", e);
        }
        return result;
    }
    
    /**
     * unzip method.
     */
    public static UnZipResult unzip(byte[] source) {
        List<ZipItem> itemList = new ArrayList<>();
        ZipItem metaDataItem = null;
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int offset;
                    while ((offset = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, offset);
                    }
                    String entryName = entry.getName();
                    if (metaDataItem == null && Constants.CONFIG_EXPORT_METADATA.equals(entryName)) {
                        metaDataItem = new ZipItem(entryName, out.toString("UTF-8"));
                        continue;
                    }
                    if (metaDataItem == null && Constants.CONFIG_EXPORT_METADATA_NEW.equals(entryName)) {
                        metaDataItem = new ZipItem(entryName, out.toString("UTF-8"));
                        continue;
                    }
                    itemList.add(new ZipItem(entryName, out.toString("UTF-8")));
                } catch (IOException e) {
                    LOGGER.error("unzip error", e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("unzip error", e);
        }
        return new UnZipResult(itemList, metaDataItem);
    }
    
}
