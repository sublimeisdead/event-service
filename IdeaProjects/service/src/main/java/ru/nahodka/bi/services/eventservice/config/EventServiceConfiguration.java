package ru.nahodka.bi.services.eventservice.config;


import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.nahodka.bi.services.eventservice.endpoint.EventServiceEndpoint;
import ru.nahodka.bi.services.eventservice.schemas.EventService;
import ru.nahodka.bi.services.eventservice.schemas.EventServicePort;

import javax.xml.ws.Endpoint;

@Configuration
public class EventServiceConfiguration {

    @Bean(name = Bus.DEFAULT_BUS_ID)
    public SpringBus springBus() {
        return new SpringBus();
    }

    @Bean
    public EventServicePort eventServicePort() {
        return new EventServiceEndpoint();
    }

    @Bean
    public Endpoint endpoint() {
        EndpointImpl endpoint = new EndpointImpl(springBus(), eventServicePort());
        // CXF JAX-WS implementation relies on the correct ServiceName as QName-Object with
        // the name-Attribute´s text <wsdl:service name="Weather"> and the targetNamespace
        // "http://www.codecentric.de/namespace/weatherservice/"
        // Also the WSDLLocation must be set
        endpoint.setServiceName(eventService().getServiceName());
        endpoint.setWsdlLocation(eventService().getWSDLDocumentLocation().toString());

        endpoint.publish("/");
        return endpoint;
    }

    @Bean
    public EventService eventService() {
        // Needed for correct ServiceName & WSDLLocation to publish contract first incl. original WSDL
        return new EventService();
    }
}
