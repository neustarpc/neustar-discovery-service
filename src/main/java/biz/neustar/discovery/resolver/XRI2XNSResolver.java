package biz.neustar.discovery.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;

import org.apache.xerces.parsers.DOMParser;
import org.openxri.util.DOMUtils;
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

	@Override
	public XRD resolve(XDI3Segment resolveXri) throws MalformedURLException, IOException, SAXException, URISyntaxException, ParseException {

		String endpointUrl = null;

		// construct endpoint URL

		if (XDIConstants.CS_EQUALS.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getEqualEndpointUrl();
		if (XDIConstants.CS_AT.equals(resolveXri.getFirstSubSegment().getCs())) endpointUrl = this.getEqualEndpointUrl();

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

		XRDS xrds = new XRDS();
		XRD xrd;

		InputStream inputStream = connection.getInputStream();
		DOMParser domParser = DOMUtils.getDOMParser();
		domParser.parse(new InputSource(inputStream));
		Document domDoc = domParser.getDocument();
		Element oElement = domDoc.getDocumentElement();

		xrds.fromDOM(oElement, false);
		xrd = xrds.getFinalXRD();

		connection.disconnect();

		// done

		if (log.isDebugEnabled()) log.debug("XRD: " + xrd);

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
