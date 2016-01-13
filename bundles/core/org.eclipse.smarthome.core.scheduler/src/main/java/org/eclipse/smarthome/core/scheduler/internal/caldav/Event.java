package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.util.Date;

public class Event {

    private String iCal;
    private String uniqueId;
    private String url;
    private Date modifiedDate;

    Event(String url, String uniqueId, Date modifiedDate, String iCal) {
        this.url = url;
        this.uniqueId = uniqueId;
        this.modifiedDate = modifiedDate;
        this.iCal = iCal;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getiCal() {
        return iCal;
    }

    public void setiCal(String iCal) {
        this.iCal = iCal;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
