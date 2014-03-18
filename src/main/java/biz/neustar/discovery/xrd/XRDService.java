package biz.neustar.discovery.xrd;

import java.util.List;

public class XRDService {

	private List<XRDType> types;
	private List<XRDUri> uris;

	public XRDService(List<XRDType> types, List<XRDUri> uris) {

		this.types = types;
		this.uris = uris;
	}

	public List<XRDType> getTypes() {

		return this.types;
	}

	public List<XRDUri> getUris() {

		return this.uris;
	}
}
