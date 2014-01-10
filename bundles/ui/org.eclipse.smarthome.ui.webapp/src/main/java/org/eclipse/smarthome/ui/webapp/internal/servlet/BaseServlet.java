/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschränkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.ui.webapp.internal.servlet;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.eclipse.smarthome.core.items.ItemRegistry;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;


/**
 * This is the base servlet class for other servlet in the WebApp UI. 
 * 
 * @author Thomas.Eichstaedt-Engelen
 */
public abstract class BaseServlet implements Servlet {
	
	/** the root path of this web application */
	public static final String WEBAPP_ALIAS = "/";
		
	protected HttpService httpService;
	protected ItemRegistry itemRegistry;

	
	public void setItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = itemRegistry;
	}

	public void unsetItemRegistry(ItemRegistry itemRegistry) {
		this.itemRegistry = null;
	}

	public void setHttpService(HttpService httpService) {
		this.httpService = httpService;
	}

	public void unsetHttpService(HttpService httpService) {
		this.httpService = null;
	}

	/**
	 * Creates a {@link HttpContext}
	 * @return a {@link HttpContext}
	 */
	protected HttpContext createHttpContext() {
		HttpContext defaultHttpContext = httpService.createDefaultHttpContext();
		return defaultHttpContext;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void init(ServletConfig config) throws ServletException {
	}

	/**
	 * {@inheritDoc}
	 */
	public ServletConfig getServletConfig() {
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getServletInfo() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void destroy() {
	}

}
