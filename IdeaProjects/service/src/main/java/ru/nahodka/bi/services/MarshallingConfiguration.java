package ru.nahodka.bi.services;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.oxm.castor.CastorMarshaller;

@Configuration
public class MarshallingConfiguration {

    @Bean
    public CastorMarshaller castorMarshaller() {
        return new CastorMarshaller();
    }

    @Bean
    public XmlConverter xmlConverter(Marshaller marshaller, Unmarshaller unmarshaller) {
        return new XmlConverter(marshaller, unmarshaller);
    }
}
