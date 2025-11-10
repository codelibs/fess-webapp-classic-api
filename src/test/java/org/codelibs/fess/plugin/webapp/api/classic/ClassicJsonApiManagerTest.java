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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.codelibs.core.CoreLibConstants;
import org.codelibs.fess.Constants;
import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;

public class ClassicJsonApiManagerTest extends LastaFluteTestCase {

    private WebApiManagerFactory webApiManagerFactory;

    private TestClassicJsonApiManager manager;

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
            public String getApiJsonResponseExceptionIncluded() {
                return Constants.FALSE;
            }
        });
        webApiManagerFactory = new WebApiManagerFactory();
        ComponentUtil.register(webApiManagerFactory, "webApiManagerFactory");
        manager = new TestClassicJsonApiManager();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    public void test_escapeCallbackName_normal() {
        String result = manager.testEscapeCallbackName("myCallback123");
        assertEquals("/**/myCallback123", result);
    }

    public void test_escapeCallbackName_withSpecialChars() {
        String result = manager.testEscapeCallbackName("my<script>alert('xss')</script>Callback");
        assertEquals("/**/myscriptalertxssscriptCallback", result);
    }

    public void test_escapeCallbackName_allowedSpecialChars() {
        String result = manager.testEscapeCallbackName("my$Callback_123.test");
        assertEquals("/**/my$Callback_123.test", result);
    }

    public void test_escapeCallbackName_empty() {
        String result = manager.testEscapeCallbackName("");
        assertEquals("/**/", result);
    }

    public void test_escapeJson_null() {
        String result = manager.testEscapeJson(null);
        assertEquals("null", result);
    }

    public void test_escapeJson_string() {
        String result = manager.testEscapeJson("test string");
        assertEquals("\"test string\"", result);
    }

    public void test_escapeJson_stringWithQuotes() {
        String result = manager.testEscapeJson("test \"quoted\" string");
        assertEquals("\"test \\\"quoted\\\" string\"", result);
    }

    public void test_escapeJson_stringWithNewlines() {
        String result = manager.testEscapeJson("test\nstring\r\nwith\nnewlines");
        assertEquals("\"test\\nstring\\r\\nwith\\nnewlines\"", result);
    }

    public void test_escapeJson_integer() {
        String result = manager.testEscapeJson(42);
        assertEquals("42", result);
    }

    public void test_escapeJson_long() {
        String result = manager.testEscapeJson(123456789L);
        assertEquals("123456789", result);
    }

    public void test_escapeJson_float() {
        String result = manager.testEscapeJson(3.14f);
        assertEquals("3.14", result);
    }

    public void test_escapeJson_double() {
        String result = manager.testEscapeJson(2.71828);
        assertEquals("2.71828", result);
    }

    public void test_escapeJson_boolean_true() {
        String result = manager.testEscapeJson(true);
        assertEquals("true", result);
    }

    public void test_escapeJson_boolean_false() {
        String result = manager.testEscapeJson(false);
        assertEquals("false", result);
    }

    public void test_escapeJson_date() {
        Date date = new Date(1642780800000L); // 2022-01-21T12:00:00.000Z
        String result = manager.testEscapeJson(date);
        SimpleDateFormat sdf = new SimpleDateFormat(CoreLibConstants.DATE_FORMAT_ISO_8601_EXTEND, Locale.ROOT);
        String expectedDate = sdf.format(date);
        assertEquals("\"" + expectedDate + "\"", result);
    }

    public void test_escapeJson_stringArray() {
        String[] array = { "test1", "test2", "test3" };
        String result = manager.testEscapeJson(array);
        assertEquals("[\"test1\",\"test2\",\"test3\"]", result);
    }

    public void test_escapeJson_emptyStringArray() {
        String[] array = {};
        String result = manager.testEscapeJson(array);
        assertEquals("[]", result);
    }

    public void test_escapeJson_list() {
        List<String> list = Arrays.asList("item1", "item2", "item3");
        String result = manager.testEscapeJson(list);
        assertEquals("[\"item1\",\"item2\",\"item3\"]", result);
    }

    public void test_escapeJson_emptyList() {
        List<String> list = Arrays.asList();
        String result = manager.testEscapeJson(list);
        assertEquals("[]", result);
    }

    public void test_escapeJson_map() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        String result = manager.testEscapeJson(map);
        assertTrue(result.contains("\"key1\":\"value1\""));
        assertTrue(result.contains("\"key2\":\"value2\""));
        assertTrue(result.startsWith("{"));
        assertTrue(result.endsWith("}"));
    }

    public void test_escapeJson_emptyMap() {
        Map<String, String> map = new HashMap<>();
        String result = manager.testEscapeJson(map);
        assertEquals("{}", result);
    }

    public void test_escapeJson_nestedStructure() {
        Map<String, Object> map = new HashMap<>();
        map.put("string", "value");
        map.put("number", 42);
        map.put("array", Arrays.asList("item1", "item2"));
        String result = manager.testEscapeJson(map);
        assertTrue(result.contains("\"string\":\"value\""));
        assertTrue(result.contains("\"number\":42"));
        assertTrue(result.contains("\"array\":[\"item1\",\"item2\"]"));
    }

    public void test_setMimeType() {
        assertEquals("application/json", manager.getMimeType());
        manager.setMimeType("application/xml");
        assertEquals("application/xml", manager.getMimeType());
    }

    public void test_escapeJson_listWithMixedTypes() {
        List<Object> list = Arrays.asList("text", 42, true, null);
        String result = manager.testEscapeJson(list);
        assertEquals("[\"text\",42,true,null]", result);
    }

    public void test_escapeJson_mapWithNullValue() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", null);
        String result = manager.testEscapeJson(map);
        assertTrue(result.contains("\"key1\":\"value1\""));
        assertTrue(result.contains("\"key2\":null"));
    }

    public void test_escapeJson_stringWithBackslash() {
        String result = manager.testEscapeJson("test\\path\\file");
        assertEquals("\"test\\\\path\\\\file\"", result);
    }

    public void test_escapeJson_stringWithTab() {
        String result = manager.testEscapeJson("test\ttab");
        assertEquals("\"test\\ttab\"", result);
    }

    public void test_escapeJson_mixedIntegerTypes() {
        List<Object> list = Arrays.asList(
            Integer.valueOf(42),
            Long.valueOf(123456789L),
            Float.valueOf(3.14f),
            Double.valueOf(2.71828)
        );
        String result = manager.testEscapeJson(list);
        assertTrue(result.contains("42"));
        assertTrue(result.contains("123456789"));
        assertTrue(result.contains("3.14"));
        assertTrue(result.contains("2.71828"));
    }

    public void test_escapeJson_complexNestedStructure() {
        Map<String, Object> inner = new HashMap<>();
        inner.put("nested_string", "value");
        inner.put("nested_number", 100);

        Map<String, Object> outer = new HashMap<>();
        outer.put("inner_map", inner);
        outer.put("simple_array", Arrays.asList(1, 2, 3));

        String result = manager.testEscapeJson(outer);
        assertTrue(result.contains("\"inner_map\":{"));
        assertTrue(result.contains("\"nested_string\":\"value\""));
        assertTrue(result.contains("\"nested_number\":100"));
        assertTrue(result.contains("\"simple_array\":[1,2,3]"));
    }

    public void test_escapeJson_stringWithUnicodeCharacters() {
        String result = manager.testEscapeJson("テスト文字列");
        assertEquals("\"テスト文字列\"", result);
    }

    public void test_escapeCallbackName_withParentheses() {
        String result = manager.testEscapeCallbackName("callback()");
        assertEquals("/**/callback", result);
    }

    public void test_escapeCallbackName_withBrackets() {
        String result = manager.testEscapeCallbackName("callback[0]");
        assertEquals("/**/callback0", result);
    }

    // Test implementation of abstract ClassicJsonApiManager for testing purposes
    private static class TestClassicJsonApiManager extends ClassicJsonApiManager {
        private String mimeType = "application/json";

        @Override
        public boolean matches(HttpServletRequest request) {
            return true;
        }

        @Override
        public void process(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            // Test implementation
        }

        @Override
        protected void write(String text, String contentType, String encoding) {
            // Test implementation
        }

        @Override
        protected void writeHeaders(HttpServletResponse response) {
            // Test implementation - no actual headers needed
        }

        // Expose protected methods for testing
        public String testEscapeCallbackName(String callbackName) {
            return escapeCallbackName(callbackName);
        }

        public String testEscapeJson(Object obj) {
            return escapeJson(obj);
        }

        public String getMimeType() {
            return mimeType;
        }

        @Override
        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }
    }
}