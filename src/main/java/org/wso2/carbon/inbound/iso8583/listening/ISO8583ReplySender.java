/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.inbound.iso8583.listening;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundResponseSender;
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import javax.xml.namespace.QName;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Base64;
import java.util.Iterator;
import java.util.Properties;

/**
 * class for handle iso8583 responses.
 */
public class ISO8583ReplySender implements InboundResponseSender {

    private static final Log log = LogFactory.getLog(ISO8583ReplySender.class.getName());

    private Socket connection;
    private InboundProcessorParams params;

    /**
     * keep the socket connection to send the response back to client.
     *
     * @param connection created socket connection.
     */
    public ISO8583ReplySender(Socket connection, InboundProcessorParams params) {
        this.connection = connection;
        this.params = params;
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
            ISOBasePackager packager = ISO8583PackagerFactory.getPackagerWithParams(params);
            Properties properties = getPropertiesFile();
            //Retrieve the SOAP envelope from the MessageContext
            SOAPEnvelope soapEnvelope = messageContext.getEnvelope();
            OMElement getElements = null;
            if (isProxy()) {
                Iterator<OMElement> iterator = soapEnvelope.getBody().getChildrenWithName(
                        new QName(ISO8583Constant.TAG_MSG));
                while (iterator.hasNext()) {
                    getElements = iterator.next();
                }
            } else {
                getElements = soapEnvelope.getBody().getFirstElement();
            }
            if (getElements == null) {
                handleException("Failed to get response message", null);
            }
            ISOMsg isoMsg = new ISOMsg();
            isoMsg.setPackager(packager);
            if (packager.getHeaderLength() > 0) {
                String header = getElements.getFirstChildWithName(new QName(ISO8583Constant.HEADER)).getText();
                isoMsg.setHeader(Base64.getDecoder().decode(header));
            }
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

            if (isProxy()) {
                byte[] msg = isoMsg.pack();
                responseMessage = new String(msg).toUpperCase();
            } else {
                /* Set the response fields */
                if (isoMsg.getMTI().equals(properties.getProperty((String) ISO8583Constant.REQUEST_MTI))) {
                    isoMsg.setMTI((properties.getProperty((String) ISO8583Constant.RESPONSE_MTI)));
                    /* Set the code for successful response */
                    isoMsg.set(properties.getProperty(ISO8583Constant.RESPONSE_FIELD),
                            properties.getProperty(ISO8583Constant.SUCCESSFUL_RESPONSE_CODE));
                    byte[] msg = isoMsg.pack();
                    responseMessage = new String(msg).toUpperCase();
                } else {
                    /* Set the code for invalid transaction response */
                    isoMsg.set(properties.getProperty(ISO8583Constant.RESPONSE_FIELD),
                            properties.getProperty(ISO8583Constant.FAILURE_RESPONSE_CODE));
                    byte[] msg = isoMsg.pack();
                    responseMessage = new String(msg).toUpperCase();
                }
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
    private Properties getPropertiesFile() {
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

    /**
     * Is this inbound endpoint act as a proxy to another service
     *
     * @return act as a proxy or not
     */
    private boolean isProxy() {
        Properties properties = this.params.getProperties();
        boolean isProxy = Boolean.parseBoolean(properties.getProperty(ISO8583Constant.INBOUND_ACT_AS_PROXY));
        return  isProxy;
    }
}
