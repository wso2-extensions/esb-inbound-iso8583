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
        getExecutorService();
    }

    /**
     * create the threadPool to handle the concurrent request.
     */
    public void getExecutorService() {
        Properties properties = params.getProperties();
        String coreThreads = properties.getProperty(ISO8583Constant.INBOUND_CORE_THREADS);
        String maxThreads = properties.getProperty(ISO8583Constant.INBOUND_MAX_THREADS);
        String threadSafeTime = properties.getProperty(ISO8583Constant.INBOUND_THREAD_ALIVE);
        String queueLength = properties.getProperty(ISO8583Constant.INBOUND_THREAD_QLEN);
        try {
            if ((!StringUtils.isEmpty(coreThreads)) && (!StringUtils.isEmpty(maxThreads)) &&
                    (!StringUtils.isEmpty(threadSafeTime)) && (!StringUtils.isEmpty(queueLength))) {
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
    }

    /**
     * create the server socket which is to accept a connection from a client.
     */
    private void createConnection() throws IOException {
        server = new ServerSocket(port);
        log.info("Server is listening on port :" + port);
        while (!listening) {
            Socket socketConnection = server.accept();
            log.debug("Client connected to socket: " + socketConnection.toString());
            handleClientRequest(socketConnection, params);
        }
    }

    public void run() {
        try {
            createConnection();
        } catch (IOException e) {
            log.warn("Exception occurred while create socket or accept the connections", e);
            reCreateConnection();
        } finally {
            try {
                server.close();
            } catch (IOException e) {
                log.error("Error while closing the serverSocket", e);
            }
        }
    }

    /**
     * reCreate the socket connections if there is any hardware failure or application crash happens.
     */
    private void reCreateConnection() {
        int retryInterval = ISO8583Constant.DEFAULT_RETRY_INTERVAL;
        try {
            if (!server.isClosed()) {
                server.close();
            }
            if (server.isClosed()) {
                log.info("Attempting to re create socket connections" + " in " + retryInterval + " ms");
                try {
                    Thread.sleep(retryInterval);
                    createConnection();
                } catch (InterruptedException e1) {
                    log.error("Error while create socket connections" + " in " + retryInterval + " ms", e1);
                } catch (IOException e2) {
                    handleException("Server could not listen or accept any connections", e2);
                }
            }
        } catch (IOException e) {
            handleException("Couldn't close the server", e);
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
            log.warn("Worker pool has reached the maximum capacity.");
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
