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

import org.apache.commons.lang.StringUtils;
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
import java.util.Properties;

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
    private int headerLength = 0;
    private byte[] header;

    public ConnectionRequestHandler(Socket connection, InboundProcessorParams params) {
        try {
            this.connection = connection;
            Properties properties = params.getProperties();
            if (StringUtils.isNotEmpty(properties.getProperty(ISO8583Constant.INBOUND_HEADER_LENGTH))) {
                this.headerLength = Integer.parseInt(properties.getProperty(ISO8583Constant.INBOUND_HEADER_LENGTH));
            }
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
            try {
                String fromClient;
                /*
                jpos sever supports the ISO message with the HeaderLength 0,2 and 4.
                If the headerLength is 2 or 4, get the ISO message Length from the header value
                and then read ISO message as byte using that messageLength.
                Otherwise(headerLength=0) read the ISO message as String.
                 */
                if (headerLength != 0) {
                    int messageLength = getMessageLength(headerLength);
                    byte[] message = new byte[messageLength];
                    getMessage(message, 0, messageLength);
                    fromClient = new String(message);
                } else {
                    fromClient = inputStreamReader.readUTF();
                }
                ISOMsg isoMessage = unpackRequest(fromClient);
                isoMessage.setHeader(header);
                msgInject.inject(isoMessage);
            } catch (ISOException e) {
                handleException("Couldn't read length of the message ", e);
            }
        }
    }

    /**
     * Get the ISO message from the input steam reader
     * @param message the buffer into which the message is read
     * @param offset the start offset of the message.
     * @param length the length of the message
     * @throws IOException
     * @throws ISOException
     */
    protected void getMessage(byte[] message, int offset, int length) throws IOException, ISOException {
        inputStreamReader.readFully(message, offset, length);
    }

    /**
     * Get the message length
     * @param headerLength the length oh the header
     * @return
     * @throws IOException
     * @throws ISOException
     */
    protected int getMessageLength(int headerLength) throws IOException, ISOException {
        if (headerLength == 4) {
            header = new byte[4];
            /*
             The size of the message will be sent by the client using the first 4 bytes of the header.
              Get that header and decoding it and getting the message size
             */
            inputStreamReader.readFully(header, 0, 4);
            return (header[0] & 0xFF) << 24 | (header[1] & 0xFF) << 16 | (header[2] & 0xFF) << 8 | header[3] & 0xFF;
        } else {
            header = new byte[2];
            inputStreamReader.readFully(header, 0, 2);
            return (header[0] & 0xFF) << 8 | header[1] & 0xFF;
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
