package com.grupomariposa.orders.infrastructure.http;

import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;

public final class EntityVersions {

    private static final Pattern ETAG = Pattern.compile("^(?:W/)?\"?(\\d{1,18})\"?$");

    private EntityVersions() {
    }

    public static long of(final Long bodyVersion, final HttpHeaders headers) {
        if (bodyVersion != null && bodyVersion > Versioned.UNKNOWN_VERSION) {
            return bodyVersion;
        }
        final String etag = headers.getETag();
        if (etag == null) {
            return Versioned.UNKNOWN_VERSION;
        }
        final Matcher matcher = ETAG.matcher(etag.trim());
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : Versioned.UNKNOWN_VERSION;
    }
}
