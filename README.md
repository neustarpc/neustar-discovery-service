<img src="http://neustarpc.github.com/neustar-clouds/images/logo.png"><br>

This is a gateway that provides an XDI discovery service backed by XRI resolution.

This is based on the [XDI2](http://github.com/projectdanube/xdi2) libraries.

A public-use deployment of this service is available at https://xdidiscoveryservice.xdi.net/. It has no web interface, but will accept requests as described below.

### How to build

First, you need to build the main [XDI2](http://github.com/projectdanube/xdi2) .

After that, just run

    mvn clean install

To build all components.

### How to run

    mvn jetty:run

Then the XDI discovery service is available at

	http://localhost:12220/

### Information

* [Example XRI Resolution](https://github.com/neustarpc/neustar-discovery-service/wiki/Example-XRI-Resolution)
* [Example XDI Discovery](https://github.com/neustarpc/neustar-discovery-service/wiki/Example-XDI-Discovery)
