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
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_ALLOCATED_TO_HOST_COUNT;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_AVERAGE_POWER;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_RUNNING_ON_HOST_COUNT;
import eu.ascetic.zabbixdatalogger.datasource.types.ApplicationOnHost;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.MonitoredEntity;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;

/**
 * This data source adaptor connects directly into a collectd database.
 *
 * @author Richard Kavanagh
 */
public class CollectDInfluxDbDataSourceAdaptor implements DataSourceAdaptor, ApplicationDataSource {

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

    /**
     * Creates a new CollectD (via InfluxDB) data source adaptor).
     * @param hostname The hostname of the database to connect to
     * @param user The username for the database
     * @param password The password
     * @param dbName The database to connect to
     */
    public CollectDInfluxDbDataSourceAdaptor(String hostname, String user, String password, String dbName) {
        this.hostname = hostname;
        this.user = user;
        this.password = password;
        this.dbName = dbName;
        influxDB = InfluxDBFactory.connect(hostname, user, password);
    }

    @Override
    public Host getHostByName(String hostname) {
        HashMap<String, Host> hostList = getHostListAsHashMap();
        if (hostList.containsKey(hostname)) {
            return hostList.get(hostname);
        } else {
            return null;
        }
    }

    @Override
    public VmDeployed getVmByName(String name) {
        Host host = getHostListAsHashMap().get(name);
        if (host == null) {
            return null;
        }
        return new VmDeployed(host.getId(), host.getHostName());
    }

    @Override
    public List<Host> getHostList() {
        HashMap<String, Host> knownHosts = getHostListAsHashMap();
        return new ArrayList<>(knownHosts.values());
    }

    private HashMap<String, Host> getHostListAsHashMap() {
        HashMap<String, Host> knownHosts = new HashMap<>();
        QueryResult results = runQuery("SHOW TAG VALUES WITH KEY=host;");
        for (QueryResult.Result result : results.getResults()) {
            for (QueryResult.Series series : result.getSeries()) {
                for (List<Object> value : series.getValues()) {
                    if (!knownHosts.containsKey((String) value.get(1))) {
                        String hostId = ((String) value.get(1)).replaceAll("[^0-9]", "");
                        Host host = new Host(Integer.parseInt(hostId), (String) value.get(1));
                        knownHosts.put((String) value.get(1), host);
                    }
            }
        }
    }
        return knownHosts;
    } 
    
    @Override
    public List<MonitoredEntity> getHostAndVmList() {
        List<MonitoredEntity> answer = new ArrayList<>();
        for (Host host : getHostListAsHashMap().values()) {
            answer.add(host);
        }
        return answer;
    }

    @Override
    public List<VmDeployed> getVmList() {
        return new ArrayList<>();
    }

    @Override
    public List<ApplicationOnHost> getHostApplicationList(ApplicationOnHost.JOB_STATUS state) {
        return getHostApplicationList(); //Can't detect job state through influx
    }

