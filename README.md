<a href="http://projectdanube.org/" target="_blank"><img src="http://peacekeeper.github.com/xdi2/images/projectdanube_logo.png" align="right"></a>
<img src="http://peacekeeper.github.com/xdi2/images/logo64.png"><br>

This is a combined XRI/XDI discovery proxy service based on the [XDI2](http://github.com/peacekeeper/xdi2) and [OpenXRI](http://openxri.org) libraries.

### How to build

First, you need to build the main [XDI2](http://github.com/peacekeeper/xdi2) project.

After that, just run

    mvn clean install

To build all components.

### How to run

    mvn jetty:run

Then the combined XRI/XDI discovery proxy service is available at

	http://localhost:12220/

### Community

Google Group: http://groups.google.com/group/xdi2
