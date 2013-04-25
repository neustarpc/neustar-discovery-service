package xdi2.xrinet;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openxri.XRI;
import org.openxri.proxy.impl.AbstractProxy;
import org.openxri.resolve.Resolver;
import org.openxri.resolve.ResolverFlags;
import org.openxri.resolve.ResolverState;
import org.openxri.resolve.exception.PartialResolutionException;
import org.openxri.xml.SEPType;
import org.openxri.xml.SEPUri;
import org.openxri.xml.Service;
import org.openxri.xml.Status;
import org.openxri.xml.XRD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.ContextNode;
import xdi2.core.Graph;
import xdi2.core.constants.XDIConstants;
import xdi2.core.features.equivalence.Equivalence;
import xdi2.core.features.nodetypes.XdiAttributeClass;
import xdi2.core.features.nodetypes.XdiAttributeInstance;
import xdi2.core.features.roots.XdiLocalRoot;
import xdi2.core.features.roots.XdiPeerRoot;
import xdi2.core.util.XDI3Util;
import xdi2.core.xri3.XDI3Segment;
import xdi2.core.xri3.XDI3SubSegment;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageResult;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.AbstractContextHandler;
import xdi2.messaging.target.AbstractMessagingTarget;
import xdi2.messaging.target.AddressHandler;
import xdi2.messaging.target.ExecutionContext;

public class XdiProxyMessagingTarget extends AbstractMessagingTarget {

	private static final Logger log = LoggerFactory.getLogger(XdiProxyMessagingTarget.class);

	public static final XDI3SubSegment XRI_URI = XDI3SubSegment.create("$uri");
	public static final String STRING_TYPE_XDI = "$xdi";
	public static final XDI3Segment XRI_TYPE_XDI = XDI3Segment.create(STRING_TYPE_XDI);

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

			// prepare XRI

			XDI3Segment xri;

			if (XdiPeerRoot.isPeerRootArcXri(targetAddress.getLastSubSegment())) {

				xri = XdiPeerRoot.getXriOfPeerRootArcXri(targetAddress.getLastSubSegment());
			} else {

				xri = targetAddress;
			}

			// resolve the XRI

			Resolver resolver = XdiProxyMessagingTarget.this.proxy.getResolver();

			ResolverFlags resolverFlags = new ResolverFlags();
			ResolverState resolverState = new ResolverState();

			XRD xrd;

			try {

				xrd = resolver.resolveSEPToXRD(new XRI(xri.toString()), null, null, resolverFlags, resolverState);
			} catch (PartialResolutionException ex) {

				xrd = ex.getPartialXRDS().getFinalXRD();
			}

			if ((! Status.SUCCESS.equals(xrd.getStatusCode())) && (! Status.SEP_NOT_FOUND.equals(xrd.getStatusCode()))) {

				throw new Xdi2MessagingException(xrd.getStatus().getValue(), null, executionContext);
			}

			// extract cloudnumber

			XDI3Segment cloudnumber = XDI3Util.canonicalIdToCloudnumber(xrd.getCanonicalID().getValue());

			// extract URIS

			Map<String, List<String>> uriMap = new HashMap<String, List<String>> ();

			for (int i=0; i<xrd.getNumServices(); i++) {

				Service service = xrd.getServiceAt(i);
				if (service.getNumTypes() == 0) continue;

				for (int ii=0; ii<service.getNumTypes(); ii++) {

					SEPType type = service.getTypeAt(ii);

					List<String> uriList = uriMap.get(type.getType());

					if (uriList == null) {

						uriList = new ArrayList<String> ();
						uriMap.put(type.getType(), uriList);
					}

					for (int iii = 0; iii<service.getNumURIs(); iii++) {

						SEPUri uri = service.getURIAt(iii);

						uriList.add(uri.getUriString());
					}
				}
			}

			// prepare result graph

			Graph graph = messageResult.getGraph();

			// add "self" peer root

			XdiLocalRoot.findLocalRoot(graph).setSelfPeerRoot(XDIConstants.XRI_S_ROOT);

			// add cloudnumber peer root

			XdiLocalRoot.findLocalRoot(graph).findPeerRoot(cloudnumber, true);

			// add URIs

			XdiAttributeClass uriXdiAttributeClass = XdiLocalRoot.findLocalRoot(graph).getXdiAttributeClass(XRI_URI, true);

			for (Entry<String, List<String>> uriMapEntry : uriMap.entrySet()) {

				String type = uriMapEntry.getKey();
				List<String> uriList = uriMapEntry.getValue();

				for (String uri : uriList) {

					XdiAttributeInstance uriXdiAttributeInstance = uriXdiAttributeClass.setXdiInstanceUnordered(null);
					uriXdiAttributeInstance.getXdiValue(true).getContextNode().setLiteral(uri);

					XdiAttributeClass typeXdiAttributeClass = XdiLocalRoot.findLocalRoot(graph).getXdiEntitySingleton(typeToSubSegment(type), true).getXdiAttributeClass(XRI_URI, true);
					XdiAttributeInstance typeXdiAttributeInstance = typeXdiAttributeClass.setXdiInstanceOrdered(-1);
					Equivalence.setReferenceContextNode(typeXdiAttributeInstance.getContextNode(), uriXdiAttributeInstance.getContextNode());
				}
			}

			// add cloudnumber and original XRI

			ContextNode cloudnumberContextNode = graph.setDeepContextNode(cloudnumber);

			if (! xri.equals(cloudnumber)) {

				ContextNode xriContextNode = graph.setDeepContextNode(xri);
				Equivalence.setReferenceContextNode(xriContextNode, cloudnumberContextNode);
			}
		}
	};

	private static XDI3SubSegment typeToSubSegment(String type) {

		log.info(type);
		
		try {

			return XDI3SubSegment.create(type);
		} catch (Exception ex) {

			try {

				return XDI3SubSegment.create("(" + URLEncoder.encode(type, "UTF-8") + ")");
			} catch (UnsupportedEncodingException ex2) {

				return null;
			}
		}
	}
}
