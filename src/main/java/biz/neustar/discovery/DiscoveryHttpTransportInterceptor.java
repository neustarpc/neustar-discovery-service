package biz.neustar.discovery;

import java.io.IOException;

import org.openxri.proxy.Proxy;

import xdi2.messaging.target.MessagingTarget;
import xdi2.server.exceptions.Xdi2ServerException;
import xdi2.server.interceptor.AbstractHttpTransportInterceptor;
import xdi2.server.servlet.ServletHttpRequest;
import xdi2.server.servlet.ServletHttpResponse;
import xdi2.server.transport.HttpRequest;
import xdi2.server.transport.HttpResponse;
import xdi2.server.transport.HttpTransport;

public class DiscoveryHttpTransportInterceptor extends AbstractHttpTransportInterceptor {

	private Proxy proxy;

	@Override
	public boolean processGetRequest(HttpTransport httpTransport, HttpRequest request, HttpResponse response, MessagingTarget messagingTarget) throws Xdi2ServerException, IOException {

		this.getProxy().process(((ServletHttpRequest) request).getHttpServletRequest(), ((ServletHttpResponse) response).getHttpServletResponse());

		return true;
	}

	public Proxy getProxy() {

		return this.proxy;
	}

	public void setProxy(Proxy proxy) {

		this.proxy = proxy;
	}
}
