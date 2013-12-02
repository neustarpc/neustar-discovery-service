package biz.neustar.discovery.resolver;

import org.openxri.xml.XRD;

import xdi2.core.xri3.XDI3Segment;

public interface XRI2Resolver {

	public void reset();
	public XRD resolve(XDI3Segment resolveXri) throws Exception;
}
