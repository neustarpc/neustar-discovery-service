<a href="http://projectdanube.org/" target="_blank"><img src="http://peacekeeper.github.com/xdi2/images/projectdanube_logo.png" align="right"></a>
<img src="http://peacekeeper.github.com/xdi2/images/logo64.png"><br>

This is a combined XRI/XDI discovery proxy service based on the [XDI2](http://github.com/peacekeeper/xdi2) and [OpenXRI](http://openxri.org) libraries.

A public-use deployment of this service is available at http://beta.xri.net/xdi2-xrinet/. It has no web interface, but will accept requests as described below.

### How to build

First, you need to build the main [XDI2](http://github.com/peacekeeper/xdi2) and [OpenXRI](http://openxri.org) projects.

After that, just run

    mvn clean install

To build all components.

### How to run

    mvn jetty:run

Then the combined XRI/XDI discovery proxy service is available at

	http://localhost:12220/

### Example XRI discovery request and response

The following functionality is compatible with [XRI Resolution 2.0](http://docs.oasis-open.org/xri/2.0/specs/xri-resolution-V2.0.html).

HTTP GET request:

	http://localhost:12220/=markus?_xrd_r=application/xrd+xml;sep=true;nodefault_t=true;debug=1&_xrd_t=xri://$xdi!($v!1)

HTTP GET response:

	<?xml version="1.0" encoding="UTF-8"?>
	<XRD version="2.0" xmlns="xri://$xrd*($v*2.0)">
		<Query>*markus</Query>
		<Status cid="verified" code="100" />
		<ServerStatus code="100" />
		<Expires>2012-11-04T15:28:14.000Z</Expires>
		<ProviderID>xri://=</ProviderID>
		<LocalID>!91f2.8153.f600.ae24</LocalID>
		<CanonicalID>=!91F2.8153.F600.AE24</CanonicalID>
		<Service priority="10">
			<ProviderID>@!26E.5985.6045.FCED</ProviderID>
			<Type select="true">xri://$xdi!($v!1)</Type>
			<Path select="true">($context)!($xdi)!($v!1) </Path>
			<MediaType match="default" select="false" />
			<URI append="none">https://xdi.fullxri.com/=!91F2.8153.F600.AE24/</URI>
		</Service>
	</XRD>

### Example XDI discovery request and response

The following functionality is compatible with [XDI Discovery 1.0](https://wiki.oasis-open.org/xdi/CategoryDiscovery).

HTTP POST request:

	=sender$($msg)$(!1234)$do/$get/=markus

HTTP POST response:

	()/()/$xdi
	()/()/(())
	()/()/(=!91F2.8153.F600.AE24)
	()/()/=!91F2.8153.F600.AE24
	()/()/=markus
	$xdi/()/$*($v)
	$xdi$*($v)/()/$!1
	(=!91F2.8153.F600.AE24)/()/$!($uri)
	()/$is$is/(())
	(())/$is/()
	(=!91F2.8153.F600.AE24)$!($uri)/$is+/$xdi$*($v)$!1
	=markus/$is/=!91F2.8153.F600.AE24
	(=!91F2.8153.F600.AE24)$!($uri)/!/(data:,https:%2F%2Fxdi.fullxri.com%2F=!91F2.8153.F600.AE24%2F)

### Community

Google Group: http://groups.google.com/group/xdi2
