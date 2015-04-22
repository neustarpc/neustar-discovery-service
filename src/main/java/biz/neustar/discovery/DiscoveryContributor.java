package biz.neustar.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xdi2.core.Graph;
import xdi2.core.features.equivalence.Equivalence;
import xdi2.core.features.nodetypes.XdiAbstractMemberUnordered;
import xdi2.core.features.nodetypes.XdiAttributeCollection;
import xdi2.core.features.nodetypes.XdiAttributeMember;
import xdi2.core.features.nodetypes.XdiAttributeSingleton;
import xdi2.core.features.nodetypes.XdiCommonRoot;
import xdi2.core.features.nodetypes.XdiPeerRoot;
import xdi2.core.impl.memory.MemoryGraphFactory;
import xdi2.core.syntax.CloudNumber;
import xdi2.core.syntax.XDIAddress;
import xdi2.core.syntax.XDIArc;
import xdi2.core.util.CopyUtil;
import xdi2.core.util.XRI2Util;
import xdi2.messaging.GetOperation;
import xdi2.messaging.MessageEnvelope;
import xdi2.messaging.MessageResult;
import xdi2.messaging.context.ExecutionContext;
import xdi2.messaging.exceptions.Xdi2MessagingException;
import xdi2.messaging.target.contributor.AbstractContributor;
import xdi2.messaging.target.contributor.ContributorMount;
import xdi2.messaging.target.contributor.ContributorResult;
import xdi2.messaging.target.interceptor.InterceptorResult;
import xdi2.messaging.target.interceptor.MessageEnvelopeInterceptor;
import biz.neustar.discovery.resolver.XRI2Resolver;
import biz.neustar.discovery.resolver.XRI2XNSResolver;
import biz.neustar.discovery.xrd.XRD;
import biz.neustar.discovery.xrd.XRDService;
import biz.neustar.discovery.xrd.XRDType;
import biz.neustar.discovery.xrd.XRDUri;

