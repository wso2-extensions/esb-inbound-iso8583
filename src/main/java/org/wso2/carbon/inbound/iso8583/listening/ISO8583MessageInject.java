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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.jpos.iso.ISOMsg;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;

import java.net.Socket;
import java.util.Properties;

/**
 * class for inject the iso xml messages into sequence.
 */
public class ISO8583MessageInject {
    private static final Log log = LogFactory.getLog(ISO8583MessageInject.class);
    private final String injectingSeq;
    private final String onErrorSeq;
    private final boolean sequential;
    private final SynapseEnvironment synapseEnvironment;
    private InboundProcessorParams params;
    private Socket connection;

    public ISO8583MessageInject(InboundProcessorParams params, Socket connection) {
        this.params = params;
        this.connection = connection;
        Properties properties = params.getProperties();
        this.injectingSeq = params.getInjectingSeq();
        this.onErrorSeq = params.getOnErrorSeq();
        this.sequential = Boolean.parseBoolean(properties.getProperty(ISO8583Constant.INBOUND_SEQUENTIAL));
        this.synapseEnvironment = params.getSynapseEnvironment();
    }

    /**
     * message builder is used to set the ISO8583 messages to the
     * message context and inject the message to the sequence.
     *
     * @param object iso fields.
     */
    public boolean inject(ISOMsg object) {
        org.apache.synapse.MessageContext msgCtx = createMessageContext();
        msgCtx.setProperty("inbound.endpoint.name", params.getName());
        InboundEndpoint inboundEndpoint = msgCtx.getConfiguration().getInboundEndpoint(params.getName());
        CustomLogSetter.getInstance().setLogAppender(inboundEndpoint.getArtifactContainerName());
        msgCtx.setProperty(ISO8583Constant.ISO8583_INBOUND_MSG_ID, msgCtx.getMessageID());

        if (StringUtils.isEmpty(injectingSeq)) {
            log.error("Seqence name not specified. Sequence : " + injectingSeq);
            return false;
        }
        SequenceMediator seq = (SequenceMediator) synapseEnvironment.getSynapseConfiguration()
                .getSequence(injectingSeq);
        try {
            OMElement parentElement = messageBuilder(object);
            msgCtx.getEnvelope().getBody().addChild(parentElement);
            ISO8583ReplySender replySender = new ISO8583ReplySender(connection);
            replySender.sendBack(msgCtx);
            if (seq != null) {
                seq.setErrorHandler(onErrorSeq);
                if (log.isDebugEnabled()) {
                    log.info("injecting message to sequence : " + injectingSeq);
                }
                synapseEnvironment.injectInbound(msgCtx, seq, sequential);
            } else {
                log.error("Sequence: " + injectingSeq + " not found");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return true;
    }

    /**
     * Create the initial message context for ISO8583
     * create a synapse environment to send ISO8583 message to Inbound.
     */
    private org.apache.synapse.MessageContext createMessageContext() {
        org.apache.synapse.MessageContext msgCtx = synapseEnvironment.createMessageContext();
        MessageContext axis2MsgCtx =
                ((org.apache.synapse.core.axis2.Axis2MessageContext) msgCtx).getAxis2MessageContext();
        axis2MsgCtx.setServerSide(true);
        axis2MsgCtx.setMessageID(UUIDGenerator.getUUID());
        msgCtx.setProperty(MessageContext.CLIENT_API_NON_BLOCKING, true);
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        axis2MsgCtx.setProperty(MultitenantConstants.TENANT_DOMAIN, carbonContext.getTenantDomain());
        return msgCtx;
    }

    /**
     * messageBuilder is used to build the xml iso messages.
     *
     * @param isomsg iso fields.
     */
    private OMElement messageBuilder(ISOMsg isomsg) {
        OMFactory OMfactory = OMAbstractFactory.getOMFactory();
        OMElement parentElement = OMfactory.createOMElement(ISO8583Constant.TAG_MSG, null);
        OMElement result = OMfactory.createOMElement(ISO8583Constant.TAG_DATA, null);
        for (int i = 0; i <= isomsg.getMaxField(); i++) {
            if (isomsg.hasField(i)) {
                String outputResult = isomsg.getString(i);
                OMElement messageElement = OMfactory.createOMElement(ISO8583Constant.TAG_FIELD, null);
                messageElement.addAttribute(OMfactory.createOMAttribute(ISO8583Constant.TAG_ID, null, String.valueOf(i)));
                messageElement.setText(outputResult);
                result.addChild(messageElement);
                parentElement.addChild(result);
            }
        }
        return parentElement;
    }
}