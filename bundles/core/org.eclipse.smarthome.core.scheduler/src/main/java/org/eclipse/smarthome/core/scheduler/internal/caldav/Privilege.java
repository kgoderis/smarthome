package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum Privilege {
    BIND(Collections.<Privilege> emptyList()),
    READ_ACL(Collections.<Privilege> emptyList()),
    READ_CONTENT(Collections.<Privilege> emptyList()),
    READ_CURRENT_USER_PRIVILEDGE(Collections.<Privilege> emptyList()),
    READ_FREE_BUSY(Collections.<Privilege> emptyList()),
    READ_PROPERTIES(Collections.<Privilege> emptyList()),
    UNBIND(Collections.<Privilege> emptyList()),
    UNLOCK(Collections.<Privilege> emptyList()),
    WRITE_ACL(Collections.<Privilege> emptyList()),
    WRITE_CONTENT(Collections.<Privilege> emptyList()),
    WRITE_PROPERTIES(Collections.<Privilege> emptyList()),
    WRITE(Arrays.asList(WRITE_CONTENT, WRITE_PROPERTIES, WRITE_ACL, UNLOCK)),
    READ(Arrays.asList(READ_CONTENT, READ_PROPERTIES, READ_ACL, READ_CURRENT_USER_PRIVILEDGE, READ_FREE_BUSY)),
    ALL(Arrays.asList(READ, WRITE, BIND, UNBIND));

    public List<Privilege> contains;

    private Privilege(List<Privilege> contains) {
        this.contains = contains;
    }
}
