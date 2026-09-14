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

import java.util.List;
import java.util.function.Consumer;

import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.entity.SearchRenderData;
import org.codelibs.fess.entity.SearchRequestParams;
import org.codelibs.fess.helper.RelatedContentHelper;
import org.codelibs.fess.helper.RelatedQueryHelper;
import org.codelibs.fess.helper.SearchHelper;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.classic_api.UnitWebappTestCase;
import org.dbflute.optional.OptionalThing;
import org.dbflute.utflute.mocklet.MockletHttpServletRequestImpl;
import org.dbflute.utflute.mocklet.MockletServletContextImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Tests what the classic JSON search response reports about a result that is not complete,
 * against a search helper that fills the render data directly.
 */
public class JsonApiManagerSearchTest extends UnitWebappTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    protected void setUp(final TestInfo testInfo) throws Exception {
        // Registered before super.setUp: the container's own jsonApiManager resolves
        // webApiManagerFactory in its @PostConstruct. The shared tearDown clears these.
        ComponentUtil.setFessConfig(new StubFessConfig());
        ComponentUtil.register(new WebApiManagerFactory(), "webApiManagerFactory");
        ComponentUtil.register(new RelatedQueryHelper() {
            @Override
            public String[] getRelatedQueries(final String query) {
                return new String[0];
            }
        }, "relatedQueryHelper");
        ComponentUtil.register(new RelatedContentHelper() {
            @Override
            public String[] getRelatedContents(final String query) {
                return new String[0];
            }
        }, "relatedContentHelper");
        super.setUp(testInfo);
    }

    @Test
    public void test_processSearchRequest_reportsAShardFailure() {
        final CapturingJsonApiManager manager = search(data -> {
            data.setPartialResults(true);
            data.setShardFailed(true);
        });
        assertEquals("status", 0, manager.status);
        assertTrue("a failed shard must not be reported as a timeout: " + manager.body,
                manager.body.contains("\"partial\":true,\"timed_out\":false,\"shard_failed\":true"));
    }

    @Test
    public void test_processSearchRequest_reportsATimeout() {
        final CapturingJsonApiManager manager = search(data -> {
            data.setPartialResults(true);
            data.setTimedOut(true);
        });
        assertEquals("status", 0, manager.status);
        assertTrue("a timeout must be reported as a timeout: " + manager.body,
                manager.body.contains("\"partial\":true,\"timed_out\":true,\"shard_failed\":false"));
    }

    @Test
    public void test_processSearchRequest_reportsNeitherCauseForACompleteResult() {
        final CapturingJsonApiManager manager = search(data -> {});
        assertEquals("status", 0, manager.status);
        assertTrue("a complete result names no cause: " + manager.body,
                manager.body.contains("\"partial\":false,\"timed_out\":false,\"shard_failed\":false"));
    }

    private CapturingJsonApiManager search(final Consumer<SearchRenderData> result) {
        ComponentUtil.register(new SearchHelper() {
            @Override
            public void search(final SearchRequestParams params, final SearchRenderData data, final OptionalThing<FessUserBean> userBean) {
                data.setDocumentItems(List.of());
                result.accept(data);
            }
        }, "searchHelper");
        final MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(new MockletServletContextImpl("/fess"), "/json");
        request.setParameter("q", "fess");
        final CapturingJsonApiManager manager = new CapturingJsonApiManager();
        manager.processSearchRequest(request, null, null);
        return manager;
    }

    /**
     * Intercepts the seam every response in this path funnels through.
     */
    private static class CapturingJsonApiManager extends JsonApiManager {
        private int status = -1;

        private String body;

        @Override
        protected void writeJsonResponse(final int status, final String body, final String errMsg) {
            this.status = status;
            this.body = body != null ? body : errMsg;
        }
    }

    /**
     * Only the keys this path reads; {@code FessConfig.SimpleImpl} throws from any other getter
     * without a loaded property object.
     */
    private static class StubFessConfig extends FessConfig.SimpleImpl {
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
    }
}
