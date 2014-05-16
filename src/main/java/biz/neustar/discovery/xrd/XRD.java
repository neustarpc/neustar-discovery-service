package biz.neustar.discovery.xrd;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;

public class XRD {

	public static final String STATUS_SUCCESS = "100";
	public static final String STATUS_QUERY_NOT_FOUND = "222";
	public static final String STATUS_SEP_NOT_FOUND = "241";

	public static final QName QNAME_XRDS = QName.get("XRDS", Namespace.get("xri://$xrds"));
	public static final QName QNAME_XRD = QName.get("XRD", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_STATUS = QName.get("Status", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_STATUS_CODE = QName.get("code");
	public static final QName QNAME_CANONICALID = QName.get("CanonicalID", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_SERVICE = QName.get("Service", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_TYPE = QName.get("Type", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_URI = QName.get("URI", Namespace.get("xri://$xrd*($v*2.0)"));
	public static final QName QNAME_URI_PRIORITY = QName.get("priority");
	public static final QName QNAME_XDI = QName.get("xdi", Namespace.get("xri://$xdi"));

	private Document document;
	private String status;
	private String canonicalId;
	private String extension;
	private List<XRDService> services;
	private XRDService defaultService;

	private XRD(Document document, String status, String canonicalId, String extension, List<XRDService> services, XRDService defaultService) {

		this.document = document;
		this.status = status;
		this.canonicalId = canonicalId;
		this.extension = extension;
		this.services = services;
		this.defaultService = defaultService;
	}

	@SuppressWarnings("unchecked")
	public static XRD read(InputStream inputStream) throws DocumentException {

		String status;
		String canonicalId;
		String extension;
		List<XRDService> services;
		XRDService defaultService;

		// read XRDS/XRD

		Document document = new SAXReader().read(inputStream);

		Element xrdsElement = document.getRootElement();
		if (! QNAME_XRDS.equals(xrdsElement.getQName())) throw new DocumentException("Invalid element: " + xrdsElement);

		Element xrdElement = xrdsElement.element(QNAME_XRD);
		if (xrdElement == null) throw new DocumentException("Cannot find element: " + QNAME_XRD);

		// read status

		Element statusElement = xrdElement.element(QNAME_STATUS);
		if (statusElement == null) throw new DocumentException("Cannot find element: " + QNAME_STATUS);

		Attribute statusCodeAttribute = statusElement.attribute(QNAME_STATUS_CODE);
		if (statusCodeAttribute == null) throw new DocumentException("Cannot find attribute: " + QNAME_STATUS_CODE);

		status = statusCodeAttribute.getValue();

		// read canonicalId

		Element canonicalIdElement = xrdElement.element(QNAME_CANONICALID);

		if (canonicalIdElement != null) {

			canonicalId = canonicalIdElement.getTextTrim();
		} else {

			canonicalId = null;
		}

		// read extension

		Element extensionElement = xrdElement.element(QNAME_XDI);

		if (extensionElement != null) {

			extension = extensionElement.getText();
		} else {

			extension = null;
		}

		// read services

		List<Element> serviceElements = xrdElement.elements(QNAME_SERVICE);
		services = new ArrayList<XRDService> ();

		for (Element serviceElement : serviceElements) {

			List<Element> typeElements = serviceElement.elements(QNAME_TYPE);
			List<XRDType> types = new ArrayList<XRDType> ();

			List<Element> uriElements = serviceElement.elements(QNAME_URI);
			List<XRDUri> uris = new ArrayList<XRDUri> ();

			for (Element typeElement : typeElements) {

				String type = typeElement.getTextTrim();

				types.add(new XRDType(type));
			}

			for (Element uriElement : uriElements) {

				Attribute priorityAttribute = uriElement.attribute(QNAME_URI_PRIORITY);

				String uri = uriElement.getTextTrim();
				Integer priority = priorityAttribute == null ? Integer.valueOf(10) : Integer.valueOf(priorityAttribute.getValue());

				uris.add(new XRDUri(uri, priority));
			}

			services.add(new XRDService(types, uris));
		}

		// read defaultService

		defaultService = null;

		// done

		return new XRD(document, status, canonicalId, extension, services, defaultService);
	}

	/*
	 * Object methods
	 */

	@Override
	public String toString() {

		return this.document.asXML();
	}

	/*
	 * Getters and setters
	 */

	public String getStatus() {

		return this.status;
	}

	public String getCanonicalId() {

		return this.canonicalId;
	}

	public String getExtension() {

		return this.extension;
	}

	public List<XRDService> getServices() {

		return this.services;
	}

	public XRDService getDefaultService() {

		return this.defaultService;
	}
}
