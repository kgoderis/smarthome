package org.eclipse.smarthome.core.scheduler.httpcontext.internal;

import org.eclipse.smarthome.core.scheduler.httpcontext.HttpContextService;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpContextProvider implements HttpContextService {

    private final Logger logger = LoggerFactory.getLogger(HttpContextProvider.class);

    private HttpService httpService;
    static HttpContext httpContext = null;

    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
        logger.debug("Setting the runtime wide DefaultHttpContext");
        httpContext = httpService.createDefaultHttpContext();
    }

    public void unsetHttpService(HttpService httpService) {
        this.httpService = null;
        HttpContextProvider.httpContext = null;
    }

    @Override
    public HttpContext getDefaultContext() {
        return httpContext;
    }

}
