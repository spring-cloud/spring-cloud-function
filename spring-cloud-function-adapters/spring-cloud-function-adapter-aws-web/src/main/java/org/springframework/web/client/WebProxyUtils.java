package org.springframework.web.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.springframework.cloud.function.adapter.aws.web.FunctionClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class WebProxyUtils {

	public static ProxyMvc buildMvcProxy(Class<?>... componentClasses) {
		if (ObjectUtils.isEmpty(componentClasses)) {
			Class<?> startClass = FunctionClassUtils.getStartClass();
			if (startClass != null) {
				componentClasses = new Class[] {startClass};
			}
			else {
				throw new IllegalStateException("Failed to discover component classes");
			}
		}
		AnnotationConfigWebApplicationContext applpicationContext = new AnnotationConfigWebApplicationContext();
		applpicationContext.register(componentClasses);

		ServletContext servletContext = new ProxyServletContext();
		ServletConfig servletConfig = new ProxyServletConfig(servletContext);

		DispatcherServlet servlet = new DispatcherServlet(applpicationContext);
		try {
			servlet.init(servletConfig);
			return new ProxyMvc(servlet, applpicationContext);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to create MVC Proxy", e);
		}
	}

	private static class ProxyServletConfig implements ServletConfig {

		private final ServletContext servletContext;

		ProxyServletConfig(ServletContext servletContext) {
			this.servletContext = servletContext;
		}

		@Override
		public String getServletName() {
			return "serverless-proxy";
		}

		@Override
		public ServletContext getServletContext() {
			return this.servletContext;
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			return Collections.enumeration(new ArrayList<String>());
		}

		@Override
		public String getInitParameter(String name) {
			return null;
		}
	}
}
