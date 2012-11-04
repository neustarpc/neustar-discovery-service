<a href="http://projectdanube.org/" target="_blank"><img src="http://peacekeeper.github.com/xdi2/images/projectdanube_logo.png" align="right"></a>
<img src="http://peacekeeper.github.com/xdi2/images/logo64.png"><br>

This is a combined XRI/XDI discovery proxy service based on the [XDI2](http://github.com/peacekeeper/xdi2) and [OpenXRI](http://openxri.org) libraries.

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

Request URI:

	http://localhost:12220/=markus?_xrd_r=application/xrd+xml;sep=true;nodefault_t=true;debug=1&_xrd_t=xri://$xdi!($v!1)

### Example XDI discovery request

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
