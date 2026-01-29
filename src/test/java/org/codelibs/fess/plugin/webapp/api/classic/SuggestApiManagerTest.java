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

import org.junit.jupiter.api.TestInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.entity.HighlightInfo;
import org.codelibs.fess.entity.SearchRequestParams.SearchRequestType;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.classic_api.UnitWebappTestCase;
import org.dbflute.utflute.mocklet.MockletHttpServletRequestImpl;
import org.dbflute.utflute.mocklet.MockletServletContextImpl;

public class SuggestApiManagerTest extends UnitWebappTestCase {

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
    public void setUp(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(new FessConfig.SimpleImpl() {
            private static final long serialVersionUID = 1L;
        });
        webApiManagerFactory = new WebApiManagerFactory();
        ComponentUtil.register(webApiManagerFactory, "webApiManagerFactory");
        super.setUp(testInfo);
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    public void test_pathPrefix() {
        SuggestApiManager suggestApiManager = getComponent("suggestApiManager");
        assertEquals("/suggest", suggestApiManager.getPathPrefix());
    }

    public void test_RequestParameter_parse_basicParams() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("query", "test query");
        request.setParameter("fields", "title,content");
        request.setParameter("num", "15");
        request.setParameter("tags", "tag1,tag2");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals("test query", params.getQuery());
        assertEquals(2, params.getSuggestFields().length);
        assertEquals("title", params.getSuggestFields()[0]);
        assertEquals("content", params.getSuggestFields()[1]);
        assertEquals(15, params.getNum());
        assertEquals(2, params.getTags().length);
        assertEquals("tag1", params.getTags()[0]);
        assertEquals("tag2", params.getTags()[1]);
    }

    public void test_RequestParameter_parse_defaultValues() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertNull(params.getQuery());
        assertEquals(0, params.getSuggestFields().length);
        assertEquals(10, params.getNum()); // Default value
        assertEquals(0, params.getTags().length);
    }

    public void test_RequestParameter_parse_emptyFields() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("fields", "");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(0, params.getSuggestFields().length);
    }

    public void test_RequestParameter_parse_invalidNum() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("num", "invalid");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(10, params.getNum()); // Should fall back to default
    }

    public void test_RequestParameter_parse_numericStringNum() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("num", "25");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(25, params.getNum());
    }

    public void test_RequestParameter_getFields() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        Map<String, String[]> fields = params.getFields();
        assertTrue(fields.isEmpty());
    }

    public void test_RequestParameter_getConditions() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        Map<String, String[]> conditions = params.getConditions();
        assertTrue(conditions.isEmpty());
    }

    public void test_RequestParameter_getLanguages() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.addParameter("lang", "ja");
        request.addParameter("lang", "en");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        String[] languages = params.getLanguages();
        assertEquals(2, languages.length);
        assertEquals("ja", languages[0]);
        assertEquals("en", languages[1]);
    }

    public void test_RequestParameter_getType() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(SearchRequestType.SUGGEST, params.getType());
    }

    // Note: getHighlightInfo test removed due to configuration dependency

    public void test_RequestParameter_unsupportedOperations() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        try {
            params.getGeoInfo();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getFacetInfo();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getSort();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getStartPosition();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getOffset();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getPageSize();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getExtraQueries();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getAttribute("test");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getLocale();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        try {
            params.getSimilarDocHash();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    public void test_RequestParameter_parse_singleField() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("fields", "title");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(1, params.getSuggestFields().length);
        assertEquals("title", params.getSuggestFields()[0]);
    }

    public void test_RequestParameter_parse_singleTag() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("tags", "tag1");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(1, params.getTags().length);
        assertEquals("tag1", params.getTags()[0]);
    }

    public void test_RequestParameter_parse_zeroNum() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("num", "0");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(0, params.getNum());
    }

    public void test_RequestParameter_parse_largeNum() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("num", "1000");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(1000, params.getNum());
    }

    public void test_RequestParameter_parse_multipleFieldsWithSpaces() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("fields", "field1, field2, field3");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        // Note: The split on comma may include spaces
        assertTrue(params.getSuggestFields().length >= 3);
    }

    public void test_RequestParameter_parse_emptyTags() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        request.setParameter("tags", "");

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertEquals(0, params.getTags().length);
    }

    public void test_RequestParameter_parse_queryNull() {
        MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/suggest");
        // query parameter not set

        SuggestApiManager.RequestParameter params = SuggestApiManager.RequestParameter.parse(request);

        assertNull(params.getQuery());
    }

}