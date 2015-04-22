package biz.neustar.discovery.resolver;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.dom4j.DocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.constants.XDIConstants;
import xdi2.core.syntax.XDIAddress;
import biz.neustar.discovery.xrd.XRD;

public class XRI2XNSResolver implements XRI2Resolver {

	protected static final Logger log = LoggerFactory.getLogger(XRI2XNSResolver.class);

	private String authorityPersonalEndpointUrl;
	private String authorityLegalEndpointUrl;

	private Map<XDIAddress, XRD> cache = new HashMap<XDIAddress, XRD> ();

	public void reset() {

		this.cache.clear();
	}

	@Override
	public XRD resolve(XDIAddress resolveXDIAddress) throws IOException, DocumentException {

		String endpointUrl = null;

		// check cache

		XRD cachedXRD = this.cache.get(resolveXDIAddress);

		if (cachedXRD != null) {

			if (log.isDebugEnabled()) log.debug("Getting cached XRD for " + resolveXDIAddress);

			return cachedXRD;
		}

		// construct endpoint URL

		if (log.isDebugEnabled()) log.debug("Resolving XRD for " + resolveXDIAddress);

		if (resolveXDIAddress.getFirstXDIArc().getCs() == null && 
				resolveXDIAddress.getFirstXDIArc().hasXRef() && 
				resolveXDIAddress.getFirstXDIArc().getXRef().hasXDIAddress()) {

			resolveXDIAddress = resolveXDIAddress.getFirstXDIArc().getXRef().getXDIAddress();
		}

		if (XDIConstants.CS_AUTHORITY_PERSONAL.equals(resolveXDIAddress.getFirstXDIArc().getCs())) endpointUrl = this.getAuthorityPersonalEndpointUrl();
		if (XDIConstants.CS_AUTHORITY_LEGAL.equals(resolveXDIAddress.getFirstXDIArc().getCs())) endpointUrl = this.getAuthorityLegalEndpointUrl();

		if (endpointUrl == null) {

			throw new IOException("Don't know how to resolve XRI: " + resolveXDIAddress);
		}

		if (resolveXDIAddress.toString().charAt(0) == '[') endpointUrl += resolveXDIAddress.toString().substring(3);
		else if (resolveXDIAddress.toString().charAt(1) == '!') endpointUrl += resolveXDIAddress.toString().substring(1);
		else endpointUrl += '*' + resolveXDIAddress.toString().substring(1);

		// connect

		if (log.isDebugEnabled()) log.debug("Connecting to " + endpointUrl);

		HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();

		int responseCode = connection.getResponseCode();
		String responseMessage = connection.getResponseMessage();

		// check response code

		if (responseCode >= 300) {

			throw new IOException("HTTP code " + responseCode + " received: " + responseMessage, null);
		}

		// read the XRD

		XRD xrd = XRD.read(connection.getInputStream());

		connection.disconnect();

		// cache

		this.cache.put(resolveXDIAddress, xrd);

		// done

		return xrd;
	}

	/*
	 * Getters and setters
	 */

	public String getAuthorityPersonalEndpointUrl() {

		return this.authorityPersonalEndpointUrl;
	}

	public void setAuthorityPersonalEndpointUrl(String authorityPersonalEndpointUrl) {

		this.authorityPersonalEndpointUrl = authorityPersonalEndpointUrl;
	}

	public String getAuthorityLegalEndpointUrl() {

		return this.authorityLegalEndpointUrl;
	}

	public void setAuthorityLegalEndpointUrl(String authorityLegalEndpointUrl) {

		this.authorityLegalEndpointUrl = authorityLegalEndpointUrl;
	}
}
