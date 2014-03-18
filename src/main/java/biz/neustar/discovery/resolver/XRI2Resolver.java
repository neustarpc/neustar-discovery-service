package biz.neustar.discovery.resolver;

import xdi2.core.xri3.XDI3Segment;
import biz.neustar.discovery.xrd.XRD;

public interface XRI2Resolver {

	public void reset();
	public XRD resolve(XDI3Segment resolveXri) throws Exception;
}
