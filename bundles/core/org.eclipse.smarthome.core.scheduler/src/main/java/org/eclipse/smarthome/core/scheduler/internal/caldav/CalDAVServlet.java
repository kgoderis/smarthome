package org.eclipse.smarthome.core.scheduler.internal.caldav;

import static org.eclipse.smarthome.core.scheduler.internal.caldav.CalDAVConstants.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.automation.Action;
import org.eclipse.smarthome.automation.RuleRegistry;
import org.eclipse.smarthome.automation.Trigger;
import org.eclipse.smarthome.automation.Visibility;
import org.eclipse.smarthome.automation.events.RuleAddedEvent;
import org.eclipse.smarthome.automation.events.RuleRemovedEvent;
import org.eclipse.smarthome.automation.events.RuleStatusInfoEvent;
import org.eclipse.smarthome.automation.events.RuleUpdatedEvent;
import org.eclipse.smarthome.core.events.Event;
import org.eclipse.smarthome.core.events.EventFilter;
import org.eclipse.smarthome.core.events.EventSubscriber;
import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.persistence.PersistenceService;
import org.eclipse.smarthome.core.scheduler.httpcontext.HttpContextService;
import org.eclipse.smarthome.core.scheduler.internal.quartz.RecurrenceCalendar;
import org.eclipse.smarthome.core.storage.StorageService;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.filter.DateInRangeRule;
import net.fortuna.ical4j.filter.Filter;
import net.fortuna.ical4j.filter.Rule;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateRange;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

/**
 *
 * The concept of using HTTP [RFC2616] and WebDAV [RFC2518] as a basis
 * for a calendar access protocol is by no means a new concept: it was
 * discussed in the IETF CALSCH working group as early as 1997 or 1998.
 * Several companies have implemented calendar access protocols using
 * HTTP to upload and download iCalendar [RFC2445] objects, and using
 * WebDAV to get listings of resources. However, those implementations
 * do not interoperate because there are many small and big decisions to
 * be made in how to model calendaring data as WebDAV resources, as well
 * as how to implement required features that aren't already part of
 * WebDAV. [RFC4791] proposes a way to model calendar data in
 * WebDAV, with additional features to make an interoperable calendar
 * access protocol.
 *
 * To advertise support for CalDAV, a server:
 *
 * o MUST support iCalendar [RFC2445] as a media type for the calendar
 * object resource format;
 *
 * o MUST support WebDAV Class 1 [RFC2518] (note that [rfc2518bis]
 * describes clarifications to [RFC2518] that aid interoperability);
 *
 * o MUST support WebDAV ACL [RFC3744] with the additional privilege
 * defined in Section 6.1 of [RFC4791];
 *
 * o MUST support transport over TLS [RFC2246] as defined in [RFC2818]
 * (note that [RFC2246] has been obsoleted by [RFC4346]);
 *
 * o MUST support ETags [RFC2616] with additional requirements
 * specified in Section 5.3.4 of [RFC4791];
 *
 * o MUST support all calendaring reports defined in Section 7 of [RFC4791]; and
 *
 * o MUST advertise support on all calendar collections and calendar
 * object resources for the calendaring reports in the DAV:supported-
 * report-set property, as defined in Versioning Extensions to WebDAV
 * [RFC3253].
 *
 *
 * @author Karel Goderis - Initial Contribution
 *
 */
public class CalDAVServlet extends HttpServlet implements PersistenceService, EventSubscriber {

    private final Logger logger = LoggerFactory.getLogger(CalDAVServlet.class);

    private final Set<String> subscribedEventTypes = ImmutableSet.of(RuleAddedEvent.TYPE, RuleRemovedEvent.TYPE,
            RuleUpdatedEvent.TYPE, RuleStatusInfoEvent.TYPE);

    private static final long serialVersionUID = 253181690392018023L;
    private static final String SERVLET_NAME = "/";

    protected HttpService httpService;
    protected HttpContextService httpContextService;
    protected RuleRegistry ruleRegistry;

