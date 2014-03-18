package biz.neustar.discovery.xrd;

import java.io.InputStream;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

public class XRD {

	public static final String STATUS_SUCCESS = "100";
	public static final String STATUS_SEP_NOT_FOUND = "241";

	private String canonicalID;
	private String status;
	private String extension;
	private List<XRDService> services;
	private XRDService defaultService;

	private XRD() {

	}

	public static XRD read(InputStream inputStream) throws DocumentException {

		Document document = new SAXReader().read(inputStream);

		return null;
	}

	public String getCanonicalID() {

		return this.canonicalID;
	}

	public String getStatus() {

		return this.status;
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
