package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * interface of request and response scanner.
 *
 * @author dingjuntao
 * @date 2021/7/8 16:48
 */
public abstract class PayLoaderProvider {
    
    /**
     * get the Request classes.
     *
     * @return Set of class extends Request
     * @throws Exception exception throws .
     */
    public Set<Class<? extends Request>> getPayLoadRequestSet() {
        String packageName = this.getClass().getPackage().getName();
        int lastIndex = packageName.lastIndexOf(".");
        String requestPackageName = packageName.substring(0, lastIndex + 1) + "request";
        ArrayList<Class> payLoadRequestList =  getAllClassByAbstractClass(Request.class, requestPackageName);
        Set<Class<? extends Request>> payLoadRequestSet = new HashSet<>();
        for (Class clazz: payLoadRequestList) {
            boolean addFlag = payLoadRequestSet.add(clazz);
            if (!addFlag) {
                throw new RuntimeException(String.format("Fail to Load Request class, clazz:%s ", clazz.getCanonicalName()));
            }
        }
        return payLoadRequestSet;
    }
    
    /**
     * get the Response classes.
     *
     * @return Set of class extends Response
     * @throws Exception exception throws .
     */
    public Set<Class<? extends Response>> getPayLoadResponseSet() {
        String packageName = this.getClass().getPackage().getName();
        int lastIndex = packageName.lastIndexOf(".");
        String responsePackageName = packageName.substring(0, lastIndex + 1) + "response";
        
        ArrayList<Class> payLoadResponseList  = getAllClassByAbstractClass(Response.class, responsePackageName);
        
        Set<Class<? extends Response>> payLoadResponseSet = new HashSet<>();
        for (Class clazz: payLoadResponseList) {
            boolean addFlag = payLoadResponseSet.add(clazz);
            if (!addFlag) {
                throw new RuntimeException(String.format("Fail to Load Response class, clazz:%s ", clazz.getCanonicalName()));
            }
        }
        return payLoadResponseSet;
    }
    
    /**
     * Get all subclasses under the specified package.
     */
    private ArrayList<Class> getAllClassByAbstractClass(Class clazz, String packageName) {
        ArrayList<Class> list = new ArrayList<>();
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            ArrayList<Class> allClass = getAllClass(packageName);
            for (Class eachClass : allClass) {
                if (clazz.isAssignableFrom(eachClass)) {
                    if (!clazz.equals(eachClass) && ! Modifier.isAbstract(eachClass.getModifiers())) {
                        list.add(eachClass);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Find all classes from a packageName.
     */
    private  ArrayList<Class> getAllClass(String packageName) {

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
    private List<String> getClassName(String packageName) {

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
            throw new RuntimeException("url of packagePath cannot be null!");
        }
        return fileNames;
    }

    /**
     * Get all the classes in a package from the project file.
     * @return classNames
     */
    private  List<String> getClassNameByFile(String filePath) {
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
    private List<String> getClassNameByJar(JarFile jarFile) {
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
