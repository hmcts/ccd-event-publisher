package uk.gov.hmcts.ccd.config;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.qpid.jms.message.JmsBytesMessage;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsMessageFacade;
import org.apache.qpid.proton.amqp.Symbol;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Session;
import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class CcdMessageConverter extends MappingJackson2MessageConverter {

    @Override
    protected BytesMessage mapToBytesMessage(Object object, Session session,
                                             ObjectWriter objectWriter) throws JMSException, IOException {
        BytesMessage message = super.mapToBytesMessage(object, session, objectWriter);
        if (message instanceof JmsBytesMessage) {
            JmsBytesMessage qpidMessage = (JmsBytesMessage) message;
            AmqpJmsMessageFacade facade = (AmqpJmsMessageFacade) qpidMessage.getFacade();
            facade.setContentType(Symbol.valueOf(APPLICATION_JSON_VALUE));
        }
        return message;
    }
}
