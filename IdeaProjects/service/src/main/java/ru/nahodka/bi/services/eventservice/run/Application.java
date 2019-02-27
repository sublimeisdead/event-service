package ru.nahodka.bi.services.eventservice.run;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class Application {

    public static void main(String[] args) throws Exception {

        Server server = new Server(9082);

        WebAppContext root = new WebAppContext("webapp", "/");


        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // fix for Windows, so Jetty doesn't lock files
            root.getInitParams().put("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        }

        root.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern", ".*/[^/]*taglibs.*\\.jar$");

        server.setHandler(root);
        server.start();



    }
}
