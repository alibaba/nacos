package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author klw
 * @ClassName: ZipUtils
 * @Description: zip压缩工具类
 * @date 2019/5/14 16:59
 */
public class ZipUtils {

    /**
     * @author klw
     * @Description: 将字符串使用gzip压缩,并返回压缩后的byte[]
     * @Date 2019/5/14 17:17
     * @Param [source]
     * @return byte[]
     */
    public static byte[] gzipString(String source){
        if (null == source || source.trim().length() <= 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(source.getBytes(Constants.ENCODE));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return out.toByteArray();
    }

    /**
     * @author klw
     * @Description: 将压缩后的byte[]解压缩为字符串
     * @Date 2019/5/14 17:24
     * @Param [source]
     * @return java.lang.String
     */
    public static String unGzipString(byte[] source){
        if(source == null || source.length <= 0){
            return null;
        }
        ByteArrayOutputStream out = null;
        ByteArrayInputStream in = null;
        GZIPInputStream gzip = null;
        String result = null;
        try {
            out = new ByteArrayOutputStream();
            in = new ByteArrayInputStream(source);
            gzip = new GZIPInputStream(in);
            byte[] buffer = new byte[1024];
            int offset  = -1;
            while ((offset  = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, offset );
            }
            result = out.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

}
