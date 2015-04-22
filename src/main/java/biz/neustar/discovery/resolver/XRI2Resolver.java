package biz.neustar.discovery.resolver;

import xdi2.core.syntax.XDIAddress;
import biz.neustar.discovery.xrd.XRD;

public interface XRI2Resolver {

	public void reset();
	public XRD resolve(XDIAddress XDIaddress) throws Exception;
}
