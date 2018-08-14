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
 *
 * This is being developed for the TANGO Project: http://tango-project.eu
 *
 */
package eu.ascetic.zabbixdatalogger.datasource;

import eu.ascetic.zabbixdatalogger.datasource.types.ApplicationOnHost;
import eu.ascetic.zabbixdatalogger.datasource.types.MonitoredEntity;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This adaptor allows for the gathering of environment information from both
 * CollectD (via InfluxDB) and SLURM, this represents the most information that
 * may be obtained from the Tango testbed.
 *
 * The reason for this class is the InfluxDB is very good at time series data, but
 * SLURM has information that is useful such as a list of current applications that
 * are running. It is therefore useful to get a hybrid data source that can get
 * information from both.
 *
 * @author Richard Kavanagh
 */
public class TangoEnvironmentDataSourceAdaptor implements DataSourceAdaptor, ApplicationDataSource {

    private final SlurmDataSourceAdaptor slurm = new SlurmDataSourceAdaptor();
    private final CollectDInfluxDbDataSourceAdaptor collectD = new CollectDInfluxDbDataSourceAdaptor();

    private final HashMap<Host, Host> collectdToSlurm = new HashMap<>();
    private final HashMap<Host, Host> slurmToCollectD = new HashMap<>();

    /**
     * This creates a new data source adaptor that queries both SLURM and CollectD.
     */
    public TangoEnvironmentDataSourceAdaptor() {
        super();
    }

    @Override
    public Host getHostByName(String hostname) {
        /**
         * SLURM provides better information about the hosts that are present
         * i.e. static information about the host
         */
        return slurm.getHostByName(hostname);
    }

    @Override
    public VmDeployed getVmByName(String name) {
        return slurm.getVmByName(name);
    }

    @Override
    public List<Host> getHostList() {
        /**
         * SLURM provides better information about the hosts that are present
         * i.e. static information about the host
         */
        return slurm.getHostList();
    }

    @Override
    public List<MonitoredEntity> getHostAndVmList() {
        /**
         * SLURM provides better information about the adaptors that are present
         * i.e. static information about the host
         */
        return slurm.getHostAndVmList();
    }

    @Override
    public List<VmDeployed> getVmList() {
        return slurm.getVmList();
    }

    @Override
    public List<ApplicationOnHost> getHostApplicationList(ApplicationOnHost.JOB_STATUS state) {
        /**
         * SLURM provides this information while CollectD does not.
         */
        return slurm.getHostApplicationList(state);
    }

    @Override
    public List<ApplicationOnHost> getHostApplicationList() {
        /**
         * SLURM provides this information while CollectD does not.
         */
        return slurm.getHostApplicationList();
    }

    @Override
    public HostMeasurement getHostData(Host host) {
        if (host == null) {
            return null;
        }
        HostMeasurement answer = slurm.getHostData(host);
        //Adds various information such as memory usage, including static upper bound values.
        Host collectDhost = convertNames(host);
        if (collectDhost != null) {
            if (answer == null) {
                answer = collectD.getHostData(collectDhost);
                answer.setHost(host); //This ensures a collectD host is not leaked
            } else {
                HostMeasurement data = collectD.getHostData(collectDhost);
                if (data.metricExists(KpiList.CPU_IDLE_KPI_NAME)) {
                    //Ensure that collectd based measures of utilisation take precedence
                    answer.deleteMetric(KpiList.CPU_IDLE_KPI_NAME);
                    answer.deleteMetric(KpiList.CPU_SPOT_USAGE_KPI_NAME);                    
                    answer.addMetric(data.getMetric(KpiList.CPU_IDLE_KPI_NAME));
                    answer.addMetric(data.getMetric(KpiList.CPU_SPOT_USAGE_KPI_NAME));
                }
                answer.addMetrics(data);
                
            }
        }
        return answer;
    }

    @Override
    public List<HostMeasurement> getHostData() {
        List<HostMeasurement> answer = new ArrayList<>();
        for (Host host : slurm.getHostList()) {
            HostMeasurement measurement = getHostData(host);
            if (measurement != null) {
                answer.add(measurement);
            }
        }
        return answer;
    }

    @Override
    public List<HostMeasurement> getHostData(List<Host> hostList) {
        if (hostList == null) {
            hostList = new ArrayList<>();
        }
        List<HostMeasurement> answer = new ArrayList<>();
        for (Host host : hostList) {
            HostMeasurement measurement = getHostData(host);
            if (measurement != null) {
                answer.add(measurement);
                measurement.setHost(host);
            }
        }
        return answer;
    }

    @Override
    public VmMeasurement getVmData(VmDeployed vm) {
        return slurm.getVmData(vm);
    }

    @Override
    public List<VmMeasurement> getVmData() {
        return slurm.getVmData();
    }

    @Override
    public List<VmMeasurement> getVmData(List<VmDeployed> vmList) {
        return slurm.getVmData(vmList);
    }

    @Override
    public double getLowestHostPowerUsage(Host host) {
        return collectD.getLowestHostPowerUsage(convertNames(host));
    }

    @Override
    public double getHighestHostPowerUsage(Host host) {
        return collectD.getHighestHostPowerUsage(convertNames(host));
    }

    @Override
    public double getCpuUtilisation(Host host, int durationSeconds) {
        return collectD.getCpuUtilisation(convertNames(host), durationSeconds);
    }

    public Host convertNames(Host host) {
        //If it contains bullx then it is from collectd
        if (host.getHostName().contains(".bullx")) {
            if (collectdToSlurm.containsKey(host)) {
                return collectdToSlurm.get(host);
            } else {
                collectdToSlurm.put(host, slurm.getHostByName(host.getHostName().substring(0, host.getHostName().length() - 6)));
                slurmToCollectD.put(slurm.getHostByName(host.getHostName().substring(0, host.getHostName().length() - 6)), host);
            }
        } else { //Slurm host received to get the collectd host
            if (slurmToCollectD.containsKey(host)) {
                return slurmToCollectD.get(host);
            } else {
                Host collectDHost = collectD.getHostByName(host.getHostName() + ".bullx");
                if (collectDHost == null) {
                    Logger.getLogger(TangoEnvironmentDataSourceAdaptor.class.getName()).log(Level.WARNING,
                        "Running Collectd host detection fix"); 
                    collectDHost = new Host(host.getId(), host.getHostName() + ".bullx", host);
                }
                collectdToSlurm.put(collectDHost, host);
                slurmToCollectD.put(host, collectDHost);
            }
        }
        return collectdToSlurm.get(host);
    }

    @Override
    public ApplicationMeasurement getApplicationData(ApplicationOnHost application) {
        return slurm.getApplicationData(application);
    }

    @Override
    public List<ApplicationMeasurement> getApplicationData() {
        return slurm.getApplicationData();
    }

    @Override
    public List<ApplicationMeasurement> getApplicationData(List<ApplicationOnHost> appList) {
        return slurm.getApplicationData(appList);
    }  

}
