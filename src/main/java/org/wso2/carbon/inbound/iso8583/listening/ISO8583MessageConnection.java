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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.*;

/**
 * Class for maintain the socket connections.
 */
public class ISO8583MessageConnection extends Thread {
    private static final Log log = LogFactory.getLog(ISO8583MessageConnection.class);
    private int port;
    private ServerSocket server;
    private InboundProcessorParams params;
    private ExecutorService threadPool;
    private boolean listening = false;

    public ISO8583MessageConnection(int port, InboundProcessorParams params) {
        this.port = port;
        this.params = params;
        this.threadPool = getExecutorService();
    }

    /**
     * create the threadPool to handle the concurrent request.
     */
    private ExecutorService getExecutorService() {
        Properties properties = params.getProperties();
        String coreThreads = properties.getProperty(ISO8583Constant.INBOUND_CORE_THREADS);
        String maxThreads = properties.getProperty(ISO8583Constant.INBOUND_MAX_THREADS);
        String threadSafeTime = properties.getProperty(ISO8583Constant.INBOUND_THREAD_ALIVE);
        String queueLength = properties.getProperty(ISO8583Constant.INBOUND_THREAD_QLEN);
        try {
            if ((StringUtils.isNotEmpty(coreThreads)) && (StringUtils.isNotEmpty(maxThreads)) &&
                    (StringUtils.isNotEmpty(threadSafeTime)) && (StringUtils.isNotEmpty(queueLength))) {
                BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(Integer.parseInt(queueLength));
                threadPool = new ThreadPoolExecutor(Integer.parseInt(coreThreads), Integer.parseInt(maxThreads)
                        , Integer.parseInt(threadSafeTime), TimeUnit.SECONDS, workQueue);
            } else {
                BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(
                        Integer.parseInt(ISO8583Constant.THREAD_QLEN));
                threadPool = new ThreadPoolExecutor(Integer.parseInt(ISO8583Constant.CORE_THREADS),
                        Integer.parseInt(ISO8583Constant.MAX_THREADS), Integer.parseInt(ISO8583Constant.KEEP_ALIVE),
                        TimeUnit.SECONDS, workQueue);
            }
        } catch (NumberFormatException e) {
            handleException("One of the property or properties of thread specified is of an invalid type", e);
        }
        return threadPool;
    }

    /**
     * create the server socket which is to accept a connection from a client.
     */
    public void run() {
        try {
            server = new ServerSocket(port);
            log.info("Server is listening on port :" + port);
            while (!listening) {
                try {
                    Socket socketConnection = server.accept();
                    if (log.isDebugEnabled()) {
                        log.debug("Client connected to socket: " + socketConnection.toString());
                    }
                    handleClientRequest(socketConnection, params);
                } catch (IOException e1) {
                    log.warn("Exception occurred while accept the connections", e1);
                }
            }
        } catch (IOException e) {
            handleException("Server could not listen on port " + port, e);
        } finally {
            try {
                server.close();
            } catch (IOException e) {
                log.error("Error while closing the serverSocket", e);
            }
        }
    }

    /**
     * destroy the inbound and connections.
     */
    public void destroyConnection() {
        log.info("destroy the connection");
        try {
            if (!server.isClosed()) {
                server.close();
                log.info("Server stop the listening on port:" + port);
                listening = false;
            }
        } catch (IOException e) {
            handleException("Couldn't close the server", e);
        }
    }

    /**
     * handle the client request
     *
     * @param connection Socket connection
     * @param params     the InboundProcessorParams
     */
    private void handleClientRequest(Socket connection, InboundProcessorParams params) {
        try {
            threadPool.execute(new ConnectionRequestHandler(connection, params));
        } catch (RejectedExecutionException re) {
            // If the pool is full complete the execution with the same thread
            log.warn("Worker pool has reached the maximum capacity.", re);
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