    protected ServletConfig servletConfig;
    protected ArrayList<Property> properties;
    protected boolean persistenceEnabled = true;
    protected Gson gson = new Gson();
    protected ResourceFactory rf = new ResourceFactory();
    protected DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    protected TransformerFactory transformerFactory = TransformerFactory.newInstance();
    protected DocumentBuilder docBuilder;
    protected Transformer transformer;

    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    public void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }

    public void setHttpContextService(HttpContextService httpContextService) {
        this.httpContextService = httpContextService;
    }

    public void unsetHttpContextService(HttpContextService httpContextService) {
        this.httpContextService = null;
    }

    protected void setStorageService(StorageService storageService) {
        rf.setStorageService(storageService);
    }

    protected void unsetStorageService(StorageService storageService) {
        rf.setStorageService(null);
    }

    protected void setRuleRegistry(RuleRegistry registry) {
        this.ruleRegistry = registry;
    }

    protected void unsetRuleRegistry(RuleRegistry registry) {
        this.ruleRegistry = null;
    }

    protected void activate(Map<String, Object> config) {
        try {
            logger.debug("Starting up CalDAV servlet at " + SERVLET_NAME);
            HttpContext context = httpContextService.getDefaultContext();

            Hashtable<String, String> props = new Hashtable<String, String>();
            httpService.registerServlet(SERVLET_NAME, this, props, context);

            docFactory.setNamespaceAware(true);
            docBuilder = docFactory.newDocumentBuilder();
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // StdSchedulerFactory.getDefaultScheduler().getListenerManager().addSchedulerListener(this);

        } catch (Exception e) {
            logger.error("An exception occurred while starting the CalDAV Servlet : '{}'", e.getMessage());
        }

    }

    protected void deactivate() {
        httpService.unregister(SERVLET_NAME);

        // try {
        // StdSchedulerFactory.getDefaultScheduler().getListenerManager().removeSchedulerListener(this);
        // } catch (SchedulerException e) {
        // logger.error("An exception occurred while unregistering the CalDAV Servlet as Quartz listener : '{}'",
        // e.getMessage());
        // }

        logger.info("Stopped CalDAV servlet");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        try {
            servletConfig = config;

            // Definitions for the relevant properties as defined and used throughout in the various RFCs. Most
            // properties are defined in the WebDAV namespace, but all CalDAV ones are complemented with properties
            // defined by Apple and calendarserver.org
            properties = new ArrayList<Property>();
            properties.add(new Property(APPLE_NS, "calendar-color").withValueType(String.class).withPersistence());
            properties
                    .add(new Property(CALDAV_NS, "calendar-description").withValueType(String.class).withPersistence());
            properties.add(new Property(CALDAV_NS, "calendar-data").withValueType(String.class).withPersistence());
            properties.add(new Property(CALDAV_NS, "calendar-order").withValueType(String.class).withPersistence());
            properties.add(new Property(CALDAV_NS, "calendar-user-type"));
            properties.add(new Property(CALDAV_NS, "calendar-timezone").withValueType(String.class).withPersistence());
            properties.add(new Property("creationdate"));
            properties.add(new Property("current-user-privilege-set"));
            properties.add(new Property("default-alarm-vevent-date").withValueType(String.class).withPersistence());
            properties.add(new Property("default-alarm-vevent-datetime").withValueType(String.class).withPersistence());
            properties.add(new Property("getcontentlength"));
            properties.add(new Property("getcontenttype").withValueType(String.class));
            properties.add(new Property("getcreated"));
            properties.add(new Property(CS_NS, "getctag").withValueType(String.class).withPersistence());
            properties.add(new Property("getetag").withValueType(String.class).withPersistence());
            properties.add(new Property("getlastmodified"));
            properties.add(new Property("iscollection"));
            properties.add(new Property("isreadonly"));
            properties.add(new Property("name"));
            properties.add(new Property("quota-available-bytes"));
            properties.add(new Property("quota-used-bytes"));
            properties.add(new Property(CALDAV_NS, "schedule-inbox-URL"));
            properties.add(new Property(CALDAV_NS, "schedule-outbox-URL"));
            properties.add(new Property(CALDAV_NS, "supported-calendar-component-set"));
            properties.add(new Property(CALDAV_NS, "supported-calendar-component-sets"));

            properties.add(
                    new Property(ECLIPSE_NS, "resource-modified-date").withValueType(Date.class).withPersistence());
            properties.add(new Property(ECLIPSE_NS, "resource-url").withValueType(String.class).withPersistence());
            properties.add(new Property(ECLIPSE_NS, "resource-uniqueid").withValueType(String.class).withPersistence());

            // All properties we re-use to set property values on our own pre-built resources
            Property principalURLProperty = new Property("principal-URL").withValueType(Href.class);
            Property calendarHomeSetProperty = new Property(CALDAV_NS, "calendar-home-set").withValueType(Href.class);
            Property currentUserPrincipalProperty = new Property("current-user-principal").withValueType(Href.class);
            Property principalCollectionSetProperty = new Property("principal-collection-set")
                    .withValueType(Href.class);
            Property resourceTypeProperty = new Property("resourcetype");
            Property supportedReportSetProperty = new Property("supported-report-set").withValueType(String.class);
            Property calendarUserAddressSetProperty = new Property(CALDAV_NS, "calendar-user-address-set")
                    .withValueType(Href.class);
            Property displayNameProperty = new Property("displayname").withValueType(String.class).withPersistence();
            Property currentUserPrivilegeSet = new Property("current-user-privilege-set");
            Property contentTypeProperty = new Property("getcontenttype").withValueType(String.class);
            properties.add(principalURLProperty);
            properties.add(calendarHomeSetProperty);
            properties.add(currentUserPrincipalProperty);
            properties.add(principalCollectionSetProperty);
            properties.add(resourceTypeProperty);
            properties.add(supportedReportSetProperty);
            properties.add(calendarUserAddressSetProperty);
            properties.add(displayNameProperty);
            properties.add(currentUserPrivilegeSet);
            properties.add(contentTypeProperty);

            // "Levels" supported by the CalDAV server. Levels let clients discover what features are implemented by the
            // CalDAV server
            ArrayList<String> supportedLevels = new ArrayList<String>();
            supportedLevels.add("1");
            supportedLevels.add("access-control");
            supportedLevels.add("calendar-access");

            // Report types supported by the CalDAV server in the http REPORT method
            ArrayList<String> supportedReports = new ArrayList<String>();
            supportedReports.add("expand-property");
            supportedReports.add("calendar-query");
            supportedReports.add("calendar-multiget");
            supportedReports.add(" free-busy-query");

            rf.addProperties(properties);

            // "Well-known" resource for automatic client configuration
            rf.addResource(
                    new Resource(rf, "/.well-known/caldav").withMethods(Arrays.asList(METHOD_PROPFIND, METHOD_GET)),
                    true);

            // Main resource for our own purposes. In order to simplify things, the url for the calendar, calendar
            // homes, principal, principal collections are all the same. This is just to prevent a configuration mess
            // both at the server *and* the client side
            rf.addResource(new Resource(rf, "/openhab/")
                    .withMethods(Arrays.asList(METHOD_PROPFIND, METHOD_DELETE, METHOD_GET, METHOD_OPTIONS,
                            METHOD_PROPPATCH, METHOD_PUT, METHOD_REPORT))
                    .withLevels(supportedLevels).withProperty(principalURLProperty, "/openhab/")
                    .withProperty(currentUserPrincipalProperty, "/openhab/")
                    .withProperty(principalCollectionSetProperty, "/openhab/")
                    .withProperty(calendarHomeSetProperty, "/openhab/")
                    .withProperty(calendarUserAddressSetProperty, "/openhab/")
                    .withProperty(supportedReportSetProperty, supportedReports)
                    .withProperty(contentTypeProperty, "text/calendar").withProperty(displayNameProperty, "openHAB")
                    .withProperty(currentUserPrivilegeSet, null).withType(ResourceType.CALENDAR)
                    .withType(ResourceType.COLLECTION).withPrivilege(Privilege.ALL), true)
                    .withComponent(ComponentType.VEVENT).withCollation("i;ascii-casemap");

            // Redirect clients doing service discovery to our main resource
            rf.setRedirect("/.well-known/caldav", "/openhab/");

            // Other resources are persisted from a previous incarnation. Load them, clean the database, and set their
            // properties
            rf.restoreResources();
            rf.pruneProperties();
            rf.restoreProperties();

            // setup the o.e.s.Automation stuff for events happening in the future
            CalendarBuilder builder = new CalendarBuilder();
            for (Resource r : rf.getResources()) {
                String calendardata = ((String) r.getPropertyValue(rf.getProperty("calendar-data")));
                if (calendardata != null) {
                    StringReader sin = new StringReader(calendardata);
                    Calendar calendar = builder.build(sin);
                    if (calendar != null) {
                        VEvent event = (VEvent) calendar.getComponent(net.fortuna.ical4j.model.Component.VEVENT);
                        if (event.getStartDate().getDate().after(new Date())
                                || event.getEndDate().getDate().after(new Date())) {
                            buildRule(calendar);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("An exception occurred while initializing the CalDAV Servlet : '{}'", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getServletInfo() {
        return "CalDAVServlet";
    }

    @Override
    public ServletConfig getServletConfig() {
        return servletConfig;
    }

    @Override
    public void service(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse)
            throws ServletException, IOException {

        try {
            if (!(servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse)) {
                throw new ServletException("non-HTTP request or response");
            }

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            String method = req.getMethod();
            String host = req.getHeader(Header.HOST.code);
            String absPath = stripPath(req.getRequestURL().toString());

            long start = System.currentTimeMillis();

            logger.debug("Received request : {}::{}:{}", new Object[] { req.getMethod(), host, absPath });
            Enumeration<String> names = req.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String value = req.getHeader(name);
                logger.trace("Request Header : {}:{}", name, value);
            }

            if (method.equals(METHOD_ACL)) {
                doACL(req, resp);
            } else if (method.equals(METHOD_CONNECT)) {
                doConnect(req, resp);
            } else if (method.equals(METHOD_COPY)) {
                doCopy(req, resp);
            } else if (method.equals(METHOD_LOCK)) {
                doLock(req, resp);
            } else if (method.equals(METHOD_MKCALENDAR)) {
                doMkCalendar(req, resp);
            } else if (method.equals(METHOD_MKCOL)) {
                doMkCol(req, resp);
            } else if (method.equals(METHOD_MOVE)) {
                doMove(req, resp);
            } else if (method.equals(METHOD_PROPFIND)) {
                doPropFind(req, resp);
            } else if (method.equals(METHOD_PROPPATCH)) {
                doPropPatch(req, resp);
            } else if (method.equals(METHOD_REPORT)) {
                doReport(req, resp);
            } else if (method.equals(METHOD_UNLOCK)) {
                doUnlock(req, resp);
            } else {
                super.service(req, resp);
            }

            long end = System.currentTimeMillis();
            logger.debug("Processed request : {}::{}:{} after {} ms",
                    new Object[] { req.getMethod(), host, absPath, end - start });

            Collection<String> respNames = resp.getHeaderNames();
            for (String name : respNames) {
                String value = resp.getHeader(name);
                logger.trace("Response Header : {}:{}", name, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void doACL(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doConnect(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doCopy(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String url = decodeURL(req);
            String ifMatch = req.getHeader(Header.IF_MATCH.code);
            boolean doesMatch = false;

            Resource r = rf.restoreResource(url);
            if (r != null) {
                if (r.hasMethod(METHOD_DELETE)) {

                    ByteArrayOutputStream copiedStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[2048];
                    for (int n = 0; n >= 0; n = req.getInputStream().read(buffer)) {
                        copiedStream.write(buffer, 0, n);
                    }
                    copiedStream.close();
                    logger.trace("Delete : Body : {}", copiedStream.toString());

                    HashSet<Resource> toBeDeleted = buildResourceSet(r);

                    // Do If-Match header processing
                    if (ifMatch != null && ifMatch.equals("*")) {
                        // ignore the etag, go ahead and delete any resource if it exists
                        // and since we already collected the resources to be deleted,
                        // we go ahead
                        doesMatch = true;
                    } else {
                        if (ifMatch != null) {
                            boolean result = true;
                            for (Resource aResource : toBeDeleted) {
                                if (!aResource.getETag().equals(ifMatch)) {
                                    result = false;
                                    break;
                                }
                            }
                            doesMatch = result;
                        } else {
                            doesMatch = true;
                        }
                    }

                    if (doesMatch) {
                        // build the response
                        resp.setStatus(Status.SC_NO_CONTENT.code);
                        resp.addHeader(Header.SERVER.code, "Eclipse SmartHome");
                        resp.addHeader(Header.ACCEPT_RANGES.code, "bytes");
                        resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

                        DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                        df.setTimeZone(TimeZone.getTimeZone("GMT"));
                        resp.addHeader(Header.DATE.code, df.format(new Date()));

                        List<String> supportedLevels = r.getLevels();
                        resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                        for (Resource aResource : toBeDeleted) {
                            // We assume that removal will always be successfull since we control the database in the
                            // first place and because we precompiled the list of resources to be deleted.
                            rf.deleteResource(aResource);
                        }

                        resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(0));
                    } else {
                        doPreconditionFail(req, resp);
                    }
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Delete method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String url = decodeURL(req);
            String s = req.getHeader(Header.EXPECT.code);
            if (s != null && s.length() > 0) {
                resp.setStatus(Status.SC_CONTINUE.code);
                return;
            }

            Resource r = rf.restoreResource(url);
            if (r != null) {
                if (r.hasMethod(METHOD_GET)) {

                    // TODO : check if authorised

                    if (rf.getRedirect(r) != null) {
                        try {
                            logger.debug("Get : Redircting the request to : {}",
                                    resp.encodeRedirectURL(rf.getRedirect(r).getURL()));
                            resp.sendRedirect(resp.encodeRedirectURL(rf.getRedirect(r).getURL()));
                        } catch (IOException e) {
                            logger.error("An exception occurred while redirecting an URL : '{}'", e.getMessage());
                        }
                        return;
                    }

                    // TODO : check if resource is locked, if so, send LOCKED status code

                    // process existing resource

                    // TODO : check if resource has been modified, if not, send notModified response

                    // TODO : send content back to client

                    // TODO : check if partial content
                    // String range = req.getHeader(Header.RANGE.code);
                    //
                    // if (range != null && range.length() != 0) {
                    // if (range.startsWith("bytes=")) {
                    // range = range.substring(6);
                    // String[] arr1 = range.split(",");
                    // List<Range> list = new ArrayList<Range>();
                    // // for (String s : arr) {
                    // // Range r = Range.parse(s);
                    // // list.add(r);
                    // // }
                    //
                    // // TODO : if list not null or empty, check length of content of resource, and if not null, do
                    // send
                    // // partial content response
                    //
                    // return;
                    // }
                    // }

                    resp.setStatus(Status.SC_OK.code);
                    resp.addHeader(Header.CONTENT_TYPE.code, "text/calendar; charset=utf-8");

                    DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    resp.addHeader(Header.DATE.code, df.format(new Date()));

                    List<String> supportedLevels = r.getLevels();
                    resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                    ServletOutputStream outputStream = resp.getOutputStream();

                    if (r.isCollection()) {
                        Calendar aggregateCalendar = new Calendar();
                        CalendarBuilder builder = new CalendarBuilder();
                        for (Resource aResource : r.getChildren()) {
                            StringReader sin = new StringReader(
                                    ((String) aResource.getPropertyValue(rf.getProperty("calendar-data"))));
                            Calendar calendar = builder.build(sin);
                            aggregateCalendar.getComponents().addAll(calendar.getComponents());
                        }
                        CalendarOutputter outputter = new CalendarOutputter();
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        outputter.output(aggregateCalendar, bout);
                        resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(bout.toByteArray().length));
                        outputter.output(aggregateCalendar, outputStream);
                    } else {
                        if (r.getPropertyValue(rf.getProperty("calendar-data")) != null) {
                            byte[] arr = ((String) r.getPropertyValue(rf.getProperty("calendar-data")))
                                    .getBytes("UTF-8");
                            resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(arr.length));
                            outputStream.write(arr);
                        } else {
                            doMethodFailure(req, resp);
                            return;
                        }
                    }
                    resp.addHeader(Header.ETAG.code, r.getETag());
                    outputStream.flush();
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Get method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void doLock(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doMethodFailure(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            resp.setStatus(Status.SC_METHOD_FAILURE.code);
            resp.addHeader(Header.CONTENT_TYPE.code, "text/html");

            String methodFailure = "<html><body><h1>${url} Method Failure (420)</h1></body></html>";
            methodFailure = methodFailure.replace("${url}", stripPath(req.getRequestURL().toString()));
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.write(methodFailure.getBytes("UTF-8"));
            outputStream.flush();
        } catch (Exception e) {
            logger.error("An exception occurred while sending a failure reponse : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void doMkCalendar(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doMkCol(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doMove(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    protected void doNotFound(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            resp.setStatus(Status.SC_NOT_FOUND.code);
            resp.addHeader(Header.CONTENT_TYPE.code, "text/html");

            String notFoundFailure = "<html><body><h1>${url} Not Found (404)</h1></body></html>";
            notFoundFailure = notFoundFailure.replace("${url}", stripPath(req.getRequestURL().toString()));
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.write(notFoundFailure.getBytes("UTF-8"));
            outputStream.flush();
        } catch (Exception e) {
            logger.error("An exception occurred while sending a not found reponse : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        try {
            String url = decodeURL(req);
            Resource r = rf.restoreResource(url);

            // TODO : check if authorised

            if (r != null) {
                if (r.hasMethod(METHOD_OPTIONS)) {

                    // common header
                    resp.setStatus(Status.SC_OK.code);
                    resp.addHeader(Header.SERVER.code, "Eclipse SmartHome");
                    resp.addHeader(Header.ACCEPT_RANGES.code, "bytes");

                    DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    resp.addHeader(Header.DATE.code, df.format(new Date()));

                    resp.setHeader(Header.ALLOW.code, encodeCSV(r.getMethods(), true));
                    resp.setHeader(Header.DAV.code, encodeCSV(r.getLevels(), true));

                    resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(0));
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Options method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void doPreconditionFail(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            resp.setStatus(Status.SC_PRECONDITION_FAILED.code);
            resp.addHeader(Header.CONTENT_TYPE.code, "text/html");

            String precFailure = "<html><body><h1>${url} Precondition Failure (412)</h1></body></html>";
            precFailure = precFailure.replace("${url}", stripPath(req.getRequestURL().toString()));
            ServletOutputStream outputStream = resp.getOutputStream();
            outputStream.write(precFailure.getBytes("UTF-8"));
            outputStream.flush();
        } catch (Exception e) {
            logger.error("An exception occurred while sending a precondition failure reponse : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void doPropFind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String url = decodeURL(req);
            String depthStr = req.getHeader(Header.DEPTH.code);
            boolean isBriefRequest = "t".equals(req.getHeader(Header.BRIEF.code));
            int depth = 3;
            if (depthStr != null) {
                if (depthStr.equals("0")) {
                    depth = 0;
                } else if (depthStr.equals("1")) {
                    depth = 1;
                } else if (depthStr.equals("infinity")) {
                    depth = 3;
                } else {
                    logger.warn("PropFind : Unknown depth value : '{}'", depthStr);
                }
            }

            // Fix for Windows 7 sending ampersands in requests
            url.replace("&", "%26");
            url = (new URI(url)).toASCIIString();

            Resource r = rf.restoreResource(url);

            if (r != null) {
                if (r.hasMethod(METHOD_PROPFIND)) {

                    boolean isAllProp = false;

                    ArrayList<QName> qNames = new ArrayList<QName>();

                    try {
                        ByteArrayOutputStream copiedStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[2048];
                        for (int n = 0; n >= 0; n = req.getInputStream().read(buffer)) {
                            copiedStream.write(buffer, 0, n);
                        }
                        copiedStream.close();
                        logger.trace("PropFind : Body : {}", copiedStream.toString());

                        Document document = docBuilder
                                .parse(new InputSource(new StringReader(copiedStream.toString())));

                        NodeList propfinds = document.getElementsByTagNameNS("*", "propfind");
                        NodeList props = document.getElementsByTagNameNS("*", "prop");
                        NodeList allprops = document.getElementsByTagNameNS("*", "allprop");

                        if (propfinds.getLength() > 0) {
                            if (allprops.getLength() > 0) {
                                isAllProp = true;
                            } else {
                                for (int i = 0; i < props.getLength(); i++) {
                                    Node node = props.item(i);
                                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                                        NodeList propDetails = node.getChildNodes();
                                        for (int j = 0; j < propDetails.getLength(); j++) {
                                            Node propdetail = propDetails.item(j);
                                            if (propdetail.getNodeType() == Node.ELEMENT_NODE) {
                                                qNames.add(new QName(propdetail.getNamespaceURI(),
                                                        propdetail.getLocalName()));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("An exception occurred while parsing a PropFind request : '{}'", e.getMessage());
                    }

                    if (isAllProp) {
                        logger.debug("PropFind : Requesting all properties");
                    } else {
                        for (QName aQName : qNames) {
                            logger.trace("PropFind : Requesting property '{}'", aQName.toString());
                        }
                    }

                    // TODO : check if the user has permissions on all the properties

                    HashMap<String, HashMap<Property, Object>> globalResolvedProperties = new HashMap<String, HashMap<Property, Object>>();
                    HashMap<String, ArrayList<QName>> globalUnresolvedProperties = new HashMap<String, ArrayList<QName>>();

                    doPropFindAtDepth(r, 0, depth, url, isAllProp, qNames, globalResolvedProperties,
                            globalUnresolvedProperties);

                    resp.setStatus(Status.SC_MULTI_STATUS.code);
                    resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

                    DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    resp.addHeader(Header.DATE.code, df.format(new Date()));

                    List<String> supportedLevels = r.getLevels();
                    resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                    Document doc = docBuilder.newDocument();
                    Element rootElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "multistatus");
                    doc.appendChild(rootElement);

                    for (String uri : globalResolvedProperties.keySet()) {
                        HashMap<Property, Object> uriMap = globalResolvedProperties.get(uri);
                        Resource uriResource = rf.restoreResource(uri);

                        Element responseElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "response");
                        rootElement.appendChild(responseElement);

                        Element hrefElement = doc.createElement(DAV_NS_PREFIX + "href");
                        hrefElement.appendChild(doc.createTextNode(uri));
                        responseElement.appendChild(hrefElement);

                        Element propstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                        responseElement.appendChild(propstatElement);
                        Element propElement = doc.createElement(DAV_NS_PREFIX + "prop");
                        propstatElement.appendChild(propElement);
                        int propertyCounter = 1;

                        for (Property aProperty : uriMap.keySet()) {
                            if (uriMap.get(aProperty) != null) {
                                Element propDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                        "prop" + propertyCounter + ":" + aProperty.getName());
                                propElement.appendChild(propDetailElement);

                                if (aProperty.getName().equals("resourcetype")) {
                                    Set<ResourceType> types = uriResource.getTypes();
                                    if (types.contains(ResourceType.PRINCIPAL)) {
                                        Element collectionElement = doc.createElementNS(aProperty.getNamespace(),
                                                "prop" + propertyCounter + ":" + "principal");
                                        propDetailElement.appendChild(collectionElement);
                                    }
                                    if (types.contains(ResourceType.COLLECTION)) {
                                        Element collectionElement = doc.createElementNS(aProperty.getNamespace(),
                                                "prop" + propertyCounter + ":" + "collection");
                                        propDetailElement.appendChild(collectionElement);
                                    }
                                    if (types.contains(ResourceType.CALENDAR)) {
                                        Element collectionElement = doc.createElementNS(CALDAV_NS,
                                                "propc" + propertyCounter + ":" + "calendar");
                                        propDetailElement.appendChild(collectionElement);
                                    }
                                } else if (aProperty.getName().equals("supported-report-set")) {
                                    @SuppressWarnings("unchecked")
                                    ArrayList<String> reports = (ArrayList<String>) uriMap.get(aProperty);
                                    for (String aReport : reports) {
                                        Element supportedReportElement = doc.createElementNS(aProperty.getNamespace(),
                                                "prop" + propertyCounter + ":" + "supported-report");
                                        propDetailElement.appendChild(supportedReportElement);
                                        Element supportedDetailReportElement = doc.createElementNS(
                                                aProperty.getNamespace(), "prop" + propertyCounter + ":" + "report");
                                        supportedReportElement.appendChild(supportedDetailReportElement);
                                        supportedDetailReportElement.appendChild(doc.createTextNode(aReport));
                                    }
                                } else if (aProperty.getName().equals("supported-calendar-component-set")) {
                                    Set<ComponentType> types = uriResource.getCompents();
                                    for (ComponentType aType : types) {
                                        Element collectionElement = doc.createElementNS(aProperty.getNamespace(),
                                                "prop" + propertyCounter + ":" + "comp");
                                        collectionElement.setAttribute("name", aType.toString());
                                        propDetailElement.appendChild(collectionElement);
                                    }
                                } else if (aProperty.getName().equals("supported-collation-set")) {
                                    Set<String> types = uriResource.getCollations();
                                    for (String aType : types) {
                                        Element collectionElement = doc.createElementNS(aProperty.getNamespace(),
                                                "prop" + propertyCounter + ":" + "supported-collation");
                                        collectionElement.appendChild(doc.createTextNode(aType));
                                        propDetailElement.appendChild(collectionElement);
                                    }
                                } else if (aProperty.getName().equals("current-user-privilege-set")) {
                                    List<Privilege> privileges = r.getPrivileges();
                                    ArrayList<Privilege> expandedList = new ArrayList<Privilege>();

                                    for (Privilege priv : privileges) {
                                        expandedList.add(priv);
                                        expandedList.addAll(priv.contains);
                                    }

                                    for (Privilege priv : expandedList) {
                                        Element privElement = doc.createElementNS(DAV_NS, "d" + ":" + "privilege");
                                        propDetailElement.appendChild(privElement);

                                        Element privDetailElement = doc.createElementNS(DAV_NS,
                                                "d" + ":" + StringUtils.replace(priv.toString(), "_", "-"));
                                        privElement.appendChild(privDetailElement);
                                    }
                                } else {
                                    if (aProperty.getValueType() == Href.class) {
                                        Element hrefDetailElement = doc.createElementNS(DAV_NS, "d" + ":" + "href");
                                        propDetailElement.appendChild(hrefDetailElement);
                                        hrefDetailElement
                                                .appendChild(doc.createTextNode(uriMap.get(aProperty).toString()));
                                    }

                                    if (aProperty.getValueType() == String.class
                                            && !uriMap.get(aProperty).toString().equals("")) {
                                        propDetailElement
                                                .appendChild(doc.createTextNode(uriMap.get(aProperty).toString()));
                                    }
                                }
                            }
                            propertyCounter++;
                        }

                        Element statusElement = doc.createElement(DAV_NS_PREFIX + "status");
                        statusElement.appendChild(doc.createTextNode(Status.SC_OK.toString()));
                        propstatElement.appendChild(statusElement);

                        ArrayList<QName> unresolvedProperties = globalUnresolvedProperties.get(uri);
                        if (unresolvedProperties != null && !isBriefRequest && unresolvedProperties.size() > 0) {
                            Element urPropstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                            responseElement.appendChild(urPropstatElement);
                            Element urPropElement = doc.createElement(DAV_NS_PREFIX + "prop");
                            urPropstatElement.appendChild(urPropElement);
                            int urPropertyCounter = 1;
                            for (QName aQName : unresolvedProperties) {
                                Element propDetailElement = doc.createElementNS(aQName.getNamespaceURI(),
                                        "unprop" + urPropertyCounter + ":" + aQName.getLocalPart());
                                urPropElement.appendChild(propDetailElement);
                                urPropertyCounter++;
                            }

                            Element urStatusElement = doc.createElement(DAV_NS_PREFIX + "status");
                            urStatusElement.appendChild(doc.createTextNode(Status.SC_NOT_FOUND.toString()));
                            urPropstatElement.appendChild(urStatusElement);
                        }
                    }

                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    transformer.transform(new DOMSource(doc), new StreamResult(bout));

                    logger.trace("PropFind : Transform : {}", bout.toString());

                    byte[] arr = bout.toByteArray();
                    resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(arr.length));
                    ServletOutputStream outputStream = resp.getOutputStream();
                    outputStream.write(arr);
                    outputStream.flush();
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Propfind method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    private void doPropFindAtDepth(Resource resource, int depth, int requestedDepth, String url, boolean isAllProp,
            ArrayList<QName> qNames, HashMap<String, HashMap<Property, Object>> globalResolvedProperties,
            HashMap<String, ArrayList<QName>> globalUnresolvedProperties) {
        ArrayList<Property> requestedProperties = new ArrayList<Property>();
        HashMap<Property, Object> resolvedProperties = new HashMap<Property, Object>();
        ArrayList<QName> unresolvedProperties = new ArrayList<QName>();

        String href = url;

        if (resource.isCollection()) {
            if (!href.endsWith("/")) {
                href = href + "/";
            }
        }

        if (isAllProp) {
            requestedProperties = properties;
        } else {
            for (QName aQname : qNames) {
                if (aQname.getLocalPart().equals("href")) {
                    resolvedProperties.put(new Property("href").withValueType(String.class), href);
                } else {
                    boolean existing = false;
                    for (Property aProperty : properties) {
                        if (aProperty.getName().equals(aQname.getLocalPart())) {
                            requestedProperties.add(aProperty);
                            existing = true;
                            break;
                        }
                    }
                    if (!existing) {
                        logger.trace("PropFind : Request for Property '{}' can not be fulfilled",
                                aQname.getLocalPart());
                        unresolvedProperties.add(aQname);
                    }
                }
            }
        }

        for (Property aProperty : requestedProperties) {
            Object value = resource.getPropertyValue(aProperty);
            if (value != null) {
                logger.trace("PropFind : Request for Property '{}' accepted", aProperty.getName());
                resolvedProperties.put(aProperty, resource.getPropertyValue(aProperty));
            } else {
                logger.trace("PropFind : Request for Property '{}' returned a null value", aProperty.getName());
                unresolvedProperties.add(new QName(aProperty.getNamespace(), aProperty.getName()));
            }
        }

        globalResolvedProperties.put(href, resolvedProperties);
        globalUnresolvedProperties.put(href, unresolvedProperties);

        if (requestedDepth > depth & resource.isCollection()) {
            List<Resource> children = resource.getChildren();
            if (children != null) {
                for (Resource child : children) {
                    if (child.hasMethod(METHOD_PROPFIND)) {
                        String childName = child.getName();
                        if (childName == null) {
                            logger.warn(
                                    "PropFind : Empty name for a resource at '{}' will not be included in the response",
                                    href);
                        } else {
                            String childHref = href + percentEncode(childName);
                            doPropFindAtDepth(child, depth + 1, requestedDepth, childHref, isAllProp, qNames,
                                    globalResolvedProperties, globalUnresolvedProperties);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            String url = decodeURL(req);
            String uniqueId = stripExtension(stripFullPath(url));
            String ifMatch = req.getHeader(Header.IF_MATCH.code);
            String ifNoneMatch = req.getHeader(Header.IF_NONE_MATCH.code);

            boolean doesMatch = false;
            boolean doesNoneMatch = false;

            Resource r = rf.restoreResource(url);
            if (r != null) {
                if (ifMatch != null && (ifMatch.equals("*") || r.getETag().equals(ifMatch))) {
                    // ignore the etag, go ahead and delete any resource if it exists
                    // and since we already collected the resources to be deleted,
                    // we go ahead
                    doesMatch = true;
                }
                if (ifNoneMatch != null && (ifNoneMatch.equals("*") || r.getETag().equals(ifNoneMatch))) {
                    doesNoneMatch = true;
                }
            } else {
                // New resource will be created
                Resource parent = rf.getParentResource(url);
                if (parent != null && parent.isCollection()) {
                    Resource newResource = new Resource(rf, url);
                    newResource.withMethods(Arrays.asList(METHOD_PROPFIND, METHOD_GET, METHOD_DELETE, METHOD_MOVE,
                            METHOD_PUT, METHOD_REPORT));
                    newResource.withLevel("1");
                    newResource.withPrivilege(Privilege.ALL);
                    newResource.setUniqueId(uniqueId);
                    newResource.withProperty(rf.getProperty("getcontenttype"), "text/calendar");
                    rf.addResource(newResource, false);

                    doesMatch = true;
                } else {
                    logger.debug("Put : Trying to create a resource in a parent resrouce that is not a collection");
                }
            }

            r = rf.restoreResource(url);
            if (r != null) {
                if (r.hasMethod(METHOD_PUT)) {
                    if (!doesNoneMatch || doesMatch) {
                        ByteArrayOutputStream copiedStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[2048];
                        for (int n = 0; n >= 0; n = req.getInputStream().read(buffer)) {
                            copiedStream.write(buffer, 0, n);
                        }
                        copiedStream.close();

                        logger.trace("Put : Body : {}", copiedStream.toString());

                        // parse the ical data
                        CalendarBuilder builder = new CalendarBuilder();
                        ByteArrayInputStream bin = new ByteArrayInputStream(copiedStream.toByteArray());
                        Calendar calendar = null;
                        try {
                            calendar = builder.build(bin);
                        } catch (ParserException e) {
                            logger.error("An exception occurred while parsing calendaring data : {}", e.getMessage());
                        }

                        buildRule(calendar);

                        // store the data
                        r.withProperty(rf.getProperty("calendar-data"), copiedStream.toString());
                        r.setModifiedDate(new Date());

                        resp.setStatus(Status.SC_CREATED.code);
                        resp.addHeader(Header.SERVER.code, "Eclipse SmartHome");
                        resp.addHeader(Header.ACCEPT_RANGES.code, "bytes");

                        DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                        df.setTimeZone(TimeZone.getTimeZone("GMT"));
                        resp.addHeader(Header.DATE.code, df.format(new Date()));

                        String eTag = r.getETag();
                        if (eTag != null) {
                            resp.addHeader(Header.ETAG.code, eTag);
                        }

                        resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(0));
                    } else {
                        doPreconditionFail(req, resp);
                    }
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Put method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }

        test();
    }

    protected void doPropPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String url = decodeURL(req);

            Resource r = rf.restoreResource(url);
            if (r != null) {
                if (r.hasMethod(METHOD_PROPPATCH)) {
                    ByteArrayOutputStream copiedStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[2048];
                    for (int n = 0; n >= 0; n = req.getInputStream().read(buffer)) {
                        copiedStream.write(buffer, 0, n);
                    }
                    copiedStream.close();
                    byte[] arr = copiedStream.toByteArray();
                    logger.trace("Proppatch : Body : {}", copiedStream.toString());

                    Document document = docBuilder.parse(new InputSource(new StringReader(copiedStream.toString())));

                    NodeList propUpdates = document.getElementsByTagNameNS("*", "propertyupdate");

                    ArrayList<Property> sucessfullProperties = new ArrayList<Property>();
                    ArrayList<QName> failedProperties = new ArrayList<QName>();

                    if (propUpdates.getLength() > 0) {
                        for (int i = 0; i < propUpdates.getLength(); i++) {
                            if (propUpdates.item(i).getNodeType() == Node.ELEMENT_NODE) {
                                Element propUpdate = (Element) propUpdates.item(i);
                                NodeList setCommands = propUpdate.getElementsByTagNameNS("*", "set");
                                for (int j = 0; j < setCommands.getLength(); j++) {
                                    if (setCommands.item(j).getNodeType() == Node.ELEMENT_NODE) {
                                        Element setCommand = (Element) setCommands.item(j);
                                        NodeList properties = setCommand.getElementsByTagNameNS("*", "prop");
                                        for (int k = 0; k < properties.getLength(); k++) {
                                            if (properties.item(k).getNodeType() == Node.ELEMENT_NODE) {
                                                Element property = (Element) properties.item(k);
                                                NodeList propDetails = property.getChildNodes();
                                                for (int l = 0; l < propDetails.getLength(); l++) {
                                                    Node propdetail = propDetails.item(j);
                                                    if (propdetail.getNodeType() == Node.ELEMENT_NODE) {
                                                        Property aProperty = rf.getProperty(propdetail.getLocalName());
                                                        if (aProperty != null) {
                                                            String value = propdetail.getTextContent();
                                                            r.withProperty(aProperty, value);
                                                            sucessfullProperties.add(aProperty);
                                                        } else {
                                                            failedProperties.add(new QName(propdetail.getNamespaceURI(),
                                                                    propdetail.getLocalName()));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                NodeList removeCommnds = propUpdate.getElementsByTagNameNS("*", "remove");
                                for (int j = 0; j < removeCommnds.getLength(); j++) {
                                    if (removeCommnds.item(j).getNodeType() == Node.ELEMENT_NODE) {
                                        Element setCommand = (Element) removeCommnds.item(j);
                                        NodeList properties = setCommand.getElementsByTagNameNS("*", "prop");
                                        for (int k = 0; k < properties.getLength(); k++) {
                                            if (properties.item(k).getNodeType() == Node.ELEMENT_NODE) {
                                                Element property = (Element) properties.item(k);
                                                NodeList propDetails = property.getChildNodes();
                                                for (int l = 0; l < propDetails.getLength(); l++) {
                                                    Node propdetail = propDetails.item(j);
                                                    if (propdetail.getNodeType() == Node.ELEMENT_NODE) {
                                                        Property aProperty = rf.getProperty(propdetail.getLocalName());
                                                        if (aProperty != null) {
                                                            r.removeProperty(aProperty);
                                                            sucessfullProperties.add(aProperty);
                                                        } else {
                                                            failedProperties.add(new QName(propdetail.getNamespaceURI(),
                                                                    propdetail.getLocalName()));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    resp.setStatus(Status.SC_MULTI_STATUS.code);
                    resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

                    DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                    df.setTimeZone(TimeZone.getTimeZone("GMT"));
                    resp.addHeader(Header.DATE.code, df.format(new Date()));

                    List<String> supportedLevels = r.getLevels();
                    resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                    Document doc = docBuilder.newDocument();
                    Element rootElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "multistatus");
                    doc.appendChild(rootElement);

                    Element responseElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "response");
                    rootElement.appendChild(responseElement);

                    Element hrefElement = doc.createElement(DAV_NS_PREFIX + "href");
                    hrefElement.appendChild(doc.createTextNode(url));
                    responseElement.appendChild(hrefElement);

                    int propertyCounter = 1;
                    for (Property aProperty : sucessfullProperties) {
                        Element propstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                        responseElement.appendChild(propstatElement);
                        Element propElement = doc.createElement(DAV_NS_PREFIX + "prop");
                        propstatElement.appendChild(propElement);

                        Element propDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                "prop" + propertyCounter + ":" + aProperty.getName());
                        propElement.appendChild(propDetailElement);

                        Element statusElement = doc.createElement(DAV_NS_PREFIX + "status");
                        statusElement.appendChild(doc.createTextNode(Status.SC_OK.toString()));
                        propstatElement.appendChild(statusElement);

                        propertyCounter++;
                    }

                    for (QName aProperty : failedProperties) {
                        Element propstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                        responseElement.appendChild(propstatElement);
                        Element propElement = doc.createElement(DAV_NS_PREFIX + "prop");
                        propstatElement.appendChild(propElement);

                        Element propDetailElement = doc.createElementNS(aProperty.getNamespaceURI(),
                                "prop" + propertyCounter + ":" + aProperty.getLocalPart());
                        propElement.appendChild(propDetailElement);

                        Element statusElement = doc.createElement(DAV_NS_PREFIX + "status");
                        statusElement.appendChild(doc.createTextNode(Status.SC_FORBIDDEN.toString()));
                        propstatElement.appendChild(statusElement);

                        propertyCounter++;
                    }

                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    transformer.transform(new DOMSource(doc), new StreamResult(bout));

                    logger.trace("PropPatch : Transform : {}", bout.toString());

                    arr = bout.toByteArray();
                    resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(arr.length));
                    ServletOutputStream outputStream = resp.getOutputStream();
                    outputStream.write(arr);
                    outputStream.flush();
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Proppatch method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    protected void doReport(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String url = decodeURL(req);

            Resource r = rf.restoreResource(url);
            if (r != null) {
                if (r.hasMethod(METHOD_REPORT)) {

                    ByteArrayOutputStream copiedStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[2048];
                    for (int n = 0; n >= 0; n = req.getInputStream().read(buffer)) {
                        copiedStream.write(buffer, 0, n);
                    }
                    copiedStream.close();
                    logger.trace("Report : Body : {}", copiedStream.toString());

                    Document document = docBuilder.parse(new InputSource(new StringReader(copiedStream.toString())));

                    String reportType = document.getDocumentElement().getLocalName();
                    logger.debug("Report : Received a request for a '{}' report", reportType);

                    switch (reportType) {
                        case "calendar-query": {
                            doCalendarQueryReport(req, resp, r, document);
                            break;
                        }
                        case "calendar-multiget": {
                            doCalendarMultigetReport(req, resp, r, document);
                            break;
                        }
                        case "free-busy-query": {
                            doFreeBuysQueryReport(req, resp, r, document);
                            break;
                        }
                        default: {
                            logger.debug("Report : This report type is not supported");
                        }
                    }
                } else {
                    doMethodFailure(req, resp);
                }
            } else {
                doNotFound(req, resp);
            }
        } catch (Exception e) {
            logger.error("An exception occurred while executing the http Report method : '{}'", e.getMessage());
            throw new ServletException(e.getMessage(), e);
        }
    }

    private void doFreeBuysQueryReport(HttpServletRequest req, HttpServletResponse resp, Resource r, Document query) {

        // Not supported because of the RFC we have to return something

        resp.setStatus(Status.SC_FORBIDDEN.code);
        resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

        DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        resp.addHeader(Header.DATE.code, df.format(new Date()));

        List<String> supportedLevels = r.getLevels();
        resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

        resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(0));

    }

    private void doCalendarMultigetReport(HttpServletRequest req, HttpServletResponse resp, Resource r,
            Document query) {
        try {

            NodeList refs = query.getElementsByTagNameNS("*", "href");
            NodeList props = query.getElementsByTagNameNS("*", "prop");

            if (r.getTypes().contains(ResourceType.CALENDAR)) {
                ArrayList<Resource> resolvedResources = new ArrayList<Resource>();
                ArrayList<String> unresolvedResources = new ArrayList<String>();
                for (int i = 0; i < refs.getLength(); i++) {
                    if (refs.item(i).getNodeType() == Node.ELEMENT_NODE) {
                        Element ref = (Element) refs.item(i);
                        logger.trace("Report : Found a refence to {}", ref.getTextContent());
                        Resource aResource = rf.restoreResource(ref.getTextContent());
                        if (aResource != null) {
                            resolvedResources.add(aResource);
                        } else {
                            unresolvedResources.add(ref.getTextContent());
                        }
                    }
                }

                ArrayList<Property> resolvedProperties = new ArrayList<Property>();
                ArrayList<String> unresolvedProperties = new ArrayList<String>();
                for (int i = 0; i < props.getLength(); i++) {
                    Node node = props.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        NodeList propDetails = node.getChildNodes();
                        for (int j = 0; j < propDetails.getLength(); j++) {
                            Node propdetail = propDetails.item(j);
                            if (propdetail.getNodeType() == Node.ELEMENT_NODE) {
                                boolean existing = false;
                                for (Property aProperty : properties) {
                                    if (aProperty.getName().equals(propdetail.getLocalName())) {
                                        resolvedProperties.add(aProperty);
                                        logger.trace("Report : Request for Property '{}' accepted",
                                                aProperty.getName());
                                        existing = true;
                                        break;
                                    }
                                }
                                if (!existing) {
                                    logger.trace("Report : Request for Property '{}' can not be fulfilled",
                                            propdetail.getLocalName());
                                    unresolvedProperties.add(propdetail.getLocalName());
                                }
                            }
                        }
                    }
                }

                // build the response
                resp.setStatus(Status.SC_MULTI_STATUS.code);
                resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

                DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                resp.addHeader(Header.DATE.code, df.format(new Date()));

                List<String> supportedLevels = r.getLevels();
                resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                Document doc = docBuilder.newDocument();
                Element rootElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "multistatus");
                doc.appendChild(rootElement);

                for (Resource aResource : resolvedResources) {
                    Element responseElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "response");
                    rootElement.appendChild(responseElement);

                    Element hrefElement = doc.createElement(DAV_NS_PREFIX + "href");
                    hrefElement.appendChild(doc.createTextNode(aResource.getURL()));
                    responseElement.appendChild(hrefElement);

                    Element propstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                    responseElement.appendChild(propstatElement);
                    Element propElement = doc.createElement(DAV_NS_PREFIX + "prop");
                    propstatElement.appendChild(propElement);

                    int propertyCounter = 1;
                    for (Property aProperty : resolvedProperties) {
                        Object value = aResource.getPropertyValue(aProperty);
                        if (value != null) {
                            Element propDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                    "prop" + propertyCounter + ":" + aProperty.getName());
                            propElement.appendChild(propDetailElement);

                            if (aProperty.getValueType() == Href.class) {
                                Element hrefDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                        "prop" + propertyCounter + ":" + "href");
                                propDetailElement.appendChild(hrefDetailElement);
                                hrefDetailElement.appendChild(doc.createTextNode((String) value));
                            }

                            if (aProperty.getValueType() == String.class && !((String) value).equals("")) {
                                propDetailElement.appendChild(doc.createTextNode((String) value));
                            }
                            propertyCounter++;
                        }
                    }

                    Element statusElement = doc.createElement(DAV_NS_PREFIX + "status");
                    statusElement.appendChild(doc.createTextNode(Status.SC_OK.toString()));
                    propstatElement.appendChild(statusElement);
                }

                for (String aResource : unresolvedResources) {
                    Element responseElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "response");
                    rootElement.appendChild(responseElement);

                    Element hrefElement = doc.createElement(DAV_NS_PREFIX + "href");
                    hrefElement.appendChild(doc.createTextNode(aResource));
                    responseElement.appendChild(hrefElement);

                    Element urStatusElement = doc.createElement(DAV_NS_PREFIX + "status");
                    urStatusElement.appendChild(doc.createTextNode(Status.SC_NOT_FOUND.toString()));
                    responseElement.appendChild(urStatusElement);
                }

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                transformer.transform(new DOMSource(doc), new StreamResult(bout));

                logger.trace("Report : Transform : {}", bout.toString());

                byte[] arr = bout.toByteArray();
                resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(arr.length));
                ServletOutputStream outputStream = resp.getOutputStream();
                outputStream.write(arr);
                outputStream.flush();
            }
        } catch (Exception e) {
            logger.error("An exception occurred while creating the calendar multiget report : '{}'", e.getMessage());
        }
    }

    private void doCalendarQueryReport(HttpServletRequest req, HttpServletResponse resp, Resource r, Document query) {
        try {
            int depth = 0;
            String depthStr = req.getHeader(Header.DEPTH.code);
            if (depthStr != null) {
                if (depthStr.equals("0")) {
                    depth = 0;
                } else if (depthStr.equals("1")) {
                    depth = 1;
                } else if (depthStr.equals("infinity")) {
                    depth = 3;
                } else {
                    logger.warn("Report : Unknown depth value : '{}'", depthStr);
                }
            }

            NodeList filters = query.getElementsByTagNameNS("*", "filter");
            NodeList props = query.getElementsByTagNameNS("*", "prop");

            if (r.getTypes().contains(ResourceType.CALENDAR)) {
                ArrayList<Property> resolvedProperties = new ArrayList<Property>();
                ArrayList<String> unresolvedProperties = new ArrayList<String>();
                for (int i = 0; i < props.getLength(); i++) {
                    Node node = props.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        NodeList propDetails = node.getChildNodes();
                        for (int j = 0; j < propDetails.getLength(); j++) {
                            Node propdetail = propDetails.item(j);
                            if (propdetail.getNodeType() == Node.ELEMENT_NODE) {
                                boolean existing = false;
                                for (Property aProperty : properties) {
                                    if (aProperty.getName().equals(propdetail.getLocalName())) {
                                        resolvedProperties.add(aProperty);
                                        logger.debug("Report : Request for Property '{}' accepted",
                                                aProperty.getName());
                                        existing = true;
                                        break;
                                    }
                                }
                                if (!existing) {
                                    logger.debug("Report : Request for Property '{}' can not be fulfilled",
                                            propdetail.getLocalName());
                                    unresolvedProperties.add(propdetail.getLocalName());
                                }
                            }
                        }
                    }
                }

                // TODO : limit the response to the components requested. Shortcut: We do not
                // stick to the RFC and return all calendar-data whatsoever

                // filter the resources
                HashSet<Resource> filteredResources = new HashSet<Resource>();

                doFilterAtDepth(r, 0, depth, filters, filteredResources);

                // build the response
                resp.setStatus(Status.SC_MULTI_STATUS.code);
                resp.addHeader(Header.CONTENT_TYPE.code, "text/xml; charset=utf-8");

                DateFormat df = new SimpleDateFormat(PATTERN_RESPONSE_HEADER);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                resp.addHeader(Header.DATE.code, df.format(new Date()));

                List<String> supportedLevels = r.getLevels();
                resp.addHeader(Header.DAV.code, encodeCSV(supportedLevels, true));

                Document doc = docBuilder.newDocument();
                Element rootElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "multistatus");
                doc.appendChild(rootElement);

                for (Resource aResource : filteredResources) {
                    Element responseElement = doc.createElementNS(DAV_NS, DAV_NS_PREFIX + "response");
                    rootElement.appendChild(responseElement);

                    Element hrefElement = doc.createElement(DAV_NS_PREFIX + "href");
                    hrefElement.appendChild(doc.createTextNode(aResource.getURL()));
                    responseElement.appendChild(hrefElement);

                    Element propstatElement = doc.createElement(DAV_NS_PREFIX + "propstat");
                    responseElement.appendChild(propstatElement);
                    Element propElement = doc.createElement(DAV_NS_PREFIX + "prop");
                    propstatElement.appendChild(propElement);

                    int propertyCounter = 1;
                    for (Property aProperty : resolvedProperties) {

                        Object value = aResource.getPropertyValue(aProperty);
                        if (value != null) {

                            Element propDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                    "prop" + propertyCounter + ":" + aProperty.getName());
                            propElement.appendChild(propDetailElement);

                            if (aProperty.getValueType() == Href.class) {
                                Element hrefDetailElement = doc.createElementNS(aProperty.getNamespace(),
                                        "prop" + propertyCounter + ":" + "href");
                                propDetailElement.appendChild(hrefDetailElement);
                                hrefDetailElement.appendChild(doc.createTextNode((String) value));
                            }

                            if (aProperty.getValueType() == String.class && !((String) value).equals("")) {
                                propDetailElement.appendChild(doc.createTextNode((String) value));
                            }
                            propertyCounter++;
                        }
                    }

                    Element statusElement = doc.createElement(DAV_NS_PREFIX + "status");
                    statusElement.appendChild(doc.createTextNode(Status.SC_OK.toString()));
                    propstatElement.appendChild(statusElement);
                }

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                transformer.transform(new DOMSource(doc), new StreamResult(bout));

                logger.trace("Report : Transform : {}", bout.toString());

                byte[] arr = bout.toByteArray();
                resp.addHeader(Header.CONTENT_LENGTH.code, String.valueOf(arr.length));
                ServletOutputStream outputStream = resp.getOutputStream();
                outputStream.write(arr);
                outputStream.flush();
            }
        } catch (Exception e) {
            logger.error("An exception occurred while creatint the calendar query report : '{}'", e.getMessage());
        }
    }

    private void doFilterAtDepth(Resource resource, int depth, int requestedDepth, NodeList filters,
            HashSet<Resource> globalFilteredResources) {
        try {
            Calendar calendar = null;
            try {
                if (resource.getPropertyValue(rf.getProperty("calendar-data")) != null) {
                    CalendarBuilder builder = new CalendarBuilder();
                    ByteArrayInputStream bin = new ByteArrayInputStream(
                            ((String) resource.getPropertyValue(rf.getProperty("calendar-data"))).getBytes("UTF-8"));
                    calendar = builder.build(bin);
                }
            } catch (ParserException e) {
                logger.error("An exception occurred while parsing calendaring data : {}", e.getMessage());
            }

            boolean result = true;
            for (int i = 0; i < filters.getLength(); i++) {
                Node filter = filters.item(i);
                if (filter.getNodeType() == Node.ELEMENT_NODE && filter.getLocalName().equals("filter")) {
                    if (!matchFilter(calendar, filter)) {
                        result = false;
                    }
                }
            }

            if (result) {
                globalFilteredResources.add(resource);
            }

            if (requestedDepth > depth & resource.isCollection()) {
                List<Resource> children = resource.getChildren();
                if (children != null) {
                    for (Resource child : children) {
                        doFilterAtDepth(child, depth + 1, requestedDepth, filters, globalFilteredResources);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("An exception occurred while filtering resources : '{}'", e.getMessage());
        }

    }

    private boolean matchFilter(Calendar calendar, Node filter) {
        NodeList componentfilters = filter.getChildNodes();
        boolean result = true;
        for (int i = 0; i < componentfilters.getLength(); i++) {
            Node componentfilter = componentfilters.item(i);
            if (componentfilter.getNodeType() == Node.ELEMENT_NODE
                    && componentfilter.getLocalName().equals("component-filter")) {
                if (!matchComponent(calendar, filter)) {
                    result = false;
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean matchComponent(Calendar calendar, Node component) {
        boolean result = true;
        String componentName = component.getAttributes().getNamedItem("name").getTextContent();

        if (component.getTextContent().isEmpty() && calendar.getComponent(componentName) != null) {
            return true;
        }

        NodeList componentelements = component.getChildNodes();

        for (int i = 0; i < componentelements.getLength(); i++) {
            Node componentelement = componentelements.item(i);
            if (componentelement.getNodeType() == Node.ELEMENT_NODE
                    && componentelement.getLocalName().equals("is-not-defined")) {
                String notDefined = componentelement.getAttributes().getNamedItem("name").getTextContent();
                if (calendar.getComponent(notDefined) == null) {
                    return true;
                }
            }
            if (componentelement.getNodeType() == Node.ELEMENT_NODE
                    && componentelement.getLocalName().equals("time-range")) {
                DateRange second = convertRange(componentelement);
                Rule<?> drRule = new DateInRangeRule(second, DateRange.INCLUSIVE_END | DateRange.INCLUSIVE_START);
                Filter<Component> filter = new Filter<Component>(new Rule[] { drRule }, Filter.MATCH_ALL);

                ArrayList<Component> list = new ArrayList<Component>();
                list.add(calendar.getComponent(componentName));
                Collection<Component> filterresult = filter.filter(list);
                if (filterresult.size() == 0) {
                    result = false;
                }
            }

            if (componentelement.getNodeType() == Node.ELEMENT_NODE
                    && componentelement.getLocalName().equals("component-filter")) {
                result = matchComponent(calendar, componentelement);
            }
            if (componentelement.getNodeType() == Node.ELEMENT_NODE
                    && componentelement.getLocalName().equals("prop-filter")) {
                result = matchProperty(calendar, componentelement);
            }
        }
        return result;
    }

    private DateRange convertRange(Node timerange) {
        String start = timerange.getAttributes().getNamedItem("start").getTextContent();
        String end = timerange.getAttributes().getNamedItem("end").getTextContent();

        DateTime startDT = null;
        try {
            startDT = new DateTime(start);
        } catch (ParseException e) {
            logger.error("An exception occurred while parsing a date : '{}'", e.getMessage());
        }
        DateTime endDT = null;
        try {
            endDT = new DateTime(end);
        } catch (ParseException e) {
            logger.error("An exception occurred while parsing a date : '{}'", e.getMessage());
        }
        return new DateRange(startDT, endDT);
    }

    private boolean matchProperty(Calendar calendar, Node property) {
        boolean result = true;
        String propertyName = property.getAttributes().getNamedItem("name").getTextContent();
        if (property.getTextContent().isEmpty() && calendar.getProperty(propertyName) != null) {
            return true;
        }

        NodeList propertyelements = property.getChildNodes();

        for (int i = 0; i < propertyelements.getLength(); i++) {
            Node propertyelement = propertyelements.item(i);
            if (propertyelement.getNodeType() == Node.ELEMENT_NODE
                    && propertyelement.getLocalName().equals("is-not-defined")) {
                String notDefined = propertyelement.getAttributes().getNamedItem("name").getTextContent();
                if (calendar.getProperty(notDefined) == null) {
                    return true;
                }
            }
            if (propertyelement.getNodeType() == Node.ELEMENT_NODE
                    && propertyelement.getLocalName().equals("time-range")) {
                DateRange range = convertRange(propertyelement);
                DateTime propertyDate = null;
                try {
                    propertyDate = new DateTime(propertyelement.getTextContent());
                } catch (Exception e) {
                    logger.error("An exception occurred while converting a date : '{}'", e.getMessage());
                }
                result = range.includes(propertyDate, DateRange.INCLUSIVE_END | DateRange.INCLUSIVE_START);
            }
            if (propertyelement.getNodeType() == Node.ELEMENT_NODE
                    && propertyelement.getLocalName().equals("text-match")) {
                result = matchText(calendar, propertyelement, calendar.getProperty(propertyName).getValue());
            }
            if (propertyelement.getNodeType() == Node.ELEMENT_NODE
                    && propertyelement.getLocalName().equals("param-filter")) {
                net.fortuna.ical4j.model.Property calendarProperty = calendar
                        .getProperty(propertyelement.getAttributes().getNamedItem("name").getTextContent());
                result = matchParameter(calendar, propertyelement, calendarProperty.getParameters());
            }
        }
        return result;
    }

    private boolean matchText(Calendar calendar, Node element, String value) {
        boolean result = true;

        String negate = element.getAttributes().getNamedItem("negate-condition").getTextContent();

        NodeList elements = element.getChildNodes();
        for (int i = 0; i < elements.getLength(); i++) {
            Node textelement = elements.item(i);
            if (textelement.getNodeType() == Node.ELEMENT_NODE) {
                boolean match = StringUtils.contains(value, textelement.getTextContent());
                if (negate.equals("no")) {
                    result = match;
                } else {
                    result = !match;
                }
            }
        }
        return result;
    }

    private boolean matchParameter(Calendar calendar, Node parameter, ParameterList parameterList) {
        boolean result = true;
        String parameterName = parameter.getAttributes().getNamedItem("name").getTextContent();

        if (parameter.getTextContent().isEmpty() && parameterList.getParameter(parameterName) != null) {
            return true;
        }

        NodeList parameterlements = parameter.getChildNodes();

        for (int i = 0; i < parameterlements.getLength(); i++) {
            Node parameterelement = parameterlements.item(i);
            if (parameterelement.getNodeType() == Node.ELEMENT_NODE
                    && parameterelement.getLocalName().equals("is-not-defined")) {
                String notDefined = parameterelement.getAttributes().getNamedItem("name").getTextContent();
                if (parameterList.getParameter(notDefined) == null) {
                    return true;
                }
            }
            if (parameterelement.getNodeType() == Node.ELEMENT_NODE
                    && parameterelement.getLocalName().equals("text-match")) {
                result = matchText(calendar, parameterelement, parameterList.getParameter(parameterName).getValue());
            }
        }
        return result;
    }

    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    private void buildRule(Calendar calendar) {
        try {
            if (calendar != null) {
                VEvent event = (VEvent) calendar.getComponent(net.fortuna.ical4j.model.Component.VEVENT);

                String description = "";
                if (event.getDescription() != null) {
                    description = event.getDescription().getValue();
                }

                String uid = event.getUid().getValue();

                logger.debug("Transforming the calendar event with id '{}' into a Rule", uid);

                // Check if there is already a rule etc for this event. If so, remove it completely
                org.eclipse.smarthome.automation.Rule existingRule = ruleRegistry.get(uid + "_Start_Rule");
                if (existingRule != null) {
                    logger.debug("Found an existing Rule with id '{}'", existingRule.getUID());
                    ruleRegistry.remove(existingRule.getUID());
                }
                existingRule = ruleRegistry.get(uid + "_End_Rule");
                if (existingRule != null) {
                    logger.debug("Found an existing Rule with id '{}'", existingRule.getUID());
                    ruleRegistry.remove(existingRule.getUID());
                }

                // delete any exclusion dates
                Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                scheduler.deleteCalendar(uid + "_Calendar");

                EventDescription ed = gson.fromJson(description, EventDescription.class);

                if (ed != null) {
                    ArrayList<org.eclipse.smarthome.automation.Trigger> startTriggers = buildTriggers("Start", uid,
                            event);
                    ArrayList<org.eclipse.smarthome.automation.Action> startActions = buildActions("Start", uid,
                            ed.StartActions);
                    ruleRegistry.add(new org.eclipse.smarthome.automation.Rule(uid + "_Start_Rule", startTriggers, null,
                            startActions, null, null, Visibility.VISIBLE));

                    ArrayList<org.eclipse.smarthome.automation.Trigger> endTriggers = buildTriggers("End", uid, event);
                    ArrayList<org.eclipse.smarthome.automation.Action> endActions = buildActions("End", uid,
                            ed.EndActions);
                    ruleRegistry.add(new org.eclipse.smarthome.automation.Rule(uid + "_End_Rule", endTriggers, null,
                            endActions, null, null, Visibility.VISIBLE));
                }
            }
        } catch (Exception e) {
            logger.error("An exception occured while converting a calander event into a Rule : '{}'", e.getMessage());
            e.printStackTrace();
        }
    }

    private ArrayList<Trigger> buildTriggers(String prefix, String uid, VEvent event) {
        try {
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            switch (prefix) {
                case "Start": {
                    calendar.setTime(event.getStartDate().getDate());
                    break;
                }
                case "End": {
                    calendar.setTime(event.getEndDate().getDate());
                    break;
                }
            }

            String triggerUID = uid + "_" + prefix + "_Trigger";
            ArrayList<org.eclipse.smarthome.automation.Trigger> triggers = new ArrayList<org.eclipse.smarthome.automation.Trigger>();
            if (event.getProperty(net.fortuna.ical4j.model.Property.RRULE) != null) {
                if (event.getProperty(net.fortuna.ical4j.model.Property.EXDATE) != null) {
                    RecurrenceCalendar aCalendar = new RecurrenceCalendar(event);
                    logger.debug("Adding exclusion dates to the scheduler");

                    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                    scheduler.addCalendar(uid + "_Calendar", aCalendar, true, false);

                }

                HashMap<String, Object> triggerConfig = new HashMap<String, Object>();
                triggerConfig.put("RRuleExpression",
                        event.getProperty(net.fortuna.ical4j.model.Property.RRULE).getValue());
                triggerConfig.put("StartDate", calendar.getTime());

                if (event.getProperty(net.fortuna.ical4j.model.Property.EXDATE) != null) {
                    triggerConfig.put("RRuleCalendar", uid + "_Calendar");
                }
                triggers.add(
                        new org.eclipse.smarthome.automation.Trigger(triggerUID, "RecurrenceTrigger", triggerConfig));
            } else {
                HashMap<String, String> triggerConfig = new HashMap<String, String>();
                String cronExpression = calendar.get(java.util.Calendar.SECOND) + " "
                        + calendar.get(java.util.Calendar.MINUTE) + " " + calendar.get(java.util.Calendar.HOUR_OF_DAY)
                        + " " + calendar.get(java.util.Calendar.DATE) + " "
                        + (calendar.get(java.util.Calendar.MONTH) + 1) + " ? " + calendar.get(java.util.Calendar.YEAR);
                triggerConfig.put("cronExpression", cronExpression);
                triggers.add(new org.eclipse.smarthome.automation.Trigger(triggerUID, "TimerTrigger", triggerConfig));
                return triggers;
            }
        } catch (Exception e) {
            logger.error("An exception occured while building a trigger for a calendar event : '{}'", e.getMessage());
        }
        return null;
    }

    private void test() {
        ArrayList<org.eclipse.smarthome.automation.Trigger> triggers = new ArrayList<org.eclipse.smarthome.automation.Trigger>();
        HashMap<String, String> triggerConfig = new HashMap<String, String>();
        triggerConfig.put("cronExpression", "0 1 * ? * *");
        triggers.add(new org.eclipse.smarthome.automation.Trigger(UUID.randomUUID().toString(), "TimerTrigger",
                triggerConfig));

        ruleRegistry.add(new org.eclipse.smarthome.automation.Rule(UUID.randomUUID().toString(), triggers, null, null,
                null, null, Visibility.VISIBLE));

    }

    private ArrayList<Action> buildActions(String prefix, String uid, List<EventAction> eventActions) {
        int counter = 1;
        ArrayList<Action> actions = new ArrayList<Action>();
        for (EventAction anAction : eventActions) {

            switch (anAction.action) {
                case "command": {
                    String actionUID = uid + "_" + prefix + "_Action_" + counter;

                    HashMap<String, String> actionConfig = new HashMap<String, String>();
                    actionConfig.put("itemName", anAction.item);
                    actionConfig.put("command", anAction.value);
                    Action action = new Action(actionUID, "ItemPostCommandAction", actionConfig, null);
                    actions.add(action);
                    break;
                }
                case "update": {
                    break;
                }
                case "persist": {
                    switch (anAction.value) {
                        case "ON": {
                            persistenceEnabled = true;
                            break;
                        }
                        case "OFF": {
                            persistenceEnabled = false;
                            break;
                        }
                    }
                    break;
                }
                default: {
                    logger.debug("{} is not a supported action", anAction.action);
                    break;
                }
            }
            counter++;
        }
        return actions;
    }

    private HashSet<Resource> buildResourceSet(Resource r) {
        HashSet<Resource> toBeDeleted = new HashSet<Resource>();
        if (r.isCollection()) {
            for (Resource rc : r.getChildren()) {
                toBeDeleted.addAll(buildResourceSet(rc));
            }
        } else {
            toBeDeleted.add(r);
        }
        return toBeDeleted;
    }

    @Override
    public String getName() {
        return "caldav";
    }

    @Override
    public void store(Item item) {
        store(item, item.getName());
    }

    @Override
    public void store(Item item, String alias) {
        try {
            if (persistenceEnabled) {
                String uid = UUID.randomUUID().toString();
                String url = "/openhab/" + uid + ".ics";

                EventDescription ed = new EventDescription();
                ed.StartActions = new ArrayList<EventAction>();
                EventAction ea = new EventAction();
                ea.action = "command";
                ea.item = item.getName();
                ea.value = item.getState().toString();
                ed.StartActions.add(ea);
                String json = gson.toJson(ed, EventDescription.class);

                String newAlias = alias != null ? alias : item.getName();
                String eventName = newAlias + " " + item.getState().toString();

                java.util.Calendar startDate = new GregorianCalendar();
                java.util.Calendar endDate = new GregorianCalendar();
                endDate.add(java.util.Calendar.MINUTE, 5);
                DateTime start = new DateTime(startDate.getTime());
                DateTime end = new DateTime(endDate.getTime());

                VEvent persistedEvent = new VEvent(start, end, eventName);
                persistedEvent.getProperties().add(new Description(json));
                persistedEvent.getProperties().add(new Uid(uid));

                Calendar calendar = new Calendar();
                calendar.getProperties().add(new ProdId("-//Eclipse Smarthome//CalDav Servlet//EN"));
                calendar.getProperties().add(Version.VERSION_2_0);
                calendar.getProperties().add(CalScale.GREGORIAN);

                calendar.getComponents().add(persistedEvent);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                CalendarOutputter outputter = new CalendarOutputter();
                outputter.output(calendar, bo);
                logger.debug("Adding persisted data to the calendar : '{}'", bo.toString("UTF-8"));

                // create resource with unique ID
                Resource r = new Resource(rf, url);
                r.withMethods(Arrays.asList(METHOD_PROPFIND, METHOD_GET, METHOD_DELETE, METHOD_MOVE, METHOD_PUT,
                        METHOD_REPORT));
                r.withLevel("1");
                r.withPrivilege(Privilege.ALL);
                r.setUniqueId(uid);
                r.withProperty(rf.getProperty("getcontenttype"), "text/calendar");
                r.withProperty(rf.getProperty("calendar-data"), bo.toString("UTF-8"));
                r.setModifiedDate(new Date());

                logger.debug("Storing a resource with persisted date and with URL '{}'", r.getURL());
                rf.addResource(r, false);

            }
        } catch (Exception e) {
            logger.error("An exception occurred while storing an item state change in the calendar : '{}'",
                    e.getMessage());
        }
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return subscribedEventTypes;
    }

    @Override
    public EventFilter getEventFilter() {
        return null;
    }

    @Override
    public void receive(Event event) {
        try {
            logger.debug("Received and Event {}", event.getType());
            logger.debug("Payload {}", event.getPayload());
            if (event instanceof RuleAddedEvent) {
                org.eclipse.smarthome.automation.Rule rule = ((RuleAddedEvent) event).getRule();
                List<Trigger> triggers = rule.getTriggers();
                for (Trigger aTrigger : triggers) {
                    if (aTrigger.getTypeUID().equals("TimerTrigger")) {
                        logger.debug("The Rule with id '{}' contains a TimerTrigger with id '{}'", rule.getUID(),
                                aTrigger.getId());
                        if (!rule.getUID().contains("_Rule_Start") && !rule.getUID().contains("_Rule_End")) {
                            // it is not one of our own rules
                            // TODO: convert cron expression into something that is RRule like for insertion into the
                            // calendar. We could for example take the next xyz times the trigger would fire as separate
                            // events and insert those into calendar

                            String cronExpression = (String) aTrigger.getConfiguration().get("cronExpression");

                            logger.debug("The Timer Trigger has '{}' as cron expression", cronExpression);

                            CronTrigger dummyTrigger = TriggerBuilder.newTrigger()
                                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression)).build();
                            Date date = new Date();
                            for (int i = 0; i < 10; i++) {
                                Date nextDate = dummyTrigger.getFireTimeAfter(date);
                                logger.debug("{} The Trigger will fire on '{}'", i, nextDate);
                                date = nextDate;
                            }
                        }
                    }
                }
            }
            if (event instanceof RuleRemovedEvent) {

            }
            if (event instanceof RuleUpdatedEvent) {

            }
            if (event instanceof RuleStatusInfoEvent) {

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected String stripPath(String url) {
        int i = url.indexOf("/", 8);
        if (i > 0) {
            url = url.substring(i);
        }
        i = url.indexOf("?");
        if (i > 0) {
            url = url.substring(0, i);
        }
        return url;
    }

    protected String stripFullPath(String url) {
        return StringUtils.substringAfterLast(url, "/");
    }

    protected String stripExtension(String url) {
        return StringUtils.substringBeforeLast(url, ".");
    }

    protected String stripServer(String href) {
        if (href.startsWith("http")) {
            return href.substring(href.indexOf("/", 8));
        } else {
            return href;
        }
    }

    protected String percentEncode(String s) {
        s = encodeURL(s, "UTF-8");
        return s;
    }

    protected String encodeURL(String str, String charset) {

        char[] hexDigits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

        StringBuilder buf = new StringBuilder();
        byte[] daten;
        try {
            daten = charset == null ? str.getBytes() : str.getBytes(charset);
        } catch (Exception e) {
            daten = str.getBytes();
        }
        int length = daten.length;
        for (int i = 0; i < length; i++) {
            char c = (char) (daten[i] & 0xFF);
            switch (c) {
                case '-':
                case '_':
                case '.':
                case '*':
                    buf.append(c);
                    break;
                default:
                    if (('0' <= c && c <= '9') || ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z')) {
                        buf.append(c);
                    } else {
                        buf.append('%');
                        buf.append(hexDigits[(c >> 4) & 0x0F]);
                        buf.append(hexDigits[c & 0x0F]);
                    }
            }
        }
        return buf.toString();
    }

    protected String encodeCSV(Collection<String> list, boolean addSpace) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        String res = "";
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            res += it.next();
            if (it.hasNext()) {
                if (addSpace) {
                    res += ", ";
                } else {
                    res += ",";
                }
            }
        }
        return res;
    }

    protected String decodeURL(HttpServletRequest req) {
        String absPath = stripPath(req.getRequestURL().toString());
        String decodedURL;
        String url;

        absPath = absPath.replace("[", "%5B").replace("]", "%5D").replace(" ", "%20");
        try {
            if (absPath.startsWith("/")) {
                decodedURL = new URI("http://eclipse.org" + absPath).getPath();
            } else {
                decodedURL = new URI("http://eclipse.org/" + absPath).getPath().substring(1);
            }
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }

        if (decodedURL.contains("/DavWWWRoot")) {
            url = decodedURL.replace("/DavWWWRoot", "");
        } else {
            url = decodedURL;
        }
        return url;
    }
}
