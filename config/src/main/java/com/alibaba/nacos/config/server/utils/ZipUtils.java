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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author klw
 * @ClassName: ZipUtils
 * @Description: zip压缩工具类
 * @date 2019/5/14 16:59
 */
public class ZipUtils {

//    /**
//     * @author klw
//     * @Description: 将字符串使用gzip压缩,并返回压缩后的byte[]
//     * @Date 2019/5/14 17:17
//     * @Param [source]
//     * @return byte[]
//     */
//    public static byte[] gzipString(String source){
//        if (null == source || source.trim().length() <= 0) {
//            return null;
//        }
//        ByteArrayOutputStream out = new ByteArrayOutputStream();
//        GZIPOutputStream gzip = null;
//        try {
//            gzip = new GZIPOutputStream(out);
//            gzip.write(source.getBytes(Constants.ENCODE));
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if(gzip != null) {
//                try {
//                    gzip.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return out.toByteArray();
//    }
//
//    /**
//     * @author klw
//     * @Description: 将压缩后的byte[]解压缩为字符串
//     * @Date 2019/5/14 17:24
//     * @Param [source]
//     * @return java.lang.String
//     */
//    public static String unGzipString(byte[] source){
//        if(source == null || source.length <= 0){
//            return null;
//        }
//        ByteArrayOutputStream out = null;
//        ByteArrayInputStream in = null;
//        GZIPInputStream gzip = null;
//        String result = null;
//        try {
//            out = new ByteArrayOutputStream();
//            in = new ByteArrayInputStream(source);
//            gzip = new GZIPInputStream(in);
//            byte[] buffer = new byte[1024];
//            int offset  = -1;
//            while ((offset  = gzip.read(buffer)) != -1) {
//                out.write(buffer, 0, offset );
//            }
//            result = out.toString("UTF-8");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }finally {
//            if (gzip != null) {
//                try {
//                    gzip.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (in != null) {
//                try {
//                    in.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (out != null) {
//                try {
//                    out.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return result;
//    }

    public static class ZipItem{

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

    public static class UnZipResult{

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

    public static byte[] zip(List<ZipItem> source){
        ByteArrayOutputStream byteOut = null;
        ZipOutputStream zipOut = null;
        byte[] result = null;
        try {
            byteOut = new ByteArrayOutputStream();
            zipOut = new ZipOutputStream(byteOut);
            for (ZipItem item : source) {
                zipOut.putNextEntry(new ZipEntry(item.getItemName()));
                zipOut.write(item.getItemData().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipOut != null) {
                try {
                    zipOut.flush();
                    zipOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (byteOut != null) {
                try {
                    byteOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        result = byteOut.toByteArray();
        return result;
    }

    public static UnZipResult unzip(byte[] source) {

        ZipInputStream zipIn = null;
        List<ZipItem> itemList = new ArrayList<>();
        ZipItem metaDataItem = null;
        try {
            zipIn = new ZipInputStream(new ByteArrayInputStream(source));
            ZipEntry entry = null;
            while ((entry = zipIn.getNextEntry()) != null && !entry.isDirectory()) {
                ByteArrayOutputStream out = null;
                try {
                    out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int offset = -1;
                    while ((offset = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, offset);
                    }
                    if(".meta.yml".equals(entry.getName())){
                        metaDataItem = new ZipItem(entry.getName(), out.toString("UTF-8"));
                    } else {
                        itemList.add(new ZipItem(entry.getName(), out.toString("UTF-8")));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (out != null) {
                        out.close();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipIn != null) {
                try {
                    zipIn.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new UnZipResult(itemList, metaDataItem);
    }



}
