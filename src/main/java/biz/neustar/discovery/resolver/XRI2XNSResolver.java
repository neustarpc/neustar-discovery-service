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
import xdi2.core.xri3.XDI3Segment;
import biz.neustar.discovery.xrd.XRD;

public class XRI2XNSResolver implements XRI2Resolver {

	protected static final Logger log = LoggerFactory.getLogger(XRI2XNSResolver.class);

	private String authorityPersonalEndpointUrl;
	private String authorityLegalEndpointUrl;

	private Map<XDI3Segment, XRD> cache = new HashMap<XDI3Segment, XRD> ();

	public void reset() {

		this.cache.clear();
	}

	@Override
	public XRD resolve(XDI3Segment resolveXri) throws IOException, DocumentException {

		String endpointUrl = null;

		// check cache

		XRD cachedXRD = this.cache.get(resolveXri);

		if (cachedXRD != null) {

			if (log.isDebugEnabled()) log.debug("Getting cached XRD for " + resolveXri);

			return cachedXRD;
		}

		// construct endpoint URL

		if (log.isDebugEnabled()) log.debug("Resolving XRD for " + resolveXri);

		if (resolveXri.getFirstSubSegment().getCs() == null && 
				resolveXri.getFirstSubSegment().hasXRef() && 
				resolveXri.getFirstSubSegment().getXRef().hasSegment()) {

			resolveXri = resolveXri.getFirstSubSegment().getXRef().getSegment();
		}

		if (XDIConstants.CS_AUTHORITY_PERSONAL.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getAuthorityPersonalEndpointUrl();
		if (XDIConstants.CS_AUTHORITY_LEGAL.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getAuthorityLegalEndpointUrl();

		if (endpointUrl == null) {

			throw new IOException("Don't know how to resolve XRI: " + resolveXri);
		}

		if (resolveXri.toString().charAt(0) == '[') endpointUrl += resolveXri.toString().substring(3);
		else if (resolveXri.toString().charAt(1) == '!') endpointUrl += resolveXri.toString().substring(1);
		else endpointUrl += '*' + resolveXri.toString().substring(1);

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

		this.cache.put(resolveXri, xrd);

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
