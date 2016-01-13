package org.eclipse.smarthome.core.scheduler.internal.caldav;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.storage.Storage;
import org.eclipse.smarthome.core.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceFactory {

    private final Logger logger = LoggerFactory.getLogger(ResourceFactory.class);

    private HashMap<String, Resource> resources;
    private HashMap<Resource, Resource> redirectMap;
    private ArrayList<Property> properties;

    private Storage<String> propertyStorage;

    public ResourceFactory() {
        resources = new HashMap<String, Resource>();
        redirectMap = new HashMap<Resource, Resource>();
        properties = new ArrayList<Property>();
    }

    public void setStorageService(StorageService storageService) {
        if (storageService == null) {
            propertyStorage = null;
        } else {
            this.propertyStorage = storageService.getStorage(String.class.getName(), this.getClass().getClassLoader());
        }
    }

    public void restoreResources() {
        if (propertyStorage != null) {
            Set<String> resources = getStoredResources();
            logger.debug("There are {} stored resources", resources.size());
            for (String resource : resources) {
                restoreResource(resource);
            }
        }
    }

    public Resource restoreResource(String url) {
        Resource r = resources.get(url);
        if (r == null && propertyStorage != null) {
            // if not existing yet, check if it already exists in our permament storage
            // Event storedEvent = eventStorage.get(url);

            // if (storedEvent != null) {
            if (retrieveProperty(url, "resource-url") != null) {

                String uniqueId = retrieveProperty(url, "resource-unique-id");

                String modifiedDateString = retrieveProperty(url, "resource-modified-date");
                Property modifiedDateProperty = getProperty("resource-modified-date");
                Date modifiedDate = (Date) modifiedDateProperty.getValue(modifiedDateString);

                logger.debug("Restoring a resource with URL '{}'", url);
                r = new Resource(this, url);
                r.withMethods(Arrays.asList(CalDAVConstants.METHOD_PROPFIND, CalDAVConstants.METHOD_GET,
                        CalDAVConstants.METHOD_DELETE, CalDAVConstants.METHOD_MOVE, CalDAVConstants.METHOD_PUT,
                        CalDAVConstants.METHOD_REPORT));
                r.withLevel("1");
                r.withPrivilege(Privilege.ALL);
                r.setUniqueId(uniqueId);
                r.setModifiedDate(modifiedDate);
                // r.withProperty(this.getProperty("calendar-data"), null);
                r.withProperty(this.getProperty("getcontenttype"), "text/calendar");
                resources.put(r.getURL(), r);

                restoreProperties(r);

            }
        }
        return r;
    }

    public void restoreProperties(Resource resource) {
        if (resource != null) {
            logger.debug("Restoring the persisted properties for resource '{}'", resource.getURL());
            // for (Property aProperty : resource.getProperties()) {
            // restoreProperty(resource, aProperty);
            // }

            Set<String> properties = getStoredProperties(resource.getURL());
            for (String aProperty : properties) {
                Property property = getProperty(aProperty);
                if (property != null) {
                    logger.trace("Restoring the persisted property '{}' for resource '{}'", property.getName(),
                            resource.getURL());
                    resource.withProperty(property, retrieveProperty(resource, property));
                } else {
                    logger.warn("Property '{}' is not supported by the resource factory '{}'", aProperty,
                            this.toString());
                }
            }
        }
    }

    public void restoreProperties() {
        for (String url : resources.keySet()) {
            restoreProperties(resources.get(url));
        }
    }

    public void persistProperty(Resource resource, Property property) {
        // PersistableProperty toPersist = new PersistableProperty(resource, property);
        if (properties.contains(property)) {
            if (resource.getPropertyValue(property) != null && propertyStorage != null) {
                logger.trace("Persisting the property '{}' for resource '{}'", property.getName(), resource.getURL());
                propertyStorage.put(encodeKey(resource, property),
                        property.asString(resource.getPropertyValue(property)));
            } else {
                if (propertyStorage == null) {
                    logger.error("No storage is defined for the resource factory '{}'", this.toString());
                } else {
                    logger.warn("The value for property '{}' could not be transformed into a storable entity",
                            property.getName());
                }
            }
        } else {
            logger.warn("The property '{}' is unknown to this resource factory '{}'", property.getName(),
                    this.toString());
        }
    }

    public void restoreProperty(Resource resource, Property property) {
        if (resource != null && property != null) {
            logger.debug("Restoring the persisted property '{}' for resource '{}'", property.getName(),
                    resource.getURL());
            resource.withProperty(property, this.retrieveProperty(resource, property));
        }
    }

    public void pruneProperties() {

        logger.debug("Pruning obsolete properties from the storage");

        Collection<String> keys = propertyStorage.getKeys();

        for (String aKey : keys) {
            String resourceURL = decodeResource(aKey);
            String propertyName = decodeProperty(aKey);

            if (!resources.containsKey(resourceURL)) {
                logger.debug("Pruning the property '{}' for resource '{}'", propertyName, resourceURL);
                propertyStorage.remove(aKey);
            }

        }
    }

    public void deleteProperties(Resource resource) {
        Collection<String> keys = propertyStorage.getKeys();

        for (String aKey : keys) {
            String resourceURL = decodeResource(aKey);
            String propertyName = decodeProperty(aKey);

            if (resource.getURL().equals(resourceURL)) {
                logger.debug("Deleting the property '{}' for resource '{}'", propertyName, resourceURL);
                propertyStorage.remove(aKey);
            }
        }
    }

    public Resource addResource(Resource resource, boolean restoreMissingProperties) {
        if (resource != null) {
            resource.setFactory(this);
            resources.put(resource.getURL(), resource);
            if (restoreMissingProperties) {
                // for (Property aProperty : properties) {
                // if (resource.getPropertyValue(aProperty) == null) {
                // restoreProperty(resource, aProperty);
                // }
                // }
                this.restoreProperties(resource);
            }

        }
        return resource;
    }

    public void deleteResource(Resource resource) {
        if (resource != null) {
            logger.debug("Deleting the resource '{}'", resource.getURL());
            resource.notifyParent();
            resources.remove(resource.getURL());
            deleteProperties(resource);
            // eventStorage.remove(resource.getURL());
        }
    }

    public void addProperties(List<Property> properties) {
        this.properties.addAll(properties);
    }

    public Property getProperty(String name) {
        for (Property aProperty : properties) {
            if (aProperty.getName().equals(name)) {
                return aProperty;
            }
        }
        return null;
    }

    public void setRedirect(Resource from, Resource to) {
        redirectMap.put(from, to);
    }

    public void setRedirect(String from, String to) {
        if (resources.get(from) != null && resources.get(to) != null) {
            redirectMap.put(resources.get(from), resources.get(to));
        }
    }

    public Resource getRedirect(Resource from) {
        return redirectMap.get(from);
    }

    // public Object getPropertyValue(Resource resource, Property property) {
    //
    // Object value = null;
    // if (resource != null && property != null) {
    // if (property == getProperty("calendar-data")) {
    // value = retrieveContent(resource);
    // } else {
    // value = retrieveProperty(resource, property);
    // }
    // }
    //
    // return value;
    //
    // }

    public List<Resource> getChildResources(Resource source) {
        String sourceURL = source.getURL();
        if (sourceURL.endsWith("/")) {
            sourceURL = StringUtils.substringBeforeLast(source.getURL(), "/");
        }
        List<Resource> children = new ArrayList<Resource>();

        for (String aResource : resources.keySet()) {
            String ar = aResource;
            if (ar.endsWith("/")) {
                ar = StringUtils.substringBeforeLast(ar, "/");
            }
            String levelUp = StringUtils.substringBeforeLast(ar, "/");

            if (levelUp.equals(sourceURL)) {
                children.add(resources.get(aResource));
            }
        }

        return children;

    }

    public Resource getParentResource(Resource resource) {
        if (resource != null) {
            return getParentResource(resource.getURL());
        }
        return null;
    }

    public Resource getParentResource(String url) {

        if (url != null) {
            if (url.endsWith("/")) {
                url = StringUtils.substringBeforeLast(url, "/");
            }
            String parentURL = StringUtils.substringBeforeLast(url, "/");

            if (!parentURL.endsWith("/")) {
                parentURL = parentURL + "/";
            }

            return restoreResource(parentURL);
        }

        return null;
    }

    public Collection<Resource> getResources() {
        return resources.values();
    }

    private Set<String> getStoredResources() {
        HashSet<String> resources = new HashSet<String>();
        for (String akey : propertyStorage.getKeys()) {
            resources.add(decodeResource(akey));
        }
        return resources;
    }

    private Set<String> getStoredProperties(String resource) {
        HashSet<String> properties = new HashSet<String>();
        for (String akey : propertyStorage.getKeys()) {
            if (this.decodeResource(akey).equals(resource)) {
                properties.add(decodeProperty(akey));
            }

        }
        return properties;
    }

    public Object retrieveProperty(Resource resource, Property property) {
        if (resource != null && property != null) {
            String storedValue = retrieveProperty(resource.getURL(), property.getName());
            return property.getValue(storedValue);
        }
        return null;
    }

    private String retrieveProperty(String url, String propertyName) {
        if (url != null && propertyName != null) {
            return propertyStorage.get(encodeKey(url, propertyName));
        }
        return null;
    }

    private String decodeResource(String key) {
        return StringUtils.substringBefore(key, "#");
    }

    private String decodeProperty(String key) {
        return StringUtils.substringAfter(key, "#");
    }

    private String encodeKey(Resource resource, Property property) {
        if (resource != null && property != null) {
            return resource.getURL() + "#" + property.getName();
        }
        return null;
    }

    private String encodeKey(String url, String propertyName) {
        if (url != null && propertyName != null) {
            return url + "#" + propertyName;
        }
        return null;
    }

}
