/*
package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.exception.AuthPluginException;

import javax.naming.AuthenticationException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
//import org.dom4j.io.SAXReader;

public class AuthXmlParse {
    public static List<AuthPlugin> getAuthPluginList() throws AuthPluginException{
        List<AuthPlugin> list = new ArrayList<>();
        
        SAXReader saxReader = new SAXReader();
        Document document = null;
        try{
            document = saxReader.read(new File("pom.xml"));
        }catch(Exception e){
            throw new AuthPluginException("read pom.xml error,"+e.getMessage());
        }
    
        Element root = document.getRootElement();
        List<?> authplugins = root.elements("plugin");
        for (Object object: authplugins){
            Element element = (Element) object;
            AuthPlugin authPlugin = new AuthPlugin();
            authPlugin.setPluginName(element.elementText("name"));
            authPlugin.setJarPath(element.elementText("jar"));
            authPlugin.setClassName(element.elementText("class"));
            list.add(authPlugin);
        }
        return list;
    }

}
*/