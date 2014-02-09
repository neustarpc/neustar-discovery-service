package biz.neustar.discovery.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.apache.xerces.parsers.DOMParser;
import org.openxri.XRI;
import org.openxri.resolve.Resolver;
import org.openxri.resolve.ResolverFlags;
import org.openxri.resolve.ResolverState;
import org.openxri.resolve.exception.PartialResolutionException;
import org.openxri.util.DOMUtils;
import org.openxri.xml.Status;
import org.openxri.xml.XRD;
import org.openxri.xml.XRDS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import xdi2.core.constants.XDIConstants;
import xdi2.core.xri3.XDI3Segment;

public class XRI2XNSResolver implements XRI2Resolver {

	protected static final Logger log = LoggerFactory.getLogger(XRI2XNSResolver.class);

	private String equalEndpointUrl;
	private String atEndpointUrl;

	private Map<XDI3Segment, XRD> cache = new HashMap<XDI3Segment, XRD> ();

	public void reset() {

		this.cache.clear();
	}

	@Override
	public XRD resolve(XDI3Segment resolveXri) throws MalformedURLException, IOException, SAXException, URISyntaxException, ParseException, PartialResolutionException {

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

		if (XDIConstants.CS_EQUALS.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getEqualEndpointUrl();
		if (XDIConstants.CS_AT.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getAtEndpointUrl();

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

		// read the response

		InputStream inputStream = connection.getInputStream();
		DOMParser domParser = DOMUtils.getDOMParser();
		domParser.parse(new InputSource(inputStream));
		Document domDoc = domParser.getDocument();
		Element oElement = domDoc.getDocumentElement();

		connection.disconnect();

		// construct XRD

		Resolver resolver = new Resolver();

		XRDS xrds = new XRDS();
		XRD xrd;

		xrds.fromDOM(oElement, false);
		xrd = xrds.getFinalXRD();
		if (xrd == null) return null;

		if (! Status.SUCCESS.equals(xrd.getStatusCode())) throw new IOException("" + xrd.getStatus().getCode() + " (" + xrd.getStatus().getText() + ")");

		try {

			resolver.selectServiceFromXRD(new XRDS(), xrd, new XRI("="), null, null, new ResolverFlags(), new ResolverState());
		} catch (PartialResolutionException ex) { 

			if (log.isDebugEnabled()) log.debug("No default SEP for " + resolveXri);
		}

		// cache

		this.cache.put(resolveXri, xrd);

		// done

		return xrd;
	}

	/*
	 * Getters and setters
	 */

	public String getEqualEndpointUrl() {

		return this.equalEndpointUrl;
	}

	public void setEqualEndpointUrl(String equalEndpointUrl) {

		this.equalEndpointUrl = equalEndpointUrl;
	}

	public String getAtEndpointUrl() {

		return this.atEndpointUrl;
	}

	public void setAtEndpointUrl(String atEndpointUrl) {

		this.atEndpointUrl = atEndpointUrl;
	}
}
