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

    public void test_JsonRequestParams_fields() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("fields.title", "search term");
        request.addParameter("fields.content", "value1");
        request.addParameter("fields.content", "value2");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        java.util.Map<String, String[]> fields = params.getFields();
        assertEquals(2, fields.size());
        assertTrue(fields.containsKey("title"));
        assertTrue(fields.containsKey("content"));
        assertEquals("search term", fields.get("title")[0]);
        assertEquals(2, fields.get("content").length);
        assertEquals("value1", fields.get("content")[0]);
        assertEquals("value2", fields.get("content")[1]);
    }

    public void test_JsonRequestParams_conditions() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("as.filetype", "pdf");
        request.addParameter("as.mimetype", "application/pdf");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        java.util.Map<String, String[]> conditions = params.getConditions();
        assertEquals(2, conditions.size());
        assertTrue(conditions.containsKey("filetype"));
        assertTrue(conditions.containsKey("mimetype"));
        assertEquals("pdf", conditions.get("filetype")[0]);
    }

    public void test_JsonRequestParams_languages() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.addParameter("lang", "ja");
        request.addParameter("lang", "en");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        String[] languages = params.getLanguages();
        assertEquals(2, languages.length);
        assertEquals("ja", languages[0]);
        assertEquals("en", languages[1]);
    }

    public void test_JsonRequestParams_sort() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("sort", "score.desc");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals("score.desc", params.getSort());
    }

    public void test_JsonRequestParams_similarDocHash() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("sdh", "abc123");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals("abc123", params.getSimilarDocHash());
    }

    public void test_JsonRequestParams_offset() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("offset", "5");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(5, params.getOffset());
    }

    public void test_JsonRequestParams_offset_default() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(0, params.getOffset());
    }

    public void test_JsonRequestParams_offset_invalid() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("offset", "invalid");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(0, params.getOffset());
    }

    public void test_JsonRequestParams_trackTotalHits() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("track_total_hits", "10000");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals("10000", params.getTrackTotalHits());
    }

    public void test_JsonRequestParams_getAttribute() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setAttribute("test_attribute", "test_value");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals("test_value", params.getAttribute("test_attribute"));
    }

    public void test_JsonRequestParams_pageSize_exceedsMax() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("num", "150"); // Exceeds max of 100

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(100, params.getPageSize()); // Should be capped at max
    }

    public void test_JsonRequestParams_pageSize_zero() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("num", "0");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(100, params.getPageSize()); // Should fall back to max
    }

    public void test_JsonRequestParams_pageSize_negative() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("num", "-10");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(100, params.getPageSize()); // Should fall back to max
    }

    public void test_JsonRequestParams_startPosition_invalid() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("start", "invalid");

        FessConfig fessConfig = ComponentUtil.getFessConfig();
        JsonApiManager.JsonRequestParams params = new JsonApiManager.JsonRequestParams(request, fessConfig);

        assertEquals(0, params.getStartPosition()); // Should fall back to default
    }

    // Test helper class to expose protected methods
    public static class TestableJsonApiManager extends JsonApiManager {
        public String testDetailedMessage(Throwable t) {
            return detailedMessage(t);
        }
    }

}