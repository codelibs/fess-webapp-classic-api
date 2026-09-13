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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.opensearch.action.update.UpdateRequestBuilder;
import org.codelibs.fess.api.WebApiManagerFactory;
import org.codelibs.fess.app.service.FavoriteLogService;
import org.codelibs.fess.app.service.FavoriteLogService.FavoriteResult;
import org.codelibs.fess.helper.SearchHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.helper.UserInfoHelper;
import org.codelibs.fess.mylasta.action.FessUserBean;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.opensearch.log.exentity.FavoriteLog;
import org.codelibs.fess.opensearch.log.exentity.UserInfo;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.webapp.classic_api.UnitWebappTestCase;
import org.dbflute.optional.OptionalEntity;
import org.dbflute.optional.OptionalThing;
import org.dbflute.utflute.mocklet.MockletHttpServletRequestImpl;
import org.dbflute.utflute.mocklet.MockletServletContextImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Covers {@link JsonApiManager#processFavoriteRequest}'s branch on the three-valued
 * {@link FavoriteResult} that {@link FavoriteLogService#addUrl} returns.
 * <p>
 * {@code addUrl} used to return a {@code boolean}. The enum splits the old {@code false} into
 * two outcomes that must be handled differently, and only one of them is still an error. The
 * contract pinned down here, which matches Fess core's own v2 {@code FavoritePostHandler}:
 * </p>
 * <ul>
 * <li>{@code ADDED} - a fresh favorite. Bump {@code favorite_count} and report status 0.</li>
 * <li>{@code ALREADY_ADDED} - the user had already marked this URL and nothing was written.
 * Report status 0 exactly as for a fresh add, because a repeated request is an idempotent
 * retry rather than an error, but <em>skip</em> the {@code favorite_count} bump: the count is a
 * documented sort key, and bumping it on a repeat would let one user push a document up the
 * ranking by clicking twice.</li>
 * <li>{@code NO_SUCH_USER} - the user code resolved to no user and nothing was written. This is
 * the one thing the old {@code false} meant, so it keeps raising the same classic error, status
 * 4.</li>
 * </ul>
 * <p>
 * The count bump is {@code searchHelper.update(id, builderLambda)}, so "was the count bumped?"
 * is asserted as "was {@code update} called, and for which document?".
 * {@link RecordingSearchHelper} records the call without running the lambda, which would
 * otherwise need a live search engine client.
 * </p>
 */
public class JsonApiManagerFavoriteTest extends UnitWebappTestCase {

    private static final String DOC_ID = "doc-1";

    private static final String INDEX_ID = "index-id-1";

    private static final String FAVORITE_URL = "http://example.com/a.html";

    private static final String USER_CODE = "user-code-1";

    private static final String QUERY_ID = "query-1";

    private RecordingSearchHelper searchHelper;

    private StubFavoriteLogService favoriteLogService;

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    protected void setUp(final TestInfo testInfo) throws Exception {
        // Everything is registered before super.setUp: the container's own jsonApiManager has a
        // @PostConstruct that resolves webApiManagerFactory, and container init fails without
        // it. setFessConfig(null) in the shared tearDown then clears ComponentUtil's component
        // map, so none of these stubs leak into another test class.
        ComponentUtil.setFessConfig(new StubFessConfig());
        searchHelper = new RecordingSearchHelper();
        favoriteLogService = new StubFavoriteLogService();
        ComponentUtil.register(new WebApiManagerFactory(), "webApiManagerFactory");
        ComponentUtil.register(searchHelper, "searchHelper");
        ComponentUtil.register(new StubUserInfoHelper(), "userInfoHelper");
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        ComponentUtil.register(favoriteLogService, FavoriteLogService.class.getCanonicalName());
        super.setUp(testInfo);
    }

    // ------------------------------------------------------------------
    // ADDED
    // ------------------------------------------------------------------

    @Test
    public void test_added_bumpsFavoriteCountAndReportsOk() {
        favoriteLogService.result = FavoriteResult.ADDED;
        final CapturingJsonApiManager manager = processFavoriteRequest();

        assertEquals("addUrl should be called exactly once", 1, favoriteLogService.calls);
        assertEquals("addUrl should be given the resolved user code", USER_CODE, favoriteLogService.lastUserCode);
        assertEquals("ADDED must bump favorite_count", 1, searchHelper.updateCalls);
        assertEquals("the bump must target the document's index id", INDEX_ID, searchHelper.lastUpdatedId);
        assertEquals("ADDED must report success", 0, manager.status);
        assertEquals("\"result\":\"ok\"", manager.body);
        assertNull("a success must carry no error message", manager.errMsg);
    }

    /**
     * The lambda {@code addUrl} is handed must populate the log entry. Without this, a manager
     * that passed an empty lambda would still satisfy every other assertion here.
     */
    @Test
    public void test_added_populatesTheFavoriteLogEntry() {
        favoriteLogService.result = FavoriteResult.ADDED;
        favoriteLogService.applyLambda = true;
        processFavoriteRequest();

        assertNotNull("the favorite log lambda should have been applied", favoriteLogService.captured);
        assertEquals(FAVORITE_URL, favoriteLogService.captured.getUrl());
        assertEquals(DOC_ID, favoriteLogService.captured.getDocId());
        assertEquals(QUERY_ID, favoriteLogService.captured.getQueryId());
        assertNotNull("createdAt should come from the system helper", favoriteLogService.captured.getCreatedAt());
    }

    // ------------------------------------------------------------------
    // ALREADY_ADDED
    // ------------------------------------------------------------------

    @Test
    public void test_alreadyAdded_isSuccessButSkipsTheFavoriteCountBump() {
        favoriteLogService.result = FavoriteResult.ALREADY_ADDED;
        final CapturingJsonApiManager manager = processFavoriteRequest();

        assertEquals("addUrl should still be attempted", 1, favoriteLogService.calls);
        assertEquals("ALREADY_ADDED must not bump favorite_count", 0, searchHelper.updateCalls);
        assertEquals("ALREADY_ADDED is an idempotent success, not an error", 0, manager.status);
        assertEquals("\"result\":\"ok\"", manager.body);
        assertNull("an idempotent repeat must carry no error message", manager.errMsg);
    }

    /**
     * The whole point of the enum: a repeated request must leave {@code favorite_count} exactly
     * where a single request left it.
     */
    @Test
    public void test_repeatedRequest_bumpsTheCountOnlyOnce() {
        favoriteLogService.result = FavoriteResult.ADDED;
        processFavoriteRequest();
        assertEquals(1, searchHelper.updateCalls);

        favoriteLogService.result = FavoriteResult.ALREADY_ADDED;
        processFavoriteRequest();
        assertEquals("the second, duplicate request must not bump the count again", 1, searchHelper.updateCalls);
    }

    // ------------------------------------------------------------------
    // NO_SUCH_USER
    // ------------------------------------------------------------------

    @Test
    public void test_noSuchUser_reportsTheAddFailureAndSkipsTheBump() {
        favoriteLogService.result = FavoriteResult.NO_SUCH_USER;
        final CapturingJsonApiManager manager = processFavoriteRequest();

        assertEquals("addUrl should still be attempted", 1, favoriteLogService.calls);
        assertEquals("NO_SUCH_USER wrote nothing, so nothing may be bumped", 0, searchHelper.updateCalls);
        assertEquals("NO_SUCH_USER keeps the status the old false reported", 4, manager.status);
        assertNull("the success body must not be written", manager.body);
        assertNotNull("an error message must be written", manager.errMsg);
        assertTrue("error message should name the url that could not be added: " + manager.errMsg,
                manager.errMsg.contains("Failed to add url: " + FAVORITE_URL));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private CapturingJsonApiManager processFavoriteRequest() {
        final CapturingJsonApiManager manager = new CapturingJsonApiManager();
        final MockletServletContextImpl servletContext = new MockletServletContextImpl("/fess");
        final MockletHttpServletRequestImpl request = new MockletHttpServletRequestImpl(servletContext, "/json");
        request.setParameter("docId", DOC_ID);
        request.setParameter("queryId", QUERY_ID);
        manager.processFavoriteRequest(request, null, null);
        return manager;
    }

    /**
     * Intercepts the single seam every response in this path funnels through. The
     * {@code (int, String, Throwable)} overload is left alone deliberately, so its real error
     * shaping still runs and the captured {@code errMsg} is the one a caller would see.
     */
    private static class CapturingJsonApiManager extends JsonApiManager {
        private int status = -1;

        private String body;

        private String errMsg;

        @Override
        protected void writeJsonResponse(final int status, final String body, final String errMsg) {
            this.status = status;
            this.body = body;
            this.errMsg = errMsg;
        }
    }

    /**
     * Only the keys this path reads. {@code FessConfig.SimpleImpl} throws
     * {@link NullPointerException} from any getter backed by {@code fess_config.properties}
     * unless the DBFlute property object is loaded, which needs a container this test does not
     * start, so each one is answered here instead.
     */
    private static class StubFessConfig extends FessConfig.SimpleImpl {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isUserFavorite() {
            return true;
        }

        @Override
        public String getIndexFieldUrl() {
            return "url";
        }

        @Override
        public String getIndexFieldLang() {
            return "lang";
        }

        @Override
        public String getIndexFieldId() {
            return "_id";
        }

        @Override
        public String getIndexFieldFavoriteCount() {
            return "favorite_count";
        }

        @Override
        public String getApiJsonResponseExceptionIncluded() {
            // Keeps the thrown message in the response instead of an opaque error code, so the
            // NO_SUCH_USER assertion can check what the caller is actually told.
            return "true";
        }
    }

    private static class StubUserInfoHelper extends UserInfoHelper {
        @Override
        public String[] getResultDocIds(final String queryId) {
            return new String[] { DOC_ID };
        }

        @Override
        public String getUserCode() {
            return USER_CODE;
        }
    }

    private static class RecordingSearchHelper extends SearchHelper {
        private int updateCalls;

        private String lastUpdatedId;

        @Override
        public OptionalEntity<Map<String, Object>> getDocumentByDocId(final String docId, final String[] fields,
                final OptionalThing<FessUserBean> userBean) {
            final FessConfig fessConfig = ComponentUtil.getFessConfig();
            final Map<String, Object> doc = new HashMap<>();
            doc.put(fessConfig.getIndexFieldUrl(), FAVORITE_URL);
            doc.put(fessConfig.getIndexFieldId(), INDEX_ID);
            return OptionalEntity.of(doc);
        }

        @Override
        public boolean update(final String id, final Consumer<UpdateRequestBuilder> builderLambda) {
            updateCalls++;
            lastUpdatedId = id;
            // The lambda is deliberately not run: it needs a live search engine client, and the
            // question here is only whether the bump was requested at all.
            return true;
        }
    }

    private static class StubFavoriteLogService extends FavoriteLogService {
        private FavoriteResult result = FavoriteResult.ADDED;

        private int calls;

        private String lastUserCode;

        private boolean applyLambda;

        private FavoriteLog captured;

        @Override
        public FavoriteResult addUrl(final String userCode, final BiConsumer<UserInfo, FavoriteLog> favoriteLogLambda) {
            calls++;
            lastUserCode = userCode;
            if (applyLambda) {
                final UserInfo userInfo = new UserInfo();
                userInfo.setId("user-info-1");
                captured = new FavoriteLog();
                favoriteLogLambda.accept(userInfo, captured);
            }
            return result;
        }
    }
}
