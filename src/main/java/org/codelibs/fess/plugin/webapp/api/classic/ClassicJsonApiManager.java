
/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.CoreLibConstants;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.Constants;
import org.codelibs.fess.api.BaseApiManager;
import org.codelibs.fess.exception.InvalidAccessTokenException;
import org.codelibs.fess.util.ComponentUtil;
import org.lastaflute.web.util.LaRequestUtil;
import org.lastaflute.web.util.LaResponseUtil;

public abstract class ClassicJsonApiManager extends BaseApiManager {

    private static final Logger logger = LogManager.getLogger(ClassicJsonApiManager.class);

    protected String mimeType = "application/json";

    protected void writeJsonResponse(final int status, final String body, final Throwable t) {
        if (t == null) {
            writeJsonResponse(status, body, (String) null);
            return;
        }

        if (t instanceof final InvalidAccessTokenException e) {
            final HttpServletResponse response = LaResponseUtil.getResponse();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer error=\"" + e.getType() + "\"");
        }

        final Supplier<String> stacktraceString = () -> {
            final StringBuilder sb = new StringBuilder();
            if (StringUtil.isBlank(t.getMessage())) {
                sb.append(t.getClass().getName());
            } else {
                sb.append(t.getMessage());
            }
            try (final StringWriter sw = new StringWriter(); final PrintWriter pw = new PrintWriter(sw)) {
                t.printStackTrace(pw);
                pw.flush();
                sb.append(" [ ").append(sw.toString()).append(" ]");
            } catch (final IOException ignore) {}
            return sb.toString();
        };
        final String message;
        if (Constants.TRUE.equalsIgnoreCase(ComponentUtil.getFessConfig().getApiJsonResponseExceptionIncluded())) {
            message = stacktraceString.get();
        } else {
            final String errorCode = UUID.randomUUID().toString();
            message = "error_code:" + errorCode;
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] {}", errorCode, stacktraceString.get().replace("\n", "\\n"));
            } else {
                logger.warn("[{}] {}", errorCode, t.getMessage());
            }
        }
        writeJsonResponse(status, body, message);
    }

    protected void writeJsonResponse(final int status, final String body, final String errMsg) {
        String content = null;
        if (status == 0) {
            if (StringUtil.isNotBlank(body)) {
                content = body;
            }
        } else {
            content = "\"message\":" + escapeJson(errMsg);
        }
        writeJsonResponse(status, content);
    }

    protected void writeJsonResponse(final int status, final String body) {
        final String callback = LaRequestUtil.getOptionalRequest().map(req -> req.getParameter("callback")).orElse(null);
        final boolean isJsonp = ComponentUtil.getFessConfig().isApiJsonpEnabled() && StringUtil.isNotBlank(callback);

        final StringBuilder buf = new StringBuilder(1000);
        if (isJsonp) {
            buf.append(escapeCallbackName(callback));
            buf.append('(');
        }
        buf.append("{\"response\":");
        buf.append("{\"version\":\"");
        buf.append(ComponentUtil.getSystemHelper().getProductVersion());
        buf.append("\",");
        buf.append("\"status\":");
        buf.append(status);
        if (StringUtil.isNotBlank(body)) {
            buf.append(',');
            buf.append(body);
        }
        buf.append('}');
        buf.append('}');
        if (isJsonp) {
            buf.append(')');
        }
        write(buf.toString(), mimeType, Constants.UTF_8);

    }

    protected String escapeCallbackName(final String callbackName) {
        return "/**/" + callbackName.replaceAll("[^0-9a-zA-Z_\\$\\.]", StringUtil.EMPTY);
    }

    protected String escapeJson(final Object obj) {
        if (obj == null) {
            return "null";
        }

        final StringBuilder buf = new StringBuilder(255);
        if (obj instanceof String[]) {
            buf.append('[');
            boolean first = true;
            for (final Object child : (String[]) obj) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }
                buf.append(escapeJson(child));
            }
            buf.append(']');
        } else if (obj instanceof List<?>) {
            buf.append('[');
            boolean first = true;
            for (final Object child : (List<?>) obj) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }
                buf.append(escapeJson(child));
            }
            buf.append(']');
        } else if (obj instanceof Map<?, ?>) {
            buf.append('{');
            boolean first = true;
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (first) {
                    first = false;
                } else {
                    buf.append(',');
                }
                buf.append(escapeJson(entry.getKey())).append(':').append(escapeJson(entry.getValue()));
            }
            buf.append('}');
        } else if ((obj instanceof Integer) || (obj instanceof Long) || (obj instanceof Float) || (obj instanceof Double)) {
            buf.append((obj));
        } else if (obj instanceof Boolean) {
            buf.append(obj.toString());
        } else if (obj instanceof Date) {
            final SimpleDateFormat sdf = new SimpleDateFormat(CoreLibConstants.DATE_FORMAT_ISO_8601_EXTEND, Locale.ROOT);
            buf.append('\"').append(StringEscapeUtils.escapeJson(sdf.format(obj))).append('\"');
        } else {
            buf.append('\"').append(StringEscapeUtils.escapeJson(obj.toString())).append('\"');
        }
        return buf.toString();
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

}
