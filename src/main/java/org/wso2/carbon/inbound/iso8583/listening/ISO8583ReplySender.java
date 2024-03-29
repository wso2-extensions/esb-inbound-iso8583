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
    private ISO8583Version iso8583Version;

    /**
     * keep the socket connection to send the response back to client.
     *
     * @param connection created socket connection.
     */
    public ISO8583ReplySender(Socket connection, InboundProcessorParams params, ISO8583Version iso8583Version) {
        this.connection = connection;
        this.params = params;
        this.iso8583Version = iso8583Version;
    }

    /**
     * Send the reply or response back to the client.
     *
     * @param messageContext to get xml iso message from message context.
     */
    @Override
    public void sendBack(MessageContext messageContext) {
        byte[] responseMessage = null;
        try {
            ISOBasePackager packager = ISO8583PackagerFactory.getPackagerWithParamsForVersion(params, iso8583Version);
            Properties properties = getPropertiesFile();
            //Retrieve the SOAP envelope from the MessageContext
            SOAPEnvelope soapEnvelope = messageContext.getEnvelope();
            OMElement getElements = soapEnvelope.getBody().getFirstElement();
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

            /* isProxy defines whether this inbound is acting as a proxy for
                another backend service or processing the message itself.
                if the inbound endpoint act as a proxy to another service
                pack the ISO message without change any field
             */
            if (isProxy()) {
                responseMessage = isoMsg.pack();
            } else {
                /* Set the response fields */
                if (isRequestMessageFunction(isoMsg.getMTI())) {
                    char requestResponseIdentifier =
                            properties.getProperty(ISO8583Constant.REQUEST_RESPONSE_MESSAGE_FUNCTION_IDENTIFIER).charAt(0);
                    String mTI = changeMTIMessageFunction(requestResponseIdentifier, isoMsg.getMTI());
                    isoMsg.setMTI(mTI);
                    /* Set the code for successful response */
                    String successResponseCode;
                    if (iso8583Version == ISO8583Version.NINTEEN_EIGHTY_SEVEN) {
                        successResponseCode = properties.getProperty(ISO8583Constant.SUCCESSFUL_RESPONSE_CODE_V87);
                    } else {
                        successResponseCode = properties.getProperty(ISO8583Constant.SUCCESSFUL_RESPONSE_CODE_V93);
                    }
                    isoMsg.set(properties.getProperty(ISO8583Constant.RESPONSE_FIELD), successResponseCode);
                    responseMessage = isoMsg.pack();
                } else {
                    /* Set the code for invalid transaction response */
                    String failureResponseCode;
                    if (iso8583Version == ISO8583Version.NINTEEN_EIGHTY_SEVEN) {
                        failureResponseCode = properties.getProperty(ISO8583Constant.FAILURE_RESPONSE_CODE_V87);
                    } else {
                        failureResponseCode = properties.getProperty(ISO8583Constant.FAILURE_RESPONSE_CODE_V93);
                    }
                    isoMsg.set(properties.getProperty(ISO8583Constant.RESPONSE_FIELD), failureResponseCode);
                    responseMessage = isoMsg.pack();
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
     * @param responseMessage byte of packed ISO response.
     */
    private void sendResponse(byte[] responseMessage) {
        try {
            DataOutputStream outToClient = new DataOutputStream(connection.getOutputStream());
            outToClient.write(responseMessage);
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

    private boolean isRequestMessageFunction(String mTI) {
        Properties properties = getPropertiesFile();
        int index = Integer.parseInt(properties.getProperty(ISO8583Constant.MESSAGE_FUNCTION_MTI_INDEX));
        String requestIdentifier = properties.getProperty(ISO8583Constant.REQUEST_MESSAGE_FUNCTION_IDENTIFIER);
        if (mTI.substring(index, index+1).equals(requestIdentifier)) {
            return true;
        } else {
            return false;
        }
    }

    private String changeMTIMessageFunction(char c, String mTI) {
        Properties properties = getPropertiesFile();
        int index = Integer.parseInt(properties.getProperty(ISO8583Constant.MESSAGE_FUNCTION_MTI_INDEX));
        StringBuilder sb = new StringBuilder(mTI);
        sb.setCharAt(index, c);
        return sb.toString();
    }
}
