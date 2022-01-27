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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ZipUtilsTest {
    
    @Test
    public void testZip() {
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        byte[] zip = ZipUtils.zip(zipItemList);
        Assert.assertTrue(zip != null && zip.length > 0);
    }
    
    @Test
    public void testUnzip() {
        
        List<ZipUtils.ZipItem> zipItemList = new ArrayList<>();
        zipItemList.add(new ZipUtils.ZipItem("test", "content"));
        byte[] zip = ZipUtils.zip(zipItemList);
        Assert.assertTrue(zip != null && zip.length > 0);
        
        ZipUtils.UnZipResult unZipResult = ZipUtils.unzip(zip);
        List<ZipUtils.ZipItem> result = unZipResult.getZipItemList();
        Assert.assertEquals(zipItemList.size(), result.size());
        Assert.assertEquals(zipItemList.get(0).getItemName(), result.get(0).getItemName());
        Assert.assertEquals(zipItemList.get(0).getItemData(), result.get(0).getItemData());
        
    }
}
