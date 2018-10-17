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

package com.alibaba.nacos.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.io.IOUtils;

/**
 * 
 * 遍历目标文件夹下的所有文件，在文件头上加上license协议
 * 注意：读取/写入文件默认用utf-8进行
 * @author en.xuze@alipay.com
 * @version $Id: AppendLicense.java, v 0.1 2018年7月4日 下午2:31:16 en.xuze@alipay.com Exp $
 */
public class AppendLicense {

    private static List<File> targetFiles = new LinkedList<File>();
    private static String licenseFile = "/Users/en.xuze/git/nacos/common/license";
    private static String targetDirOrFile = "/Users/en.xuze/git/nacos";

    public static void main(String[] args) throws Exception {
        List<String> licenseContents = IOUtils.readLines(new FileInputStream(new File(licenseFile)), "utf-8");
        readFiles(targetDirOrFile);
        for (Iterator<File> iterator = targetFiles.iterator(); iterator.hasNext();) {
            File file = (File) iterator.next();
            List<String> srcFileContents = IOUtils.readLines(new FileInputStream(file), "utf-8");
            List<String> writeContents = new ArrayList<String>();
            writeContents.addAll(licenseContents);
            writeContents.addAll(srcFileContents);
            IOUtils.writeLines(writeContents, "\n", new FileOutputStream(file));
            System.out.println("append license to file:" + file.getAbsolutePath());
        }
    }

    private static void readFiles(String filePath) {
        if (filePath == null) {
            return;
        }
        File temp = new File(filePath);
        File[] files = null;
        if (temp.isFile()) {
            if (needAppend(temp.getName())) {
                targetFiles.add(temp);
            }
        } else {
            files = temp.listFiles();
        }
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile()) {
                if (needAppend(f.getName())) {
                    targetFiles.add(f);
                }
            } else if (f.isDirectory()) {
                readFiles(f.getPath());
            }
        }
    }

    private static boolean needAppend(String fileName) {
        return (fileName.endsWith(".java"));
    }
}
