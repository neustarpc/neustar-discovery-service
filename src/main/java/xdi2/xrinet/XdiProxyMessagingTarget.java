package xdi2.xrinet;

import java.util.ArrayList;
import java.util.List;

import org.openxri.XRI;
import org.openxri.proxy.impl.AbstractProxy;
import org.openxri.resolve.Resolver;
import org.openxri.resolve.ResolverFlags;
import org.openxri.resolve.ResolverState;
import org.openxri.resolve.exception.PartialResolutionException;
import org.openxri.util.PrioritizedList;
import org.openxri.xml.Service;
import org.openxri.xml.Status;
import org.openxri.xml.XRD;

import xdi2.core.ContextNode;
import xdi2.core.Graph;
import xdi2.core.constants.XDIConstants;
import xdi2.core.constants.XDIDictionaryConstants;
import xdi2.core.features.dictionary.Dictionary;
import xdi2.core.features.multiplicity.XdiAttributeSingleton;
import xdi2.core.features.multiplicity.XdiSubGraph;
import xdi2.core.features.remoteroots.RemoteRoots;
import xdi2.core.xri3.impl.XDI3Segment;
import xdi2.core.xri3.impl.XDI3SubSegment;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageResult;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.AbstractContextHandler;
import xdi2.messaging.target.AbstractMessagingTarget;
import xdi2.messaging.target.AddressHandler;
import xdi2.messaging.target.ExecutionContext;

public class XdiProxyMessagingTarget extends AbstractMessagingTarget {

	public static final String XRI_URI = "$uri";
	public static final String STRING_TYPE_XDI = "$xdi$*($v)$!1";
	public static final XDI3Segment XRI_TYPE_XDI = new XDI3Segment(STRING_TYPE_XDI);

	private AbstractProxy proxy;

	@Override
	public void init() throws Exception {

		super.init();
	}

	@Override
	public AddressHandler getAddressHandler(XDI3Segment address) throws Xdi2MessagingException {

		return this.addressHandler;
	}

	public AbstractProxy getProxy() {

		return this.proxy;
	}

	public void setProxy(AbstractProxy proxy) {

		this.proxy = proxy;
	}

	private AddressHandler addressHandler = new AbstractContextHandler() {

		@Override
		public void getContext(XDI3Segment targetAddress, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

			// is this a remote root context XRI?

			XDI3Segment xri;

			if (RemoteRoots.isRemoteRootXri(targetAddress)) {

				xri = RemoteRoots.xriOfRemoteRootXri(targetAddress);
			} else {

				xri = targetAddress;
			}

			// resolve the XRI

			Resolver resolver = XdiProxyMessagingTarget.this.proxy.getResolver();

			ResolverFlags resolverFlags = new ResolverFlags();
			resolverFlags.setNoDefaultT(true);

			ResolverState resolverState = new ResolverState();

			XRD xrd;

			try {

				xrd = resolver.resolveSEPToXRD(new XRI(xri.toString()), "xri://$xdi!($v!1)", null, resolverFlags, resolverState);
			} catch (PartialResolutionException ex) {

				xrd = ex.getPartialXRDS().getFinalXRD();
			}

			if ((! Status.SUCCESS.equals(xrd.getStatusCode())) && (! Status.SEP_NOT_FOUND.equals(xrd.getStatusCode()))) {

				throw new Xdi2MessagingException(xrd.getStatus().getValue(), null, executionContext);
			}

			// extract inumber and URI

			XDI3Segment inumber = new XDI3Segment(xrd.getCanonicalID().getValue());
			List<String> uris = new ArrayList<String> ();

			PrioritizedList selectedServicesPrioritizedList = xrd.getSelectedServices();
			ArrayList<?> selectedServices = selectedServicesPrioritizedList == null ? null : selectedServicesPrioritizedList.getList();

			if (selectedServices != null) {

				for (int i=0; i<selectedServices.size(); i++) {

					Service selectedService = (Service) selectedServices.get(i);

					for (int ii=0; ii<selectedService.getNumURIs(); ii++) {

						uris.add(selectedService.getURIAt(ii).getUriString());
					}
				}
			}

			// prepare result graph

			Graph graph = messageResult.getGraph();

			// add "self" remote root context nodes

			RemoteRoots.setSelfRemoteRootContextNode(graph, XDIConstants.XRI_S_ROOT);

			// add I-Number remote root context nodes

			ContextNode inumberRemoteRootContextNode = RemoteRoots.findRemoteRootContextNode(graph, inumber, true);

			// add URIs

			if (uris.size() > 0) {

				XdiAttributeSingleton uriAttributeSingleton = XdiSubGraph.fromContextNode(inumberRemoteRootContextNode).getAttributeSingleton(new XDI3SubSegment(XRI_URI), true);
				Dictionary.addContextNodeType(uriAttributeSingleton.getContextNode(), XRI_TYPE_XDI);
				uriAttributeSingleton.getContextNode().createLiteral(uris.get(0));
			}

			// add I-Number and original XRI

			ContextNode inumberContextNode = graph.findContextNode(inumber, true);

			if (! xri.equals(inumber)) {

				ContextNode xriContextNode = graph.findContextNode(xri, true);
				xriContextNode.createRelation(XDIDictionaryConstants.XRI_S_IS, inumberContextNode);
			}
		}
	};
}
