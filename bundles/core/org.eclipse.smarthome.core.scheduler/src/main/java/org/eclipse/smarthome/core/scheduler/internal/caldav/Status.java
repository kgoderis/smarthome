package org.eclipse.smarthome.core.scheduler.internal.caldav;

public enum Status {

    SC_ACCEPTED(202),
    SC_BAD_GATEWAY(502),
    SC_BAD_REQUEST(400),
    SC_CONFLICT(409),
    SC_CONTINUE(100),
    SC_CREATED(201),
    SC_EXPECTATION_FAILED(417),
    SC_FORBIDDEN(403),
    SC_INSUFFICIENT_STORAGE(507),
    SC_INTERNAL_SERVER_ERROR(500),
    SC_LOCKED(423),
    SC_METHOD_FAILURE(420),
    SC_METHOD_NOT_ALLOWED(405),
    SC_MOVED_PERMANENTLY(301),
    SC_MOVED_TEMPORARILY(302),
    SC_MULTI_STATUS(207, "Multi-status"),
    SC_NO_CONTENT(204),
    SC_NOT_FOUND(404, "Not Found"),
    SC_NOT_IMPLEMENTED(501),
    SC_NOT_MODIFIED(304),
    SC_OK(200, "OK"),
    SC_PARTIAL_CONTENT(206),
    SC_PRECONDITION_FAILED(412),
    SC_REQUEST_TOO_LONG(413),
    SC_SERVICE_UNAVAILABLE(503),
    SC_TEMPORARY_REDIRECT(307),
    SC_UNAUTHORIZED(401),
    SC_UNPROCESSABLE_ENTITY(418),
    SC_UNSUPPORTED_MEDIA_TYPE(415);

    public int code;
    public String text;

    Status(int code, String text) {
        this.code = code;
        this.text = text;
    }

    Status(int code) {
        this.code = code;
        this.text = null;
    }

    @Override
    public String toString() {
        if (text != null) {
            return "HTTP/1.1 " + code + " " + text;
        } else {
            return "HTTP/1.1 " + code;
        }
    }

    public static Status fromCode(int i) {
        for (Status s : values()) {
            if (s.code == i) {
                return s;
            }
        }
        return null;
    }

}
