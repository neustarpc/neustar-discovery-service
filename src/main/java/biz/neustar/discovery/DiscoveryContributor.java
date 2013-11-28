package biz.neustar.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openxri.util.PrioritizedList;
import org.openxri.xml.CanonicalID;
import org.openxri.xml.SEPType;
import org.openxri.xml.SEPUri;
import org.openxri.xml.Service;
import org.openxri.xml.Status;
import org.openxri.xml.XRD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.features.equivalence.Equivalence;
import xdi2.core.features.nodetypes.XdiAbstractMemberUnordered;
import xdi2.core.features.nodetypes.XdiAttributeCollection;
import xdi2.core.features.nodetypes.XdiAttributeMember;
import xdi2.core.features.nodetypes.XdiAttributeSingleton;
import xdi2.core.features.nodetypes.XdiLocalRoot;
import xdi2.core.features.nodetypes.XdiPeerRoot;
import xdi2.core.util.XRI2Util;
import xdi2.core.xri3.CloudNumber;
import xdi2.core.xri3.XDI3Segment;
import xdi2.core.xri3.XDI3SubSegment;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageResult;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.ExecutionContext;
import xdi2.messaging.target.contributor.AbstractContributor;
import xdi2.messaging.target.contributor.ContributorXri;
import biz.neustar.discovery.resolver.XRI2Resolver;
import biz.neustar.discovery.resolver.XRI2XNSResolver;

@ContributorXri(addresses={"{()}"})
public class DiscoveryContributor extends AbstractContributor {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryContributor.class);

	public static final XDI3Segment XRI_SELF = XDI3Segment.create("[=]");
	public static final XDI3SubSegment XRI_URI = XDI3SubSegment.create("$uri");

	private XRI2Resolver resolver = new XRI2XNSResolver();

	@Override
	public boolean executeGetOnAddress(XDI3Segment[] contributorXris, XDI3Segment contributorsXri, XDI3Segment relativeTargetAddress, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		// prepare XRI

		XDI3Segment requestedXdiPeerRootXri = contributorXris[contributorXris.length - 1];

		XDI3Segment resolveXri = XdiPeerRoot.getXriOfPeerRootArcXri(requestedXdiPeerRootXri.getFirstSubSegment());
		if (resolveXri == null) return false;

		CloudNumber resolveCloudNumber = CloudNumber.fromXri(resolveXri);
		String resolveINumber = resolveCloudNumber == null ? null : XRI2Util.cloudNumberToINumber(resolveCloudNumber);
		if (resolveINumber != null) resolveXri = XDI3Segment.create(resolveINumber);

		// resolve the XRI

		if (log.isDebugEnabled()) log.debug("Resolving " + resolveXri);

		XRD xrd;

		try {

			xrd = this.resolver.resolve(resolveXri);
			if (xrd == null) throw new Exception("No XRD.");
		} catch (Exception ex) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 XRD Problem: " + ex.getMessage(), ex, executionContext);
		}

		if (log.isDebugEnabled()) log.debug("XRD Status: " + xrd.getStatus().getCode());

		if ((! Status.SUCCESS.equals(xrd.getStatusCode())) && (! Status.SEP_NOT_FOUND.equals(xrd.getStatusCode()))) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 Status Problem: " + xrd.getStatusCode() + " (" + xrd.getStatus().getValue() + ")", null, executionContext);
		}

		// extract cloud number

		CanonicalID canonicalID = xrd.getCanonicalID();
		if (canonicalID == null) throw new Xdi2MessagingException("Unable to read CanonicalID from XRD.", null, executionContext);

		String iNumber = canonicalID.getValue();
		CloudNumber cloudNumber = XRI2Util.iNumberToCloudNumber(iNumber);
		if (cloudNumber == null) cloudNumber = CloudNumber.create(iNumber);
		if (cloudNumber == null) throw new Xdi2MessagingException("Unable to read Cloud Number from CanonicalID: " + xrd.getCanonicalID().getValue(), null, executionContext);

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

			XdiPeerRoot cloudNumberXdiPeerRoot = XdiLocalRoot.findLocalRoot(messageResult.getGraph()).findPeerRoot(cloudNumber.getXri(), true);

			Equivalence.setReferenceContextNode(requestedXdiPeerRoot.getContextNode(), cloudNumberXdiPeerRoot.getContextNode());

			return false;
		}

		// add all URIs for all types

		XdiAttributeCollection uriXdiAttributeCollection = requestedXdiPeerRoot.getXdiAttributeCollection(XRI_URI, true);

		for (Entry<String, List<String>> uriMapEntry : uriMap.entrySet()) {

			String type = uriMapEntry.getKey();
			List<String> uriList = uriMapEntry.getValue();

			XDI3SubSegment typeXdiEntitySingletonArcXri = XRI2Util.typeToXdiEntitySingletonArcXri(type);

			for (String uri : uriList) {

				XDI3SubSegment uriXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createHashArcXri(uri, true);

				XdiAttributeMember uriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(uriXdiMemberUnorderedArcXri);
				uriXdiAttributeMember.getXdiValue(true).getContextNode().setLiteral(uri);

				XdiAttributeCollection typeXdiAttributeCollection = requestedXdiPeerRoot.getXdiEntitySingleton(typeXdiEntitySingletonArcXri, true).getXdiAttributeCollection(XRI_URI, true);
				XdiAttributeMember typeXdiAttributeMember = typeXdiAttributeCollection.setXdiMemberOrdered(-1);
				Equivalence.setReferenceContextNode(typeXdiAttributeMember.getContextNode(), uriXdiAttributeMember.getContextNode());
			}

			// add default URI for this type

			if (uriList.size() > 0) {

				String defaultUriForType = uriList.get(0);

				XDI3SubSegment defaultUriForTypeXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createHashArcXri(defaultUriForType, true);

				XdiAttributeMember defaultUriForTypeXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriForTypeXdiMemberUnorderedArcXri);
				XdiAttributeSingleton defaultUriForTypeXdiAttributeSingleton = requestedXdiPeerRoot.getXdiEntitySingleton(typeXdiEntitySingletonArcXri, true).getXdiAttributeSingleton(XRI_URI, true);
				Equivalence.setReferenceContextNode(defaultUriForTypeXdiAttributeSingleton.getContextNode(), defaultUriForTypeXdiAttributeMember.getContextNode());
			}
		}

		// add default URI

		if (defaultUri != null) {

			XDI3SubSegment defaultUriXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createHashArcXri(defaultUri, true);

			XdiAttributeMember defaultUriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriXdiMemberUnorderedArcXri);
			XdiAttributeSingleton defaultUriXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(XRI_URI, true);
			Equivalence.setReferenceContextNode(defaultUriXdiAttributeSingleton.getContextNode(), defaultUriXdiAttributeMember.getContextNode());
		}

		// done

		return false;
	}

	/*
	 * Getters and setters
	 */

	public XRI2Resolver getResolver() {

		return this.resolver;
	}

	public void setResolver(XRI2Resolver resolver) {

		this.resolver = resolver;
	}
}
