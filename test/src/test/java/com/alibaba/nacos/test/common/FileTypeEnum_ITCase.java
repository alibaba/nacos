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
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName(value.name());
            Assert.assertEquals(fileTypeEnum, value);
        }
    }
    
    @Test
    public void fileTypeEnum_test2() {
        for (FileTypeEnum value : FileTypeEnum.values()) {
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName(value.getFileType());
            Assert.assertNotNull(fileTypeEnum);
        }
    }
    
    @Test
    public void fileTypeEnum_test3() {
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName("t");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName("");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName(".");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName("1");
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
        
        fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionName(null);
        Assert.assertEquals(fileTypeEnum, FileTypeEnum.TEXT);
    }
    
}
