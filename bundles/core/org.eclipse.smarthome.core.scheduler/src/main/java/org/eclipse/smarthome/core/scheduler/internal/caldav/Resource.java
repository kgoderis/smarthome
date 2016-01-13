package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Resource {

    private final Logger logger = LoggerFactory.getLogger(Resource.class);

    private List<String> methods;
    private String url;
    private UUID uuid;
    private String uniqueId;
    private Date modifiedDate;
    private ResourceFactory rf;
    private HashMap<Property, Object> properties;
    private List<String> levels;
    private HashMap<String, Privilege> privileges;
    private HashSet<ResourceType> types;
    private HashSet<ComponentType> components;
    private HashSet<String> collations;

    public Resource(ResourceFactory rf, String url) {
        this.rf = rf;
        this.url = url;
        this.methods = new ArrayList<String>();
        uuid = UUID.randomUUID();
        uniqueId = null;
        properties = new HashMap<Property, Object>();
        privileges = new HashMap<String, Privilege>();
        levels = new ArrayList<String>();
        types = new HashSet<ResourceType>();
        components = new HashSet<ComponentType>();
        collations = new HashSet<String>();

        this.withProperty(rf.getProperty("resource-url"), url);
        this.withProperty(rf.getProperty("resource-uniqueid"), uuid.toString());

        this.setModifiedDate(new Date());
        // notifyParent();
    }

    // public void setContent(String content) {
    // rf.storeContent(this, content);
    // notifyParent();
    // }

    public Resource withCollation(String collation) {
        this.collations.add(collation);
        return this;
    }

    public Resource withComponent(ComponentType type) {
        this.components.add(type);
        return this;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;

        Property uProperty = rf.getProperty("resource-uniqueid");
        if (uProperty != null) {
            this.withProperty(uProperty, uniqueId);
        }

        Property typeProperty = rf.getProperty("getetag");
        if (typeProperty != null) {
            this.withProperty(typeProperty, getETag());
        }
        if (types.contains(ResourceType.CALENDAR)) {
            Property ctagProperty = rf.getProperty("getctag");
            if (ctagProperty != null) {
                properties.put(ctagProperty, getETag());
            }
        }
        notifyParent();
    }

    public Resource withMethods(List<String> methods) {
        this.methods.addAll(methods);
        return this;
    }

    public Resource withLevels(List<String> levels) {
        this.levels.addAll(levels);
        return this;
    }

    public Resource withLevel(String level) {
        this.levels.add(level);
        return this;
    }

    public Resource withPrivilege(String principal, Privilege priv) {
        this.privileges.put(principal, priv);
        return this;
    }

    public Resource withPrivilege(Privilege priv) {
        Property principalProperty = rf.getProperty("principal-URL");
        String principal = (String) properties.get(principalProperty);
        if (principal != null) {
            this.privileges.put(principal, priv);
        }
        return this;
    }

    public List<Privilege> getPrivileges(String principal) {
        List<Privilege> privs = new ArrayList<Privilege>();
        for (String owner : privileges.keySet()) {
            if (owner.equals(principal)) {
                privs.add(privileges.get(owner));
            }
        }
        return privs;
    }

    public List<Privilege> getPrivileges() {
        Property principalProperty = rf.getProperty("principal-URL");
        String principal = (String) properties.get(principalProperty);
        return getPrivileges(principal);
    }

    public Resource byFactory(ResourceFactory rf) {
        this.rf = rf;
        return this;
    }

    public Resource withType(ResourceType type) {
        this.types.add(type);
        if (type == ResourceType.CALENDAR) {
            // add a ctag property to this resource, which in case of a CALENDAR, is equal to the ETag
            Property ctagProperty = rf.getProperty("getctag");
            if (ctagProperty != null) {
                properties.put(ctagProperty, getETag());
            }
        }
        if (types.size() > 0) {
            Property typeProperty = rf.getProperty("resourcetype");
            if (typeProperty != null) {
                this.withProperty(typeProperty, "");
            }
        }
        return this;
    }

    public Set<ResourceType> getTypes() {
        return types;
    }

    public Set<ComponentType> getCompents() {
        return components;
    }

    public Set<String> getCollations() {
        return collations;
    }

    public Resource withProperty(Property property, Object value) {
        return withProperty(property, value, true);
    }

    public Resource withProperty(Property property, Object value, boolean persist) {
        if (value != null) {
            properties.put(property, value);

            if (persist && property.getPersistence()) {
                rf.persistProperty(this, property);
            }

            notifyParent();
        }
        return this;
    }

    public Set<Property> getProperties() {
        return this.properties.keySet();
    }

    public void removeProperty(Property property) {
        properties.remove(property);
    }

    public List<String> getLevels() {
        return levels;
    }

    public boolean hasMethod(String method) {
        return methods.contains(method);
    }

    public void setFactory(ResourceFactory rf) {
        this.rf = rf;
    }

    public Object getPropertyValue(Property property) {
        Object value = properties.get(property);
        if (value == null && rf != null && properties.containsKey(property)) {
            value = rf.retrieveProperty(this, property);
        }
        return value;
    }

    public String getURL() {
        return url;
    }

    public List<String> getMethods() {
        return methods;
    }

    public String getUniqueId() {
        if (uniqueId == null) {
            return uuid.toString();
        }

        return uniqueId;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date date) {
        logger.trace("Setting modified date for {} to {}", this.getURL(), date);
        modifiedDate = date;
        this.withProperty(rf.getProperty("resource-modified-date"), modifiedDate);

        Property typeProperty = rf.getProperty("getetag");
        if (typeProperty != null) {
            logger.trace("Setting etag for {} to {}", this.getURL(), getETag());
            this.withProperty(typeProperty, getETag());
        }
        if (types.contains(ResourceType.CALENDAR)) {
            Property ctagProperty = rf.getProperty("getctag");
            if (ctagProperty != null) {
                logger.trace("Setting ctag for {} to {}", this.getURL(), getETag());
                this.withProperty(ctagProperty, getETag());
            }
        }
        // notifyParent("setdate");
    }

    public String getETag() {
        String s = getUniqueId();
        if (s == null) {
            return null;
        } else {
            Date dt = getModifiedDate();
            if (dt != null) {
                s = s + "-" + dt.hashCode();
            } else {
            }
            // return "\"" + s + "\"";
            return s;
        }
    }

    public boolean isCollection() {
        return types.contains(ResourceType.COLLECTION);
    }

    public String getName() {
        String[] segments = StringUtils.split(url, "/");
        return segments[segments.length - 1];
    }

    public List<Resource> getChildren() {
        return rf.getChildResources(this);
    }

    public void notifyParent() {
        Resource parent = rf.getParentResource(this);
        if (parent != null) {
            parent.setModifiedDate(new Date());
        }
    }
}
