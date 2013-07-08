package xdi2.xrinet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.openxri.util.PrioritizedList;
import org.openxri.xml.SEPType;
import org.openxri.xml.SEPUri;
import org.openxri.xml.Service;
import org.openxri.xml.Status;
import org.openxri.xml.XRD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.features.equivalence.Equivalence;
import xdi2.core.features.nodetypes.XdiAbstractInstanceUnordered;
import xdi2.core.features.nodetypes.XdiAttributeClass;
import xdi2.core.features.nodetypes.XdiAttributeInstance;
import xdi2.core.features.nodetypes.XdiAttributeSingleton;
import xdi2.core.features.nodetypes.XdiLocalRoot;
import xdi2.core.features.nodetypes.XdiPeerRoot;
import xdi2.core.util.XRI2Util;
import xdi2.core.xri3.XDI3Segment;
import xdi2.core.xri3.XDI3SubSegment;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageResult;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.ExecutionContext;
import xdi2.messaging.target.contributor.AbstractContributor;
import xdi2.messaging.target.contributor.ContributorXri;

@ContributorXri(addresses={"{()}"})
public class XrinetContributor extends AbstractContributor {

	private static final Logger log = LoggerFactory.getLogger(XrinetContributor.class);

	public static final XDI3Segment XRI_SELF = XDI3Segment.create("[=]");
	public static final XDI3SubSegment XRI_URI = XDI3SubSegment.create("$uri");

	private AbstractProxy proxy;

	public AbstractProxy getProxy() {

		return this.proxy;
	}

	public void setProxy(AbstractProxy proxy) {

		this.proxy = proxy;
	}

	@Override
	public boolean getContext(XDI3Segment[] contributorXris, XDI3Segment contributorsXri, XDI3Segment contextNodeXri, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		// prepare XRI

		XDI3Segment requestedXdiPeerRootXri = contributorXris[contributorXris.length - 1];

		XDI3Segment resolveXri = XdiPeerRoot.getXriOfPeerRootArcXri(requestedXdiPeerRootXri.getFirstSubSegment());
		if (resolveXri == null) return false;

		String canonicalId = XRI2Util.cloudNumberToCanonicalId(resolveXri);
		if (canonicalId != null) resolveXri = XDI3Segment.create(canonicalId);

		// resolve the XRI

		if (log.isDebugEnabled()) log.debug("Resolving " + resolveXri);

		Resolver resolver = XrinetContributor.this.proxy.getResolver();

		ResolverFlags resolverFlags = new ResolverFlags();
		ResolverState resolverState = new ResolverState();

		XRD xrd;

		try {

			xrd = resolver.resolveSEPToXRD(new XRI(resolveXri.toString()), null, null, resolverFlags, resolverState);
		} catch (PartialResolutionException ex) {

			xrd = ex.getPartialXRDS().getFinalXRD();
		}

		if (log.isDebugEnabled()) log.debug("XRD Status: " + xrd.getStatus().getCode());

		if ((! Status.SUCCESS.equals(xrd.getStatusCode())) && (! Status.SEP_NOT_FOUND.equals(xrd.getStatusCode()))) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 Problem: " + xrd.getStatusCode() + " (" + xrd.getStatus().getValue() + ")", null, executionContext);
		}

		// extract cloud number

		XDI3Segment cloudNumber = XRI2Util.canonicalIdToCloudNumber(xrd.getCanonicalID().getValue());

		if (log.isDebugEnabled()) log.debug("Cloud Number: " + cloudNumber);

		// extract URIs

		Map<String, List<String>> uriMap = new HashMap<String, List<String>> ();

		for (int i=0; i<xrd.getNumServices(); i++) {

			Service service = xrd.getServiceAt(i);
			if (service.getNumTypes() == 0) continue;

			for (int ii=0; ii<service.getNumTypes(); ii++) {

				SEPType type = service.getTypeAt(ii);
				if (type == null || type.getType() == null || type.getType().trim().isEmpty()) continue;

				List<String> uriList = uriMap.get(type.getType());

				if (uriList == null) {

					uriList = new ArrayList<String> ();
					uriMap.put(type.getType(), uriList);
				}

				List<?> uris = service.getURIs();
				Collections.sort(uris, new Comparator<Object> () {

					@SuppressWarnings("null")
					@Override
					public int compare(Object uri1, Object uri2) {

						Integer priority1 = ((SEPUri) uri1).getPriority();
						Integer priority2 = ((SEPUri) uri2).getPriority();

						if (priority1 == null && priority2 == null) return 0;
						if (priority1 == null && priority2 != null) return 1;
						if (priority1 != null && priority2 == null) return -1;

						if (priority1.intValue() == priority2.intValue()) return 0;

						return priority1.intValue() > priority2.intValue() ? 1 : -1;
					}
				});

				for (int iii = 0; iii<uris.size(); iii++) {

					SEPUri uri = (SEPUri) uris.get(iii);
					if (uri == null || uri.getUriString() == null || uri.getUriString().trim().isEmpty()) continue;

					uriList.add(uri.getUriString());
				}
			}
		}

