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
import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.packager.GenericPackager;

import java.util.Properties;

/**
 * class for get ISOPackager.
 */
public class ISO8583PackagerFactory {
    private static final Log log = LogFactory.getLog(ISO8583PackagerFactory.class);

    public static ISOPackager getPackager() {
        ISOPackager packager = null;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            packager = new GenericPackager(loader.getResourceAsStream(ISO8583Constant.PACKAGER));
        } catch (ISOException e) {
            handleException("Error while get the ISOPackager", e);
        }
        return packager;
    }

    public static ISOBasePackager getPackagerWithParams(InboundProcessorParams params) {
        ISOBasePackager packager = null;
        try {
            Properties properties = params.getProperties();
            int headerLength = Integer.parseInt(properties.getProperty(ISO8583Constant.INBOUND_HEADER_LENGTH));
            headerLength = headerLength > -1 ? headerLength : 0;
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            packager = new GenericPackager(loader.getResourceAsStream(ISO8583Constant.PACKAGER));
            packager.setHeaderLength(headerLength);
        } catch (NumberFormatException e) {
            handleException("One of the properties are of an invalid type", e);
        } catch (ISOException e) {
            handleException("Error while get the ISOPackager", e);
        }
        return packager;
    }

    /**
     * handle the Exception
     *
     * @param msg error message
     * @param e   an Exception
     */
    private static void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg);
    }
}
