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
package com.alibaba.nacos.config.mock;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;




public class ServletContextMock implements ServletContext {

    @Override
    public String getContextPath() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServletContext getContext(String uripath) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getMajorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMinorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set getResourcePaths(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Servlet getServlet(String name) throws ServletException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getServlets() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getServletNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void log(String msg) {
        // TODO Auto-generated method stub

    }

    @Override
    public void log(Exception exception, String msg) {
        // TODO Auto-generated method stub

    }

    @Override
    public void log(String message, Throwable throwable) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getRealPath(String path) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getServerInfo() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getInitParameter(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getInitParameterNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getAttributeNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setAttribute(String name, Object object) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeAttribute(String name) {
        // TODO Auto-generated method stub

    }

    @Override
    public String getServletContextName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dynamic addFilter(String arg0, String arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dynamic addFilter(String arg0, Filter arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dynamic addFilter(String arg0, Class<? extends Filter> arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void addListener(Class<? extends EventListener> arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void addListener(String arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public <T extends EventListener> void addListener(T arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, String arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, Servlet arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0,
            Class<? extends Servlet> arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> arg0) throws ServletException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> arg0) throws ServletException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> arg0) throws ServletException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void declareRoles(String... arg0) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public ClassLoader getClassLoader() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getEffectiveMajorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getEffectiveMinorVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean setInitParameter(String arg0, String arg1) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> arg0)
            throws IllegalStateException, IllegalArgumentException {
        // TODO Auto-generated method stub
    }

	@Override
	public String getVirtualServerName() {
		// TODO Auto-generated method stub
		return null;
	}

}
