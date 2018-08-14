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

import eu.ascetic.ioutils.Settings;
import eu.ascetic.zabbixdatalogger.datasource.types.MonitoredEntity;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.annotation.Column;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import java.time.Instant;
import org.influxdb.impl.InfluxDBResultMapper;

/**
 * This data source adaptor connects directly into a collectd database.
 *
 * @author Richard Kavanagh
 */
public class CollectDInfluxDbDataSourceAdaptor implements DataSourceAdaptor {

    private final HashMap<String, Host> knownHosts = new HashMap<>();
    private final Settings settings = new Settings(CONFIG_FILE);
    private static final String CONFIG_FILE = "energy-modeller-influx-db-config.properties";
    private final String hostname;
    private final String user;
    private final String password;
    private final String dbName;

    private final InfluxDB influxDB;
    private final InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();

    public CollectDInfluxDbDataSourceAdaptor() {
        dbName = settings.getString("energy.modeller.influx.db.name", "collectd");
        user = settings.getString("energy.modeller.influx.db.user", "");
        password = settings.getString("energy.modeller.influx.db.password", "");
        hostname = settings.getString("energy.modeller.influx.db.hostname", "http://ns54.bullx:8086");
        influxDB = InfluxDBFactory.connect(hostname, user, password);
        if (settings.isChanged()) {
            settings.save(CONFIG_FILE);
        }
    }

    public CollectDInfluxDbDataSourceAdaptor(String hostname, String user, String password, String dbName) {
        this.hostname = hostname;
        this.user = user;
        this.password = password;
        this.dbName = dbName;
        influxDB = InfluxDBFactory.connect(hostname, user, password);
    }

    @Override
    public Host getHostByName(String hostname) {
        populateHostList();
        if (knownHosts.containsKey(hostname)) {
            return knownHosts.get(hostname);
        } else {
            return null;
        }
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
        populateHostList();
        return new ArrayList<>(knownHosts.values());
    }

    /**
     * This ensures the hostlist is fully populated before querying.
     */
    private void populateHostList() {
        QueryResult results = runQuery("SHOW TAG VALUES WITH KEY=host;");
        List<HostList> hosts = resultMapper.toPOJO(results, HostList.class);
        for (HostList item : hosts) {
            if (!knownHosts.containsKey(item.value)) {
                String hostId = hostname.replaceAll("[^0-9]", "");
                Host host = new Host(Integer.parseInt(hostId), item.value);
                knownHosts.put(hostId, host);
            }
        }
    }

    @org.influxdb.annotation.Measurement(name = "host_tags")
    public class HostList {

        @Column(name = "key")
        private String key;
        @Column(name = "value")
        private String value;
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
        HostMeasurement answer;
        String listMeasurements = "";
        ArrayList<String> measurements = getMeasurements();
        for (String measurement : measurements) {
            if (listMeasurements.isEmpty()) {
                listMeasurements = measurement;
            } else {
                listMeasurements = listMeasurements + ", " + measurement;
            }
        }
        QueryResult results = runQuery("SELECT last(value),type_instance FROM " + listMeasurements + " WHERE host = " + host.getHostName() + ";");
        answer = convertToHostMeasurement(host, results);
        return answer;
    }
    
    private HostMeasurement convertToHostMeasurement(Host host, QueryResult results) {
         HostMeasurement answer = new HostMeasurement(host);
         for(QueryResult.Result result: results.getResults()) {
             for (QueryResult.Series series : result.getSeries()) {
                 for(List<Object> value : series.getValues()) {
                     System.out.println(value);
                 }
                 //MetricValue metric = new MetricValue(series.getName(),series.getName(),0, 0);
             }
         }
         return answer;
    }

    @Override
    public List<HostMeasurement> getHostData() {
        return getHostData(getHostList());
    }

    /**
     * This lists which metrics are available.
     */
    private ArrayList<String> getMeasurements() {
        ArrayList<String> answer = new ArrayList<>();
        QueryResult results = runQuery("show measurements");
        List<MeasurementName> measurements = resultMapper.toPOJO(results, MeasurementName.class);
        for (MeasurementName measurement : measurements) {
            answer.add(measurement.name);
        }
        return answer;
    }
    
    @org.influxdb.annotation.Measurement(name = "measurements")
    public class MeasurementName {
        @Column(name = "name")
        private String name;
    }    

    @Override
    public List<HostMeasurement> getHostData(List<Host> hostList) {
        ArrayList<HostMeasurement> answer = new ArrayList<>();
        for (Host host : hostList) {
            answer.add(getHostData(host));
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

    @org.influxdb.annotation.Measurement(name = "power_value")
    public class CurrentPower {

        @Column(name = "time")
        private Instant time;
        @Column(name = "last(value)")
        private double value;
    }

    @Override
    public double getLowestHostPowerUsage(Host host) {
        QueryResult results = runQuery("SELECT min(value) FROM power_value WHERE host = '" + host.getHostName() + "'");
        List<LowestPower> power = resultMapper.toPOJO(results, LowestPower.class);
        for (LowestPower item : power) {
            return item.value;
        }
        return 0.0;
    }

    @org.influxdb.annotation.Measurement(name = "power_value")
    public class LowestPower {

        @Column(name = "time")
        private Instant time;
        @Column(name = "min(value)")
        private double value;
    }

    @Override
    public double getHighestHostPowerUsage(Host host) {
        QueryResult results = runQuery("SELECT max(value) FROM power_value WHERE host = '" + host.getHostName() + "';");
        List<HighestPower> power = resultMapper.toPOJO(results, HighestPower.class);
        for (HighestPower item : power) {
            return item.value;
        }
        return 0.0;
    }

    @org.influxdb.annotation.Measurement(name = "power_value")
    public class HighestPower {

        @Column(name = "time")
        private Instant time;
        @Column(name = "max(value)")
        private double value;
    }

    @Override
    public double getCpuUtilisation(Host host, int durationSeconds) {
        // {"results":[{"series":[{"name":"cpu","columns":["time","value"],
        // "values":[["2015-06-06T14:55:27.195Z",90],["2015-06-06T14:56:24.556Z",90]]}]}]}
        // {"results":[{"series":[{"name":"databases","columns":["name"],"values":[["mydb"]]}]}]}
        long time = ((TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - durationSeconds) << 30);
        QueryResult results = runQuery("SELECT mean(value) FROM cpu_value WHERE host = '" + host.getHostName() + "' AND type_instance = 'idle' time > " + time);
        List<RecentCpu> cpuList = resultMapper.toPOJO(results, RecentCpu.class);
        for (RecentCpu item : cpuList) {
            return item.value;
        }
        return 0.0;
    }

    @org.influxdb.annotation.Measurement(name = "power_value")
    public class RecentCpu {

        @Column(name = "time")
        private Instant time;
        @Column(name = "mean(value)")
        private double value;
    }

    /**
     * Runs the query against the influxdb database
     *
     * @param queryStr The string representation of the query
     * @return The query's result set
     */
    private QueryResult runQuery(String queryStr) {
        Query query = new Query(queryStr, dbName);
        return influxDB.query(query);
    }
}
