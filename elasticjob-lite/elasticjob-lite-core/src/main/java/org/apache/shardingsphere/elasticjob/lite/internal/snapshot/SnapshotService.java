/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.elasticjob.lite.internal.snapshot;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.shardingsphere.elasticjob.lite.internal.util.SensitiveInfoUtils;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.apache.shardingsphere.elasticjob.reg.zookeeper.ZookeeperRegistryCenter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Snapshot service.
 */
@Slf4j
public final class SnapshotService {
    
    public static final String DUMP_COMMAND = "dump@";

    private final int port;
    
    private final CoordinatorRegistryCenter regCenter;
    
    private ServerSocket serverSocket;
    
    private volatile boolean closed;
    
    public SnapshotService(final CoordinatorRegistryCenter regCenter, final int port) {
        Preconditions.checkArgument(port >= 0 && port <= 0xFFFF, "Port value out of range: " + port);
        this.regCenter = regCenter;
        this.port = port;
    }
    
    /**
     * Start to listen.
     */
    public void listen() {
        try {
            log.info("ElasticJob: Snapshot service is running on port '{}'", openSocket(port));
        } catch (final IOException ex) {
            log.error("ElasticJob: Snapshot service listen failure, error is: ", ex);
        }
    }
    
    private int openSocket(final int port) throws IOException {
        serverSocket = new ServerSocket(port);
        int localPort = serverSocket.getLocalPort();
        String threadName = String.format("elasticjob-snapshot-service-%d", localPort);
        new Thread(() -> {
            while (!closed) {
                try {
                    process(serverSocket.accept());
                } catch (final IOException ex) {
                    if (isIgnoredException()) {
                        return;
                    }
                    log.error("ElasticJob: Snapshot service open socket failure, error is: ", ex);
                }
            }
        }, threadName).start();
        return localPort;
    }
    
    private boolean isIgnoredException() {
        return serverSocket.isClosed();
    }
    
    private void process(final Socket socket) throws IOException {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                Socket ignored = socket) {
            String cmdLine = reader.readLine();
            if (null != cmdLine && cmdLine.startsWith(DUMP_COMMAND) && cmdLine.split("@").length == 2) {
                List<String> result = new ArrayList<>();
                String jobName = cmdLine.split("@")[1];
                dumpDirectly("/" + jobName, jobName, result);
                outputMessage(writer, String.join("\n", SensitiveInfoUtils.filterSensitiveIps(result)) + "\n");
            }
        }
    }
    
    private void dumpDirectly(final String path, final String jobName, final List<String> result) {
        for (String each : regCenter.getChildrenKeys(path)) {
            String zkPath = path + "/" + each;
            String zkValue = Optional.ofNullable(regCenter.get(zkPath)).orElse("");
            String cachePath = zkPath;
            String cacheValue = zkValue;
            // TODO Decoupling ZooKeeper
            if (regCenter instanceof ZookeeperRegistryCenter) {
                TreeCache cache = (TreeCache) regCenter.getRawCache("/" + jobName);
                if (null != cache) {
                    Optional<ChildData> cacheData = Optional.ofNullable(cache.getCurrentData(zkPath));
                    cachePath = cacheData.map(ChildData::getPath).orElse("");
                    cacheValue = cacheData.map(ChildData::getData).map(String::new).orElse("");
                }
            }
            if (zkValue.equals(cacheValue) && zkPath.equals(cachePath)) {
                result.add(String.join(" | ", zkPath, zkValue));
            } else {
                result.add(String.join(" | ", zkPath, zkValue, cachePath, cacheValue));
            }
            dumpDirectly(zkPath, jobName, result);
        }
    }
    
    private void outputMessage(final BufferedWriter outputWriter, final String msg) throws IOException {
        outputWriter.append(msg);
        outputWriter.flush();
    }
    
    /**
     * Close listener.
     */
    public void close() {
        closed = true;
        if (null != serverSocket && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (final IOException ex) {
                log.error("ElasticJob: Snapshot service close failure, error is: ", ex);
            }
        }
    }
}
