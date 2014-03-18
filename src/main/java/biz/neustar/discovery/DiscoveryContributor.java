package biz.neustar.discovery;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import xdi2.core.Graph;
import xdi2.core.features.equivalence.Equivalence;
import xdi2.core.features.nodetypes.XdiAbstractMemberUnordered;
import xdi2.core.features.nodetypes.XdiAttributeCollection;
import xdi2.core.features.nodetypes.XdiAttributeMember;
import xdi2.core.features.nodetypes.XdiAttributeSingleton;
import xdi2.core.features.nodetypes.XdiLocalRoot;
import xdi2.core.features.nodetypes.XdiPeerRoot;
import xdi2.core.impl.memory.MemoryGraphFactory;
import xdi2.core.util.CopyUtil;
import xdi2.core.util.XRI2Util;
import xdi2.core.xri3.CloudNumber;
import xdi2.core.xri3.XDI3Segment;
import xdi2.core.xri3.XDI3SubSegment;
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

@ContributorMount(contributorXris={"{()}"})
public class DiscoveryContributor extends AbstractContributor implements MessageEnvelopeInterceptor {

	private static final Logger log = LoggerFactory.getLogger(DiscoveryContributor.class);

	public static final XDI3SubSegment XRI_SS_AC_URI = XDI3SubSegment.create("[<$uri>]");
	public static final XDI3SubSegment XRI_SS_AS_URI = XDI3SubSegment.create("<$uri>");

	private XRI2Resolver resolver = new XRI2XNSResolver();

	@Override
	public ContributorResult executeGetOnAddress(XDI3Segment[] contributorXris, XDI3Segment contributorsXri, XDI3Segment relativeTargetAddress, GetOperation operation, MessageResult messageResult, ExecutionContext executionContext) throws Xdi2MessagingException {

		// prepare XRI

		XDI3Segment requestedXdiPeerRootXri = contributorXris[contributorXris.length - 1];

		XDI3Segment resolveXri = XdiPeerRoot.getXriOfPeerRootArcXri(requestedXdiPeerRootXri.getFirstSubSegment());
		if (resolveXri == null) return ContributorResult.DEFAULT;

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

		if (log.isDebugEnabled()) log.debug("XRD: " + xrd);

		if (log.isDebugEnabled()) log.debug("XRD Status: " + xrd.getStatus());

		if ((! XRD.STATUS_SUCCESS.equals(xrd.getStatus())) && (! XRD.STATUS_SEP_NOT_FOUND.equals(xrd.getStatus()))) {

			throw new Xdi2MessagingException("XRI Resolution 2.0 Status Problem: " + xrd.getStatus(), null, executionContext);
		}

		// extract cloud number

		String canonicalID = xrd.getCanonicalID();
		if (canonicalID == null) throw new Xdi2MessagingException("Unable to read CanonicalID from XRD.", null, executionContext);

		String iNumber = canonicalID;
		CloudNumber cloudNumber = XRI2Util.iNumberToCloudNumber(iNumber);
		if (cloudNumber == null) cloudNumber = CloudNumber.create(iNumber);
		if (cloudNumber == null) throw new Xdi2MessagingException("Unable to read Cloud Number from CanonicalID: " + canonicalID, null, executionContext);

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

		String extensionXml = xrd.getExtension();
		Graph extensionGraph;

		try {

			if (extensionXml != null && ! extensionXml.trim().isEmpty()) {

				Document extensionDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(extensionXml)));
				String extensionXdi = extensionDocument.getDocumentElement().getFirstChild().getTextContent();
				
				extensionGraph = MemoryGraphFactory.getInstance().parseGraph(extensionXdi, "XDI DISPLAY", null);
			} else {
				
				extensionGraph = null;
			}
		} catch (Exception ex) {

			throw new Xdi2MessagingException("Extension Problem: " + ex.getMessage(), ex, executionContext);
		}

		if (log.isDebugEnabled()) log.debug("Extension: " + extensionGraph);

		// prepare result graph

		XdiPeerRoot requestedXdiPeerRoot = XdiPeerRoot.fromContextNode(messageResult.getGraph().setDeepContextNode(requestedXdiPeerRootXri));

		// add original peer root

		if (! cloudNumber.getXri().equals(requestedXdiPeerRoot.getXriOfPeerRoot())) {

			XdiPeerRoot cloudNumberXdiPeerRoot = XdiLocalRoot.findLocalRoot(messageResult.getGraph()).findPeerRoot(cloudNumber.getXri(), true);

			Equivalence.setReferenceContextNode(requestedXdiPeerRoot.getContextNode(), cloudNumberXdiPeerRoot.getContextNode());

			return ContributorResult.SKIP_MESSAGING_TARGET;
		}

		// add all URIs for all types

		XdiAttributeCollection uriXdiAttributeCollection = requestedXdiPeerRoot.getXdiAttributeCollection(XRI_SS_AC_URI, true);

		for (Entry<String, List<String>> uriMapEntry : uriMap.entrySet()) {

			String type = uriMapEntry.getKey();
			List<String> uriList = uriMapEntry.getValue();

			XDI3SubSegment typeXdiArcXri = XRI2Util.typeToXdiArcXri(type);

			for (String uri : uriList) {

				if (log.isDebugEnabled()) log.debug("Mapping URI " + uri + " for type XRI " + typeXdiArcXri);

				XDI3SubSegment uriXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createDigestArcXri(uri, true);

				XdiAttributeMember uriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(uriXdiMemberUnorderedArcXri);
				uriXdiAttributeMember.getXdiValue(true).getContextNode().setLiteral(uri);

				XdiAttributeCollection typeXdiAttributeCollection = requestedXdiPeerRoot.getXdiAttributeSingleton(typeXdiArcXri, true).getXdiAttributeCollection(XRI_SS_AC_URI, true);
				XdiAttributeMember typeXdiAttributeMember = typeXdiAttributeCollection.setXdiMemberOrdered(-1);
				Equivalence.setReferenceContextNode(typeXdiAttributeMember.getContextNode(), uriXdiAttributeMember.getContextNode());
			}

			// add default URI for this type

			if (uriList.size() > 0) {

				String defaultUriForType = uriList.get(0);

				if (log.isDebugEnabled()) log.debug("Mapping default URI " + defaultUriForType + " for type XRI " + typeXdiArcXri);

				XDI3SubSegment defaultUriForTypeXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createDigestArcXri(defaultUriForType, true);

				XdiAttributeMember defaultUriForTypeXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriForTypeXdiMemberUnorderedArcXri);
				XdiAttributeSingleton defaultUriForTypeXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(typeXdiArcXri, true).getXdiAttributeSingleton(XRI_SS_AS_URI, true);
				Equivalence.setReferenceContextNode(defaultUriForTypeXdiAttributeSingleton.getContextNode(), defaultUriForTypeXdiAttributeMember.getContextNode());
			}
		}

		// add default URI

		if (defaultUri != null) {

			if (log.isDebugEnabled()) log.debug("Mapping default URI " + defaultUri);

			XDI3SubSegment defaultUriXdiMemberUnorderedArcXri = XdiAbstractMemberUnordered.createDigestArcXri(defaultUri, true);

			XdiAttributeMember defaultUriXdiAttributeMember = uriXdiAttributeCollection.setXdiMemberUnordered(defaultUriXdiMemberUnorderedArcXri);
			XdiAttributeSingleton defaultUriXdiAttributeSingleton = requestedXdiPeerRoot.getXdiAttributeSingleton(XRI_SS_AS_URI, true);
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
