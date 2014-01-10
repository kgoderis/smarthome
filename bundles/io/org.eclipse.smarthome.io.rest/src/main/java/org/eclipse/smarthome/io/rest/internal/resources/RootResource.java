/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschränkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.io.rest.internal.resources;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.smarthome.io.rest.MediaTypeHelper;
import org.eclipse.smarthome.io.rest.RESTApplication;
import org.eclipse.smarthome.io.rest.RESTResource;
import org.eclipse.smarthome.io.rest.internal.resources.beans.RootBean;

import com.sun.jersey.api.json.JSONWithPadding;

/**
 * <p>This class acts as an entry point / root resource for the REST API.</p>
 * <p>In good HATEOAS manner, it provides links to other offered resources.</p>
 * 
 * <p>The result is returned as XML or JSON</p>
 * 
 * <p>This resource is registered with the Jersey servlet.</p>
 *
 * @author Kai Kreuzer - Initial contribution and API
 */
@Path("/")
public class RootResource {

    @Context UriInfo uriInfo;

    @GET 
    @Produces( { MediaType.WILDCARD })
    public Response getRoot(
    		@Context HttpHeaders headers,
    		@QueryParam("type") String type, 
    		@QueryParam("jsoncallback") @DefaultValue("callback") String callback) {
    	String responseType = MediaTypeHelper.getResponseMediaType(headers.getAcceptableMediaTypes(), type);
    	if(responseType!=null) {
	    	Object responseObject = responseType.equals(MediaTypeHelper.APPLICATION_X_JAVASCRIPT) ?
	    			new JSONWithPadding(getRootBean(), callback) : getRootBean();
	    	return Response.ok(responseObject, responseType).build();
    	} else {
			return Response.notAcceptable(null).build();
    	}
    }

	private RootBean getRootBean() {
		RootBean bean = new RootBean();
	    
		for(RESTResource resource : RESTApplication.getRestResources()) {
			String path = resource.getClass().getAnnotation(Path.class).value();
			bean.links.put(path, uriInfo.getBaseUriBuilder().path(path).build().toASCIIString());
		}
	    
	    return bean;
	}

}
