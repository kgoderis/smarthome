package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class EventDescription {
    @SerializedName("begin")
    List<EventAction> StartActions = new ArrayList<EventAction>();
    @SerializedName("end")
    List<EventAction> EndActions = new ArrayList<EventAction>();
}
