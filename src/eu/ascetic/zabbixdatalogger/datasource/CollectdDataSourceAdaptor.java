/**
 * Copyright 2017 University of Leeds
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package eu.ascetic.zabbixdatalogger.datasource;

import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.MonitoredEntity;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jcollectd.agent.api.DataSource;
import org.jcollectd.agent.api.Notification;
import org.jcollectd.agent.api.Values;
import org.jcollectd.agent.protocol.Dispatcher;
import org.jcollectd.server.protocol.UdpReceiver;

/**
 * This adaptor allows for the use of CollectD as a data source.
 *
 * @author Richard Kavanagh
 */
public class CollectdDataSourceAdaptor implements DataSourceAdaptor, Dispatcher {

    private final HashMap<String, Host> knownHosts = new HashMap<>();
    private final HashMap<Host, HostMeasurement> recentMeasurements = new HashMap<>();
    private final UdpReceiver reciever = new UdpReceiver();
    private final Thread recieverThread = new Thread(reciever);
    
    public CollectdDataSourceAdaptor() {
        reciever.setDispatcher(this);
        recieverThread.setDaemon(true);
        recieverThread.start();
    }
    
    @Override
    public Host getHostByName(String hostname) {
        return knownHosts.get(hostname);
    }

    @Override
    public VmDeployed getVmByName(String name) {
        Host host = knownHosts.get(name);
        if (host == null) {
            return null;
        }
        return new VmDeployed(host.getId(), host.getHostName());
    }

    @Override
    public List<Host> getHostList() {
        return new ArrayList<>(knownHosts.values());
    }

    @Override
    public List<MonitoredEntity> getHostAndVmList() {
        List<MonitoredEntity> answer = new ArrayList<>();
        for (Host host : knownHosts.values()) {
            answer.add(host);
        }
        return answer;
    }

    @Override
    public List<VmDeployed> getVmList() {
        return new ArrayList<>();
    }

    @Override
    public HostMeasurement getHostData(Host host) {
        return recentMeasurements.get(host);
    }

    @Override
    public List<HostMeasurement> getHostData() {
        return new ArrayList<>(recentMeasurements.values());
    }

    @Override
    public List<HostMeasurement> getHostData(List<Host> hostList) {
        ArrayList<HostMeasurement> answer = new ArrayList<>();
        for (Host host : hostList) {

            if (recentMeasurements.containsKey(host)) {
                answer.add(recentMeasurements.get(host));
            }
        }
        return answer;
    }

    @Override
    public VmMeasurement getVmData(VmDeployed vm) {
        return null; //VMs are not currently handled by this data source adaptor.
    }

    @Override
    public List<VmMeasurement> getVmData() {
        return null; //VMs are not currently handled by this data source adaptor.
    }

    @Override
    public List<VmMeasurement> getVmData(List<VmDeployed> vmList) {
        return null; //VMs are not currently handled by this data source adaptor.
    }

    @Override
    public double getLowestHostPowerUsage(Host host) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getHighestHostPowerUsage(Host host) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public double getCpuUtilisation(Host host, int durationSeconds) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void dispatch(Values values) {
        Host host;
        //Populate the host list
        if (!knownHosts.containsKey(values.getHost())) {
            String hostId = values.getHost().replaceAll("[^0-9]", "");
            host = new Host(Integer.parseInt(hostId), values.getHost());
            knownHosts.put(values.getHost(), host);
        } else {
            host = knownHosts.get(values.getHost());
        }
        HostMeasurement measurement = recentMeasurements.get(host);
        if (measurement == null) {
            measurement = new HostMeasurement(host); 
        }
        if (values.getDataSource() != null) {
            parseWithDataSource(measurement, values);
        } else {
            parseWithoutDataSource(measurement, values);
        }
        recentMeasurements.put(host, measurement);
    }

    /**
     * This parses a values data packet when there is no Data source, this means
     * there is only one value in the list.
     *
     * @param toUpdate The Host measurement to update
     * @param values The values to add into the host measurement
     * @return The updated host measurement
     */
    private HostMeasurement parseWithoutDataSource(HostMeasurement toUpdate, Values values) {
        long clock = (values.getTime() >> 30);
        String name = values.getPlugin();
        if (values.getPluginInstance() != null) {
            name = name + " " + values.getPluginInstance();
        } else if (values.getTypeInstance() != null) {
            name = name + " " + values.getTypeInstance();
        }
        for (Number value : values.getData()) {
            MetricValue metric = new MetricValue(name, name, value.toString(), clock);
            toUpdate.addMetric(metric);
        }
        toUpdate.setClock(clock);
        return toUpdate;
    }

    /**
     * This parses a values data packet when there is a data source, this means
     * there is likely to be many values in the list.
     *
     * @param toUpdate The Host measurement to update
     * @param values The values to add into the host measurement
     * @return The updated host measurement
     */
    private HostMeasurement parseWithDataSource(HostMeasurement toUpdate, Values values) {
        long clock = (values.getTime() >> 30);
        String name = values.getPlugin();
        if (values.getTypeInstance() != null) {
            name = name + " " + values.getTypeInstance();
        }
        if (values.getPluginInstance() != null) {
            name = name + " " + values.getPluginInstance();
        }
        int i = 0;
        List<DataSource> datasource = values.getDataSource();
        for (Number value : values.getData()) {
            String instanceName = name;
            if (datasource.get(i) != null && !datasource.get(i).getName().equals("value")) {
                instanceName = instanceName + " " + datasource.get(i).getName();
            }
            MetricValue metric = new MetricValue(instanceName, instanceName, value.toString(), clock);
            toUpdate.addMetric(metric);
        }
        toUpdate.setClock(clock);        
        return toUpdate;
    }

    @Override
    public void dispatch(Notification notification) {
        
        /**
         * Host: VM10-10-1-13
         * Severity: FAILURE
         * Data: Host VM10-10-1-13, plugin aggregation (instance cpu-average) type cpu (instance idle): Data source "value" is currently nan. That is within the failure region of 0.000000 and 12000.000000.
         * Message: Host VM10-10-1-13, plugin aggregation (instance cpu-average) type cpu (instance idle): Data source "value" is currently nan. That is within the failure region of 0.000000 and 12000.000000.
         * Plugin: aggregation
         * Plugin Instance: cpu-average
         * Source: VM10-10-1-13/aggregation/cpu-average/cpu/idle
         * Type: cpu
         * Type Instance: idle
         */
        System.out.println(notification);
        System.out.println("Host: " + notification.getHost());
        System.out.println("Severity: " + notification.getSeverity());        
        System.out.println("Data: " + notification.getData());
        System.out.println("Interval: " + notification.getInterval());
        System.out.println("Message: " + notification.getMessage());
        System.out.println("Plugin: " + notification.getPlugin());
        System.out.println("Plugin Instance: " + notification.getPluginInstance());
        System.out.println("Source: " + notification.getSource());
        System.out.println("Type: " + notification.getType());
        System.out.println("Type Instance: " + notification.getTypeInstance());
        
        System.out.println("The aim of this data source adaptor is to focus on reading Values");
    }

}
