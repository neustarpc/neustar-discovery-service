package xdi2.xrinet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openxri.proxy.Proxy;

import xdi2.messaging.target.MessagingTarget;
import xdi2.server.EndpointServlet;
import xdi2.server.RequestInfo;
import xdi2.server.interceptor.AbstractEndpointServletInterceptor;

public class XriProxyEndpointServletInterceptor extends AbstractEndpointServletInterceptor {

	private Proxy proxy;

	@Override
	public boolean processGetRequest(EndpointServlet endpointServlet, HttpServletRequest request, HttpServletResponse response, RequestInfo requestInfo, MessagingTarget messagingTarget) throws ServletException, IOException {

		this.getProxy().process(request, response);

		return true;
	}

	public Proxy getProxy() {

		return this.proxy;
	}

	public void setProxy(Proxy proxy) {

		this.proxy = proxy;
	}
}