		if (log.isDebugEnabled()) log.debug("URIs: " + uriMap);

		// extract default URI

		PrioritizedList defaultUriPrioritizedList = xrd.getSelectedServices();
		ArrayList<?> defaultUriList = defaultUriPrioritizedList == null ? null : defaultUriPrioritizedList.getList();
		Service defaultUriService = defaultUriList == null || defaultUriList.size() < 1 ? null : (Service) defaultUriList.get(0);
		String defaultUri = defaultUriService == null ? null : defaultUriService.getURIAt(0).getUriString();

		if (log.isDebugEnabled()) log.debug("Default URI: " + defaultUri);

		// prepare result graph

		XdiPeerRoot requestedXdiPeerRoot = XdiPeerRoot.fromContextNode(messageResult.getGraph().setDeepContextNode(requestedXdiPeerRootXri));

		// add original peer root

		if (! cloudNumber.equals(requestedXdiPeerRoot.getXriOfPeerRoot())) {

			XdiPeerRoot cloudNumberXdiPeerRoot = XdiLocalRoot.findLocalRoot(messageResult.getGraph()).findPeerRoot(cloudNumber, true);

			Equivalence.setReferenceContextNode(requestedXdiPeerRoot.getContextNode(), cloudNumberXdiPeerRoot.getContextNode());

			return false;
		}

		// add all URIs for all types

		XdiAttributeClass uriXdiAttributeClass = requestedXdiPeerRoot.getXdiAttributeClass(XRI_URI, true);

		for (Entry<String, List<String>> uriMapEntry : uriMap.entrySet()) {

			String type = uriMapEntry.getKey();
			List<String> uriList = uriMapEntry.getValue();

			XDI3SubSegment typeXdiEntitySingletonArcXri = XRI2Util.typeToXdiEntitySingletonArcXri(type);

			for (String uri : uriList) {

				XDI3SubSegment uriXdiInstanceUnorderedArcXri = XdiAbstractInstanceUnordered.createArcXriFromHash(uri, false);

				XdiAttributeInstance uriXdiAttributeInstance = uriXdiAttributeClass.setXdiInstanceUnordered(uriXdiInstanceUnorderedArcXri);
				uriXdiAttributeInstance.getXdiValue(true).getContextNode().setLiteral(uri);

				XdiAttributeClass typeXdiAttributeClass = requestedXdiPeerRoot.getXdiEntitySingleton(typeXdiEntitySingletonArcXri, true).getXdiAttributeClass(XRI_URI, true);
				XdiAttributeInstance typeXdiAttributeInstance = typeXdiAttributeClass.setXdiInstanceOrdered(-1);
				Equivalence.setReferenceContextNode(typeXdiAttributeInstance.getContextNode(), uriXdiAttributeInstance.getContextNode());
			}

			// add default URI for this type

			if (uriList.size() > 0) {

				String defaultUriForType = uriList.get(0);

				XDI3SubSegment defaultUriForTypeXdiInstanceUnorderedArcXri = XdiAbstractInstanceUnordered.createArcXriFromHash(defaultUriForType, false);

				XdiAttributeInstance defaultUriForTypeXdiAttributeInstance = uriXdiAttributeClass.setXdiInstanceUnordered(defaultUriForTypeXdiInstanceUnorderedArcXri);
				XdiAttributeSingleton defaultUriForTypeXdiAttributeSingleton = requestedXdiPeerRoot.getXdiEntitySingleton(typeXdiEntitySingletonArcXri, true).getXdiAttributeSingleton(XRI_URI, true);
				Equivalence.setReferenceContextNode(defaultUriForTypeXdiAttributeSingleton.getContextNode(), defaultUriForTypeXdiAttributeInstance.getContextNode());
			}
		}

		// add default URI

		if (defaultUri != null) {

			XDI3SubSegment defaultUriXdiInstanceUnorderedArcXri = XdiAbstractInstanceUnordered.createArcXriFromHash(defaultUri, false);

			XdiAttributeInstance defaultUriXdiAttributeInstance = uriXdiAttributeClass.setXdiInstanceUnordered(defaultUriXdiInstanceUnorderedArcXri);
			XdiAttributeSingleton defaultUriXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(XRI_URI, true);
			Equivalence.setReferenceContextNode(defaultUriXdiAttributeSingleton.getContextNode(), defaultUriXdiAttributeInstance.getContextNode());
		}

		// done

		return false;
	}
}