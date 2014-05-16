package biz.neustar.discovery.xrd;

public class XRDUri {

	private String uri;
	private Integer priority;

	public XRDUri(String uri, Integer priority) {

		this.uri = uri;
		this.priority = priority;
	}

	public String getUri() {

		return this.uri;
	}

	public Integer getPriority() {

		return this.priority;
	}
}
