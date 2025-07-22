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
import org.dbflute.utflute.mocklet.MockletHttpServletRequestImpl;
import org.dbflute.utflute.mocklet.MockletServletContextImpl;

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
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
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
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertNull(params.getQuery());
        assertTrue(params.getPageSize() > 0);
        assertTrue(params.getStartPosition() >= 0);
    }

    public void test_JsonRequestParams_invalidPageSize() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("num", "invalid");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertTrue(params.getPageSize() > 0); // Should fall back to default
    }

    public void test_JsonRequestParams_extraQueries() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
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
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(SearchRequestType.JSON, params.getType());
    }

    public void test_JsonRequestParams_locale() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
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

}