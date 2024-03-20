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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

/**
 * Class for handling the iso message request.
 */
public class ConnectionRequestHandler implements Runnable {
    private static final Log log = LogFactory.getLog(ConnectionRequestHandler.class);
    private Socket connection;
    private ISOPackager packager;
    private ISO8583MessageInject msgInject;
    private DataInputStream inputStreamReader;
    private DataOutputStream outToClient;
    private InboundProcessorParams parameters;

    public ConnectionRequestHandler(Socket connection, InboundProcessorParams params) {
        try {
            this.connection = connection;
            this.packager = ISO8583PackagerFactory.getPackagerWithParams(params);
            this.msgInject = new ISO8583MessageInject(params, connection);
            this.inputStreamReader = new DataInputStream(connection.getInputStream());
            this.outToClient = new DataOutputStream(connection.getOutputStream());
            this.parameters = params;
        } catch (IOException e) {
            handleException("Couldn't read the input streams ", e);
        }
    }

    /**
     * connect method for read the request from inputStreamReader and inject into sequence.
     */
    public void connect() throws IOException {
        if (connection.isConnected() && inputStreamReader.available() >0) {
            int messageLength = inputStreamReader.available();
            byte[] message = new byte[messageLength];
            inputStreamReader.readFully(message, 0, messageLength);
            ISO8583Version iso8583Version = getISO8583Version(message);
            if (ISO8583Version.NINTEEN_EIGHTY_SEVEN == iso8583Version) {
                this.packager = ISO8583PackagerFactory.getPackagerWithParamsForVersion(this.parameters, ISO8583Version.NINTEEN_EIGHTY_SEVEN);
            } else {
                this.packager = ISO8583PackagerFactory.getPackagerWithParamsForVersion(this.parameters, ISO8583Version.NINTEEN_NINTY_THREE);
            }
            ISOMsg isoMessage = unpackRequest(message);
            msgInject.setIso8583Version(iso8583Version);
            msgInject.inject(isoMessage);

        }
    }

    public void run() {
        try {
            connect();
        } catch (IOException e) {
            handleException("Client may be disconnect the connection", e);
        } finally {
            try {
                inputStreamReader.close();
                connection.close();
            } catch (IOException e) {
                log.error("Couldn't close I/O streams", e);
            }
        }
    }

    /**
     * unpack the string iso message to obtain its fields.
     *
     * @param message String ISOMessage
     */
    private ISOMsg unpackRequest(byte[] message) {
        ISOMsg isoMsg = null;
        try {
            isoMsg = new ISOMsg();
            isoMsg.setPackager(packager);
            isoMsg.unpack(message);
        } catch (ISOException e) {
            handleISOException(Arrays.toString(message), e);
        }
        return isoMsg;
    }

    /**
     * handle the ISOMessage which is not in the ISO Standard.
     *
     * @param message String ISOMessage
     */
    private void handleISOException(String message, ISOException e) {
        try {
            outToClient.writeBytes("Request ISO message is not in ISO Standard :" + message);
            handleException("Couldn't unpack the message since financial message is not in ISO Standard", e);
        } catch (IOException e1) {
            handleException("OutputStream may be closed ", e1);
        }
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

    private ISO8583Version getISO8583Version (byte[] message) {
        log.debug("Received ISO message: " + message);
        Character versionIndicator = new String(message).charAt(0);
        if (versionIndicator == '0') {
            log.debug("IS08583 v1987 identified.");
            return ISO8583Version.NINTEEN_EIGHTY_SEVEN;
        } else if (versionIndicator == '1') {
            log.debug("IS08583 v1993 identified.");
            return ISO8583Version.NINTEEN_NINTY_THREE;
        } else {
            log.error("The format of the message is not supported.");
            throw new UnsupportedOperationException("The format of the message is not supported.");
        }
    }
}
