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

/**
 * Class for handling the iso message request.
 * @since 1.0.1
 */
public class ConnectionRequestHandler implements Runnable {
    private static final Log log = LogFactory.getLog(ConnectionRequestHandler.class);
    private Socket connection;
    private ISOPackager packager;
    private ISO8583MessageInject msgInject;
    private DataInputStream inputStreamReader;
    private DataOutputStream outToClient;

    /**
     * connectionRequestHandler method to handle socket connections with InboundProcessorParams.
     * @param connection socket connection
     * @param params InboundProcessor params
     */
    public ConnectionRequestHandler(Socket connection, InboundProcessorParams params) {
        try {
            this.connection = connection;
            this.packager = ISO8583PackagerFactory.getPackager();
            this.msgInject = new ISO8583MessageInject(params, connection);
            this.inputStreamReader = new DataInputStream(connection.getInputStream());
            this.outToClient = new DataOutputStream(connection.getOutputStream());
        } catch (IOException e) {
            handleException("Couldn't read the input streams ", e);
        }
    }

    /**
     * connect method for read the request from inputStreamReader and inject into sequence.
     */
    public void connect() throws IOException {
        if (connection.isConnected()) {
            String fromClient = inputStreamReader.readUTF();
            outToClient.writeBytes("ISOMessage from " + Thread.currentThread().getName() + " is consumed :");
            ISOMsg isoMessage = unpackRequest(fromClient);
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
    private ISOMsg unpackRequest(String message) {
        ISOMsg isoMsg = null;
        try {
            isoMsg = new ISOMsg();
            isoMsg.setPackager(packager);
            isoMsg.unpack(message.getBytes());
        } catch (ISOException e) {
            handleISOException(message, e);
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
}