/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.control.common.controllers;

import org.apache.hyracks.api.application.IApplicationConfig;
import org.apache.hyracks.control.common.application.IniApplicationConfig;
import org.ini4j.Ini;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StopOptionHandler;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public class NCConfig implements Serializable {
    private static final long serialVersionUID = 2L;

    @Option(name = "-cc-host", usage = "Cluster Controller host name (required unless specified in config file)", required = false)
    public String ccHost = null;

    @Option(name = "-cc-port", usage = "Cluster Controller port (default: 1099)", required = false)
    public int ccPort = 1099;

    @Option(name = "-address", usage = "IP Address for NC (default: localhost)", required = false)
    public String ipAddress = InetAddress.getLoopbackAddress().getHostAddress();

    @Option(name = "-cluster-net-ip-address", usage = "IP Address to bind cluster listener (default: same as -address)", required = false)
    public String clusterNetIPAddress;

    @Option(name = "-cluster-net-port", usage = "IP port to bind cluster listener (default: random port)", required = false)
    public int clusterNetPort = 0;

    @Option(name = "-cluster-net-public-ip-address", usage = "Public IP Address to announce cluster listener (default: same as -cluster-net-ip-address)", required = false)
    public String clusterNetPublicIPAddress;

    @Option(name = "-cluster-net-public-port", usage = "Public IP port to announce cluster listener (default: same as -cluster-net-port; must set -cluser-net-public-ip-address also)", required = false)
    public int clusterNetPublicPort = 0;

    @Option(name = "-node-id", usage = "Logical name of node controller unique within the cluster (required unless specified in config file)", required = false)
    public String nodeId = null;

    @Option(name = "-data-ip-address", usage = "IP Address to bind data listener (default: same as -address)", required = false)
    public String dataIPAddress;

    @Option(name = "-data-port", usage = "IP port to bind data listener (default: random port)", required = false)
    public int dataPort = 0;

    @Option(name = "-data-public-ip-address", usage = "Public IP Address to announce data listener (default: same as -data-ip-address)", required = false)
    public String dataPublicIPAddress;

    @Option(name = "-data-public-port", usage = "Public IP port to announce data listener (default: same as -data-port; must set -data-public-ip-address also)", required = false)
    public int dataPublicPort = 0;

    @Option(name = "-result-ip-address", usage = "IP Address to bind dataset result distribution listener (default: same as -address)", required = false)
    public String resultIPAddress;

    @Option(name = "-result-port", usage = "IP port to bind dataset result distribution listener (default: random port)", required = false)
    public int resultPort = 0;

    @Option(name = "-result-public-ip-address", usage = "Public IP Address to announce dataset result distribution listener (default: same as -result-ip-address)", required = false)
    public String resultPublicIPAddress;

    @Option(name = "-result-public-port", usage = "Public IP port to announce dataset result distribution listener (default: same as -result-port; must set -result-public-ip-address also)", required = false)
    public int resultPublicPort = 0;

    @Option(name = "-retries", usage ="Number of attempts to contact CC before giving up (default = 5)")
    public int retries = 5;

    @Option(name = "-iodevices", usage = "Comma separated list of IO Device mount points (default: One device in default temp folder)", required = false)
    public String ioDevices = System.getProperty("java.io.tmpdir");

    @Option(name = "-net-thread-count", usage = "Number of threads to use for Network I/O (default: 1)")
    public int nNetThreads = 1;

    @Option(name = "-net-buffer-count", usage = "Number of network buffers per input/output channel (default:1)", required = false)
    public int nNetBuffers = 1;

    @Option(name = "-max-memory", usage = "Maximum memory usable at this Node Controller in bytes (default: -1 auto)")
    public int maxMemory = -1;

    @Option(name = "-result-time-to-live", usage = "Limits the amount of time results for asynchronous jobs should be retained by the system in milliseconds. (default: 24 hours)")
    public long resultTTL = 86400000;

    @Option(name = "-result-sweep-threshold", usage = "The duration within which an instance of the result cleanup should be invoked in milliseconds. (default: 1 minute)")
    public long resultSweepThreshold = 60000;

    @Option(name = "-result-manager-memory", usage = "Memory usable for result caching at this Node Controller in bytes (default: -1 auto)")
    public int resultManagerMemory = -1;

    @Option(name = "-app-nc-main-class", usage = "Application NC Main Class")
    public String appNCMainClass;

    @Option(name = "-config-file", usage = "Specify path to local configuration file (default: no local config)", required = false)
    public String configFile = null;

    @Argument
    @Option(name = "--", handler = StopOptionHandler.class)
    public List<String> appArgs;

    private transient Ini ini = null;

    private void loadINIFile() throws IOException {
        ini = IniUtils.loadINIFile(configFile);
        // QQQ This should default to cc/address if cluster.address not set, but
        // that logic really should be handled by the ini file sent from the CC
        ccHost = IniUtils.getString(ini, "cc", "cluster.address", ccHost);
        ccPort = IniUtils.getInt(ini, "cc", "cluster.port", ccPort);
        nodeId = IniUtils.getString(ini, "localnc", "id", nodeId);

        // Network ports

        ipAddress = IniUtils.getString(ini, "localnc", "address", ipAddress);

        clusterNetIPAddress = IniUtils.getString(ini, "localnc", "cluster.address", clusterNetIPAddress);
        clusterNetPort = IniUtils.getInt(ini, "localnc", "cluster.port", clusterNetPort);
        dataIPAddress = IniUtils.getString(ini, "localnc", "data.address", dataIPAddress);
        dataPort = IniUtils.getInt(ini, "localnc", "data.port", dataPort);
        resultIPAddress = IniUtils.getString(ini, "localnc", "result.address", resultIPAddress);
        resultPort = IniUtils.getInt(ini, "localnc", "result.port", resultPort);

        clusterNetPublicIPAddress = IniUtils.getString(ini, "localnc", "public.cluster.address", clusterNetPublicIPAddress);
        clusterNetPublicPort = IniUtils.getInt(ini, "localnc", "public.cluster.port", clusterNetPublicPort);
        dataPublicIPAddress = IniUtils.getString(ini, "localnc", "public.data.address", dataPublicIPAddress);
        dataPublicPort = IniUtils.getInt(ini, "localnc", "public.data.port", dataPublicPort);
        resultPublicIPAddress = IniUtils.getString(ini, "localnc", "public.result.address", resultPublicIPAddress);
        resultPublicPort = IniUtils.getInt(ini, "localnc", "public.result.port", resultPublicPort);

        retries = IniUtils.getInt(ini, "localnc", "retries", retries);

        // Directories
        ioDevices = IniUtils.getString(ini, "localnc", "iodevices", ioDevices);

        // Hyracks client entrypoint
        appNCMainClass = IniUtils.getString(ini, "localnc", "app.class", appNCMainClass);
    }

    /*
     * Once all @Option fields have been loaded from command-line or otherwise
     * specified programmatically, call this method to:
     * 1. Load options from a config file (as specified by -config-file)
     * 2. Set default values for certain derived values, such as setting
     *    clusterNetIpAddress to ipAddress
     */
    public void loadConfigAndApplyDefaults() throws IOException {
        if (configFile != null) {
            loadINIFile();
        }

        // "address" is the default for all IP addresses
        if (clusterNetIPAddress == null) clusterNetIPAddress = ipAddress;
        if (dataIPAddress == null) dataIPAddress = ipAddress;
        if (resultIPAddress == null) resultIPAddress = ipAddress;

        // All "public" options default to their "non-public" versions
        if (clusterNetPublicIPAddress == null) clusterNetPublicIPAddress = clusterNetIPAddress;
        if (clusterNetPublicPort == 0) clusterNetPublicPort = clusterNetPort;
        if (dataPublicIPAddress == null) dataPublicIPAddress = dataIPAddress;
        if (dataPublicPort == 0) dataPublicPort = dataPort;
        if (resultPublicIPAddress == null) resultPublicIPAddress = resultIPAddress;
        if (resultPublicPort == 0) resultPublicPort = resultPort;
    }

    /**
     * @return An IApplicationConfig representing this NCConfig.
     * Note: Currently this only includes the values from the configuration
     * file, not anything specified on the command-line. QQQ
     */
    public IApplicationConfig getAppConfig() {
        return new IniApplicationConfig(ini);
    }

    public void toCommandLine(List<String> cList) {
        cList.add("-cc-host");
        cList.add(ccHost);
        cList.add("-cc-port");
        cList.add(String.valueOf(ccPort));
        cList.add("-cluster-net-ip-address");
        cList.add(clusterNetIPAddress);
        cList.add("-cluster-net-port");
        cList.add(String.valueOf(clusterNetPort));
        cList.add("-cluster-net-public-ip-address");
        cList.add(clusterNetPublicIPAddress);
        cList.add("-cluster-net-public-port");
        cList.add(String.valueOf(clusterNetPublicPort));
        cList.add("-node-id");
        cList.add(nodeId);
        cList.add("-data-ip-address");
        cList.add(dataIPAddress);
        cList.add("-data-port");
        cList.add(String.valueOf(dataPort));
        cList.add("-data-public-ip-address");
        cList.add(dataPublicIPAddress);
        cList.add("-data-public-port");
        cList.add(String.valueOf(dataPublicPort));
        cList.add("-result-ip-address");
        cList.add(resultIPAddress);
        cList.add("-result-port");
        cList.add(String.valueOf(resultPort));
        cList.add("-result-public-ip-address");
        cList.add(resultPublicIPAddress);
        cList.add("-result-public-port");
        cList.add(String.valueOf(resultPublicPort));
        cList.add("-retries");
        cList.add(String.valueOf(retries));
        cList.add("-iodevices");
        cList.add(ioDevices);
        cList.add("-net-thread-count");
        cList.add(String.valueOf(nNetThreads));
        cList.add("-net-buffer-count");
        cList.add(String.valueOf(nNetBuffers));
        cList.add("-max-memory");
        cList.add(String.valueOf(maxMemory));
        cList.add("-result-time-to-live");
        cList.add(String.valueOf(resultTTL));
        cList.add("-result-sweep-threshold");
        cList.add(String.valueOf(resultSweepThreshold));
        cList.add("-result-manager-memory");
        cList.add(String.valueOf(resultManagerMemory));

        if (appNCMainClass != null) {
            cList.add("-app-nc-main-class");
            cList.add(appNCMainClass);
        }
        if (appArgs != null && !appArgs.isEmpty()) {
            cList.add("--");
            for (String appArg : appArgs) {
                cList.add(appArg);
            }
        }
    }

    public void toMap(Map<String, String> configuration) {
        configuration.put("cc-host", ccHost);
        configuration.put("cc-port", (String.valueOf(ccPort)));
        configuration.put("cluster-net-ip-address", clusterNetIPAddress);
        configuration.put("cluster-net-port", String.valueOf(clusterNetPort));
        configuration.put("cluster-net-public-ip-address", clusterNetPublicIPAddress);
        configuration.put("cluster-net-public-port", String.valueOf(clusterNetPublicPort));
        configuration.put("node-id", nodeId);
        configuration.put("data-ip-address", dataIPAddress);
        configuration.put("data-port", String.valueOf(dataPort));
        configuration.put("data-public-ip-address", dataPublicIPAddress);
        configuration.put("data-public-port", String.valueOf(dataPublicPort));
        configuration.put("result-ip-address", resultIPAddress);
        configuration.put("result-port", String.valueOf(resultPort));
        configuration.put("result-public-ip-address", resultPublicIPAddress);
        configuration.put("result-public-port", String.valueOf(resultPublicPort));
        configuration.put("retries", String.valueOf(retries));
        configuration.put("iodevices", ioDevices);
        configuration.put("net-thread-count", String.valueOf(nNetThreads));
        configuration.put("net-buffer-count", String.valueOf(nNetBuffers));
        configuration.put("max-memory", String.valueOf(maxMemory));
        configuration.put("result-time-to-live", String.valueOf(resultTTL));
        configuration.put("result-sweep-threshold", String.valueOf(resultSweepThreshold));
        configuration.put("result-manager-memory", String.valueOf(resultManagerMemory));

        if (appNCMainClass != null) {
            configuration.put("app-nc-main-class", appNCMainClass);
        }

    }
}