    /**
     * This takes a query result from the data source and converts it into a
     * host measurement.
     *
     * @param host The host to convert the data for
     * @param results The result set to convert the data for
     * @return The host measurement
     */
    private List<ApplicationOnHost> convertToApplication(QueryResult results) {
        if (results == null) {
            return null;
        }
        List<ApplicationOnHost> answer = new ArrayList<>();
        for (QueryResult.Result result : results.getResults()) {
            if (result == null || result.getSeries() == null) {
                Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.WARNING,
                    "The conversion from InfluxDB to the programs internal "
                    + "representation of an application on a host failed!");
                return null;
            }
            for (QueryResult.Series series : result.getSeries()) {
                if (series == null || series.getValues() == null) {
                    Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.WARNING,
                        "The conversion from InfluxDB to the programs internal "
                        + "representation of an application on a host failed!");                    
                    return null;
                }
                for (List<Object> value : series.getValues()) {
                    /**
                     * 
                     * Example of metric: app_power:RK-Bench:3110
                     * 
                     * Clock, last(value), type, host, type_instance
                     * Clock, 0.0, 3110, ns50, RK-BENCH
                     * type_instance = app name
                     * type = app id
                     * 
                     */
                     ApplicationOnHost app = new ApplicationOnHost(Integer.getInteger(value.get(3) + ""), value.get(4) + "", getHostByName(value.get(3) + ""));
                     answer.add(app);
                }
            }
        }
        return answer;
    }    

    @Override
    public List<ApplicationOnHost> getHostApplicationList() {
        /**
         * Application power can list all applications that were running. It can therefore get the start and end times of any application as well.
         * 
         * A query such as: SELECT last(value), type, host, type_instance FROM 
         * app_power WHERE time > now() - 30s GROUP BY type, host;
         * 
         * Followed by first on the selected applications. 
         * 
         * should be effective
         */
        List<ApplicationOnHost> answer;
        QueryResult results = runQuery("SELECT last(value), type, host, type_instance FROM app_power WHERE time > now() - 30s GROUP BY type, host");
        answer = convertToApplication(results);
        return answer;
    }

    @Override
    public HostMeasurement getHostData(Host host) {
        if (host == null) {
            Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                        "The host to get data for was null"); 
            return null;
        }        
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
        QueryResult results = runQuery("SELECT last(value),type_instance, instance, type FROM " + listMeasurements + " WHERE host = '" + host.getHostName() + "' AND time > now() - 30s GROUP BY instance, type_instance, type;");
        answer = convertToHostMeasurement(host, results);
        return answer;
    }
    
    /**
     * This takes a query result from the data source and converts it into a
     * host measurement.
     *
     * @param host The host to convert the data for
     * @param results The result set to convert the data for
     * @return The host measurement
     */
    private HostMeasurement convertToHostMeasurement(Host host, QueryResult results) {
        if (results == null) {
            return null;
        }
         HostMeasurement answer = new HostMeasurement(host);
        double acceleratorPowerUsed = 0.0;
         for(QueryResult.Result result: results.getResults()) {
            if (result == null || result.getSeries() == null) {
                return null;
            }
            addCpuUtilisationInfo(answer, result);
             for (QueryResult.Series series : result.getSeries()) {
                if (series == null || series.getValues() == null) {
                    return null;
                }
                 for(List<Object> value : series.getValues()) {
                    Instant time = Instant.parse((String) value.get(0));
                    String metricName = series.getName() + ":" + (value.get(2) == null ? "" : value.get(2));
                    if (value.size() >= 4) {
                        metricName = metricName + ":" + (value.get(3) == null ? "" : value.get(3));
                    }
                    if (value.size() >= 5) {
                        metricName = metricName + ":" + (value.get(4) == null ? "" : value.get(4));
                    }
                    if (metricName.equals("power_value:estimated::power")) {
                        MetricValue estimatedPower = new MetricValue(KpiList.ESTIMATED_POWER_KPI_NAME, KpiList.ESTIMATED_POWER_KPI_NAME, value.get(1).toString(), time.getEpochSecond());
                        answer.addMetric(estimatedPower);
                    }
                    if (metricName.equals("power_value:measured::power")) {
                        MetricValue estimatedPower = new MetricValue(KpiList.POWER_KPI_NAME, KpiList.POWER_KPI_NAME, value.get(1).toString(), time.getEpochSecond());
                        answer.addMetric(estimatedPower);
                    }                      
                    /**
                     * This counts up all power consumed and reported by the
                     * monitoring infrastructure usually in the format:
                     * nvidia_value::0:nvidia:power (i.e. card 1)
                     * nvidia_value::1:nvidia:power (and card 2)
                     */
                    try {
                        if (metricName.matches("nvidia_value::[0-9]+:power")) {
                            acceleratorPowerUsed = acceleratorPowerUsed + Double.parseDouble(value.get(1).toString());
                        }
                    } catch (NumberFormatException ex) {
                        Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.WARNING, "Parsing input from collectd failed", ex);
                 }
                    MetricValue metric = new MetricValue(metricName, metricName, value.get(1).toString(), time.getEpochSecond());
                    answer.addMetric(metric);
                    if (time.getEpochSecond() > answer.getClock()) {
                        answer.setClock(time.getEpochSecond());
                    }
             }
         }
        }
        if (acceleratorPowerUsed > 0) {
            MetricValue metric = new MetricValue(KpiList.ACCELERATOR_POWER_USED, KpiList.ACCELERATOR_POWER_USED, Double.toString(acceleratorPowerUsed), answer.getClock());
            answer.addMetric(metric);
        }
         return answer;
    }

    /**
     * This method appends to a host measurement cpu utilisation information.
     * @param measurement The host measurement to append
     * @param result The results that contain cpu utilisation information.
     */
    private HostMeasurement addCpuUtilisationInfo(HostMeasurement measurement, QueryResult.Result result) {
        double count = 0;
        double idleValue = 0;
        Instant time = null;
        for (QueryResult.Series series : result.getSeries()) {
            for (List<Object> value : series.getValues()) {
                time = Instant.parse((String) value.get(0));
                String metricName = series.getName() + ":" + (value.get(2) == null ? "" : value.get(2));
                if (value.size() >= 4) {
                    metricName = metricName + ":" + (value.get(3) == null ? "" : value.get(3));
                }
                if (value.size() >= 5) {
                    metricName = metricName + ":" + (value.get(4) == null ? "" : value.get(4));
                }   
                if (metricName.matches("cpu_value:idle:[0-9]+:percent")) {
                    count = count + 1;
                    idleValue = idleValue + Double.parseDouble(value.get(1).toString());
                }
            }  
        }
        if (count > 0 && time != null) {
            double idleMetricValue = idleValue / count;
            idleMetricValue = idleMetricValue / 100; //make sure its in the range 0..1 instead of 0..100
            MetricValue idle = new MetricValue(KpiList.CPU_IDLE_KPI_NAME, KpiList.CPU_IDLE_KPI_NAME, idleMetricValue + "", time.getEpochSecond());
            measurement.addMetric(idle);
            MetricValue spotCpu = new MetricValue(KpiList.CPU_SPOT_USAGE_KPI_NAME, KpiList.CPU_SPOT_USAGE_KPI_NAME, 1 - idleMetricValue + "", time.getEpochSecond());
            measurement.addMetric(spotCpu);             
        }
        return measurement;
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
        for (QueryResult.Result result : results.getResults()) {
            for (QueryResult.Series series : result.getSeries()) {
                for (List<Object> value : series.getValues()) {
                    answer.add((String) value.get(0));
                }
        }
        }
        return answer;
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
        return new ArrayList<>(); //VMs are not currently handled by this data source adaptor.
    }

    @Override
    public List<VmMeasurement> getVmData(List<VmDeployed> vmList) {
        return new ArrayList<>(); //VMs are not currently handled by this data source adaptor.
    }

    @Override
    public double getLowestHostPowerUsage(Host host) {
        if (host == null) {
            Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                        "The host to get the lowest power usage was null"); 
            return 0.0;
        }        
        QueryResult results = runQuery("SELECT min(value) FROM power_value WHERE host = '" + host.getHostName() + "'");
        return getSingleValueOut(results);
        }

    @Override
    public double getHighestHostPowerUsage(Host host) {
        if (host == null) {
            Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                        "The host to get highest power usage was null"); 
        return 0.0;
    }
        QueryResult results = runQuery("SELECT max(value) FROM power_value WHERE host = '" + host.getHostName() + "';");
        return getSingleValueOut(results);
    }

    @Override
    public double getCpuUtilisation(Host host, int durationSeconds) {
        if (host == null) {
            Logger.getLogger(CollectDInfluxDbDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                        "The host to get the CPU utilisation was null"); 
            return 0.0;
        }        
        /**
         * An example output of the query result looks like:
         * {"results":[{"series":[{"name":"cpu","columns":["time","value"],
         * "values":[["2015-06-06T14:55:27.195Z",90],["2015-06-06T14:56:24.556Z",90]]}]}]}
         * {"results":[{"series":[{"name":"databases","columns":["name"],"values":[["mydb"]]}]}]}
         */
        QueryResult results = runQuery("SELECT mean(value) FROM cpu_value WHERE host = '" + host.getHostName() + "' AND type='percent' AND type_instance = 'idle' AND time > now() - " + durationSeconds + "s");
        if (isQueryResultEmpty(results)) {
            return 0.0; //Not enough data to know therefore assume zero usage.
        }
        BigDecimal answer = BigDecimal.valueOf(1 - getSingleValueOut(results) / 100d);
        answer = answer.setScale(2, BigDecimal.ROUND_HALF_UP);
        return answer.doubleValue();
    }

    /**
     * This checks to see if the returned result is empty or not
     * @param results The query result to test for emptiness
     * @return If the query result is empty or not
     */
    private boolean isQueryResultEmpty(QueryResult results) {
        if (results.getResults() == null || results.getResults().isEmpty()) {
            return true;
        }
        QueryResult.Result result = results.getResults().get(0);
        if (result.getSeries() == null || result.getSeries().isEmpty()) {
            return true;
        }
        QueryResult.Series series = result.getSeries().get(0);
        return (series.getValues() == null || series.getValues().isEmpty());
    }

    /**
     * This parses the result of a query that provides a single result.
     *
     * @param results The result object to parse
     * @return The single value returned from the query.
     */
    private double getSingleValueOut(QueryResult results) {
        if (results.getResults() == null || results.getResults().isEmpty()) {
            return 0.0;
        }
        QueryResult.Result result = results.getResults().get(0);
        if (result.getSeries() == null || result.getSeries().isEmpty()) {
            return 0.0;
        }
        QueryResult.Series series = result.getSeries().get(0);
        if (series.getValues() == null || series.getValues().isEmpty()) {
            return 0.0;
        }
        List<Object> value = series.getValues().get(0);
        return (Double) value.get(1);
    }

    /**
     * This parses the result of a query that provides the average (such as for
     * CPU utilisation).
     *
     * @param results The result object to parse
     * @return The average value returned from the query.
     */
    private double getAverage(QueryResult results) {
        double total = 0.0;
        double count = 0;
        for (QueryResult.Result result : results.getResults()) {
            if (result.getSeries() == null || result.getSeries().isEmpty()) {
                return 0.0;
        }
            for (QueryResult.Series series : result.getSeries()) {
                for (List<Object> value : series.getValues()) {
                    count = count + 1;
                    total = total + (Double) value.get(1);
    }
    }
        }
        if (count == 0) {
            return 0;
    }
        return total / count;
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

    @Override
    public ApplicationMeasurement getApplicationData(ApplicationOnHost application) {
        Host host = application.getAllocatedTo();
        HostMeasurement measure = getHostData(host);
        if (measure == null) {
            return null;
        }
        ApplicationMeasurement answer = new ApplicationMeasurement(
            application,
            measure.getClock());
            answer.setMetrics(measure.getMetrics());
        List<ApplicationOnHost> appsOnThisHost = ApplicationOnHost.filter(getHostApplicationList(), measure.getHost());
        answer.addMetric(new MetricValue(APPS_ALLOCATED_TO_HOST_COUNT, APPS_ALLOCATED_TO_HOST_COUNT, appsOnThisHost.size() + "", measure.getClock()));
        //TODO change the assumption here regarding running applications
        //Must assume all applications are running, as can't get job status.
        answer.addMetric(new MetricValue(APPS_RUNNING_ON_HOST_COUNT, APPS_RUNNING_ON_HOST_COUNT, appsOnThisHost.size() + "", measure.getClock()));
        answer.addMetric(new MetricValue(APPS_AVERAGE_POWER, APPS_AVERAGE_POWER, getAverageAppPower(application) + "", measure.getClock()));
        //TODO add power consumption info? running energy for application?? or just utilisation information?? latter is best
        return answer;
    }
    
    /**
     * This gets for a given application the average power that it consumes,
     * over its lifetime.
     * @param application The application to query. 
     * @return The average power consumption of an application 
     */
    public double getAverageAppPower(ApplicationOnHost application) {
        QueryResult results = runQuery("SELECT mean(value) WHERE host= '" + application.getAllocatedTo().getHostName() + "' AND type = '" + application.getId() + "' FROM app_power");
        if (isQueryResultEmpty(results)) {
            return 0.0; //Not enough data to know therefore assume zero usage.
        }
        BigDecimal answer = BigDecimal.valueOf(1 - getSingleValueOut(results) / 100d);
        answer = answer.setScale(2, BigDecimal.ROUND_HALF_UP);
        return answer.doubleValue();                
    }
    
    /**
     * This gets for a given application the average power that it consumes,
     * over its lifetime.
     * @param applicationName The application to query.
     * @param hostname The hostname to query against, if null or empty runs against all hosts.
     * @return average power consumption for the application
     */
    public double getAverageAppPower(String applicationName, String hostname) {
        String hostQueryString = "";
        if (hostname != null && !hostname.isEmpty()) {
            hostQueryString = " AND host= '" + hostname + "' ";
        }
        QueryResult results = runQuery("SELECT mean(value) WHERE instance_type = '" + applicationName + "' " + hostQueryString + "FROM app_power");
        if (isQueryResultEmpty(results)) {
            return 0.0; //Not enough data to know therefore assume zero usage.
        }
        BigDecimal answer = BigDecimal.valueOf(1 - getSingleValueOut(results) / 100d);
        answer = answer.setScale(2, BigDecimal.ROUND_HALF_UP);
        return answer.doubleValue();                
    }    

    @Override
    public List<ApplicationMeasurement> getApplicationData() {
        return getApplicationData(getHostApplicationList());
    }

    @Override
    public List<ApplicationMeasurement> getApplicationData(List<ApplicationOnHost> appList) {
        if (appList == null) {
            return getApplicationData();
        }
        ArrayList<ApplicationMeasurement> answer = new ArrayList<>();
        for (ApplicationOnHost app : appList) {
            ApplicationMeasurement measurement = getApplicationData(app);
            if (measurement != null) {
                answer.add(measurement);
            }
        }
        return answer;
    }
       
}
