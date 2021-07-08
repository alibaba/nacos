package com.alibaba.nacos.api.utils;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.net.JarURLConnection;
import java.util.jar.JarEntry;

/**
 * Get subclass.
 *
 * @author dingjuntao
 * @date 2021/7/8 19:26
 */
public class ClassUtils {
    
    /**
     * Get all subclasses under the specified package.
     */
    public static ArrayList<Class> getAllClassByAbstractClass(Class clazz, String packageName) {
        
        ArrayList<Class> list = new ArrayList<>();
        
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            try {
                ArrayList<Class> allClass = getAllClass(packageName);
                for (Class eachClass : allClass) {
                    if (clazz.isAssignableFrom(eachClass)) {
                        if (!clazz.equals(eachClass)) {
                            list.add(eachClass);
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("getAllClassByAbstractClass Error: " + e.getMessage());
            }
        }
        return list;
    }
    
    /**
     * Find all classes from a packageName.
     */
    private static ArrayList<Class> getAllClass(String packageName) {
        
        List<String> classNameList =  getClassName(packageName);
        ArrayList<Class> list = new ArrayList<>();
        
        for (String className : classNameList) {
            try {
                list.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("load class from name failed:" + className + e.getMessage());
            }
        }
        
        return list;
    }
    
    /**
     *  Get all classes under the package.
     * @return list of class name
     */
    public static List<String> getClassName(String packageName) {
        
        List<String> fileNames = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        String packagePath = packageName.replace(".", "/");
        URL url = loader.getResource(packagePath);
        
        if (url != null) {
            String type = url.getProtocol();
            if (type.equals("file")) {
                String fileSearchPath = url.getPath();
                fileNames = getClassNameByFile(fileSearchPath);
            } else if (type.equals("jar")) {
                try {
                    JarURLConnection jarUrlConnection = (JarURLConnection) url.openConnection();
                    JarFile jarFile = jarUrlConnection.getJarFile();
                    fileNames = getClassNameByJar(jarFile);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("open Package URL failed：" + e.getMessage());
                }
                
            } else {
                throw new RuntimeException("file system not support! cannot load MsgProcessor！");
            }
        } else {
            throw new RuntimeException("url cannot be null!");
        }
        return fileNames;
    }
    
    /**
     * Get all the classes in a package from the project file.
     * @return classNames
     */
    private static List<String> getClassNameByFile(String filePath) {
        List<String> classNames = new ArrayList<String>();
        File file = new File(filePath);
        File[] childFiles = file.listFiles();
        for (File childFile : childFiles) {
            if (childFile.isDirectory()) {
                classNames.addAll(getClassNameByFile(childFile.getPath()));
            } else {
                String childFilePath = childFile.getPath();
                if (childFilePath.endsWith(".class")) {
                    childFilePath = childFilePath.substring(childFilePath.indexOf("\\classes") + 9, childFilePath.lastIndexOf("."));
                    childFilePath = childFilePath.replace("\\", ".");
                    classNames.add(childFilePath);
                }
            }
        }
        return classNames;
    }
    
    /**
     * Get all classes in a package from jar .
     * @return classNames
     */
    private static List<String> getClassNameByJar(JarFile jarFile) {
        List<String> classNames = new ArrayList<String>();
        try {
            Enumeration<JarEntry> entrys = jarFile.entries();
            while (entrys.hasMoreElements()) {
                JarEntry jarEntry = entrys.nextElement();
                String entryName = jarEntry.getName();
                if (entryName.endsWith(".class")) {
                    entryName = entryName.replace("/", ".").substring(0, entryName.lastIndexOf("."));
                    classNames.add(entryName);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("getClassNameByJar Error:" + e.getMessage());
        }
        return classNames;
    }
    
}