@ContributorMount(contributorXDIAddresses={"{()}"})
public class DiscoveryContributor extends AbstractContributor implements MessageEnvelopeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryContributor.class);

	public static final XDIArc XRI_ARC_AC_URI = XDIArc.create("[<$uri>]");
	public static final XDIArc XRI_ARC_AS_URI = XDIArc.create("<$uri>");

	private XRI2Resolver resolver = new XRI2XNSResolver();

	@Override
	public ContributorResult executeGetOnAddress(XDIAddress[] contributorXDIAddresses, XDIAddress contributorsXDIAddress, XDIAddress relativeTargetXDIAddress, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		// prepare XRI

		XDIAddress requestedXdiPeerRootXDIAddress = contributorXDIAddresses[contributorXDIAddresses.length - 1];

		XDIAddress resolveXDIAddress = XdiPeerRoot.getXDIAddressOfPeerRootXDIArc(requestedXdiPeerRootXDIAddress.getFirstXDIArc());
		if (resolveXDIAddress == null) return ContributorResult.DEFAULT;

		CloudNumber resolveCloudNumber = CloudNumber.fromXDIAddress(resolveXDIAddress);
		String resolveINumber = resolveCloudNumber == null ? null : XRI2Util.cloudNumberToINumber(resolveCloudNumber);
		if (resolveINumber != null) resolveXDIAddress = XDIAddress.create(resolveINumber);

		// resolve the XRI

		if (log.isDebugEnabled()) log.debug("Resolving " + resolveXDIAddress);

		XRD xrd;

		try {

			xrd = this.resolver.resolve(resolveXDIAddress);
			if (xrd == null) throw new Exception("No XRD.");
		} catch (Exception ex) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 XRD Problem: " + ex.getMessage(), ex, executionContext);
		}

		if (log.isDebugEnabled()) log.debug("XRD: " + xrd);

		if (log.isDebugEnabled()) log.debug("XRD Status: " + xrd.getStatus());

		if (XRD.STATUS_QUERY_NOT_FOUND.equals(xrd.getStatus())) {

			return ContributorResult.DEFAULT;
		}

		if ((! XRD.STATUS_SUCCESS.equals(xrd.getStatus())) && (! XRD.STATUS_SEP_NOT_FOUND.equals(xrd.getStatus()))) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 Status Problem: " + xrd.getStatus(), null, executionContext);
		}

		// extract cloud number

		String canonicalId = xrd.getCanonicalId();
		if (canonicalId == null) throw new Xdi2MessagingException("Unable to read CanonicalId from XRD.", null, executionContext);

		String iNumber = canonicalId;
		CloudNumber cloudNumber = XRI2Util.iNumberToCloudNumber(iNumber);
		if (cloudNumber == null) cloudNumber = CloudNumber.create(iNumber);
		if (cloudNumber == null) throw new Xdi2MessagingException("Unable to read Cloud Number from CanonicalId: " + canonicalId, null, executionContext);

		if (log.isDebugEnabled()) log.debug("Cloud Number: " + cloudNumber);

		// extract URIs

		Map<String, List<String>> uriMap = new HashMap<String, List<String>> ();

		for (XRDService service : xrd.getServices()) {

			for (XRDType type : service.getTypes()) {

				if (type.getType() == null || type.getType().trim().isEmpty()) continue;

				List<String> uriList = uriMap.get(type.getType());

				if (uriList == null) {

					uriList = new ArrayList<String> ();
					uriMap.put(type.getType(), uriList);
				}

				List<XRDUri> uris = service.getUris();
				Collections.sort(uris, new Comparator<Object> () {

					@Override
					public int compare(Object uri1, Object uri2) {

						Integer priority1 = ((XRDUri) uri1).getPriority();
						Integer priority2 = ((XRDUri) uri2).getPriority();

						if (priority1 == null && priority2 == null) return 0;
						if (priority1 == null && priority2 != null) return 1;
						if (priority1 != null && priority2 == null) return -1;

						if (priority1.intValue() == priority2.intValue()) return 0;

						return priority1.intValue() > priority2.intValue() ? 1 : -1;
					}
				});

				for (XRDUri uri : service.getUris()) {

					if (uri.getUri() == null || uri.getUri().trim().isEmpty()) continue;

					uriList.add(uri.getUri());
				}
			}
		}

		if (log.isDebugEnabled()) log.debug("URIs: " + uriMap);

		// extract default URI

		XRDService defaultUriService = xrd.getDefaultService();
		String defaultUri = defaultUriService == null ? null : defaultUriService.getUris().get(0).getUri();

		if (log.isDebugEnabled()) log.debug("Default URI: " + defaultUri);

		// extract extension

		String extension = xrd.getExtension();
		Graph extensionGraph;

		try {

			if (extension != null && ! extension.trim().isEmpty()) {
				extensionGraph = MemoryGraphFactory.getInstance().parseGraph(extension, "XDI DISPLAY", null);
			} else {

				extensionGraph = null;
			}
		} catch (Exception ex) {

			throw new Xdi2MessagingException("Extension Problem: " + ex.getMessage(), ex, executionContext);
		}

		if (log.isDebugEnabled()) log.debug("Extension: " + extensionGraph);

		// prepare result graph

		XdiPeerRoot requestedXdiPeerRoot = XdiPeerRoot.fromContextNode(messageResult.getGraph().setDeepContextNode(requestedXdiPeerRootXDIAddress));

		// add original peer root

		if (! cloudNumber.getXDIAddress().equals(requestedXdiPeerRoot.getXDIAddressOfPeerRoot())) {

			XdiPeerRoot cloudNumberXdiPeerRoot = XdiCommonRoot.findCommonRoot(messageResult.getGraph()).getPeerRoot(cloudNumber.getXDIAddress(), true);

			Equivalence.setReferenceContextNode(requestedXdiPeerRoot.getContextNode(), cloudNumberXdiPeerRoot.getContextNode());

			return ContributorResult.SKIP_MESSAGING_TARGET;
		}

		// add all URIs for all types

		XdiAttributeCollection uriXdiAttributeCollection = requestedXdiPeerRoot.getXdiAttributeCollection(XRI_ARC_AC_URI, true);

		for (Entry<String, List<String>> uriMapEntry : uriMap.entrySet()) {

			String type = uriMapEntry.getKey();
			List<String> uriList = uriMapEntry.getValue();

			XDIArc typeXdiArcXri = XRI2Util.typeToXDIArc(type);

			for (String uri : uriList) {

				if (log.isDebugEnabled()) log.debug("Mapping URI " + uri + " for type XRI " + typeXdiArcXri);

				XDIArc uriXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createDigestXDIArc(uri, XdiAttributeCollection.class);

				XdiAttributeMember uriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(uriXdiMemberUnorderedArcXri);
				uriXdiAttributeMember.setLiteralDataString(uri);

				XdiAttributeCollection typeXdiAttributeCollection = requestedXdiPeerRoot.getXdiAttributeSingleton(typeXdiArcXri, true).getXdiAttributeCollection(XRI_ARC_AC_URI, true);
				XdiAttributeMember typeXdiAttributeMember = typeXdiAttributeCollection.setXdiMemberOrdered(-1);
				Equivalence.setReferenceContextNode(typeXdiAttributeMember.getContextNode(), uriXdiAttributeMember.getContextNode());
			}

			// add default URI for this type

			if (uriList.size() > 0) {

				String defaultUriForType = uriList.get(0);

				if (log.isDebugEnabled()) log.debug("Mapping default URI " + defaultUriForType + " for type XRI " + typeXdiArcXri);

				XDIArc defaultUriForTypeXdiMemberUnorderedXDIArc = XdiAbstractMemberUnordered.createDigestXDIArc(defaultUriForType, XdiAttributeCollection.class);

				XdiAttributeMember defaultUriForTypeXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriForTypeXdiMemberUnorderedXDIArc);
				XdiAttributeSingleton defaultUriForTypeXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(typeXdiArcXri, true).getXdiAttributeSingleton(XRI_ARC_AS_URI, true);
				Equivalence.setReferenceContextNode(defaultUriForTypeXdiAttributeSingleton.getContextNode(), defaultUriForTypeXdiAttributeMember.getContextNode());
			}
		}

		// add default URI

		if (defaultUri != null) {

			if (log.isDebugEnabled()) log.debug("Mapping default URI " + defaultUri);

			XDIArc defaultUriXdiMemberUnorderedXDIArc = XdiAbstractMemberUnordered.createDigestXDIArc(defaultUri, XdiAttributeCollection.class);

			XdiAttributeMember defaultUriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriXdiMemberUnorderedXDIArc);
			XdiAttributeSingleton defaultUriXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(XRI_ARC_AS_URI, true);
			Equivalence.setReferenceContextNode(defaultUriXdiAttributeSingleton.getContextNode(), defaultUriXdiAttributeMember.getContextNode());
		}

		// add extension

		if (extensionGraph != null) {

			CopyUtil.copyGraph(extensionGraph, messageResult.getGraph(), null);
		}

		// done

		return ContributorResult.SKIP_MESSAGING_TARGET;
	}

	/*
	 * MessageEnvelopeInterceptor
	 */

	@Override
	public InterceptorResult before(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		this.getResolver().reset();

		return InterceptorResult.DEFAULT;
	}

	@Override
	public InterceptorResult after(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		return InterceptorResult.DEFAULT;
	}

	@Override
	public void exception(MessageEnvelope messageEnvelope, MessageResult messageResult, ExecutionContext executionContext, Exception ex) {

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
