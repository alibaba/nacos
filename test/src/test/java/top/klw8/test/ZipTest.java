package top.klw8.test;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author klw
 * @ClassName: ZipTest
 * @Description: zip测试
 * @date 2019/5/16 14:22
 */
public class ZipTest {

    public static void main(String[] args) throws IOException {
        // 压缩
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(byteOut);
        zipOut.putNextEntry(new ZipEntry("DEFAULT_GROUP2/"));
        zipOut.putNextEntry(new ZipEntry("DEFAULT_GROUP/test10.yml"));
        zipOut.write("abc: 123".getBytes());
        zipOut.putNextEntry(new ZipEntry("DEFAULT_GROUP/test11.yml"));
        zipOut.write("def: 456".getBytes());
        zipOut.close();
        FileOutputStream fileOut = new FileOutputStream(new File("E:/test.zip"));
        byte[] zipBytes = byteOut.toByteArray();
        fileOut.write(zipBytes);
        fileOut.close();
        byteOut.close();

        //解压
        ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry = null;
        while((entry = zipIn.getNextEntry()) != null){
            System.out.println(entry.getName());
            if(entry.isDirectory()){
                System.out.println("是文件夹");
            } else {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int offset  = -1;
                while ((offset  = zipIn.read(buffer)) != -1) {
                    out.write(buffer, 0, offset );
                }
                System.out.println(out.toString("UTF-8"));
                out.close();
            }
        }
        zipIn.close();
    }
}
