package org.eclipse.smarthome.core.scheduler.internal.caldav;

public enum Header {

    ACCEPT("Accept"),
    ACCEPT_ENCODING("Accept-Encoding"),
    ACCEPT_LANGUAGE("Accept-Language"),
    AUTHORIZATION("Authorization"),
    BRIEF("Brief"),
    CACHE_CONTROL("Cache-Control"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_RANGE("Content-Range"),
    CONTENT_TYPE("Content-Type"),
    DEPTH("Depth"),
    DESTINATION("Destination"),
    EXPECT("Expect"),
    HOST("Host"),
    IF("If"),
    IF_MATCH("If-Match"),
    IF_MODIFIED("If-Modified-Since"),
    IF_NONE_MATCH("If-None-Match"),
    IF_NOT_MODIFIED("If-Unmodified-Since"),
    IF_RANGE("If-Range"),
    LOCK_TOKEN("Lock-Token"),
    ORIGIN("Origin"),
    OVERWRITE("Overwrite"),
    RANGE("Range"),
    REFERER("Referer"),
    SERVER("Server"),
    TIMEOUT("Timeout"),
    USER_AGENT("User-Agent"),
    WWW_AUTHENTICATE("WWW-Authenticate"),
    X_EXPECTED_ENTITY_LENGTH("X-Expected-Entity-Length"),
    CONTENT_ENCODING("Content-Encoding"),
    LOCATION("Location"),
    ALLOW("Allow"),
    DAV("DAV"),
    DATE("Date"),
    LAST_MODIFIED("Last-Modified"),
    EXPIRES("Expires"),
    ETAG("ETag"),
    VARY("Vary"),
    ACCESS_CONTROL_ALLOW_ORIGIN("Access-Control-Allow-Origin"),
    ACCEPT_RANGES("Accept-Ranges");

    public String code;

    Header(String code) {
        this.code = code;
    }
}
