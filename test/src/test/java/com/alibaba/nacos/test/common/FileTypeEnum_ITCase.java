package com.alibaba.nacos.test.common;

import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author by jiangmin.wu on 2020/12/30
 */
public class FileTypeEnum_ITCase {
    
    @Test
    public void fileTypeEnum_test1() {
        for (FileTypeEnum value : FileTypeEnum.values()) {
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(value.name());
            Assert.assertEquals(fileTypeEnum, value);
        }
    }
    
    @Test
    public void fileTypeEnum_test2() {
        for (FileTypeEnum value : FileTypeEnum.values()) {
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(value.getFileType());
            Assert.assertNotNull(fileTypeEnum);
        }
    }
    
    @Test
    public void fileTypeEnum_test3() {
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("t");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(".");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType("1");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(null);
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
    }
    
}
