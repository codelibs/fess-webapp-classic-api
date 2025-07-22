/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.plugin.webapp.api.classic;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.entity.SearchRequestParams.SearchRequestType;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;

public class JsonApiManagerTest extends LastaFluteTestCase {

    private WebApiManagerFactory webApiManagerFactory;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        ComponentUtil.setFessConfig(new FessConfig.SimpleImpl() {
            private static final long serialVersionUID = 1L;

            @Override
            public Integer getPagingSearchPageStartAsInteger() {
                return 0;
            }

            @Override
            public Integer getPagingSearchPageSizeAsInteger() {
                return 20;
            }

            @Override
            public Integer getPagingSearchPageMaxSizeAsInteger() {
                return 100;
            }
        });
        webApiManagerFactory = new WebApiManagerFactory();
        ComponentUtil.register(webApiManagerFactory, "webApiManagerFactory");
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    public void test_pathPrefix() {
        JsonApiManager jsonApiManager = getComponent("jsonApiManager");
        assertEquals("/json", jsonApiManager.getPathPrefix());
    }

    public void test_JsonRequestParams_construction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("q", "test query");
        request.setParameter("num", "20");
        request.setParameter("start", "10");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals("test query", params.getQuery());
        assertEquals(20, params.getPageSize());
        assertEquals(10, params.getStartPosition());
    }

    public void test_JsonRequestParams_defaultValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertNull(params.getQuery());
        assertTrue(params.getPageSize() > 0);
        assertTrue(params.getStartPosition() >= 0);
    }

    public void test_JsonRequestParams_invalidPageSize() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("num", "invalid");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertTrue(params.getPageSize() > 0); // Should fall back to default
    }

    public void test_JsonRequestParams_extraQueries() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ex_q", "extra1");
        request.addParameter("ex_q", "extra2");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        String[] extraQueries = params.getExtraQueries();
        assertEquals(2, extraQueries.length);
        assertEquals("extra1", extraQueries[0]);
        assertEquals("extra2", extraQueries[1]);
    }

    public void test_JsonRequestParams_type() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(SearchRequestType.JSON, params.getType());
    }

    public void test_JsonRequestParams_locale() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(Locale.ROOT, params.getLocale());
    }

    public void test_detailedMessage_nullException() {
        TestableJsonApiManager manager = new TestableJsonApiManager();
        String result = manager.testDetailedMessage(null);
        assertEquals("Unknown", result);
    }

    public void test_detailedMessage_simpleException() {
        TestableJsonApiManager manager = new TestableJsonApiManager();
        RuntimeException ex = new RuntimeException("Test error");
        String result = manager.testDetailedMessage(ex);
        assertEquals("RuntimeException[Test error]", result);
    }

    public void test_detailedMessage_exceptionWithoutMessage() {
        TestableJsonApiManager manager = new TestableJsonApiManager();
        RuntimeException ex = new RuntimeException();
        String result = manager.testDetailedMessage(ex);
        // The actual implementation includes [null] when message is null
        assertEquals("RuntimeException[null]", result);
    }

    public void test_detailedMessage_nestedException() {
        TestableJsonApiManager manager = new TestableJsonApiManager();
        RuntimeException cause = new RuntimeException("Root cause");
        IllegalArgumentException ex = new IllegalArgumentException("Wrapper exception", cause);
        String result = manager.testDetailedMessage(ex);
        assertTrue(result.contains("IllegalArgumentException[Wrapper exception]"));
        assertTrue(result.contains("nested:"));
        assertTrue(result.contains("RuntimeException[Root cause]"));
    }

    // Test helper class to expose protected methods
    public static class TestableJsonApiManager extends JsonApiManager {
        public String testDetailedMessage(Throwable t) {
            return detailedMessage(t);
        }
    }

    // Mock HttpServletRequest for testing
    private static class MockHttpServletRequest implements HttpServletRequest {
        private final Map<String, String[]> parameters = new HashMap<>();
        private final Map<String, Object> attributes = new HashMap<>();

        public void setParameter(String name, String value) {
            parameters.put(name, new String[] { value });
        }

        public void addParameter(String name, String value) {
            String[] existing = parameters.get(name);
            if (existing == null) {
                parameters.put(name, new String[] { value });
            } else {
                String[] newArray = new String[existing.length + 1];
                System.arraycopy(existing, 0, newArray, 0, existing.length);
                newArray[existing.length] = value;
                parameters.put(name, newArray);
            }
        }

        @Override
        public String getParameter(String name) {
            String[] values = parameters.get(name);
            return values != null && values.length > 0 ? values[0] : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            return parameters.get(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return parameters;
        }

        @Override
        public Object getAttribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }

        // Stub implementations for other required methods
        @Override
        public String getAuthType() {
            return null;
        }

        @Override
        public jakarta.servlet.http.Cookie[] getCookies() {
            return null;
        }

        @Override
        public long getDateHeader(String name) {
            return 0;
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            return null;
        }

        @Override
        public java.util.Enumeration<String> getHeaderNames() {
            return null;
        }

        @Override
        public int getIntHeader(String name) {
            return 0;
        }

        @Override
        public String getMethod() {
            return null;
        }

        @Override
        public String getPathInfo() {
            return null;
        }

        @Override
        public String getPathTranslated() {
            return null;
        }

        @Override
        public String getContextPath() {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public java.security.Principal getUserPrincipal() {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public String getRequestURI() {
            return null;
        }

        @Override
        public StringBuffer getRequestURL() {
            return null;
        }

        @Override
        public String getServletPath() {
            return null;
        }

        @Override
        public jakarta.servlet.http.HttpSession getSession(boolean create) {
            return null;
        }

        @Override
        public jakarta.servlet.http.HttpSession getSession() {
            return null;
        }

        @Override
        public String changeSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }

        @Override
        public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) {
            return false;
        }

        @Override
        public void login(String username, String password) {
        }

        @Override
        public void logout() {
        }

        @Override
        public java.util.Collection<jakarta.servlet.http.Part> getParts() {
            return null;
        }

        @Override
        public jakarta.servlet.http.Part getPart(String name) {
            return null;
        }

        @Override
        public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
            return null;
        }

        @Override
        public java.util.Enumeration<String> getAttributeNames() {
            return null;
        }

        @Override
        public String getCharacterEncoding() {
            return null;
        }

        @Override
        public void setCharacterEncoding(String env) {
        }

        @Override
        public int getContentLength() {
            return 0;
        }

        @Override
        public long getContentLengthLong() {
            return 0;
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            return null;
        }

        @Override
        public java.util.Enumeration<String> getParameterNames() {
            return null;
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public String getScheme() {
            return null;
        }

        @Override
        public String getServerName() {
            return null;
        }

        @Override
        public int getServerPort() {
            return 0;
        }

        @Override
        public java.io.BufferedReader getReader() {
            return null;
        }

        @Override
        public String getRemoteAddr() {
            return null;
        }

        @Override
        public String getRemoteHost() {
            return null;
        }

        @Override
        public void removeAttribute(String name) {
        }

        @Override
        public java.util.Locale getLocale() {
            return null;
        }

        @Override
        public java.util.Enumeration<java.util.Locale> getLocales() {
            return null;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) {
            return null;
        }

        // getRealPath is deprecated in newer servlet API versions
        public String getRealPath(String path) {
            return null;
        }

        @Override
        public int getRemotePort() {
            return 0;
        }

        @Override
        public String getLocalName() {
            return null;
        }

        @Override
        public String getLocalAddr() {
            return null;
        }

        @Override
        public int getLocalPort() {
            return 0;
        }

        @Override
        public jakarta.servlet.ServletContext getServletContext() {
            return null;
        }

        @Override
        public jakarta.servlet.AsyncContext startAsync() {
            return null;
        }

        @Override
        public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest servletRequest,
                jakarta.servlet.ServletResponse servletResponse) {
            return null;
        }

        @Override
        public boolean isAsyncStarted() {
            return false;
        }

        @Override
        public boolean isAsyncSupported() {
            return false;
        }

        @Override
        public jakarta.servlet.AsyncContext getAsyncContext() {
            return null;
        }

        @Override
        public jakarta.servlet.DispatcherType getDispatcherType() {
            return null;
        }

        // Note: Some methods from newer servlet versions may not be available
        public String getRequestId() {
            return null;
        }

        public String getProtocolRequestId() {
            return null;
        }

        public jakarta.servlet.ServletConnection getServletConnection() {
            return null;
        }
    }
}