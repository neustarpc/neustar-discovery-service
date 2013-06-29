<a href="http://projectdanube.org/" target="_blank"><img src="http://projectdanube.github.com/xdi2/images/projectdanube_logo.png" align="right"></a>
<img src="http://projectdanube.github.com/xdi2/images/logo64.png"><br>

This is a gateway that provides an XDI discovery service, backed by XRI resolution.

This is based on the [XDI2](http://github.com/projectdanube/xdi2) and [OpenXRI](http://openxri.org) libraries.

A public-use deployment of this service is available at http://mycloud.neustar.biz:12220/. It has no web interface, but will accept requests as described below.

### How to build

First, you need to build the main [XDI2](http://github.com/projectdanube/xdi2) and [OpenXRI](http://openxri.org) projects.

After that, just run

    mvn clean install

To build all components.

### How to run

    mvn jetty:run

Then the XDI discovery service is available at

	http://mycloud.neustar.biz:12220/

### Information

* [Example XRI Resolution](https://github.com/projectdanube/xdi2-xrinet/wiki/Example-XRI-Resolution)
* [Example XDI Discovery](https://github.com/projectdanube/xdi2-xrinet/wiki/Example-XDI-Discovery)

### Community

Google Group: http://groups.google.com/group/xdi2
