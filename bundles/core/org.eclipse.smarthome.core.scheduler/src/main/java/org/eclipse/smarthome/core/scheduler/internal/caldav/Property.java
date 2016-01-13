package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.xml.namespace.QName;

public class Property {

    private QName qName;
    @SuppressWarnings("rawtypes")
    private Class valueType;
    private boolean needsPersistence = false;

    public Property(QName qName) {
        this.qName = qName;
    }

    public Property(String localName) {
        this.qName = new QName(CalDAVConstants.DAV_NS, localName);
    }

    public Property(String namespace, String localName) {
        this.qName = new QName(namespace, localName);
    }

    public Property withValueType(Class<?> valueType) {
        this.valueType = valueType;
        return this;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public String getName() {
        return qName.getLocalPart();
    }

    public String getNamespace() {
        return qName.getNamespaceURI();
    }

    public Property withPersistence() {
        this.needsPersistence = true;
        return this;
    }

    public boolean getPersistence() {
        return needsPersistence;
    }

    public Object getValue(String input) {
        if (getValueType() != null && input != null) {
            if (getValueType() == String.class) {
                return input;
            } else if (getValueType() == Href.class) {
                return input;
            } else if (getValueType() == Date.class) {
                DateFormat df = new SimpleDateFormat(CalDAVConstants.PATTERN_RESPONSE_HEADER);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                try {
                    return df.parse(input);
                } catch (ParseException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return null;
                }
            } else {
                // TODO : add support for other value types
                return null;
            }
        }
        return null;
    }

    public String asString(Object propertyValue) {
        if (getValueType() != null) {
            if (getValueType() == String.class) {
                return propertyValue.toString();
            } else if (getValueType() == Href.class) {
                return propertyValue.toString();
            } else if (getValueType() == Date.class) {
                DateFormat df = new SimpleDateFormat(CalDAVConstants.PATTERN_RESPONSE_HEADER);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                return df.format((Date) propertyValue);
            } else {
                // TODO : add support for other value types
                return null;
            }
        }
        return null;
    }
}
