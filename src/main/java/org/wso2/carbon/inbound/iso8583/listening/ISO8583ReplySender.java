package org.wso2.carbon.inbound.iso8583.listening;


import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundResponseSender;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import javax.xml.namespace.QName;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Iterator;
import java.util.Properties;

/**
 * class for handle iso8583 responses.
 */
public class ISO8583ReplySender implements InboundResponseSender {

    private static final Log log = LogFactory.getLog(ISO8583ReplySender.class.getName());

    private Socket connection;

    /**
     * keep the socket connection to send the response back to client.
     *
     * @param connection created socket connection.
     */
    public ISO8583ReplySender(Socket connection) {
        this.connection = connection;
    }

    /**
     * Send the reply or response back to the client.
     *
     * @param messageContext to get xml iso message from message context.
     */
    @Override
    public void sendBack(MessageContext messageContext) {
        String responseMessage = null;
        try {
            ISOPackager packager = ISO8583PackagerFactory.getPackager();
            Properties properties = getPropertiesFile();
            //Retrieve the SOAP envelope from the MessageContext
            SOAPEnvelope soapEnvelope = messageContext.getEnvelope();
            OMElement getElements = soapEnvelope.getBody().getFirstElement();
            ISOMsg isoMsg = new ISOMsg();
            isoMsg.setPackager(packager);
            Iterator fields = getElements.getFirstChildWithName(
                    new QName(ISO8583Constant.TAG_DATA)).getChildrenWithLocalName(ISO8583Constant.TAG_FIELD);
            while (fields.hasNext()) {
                OMElement element = (OMElement) fields.next();
                String getValue = element.getText();
                try {
                    int fieldID = Integer.parseInt(element.getAttribute(
                            new QName(ISO8583Constant.TAG_ID)).getAttributeValue());
                    isoMsg.set(fieldID, getValue);
                } catch (NumberFormatException e) {
                    log.warn("The fieldID does not contain a parsable integer" + e.getMessage(), e);
                }
            }
            /* Set the response fields */
            if (isoMsg.getMTI().equals(properties.getProperty("requestMTI"))) {
                isoMsg.setMTI((properties.getProperty("responseMTI")));
                /* Set the code for successful response */
                isoMsg.set(properties.getProperty("responseField"), properties.getProperty("successfulResponseCode"));
                byte[] msg = isoMsg.pack();
                responseMessage = new String(msg).toUpperCase();
            } else {
                /* Set the code for invalid transaction response */
                isoMsg.set(properties.getProperty("responseField"), properties.getProperty("failureResponseCode"));
                byte[] msg = isoMsg.pack();
                responseMessage = new String(msg).toUpperCase();
            }
        } catch (ISOException e) {
            handleException("Couldn't packed ISO8583 Messages", e);
        }
        sendResponse(responseMessage);
    }

    /**
     * writes the packed iso message response to the client.
     *
     * @param responseMessage String of packed ISO response.
     */
    private void sendResponse(String responseMessage) {
        try {
            DataOutputStream outToClient = new DataOutputStream(connection.getOutputStream());
            outToClient.writeBytes(responseMessage);
        } catch (IOException e) {
            handleException("OutputStream may be closed ", e);
        }
    }

    /**
     * get config files to map iso request and response fields.
     */
    public Properties getPropertiesFile() {
        String resourceName = ISO8583Constant.PROPERTIES_FILE;
        Properties prop = new Properties();
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        try {
            if (resourceStream != null)
                prop.load(resourceStream);
        } catch (IOException e) {
            handleException("Could not find the properties file ", e);
        } finally {
            try {
                if (resourceStream != null)
                    resourceStream.close();
            } catch (IOException e) {
                log.error("Couldn't close the inputStream");
            }
        }
        return prop;
    }

    /**
     * handle the Exception
     *
     * @param msg error message
     * @param e   an Exception
     */
    private void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg);
    }
}
