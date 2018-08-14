/**
 * Copyright 2016 University of Leeds
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

import eu.ascetic.ioutils.Settings;
import eu.ascetic.zabbixdatalogger.datasource.types.Accelerator;
import eu.ascetic.zabbixdatalogger.datasource.types.ApplicationOnHost;
import eu.ascetic.zabbixdatalogger.datasource.types.ApplicationOnHost.JOB_STATUS;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.MonitoredEntity;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_ALLOCATED_TO_HOST_COUNT;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_RUNNING_ON_HOST_COUNT;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.APPS_STATUS;

/**
 * This requests information for SLURM via the command "scontrol show node="
 *
 * @author Richard Kavanagh
 */
public class SlurmDataSourceAdaptor implements DataSourceAdaptor, ApplicationDataSource {

    private Tailer tailer;
    private final HashMap<String, Host> hosts = new HashMap<>();
    private final HashMap<String, HostMeasurement> lowest = new HashMap<>();
    private final HashMap<String, HostMeasurement> highest = new HashMap<>();
    private final HashMap<String, HostMeasurement> current = new HashMap<>();
    private SlurmDataSourceAdaptor.SlurmTailer fileTailer;
    private SlurmPoller poller;
    private final Settings settings = new Settings("energy-modeller-slurm-config.properties");
    private final HashMap<String, CircularFifoQueue<SlurmDataSourceAdaptor.CPUUtilisation>> cpuMeasure = new HashMap<>();

    public SlurmDataSourceAdaptor() {
        startup(1);
    }

    /**
     * This is the generic startup code for the Slurm data source adaptor
     *
     * @param interval The interval at which to take logging data.
     */
    public final void startup(int interval) {
        String filename = settings.getString("energy.modeller.slurm.scrape.file", "slurm-host-data.log");
        boolean useFileScraper = settings.getBoolean("energy.modeller.slurm.scrape.from.file", false);
        if (useFileScraper) {
            File scrapeFile = new File(filename);
            fileTailer = new SlurmDataSourceAdaptor.SlurmTailer();
            tailer = Tailer.create(scrapeFile, fileTailer, (interval * 1000) / 16, true);
            Thread tailerThread = new Thread(tailer);
            tailerThread.setDaemon(true);
            tailerThread.start();
            System.out.println("Scraping from Slurm output file");
        } else {
            //Parse directly from SLURM
            String hostsList = settings.getString("energy.modeller.slurm.hosts", "ns[52-53]");
            poller = new SlurmPoller(hostsList, interval);
            Thread pollerThread = new Thread(poller);
            pollerThread.setDaemon(true);
            pollerThread.start();
            System.out.println("Scraping from Slurm directly");
        }
        if (settings.isChanged()) {
            settings.save("energy-modeller-slurm-config.properties");
        }        
    }

    /**
     * This obtains the current cluster level power cap from SLURM. In the event 
     * the value isn't read correctly the value Double.NaN is provided instead.
     *
     * @return The current power cap value
     */
    public static double getCurrentPowerCap() {
        ArrayList<String> powerStr = execCmd("scontrol show power");
        if (powerStr.isEmpty()) {
            return Double.NaN;
        }
        try {
            String[] values = powerStr.get(0).split(" ");
            for (String value : values) {
                if (value.startsWith("PowerCap")) {
                    return Double.parseDouble(value.split("=")[1]);
                }
            }
        } catch (NumberFormatException ex) {

        }
        return Double.NaN;
    }    

    /**
     * The host string from SLURM follows a format that must be parsed into a
     * list of separate hosts. This method achieves this.
     *
     * @param hostList The list of hosts in a compressed format. examples
     * include: "ns54" or "ns[54-56]" or "ns[54-56],ns[58-60]" or "ns54,ns56"
     * @return The list of hosts in an array ready for processing.
     */
    private ArrayList<String> getHostList(String hostList) {
        ArrayList<String> answer = new ArrayList<>();
        String[] partialAnswer = hostList.split(",");
        for (String part : partialAnswer) {
            if (part.contains("[")) { //test if host is in range i.e. node[51-54]
                String hostPrefix = part.substring(0, part.indexOf("["));
                try (Scanner parser = new Scanner(part).useDelimiter("[^0-9]+")) {
                    int start = parser.nextInt();
                    int end = parser.nextInt();
                    for (int i = start; i <= end; i++) {
                        answer.add(hostPrefix + i);
                    }
                }
            } else {
                answer.add(part); //Host name is singular, e.g. ns54
            }
        }
        return answer;
    }

    @Override
    public Host getHostByName(String hostname) {
        return hosts.get(hostname);
    }

    @Override
    public VmDeployed getVmByName(String name) {
        Host host = hosts.get(name);
        if (host == null) {
            return null;
        }
        VmDeployed answer = new VmDeployed(host.getId(), host.getHostName());
        return answer;
    }

    @Override
    public List<Host> getHostList() {
        ArrayList<Host> answer = new ArrayList<>();
        answer.addAll(hosts.values());
        return answer;
    }

    @Override
    public List<MonitoredEntity> getHostAndVmList() {
        ArrayList<MonitoredEntity> answer = new ArrayList<>();
        answer.addAll(hosts.values());
        return answer;
    }

    @Override
    public List<VmDeployed> getVmList() {
        return new ArrayList<>();
    }

    @Override
    public List<ApplicationOnHost> getHostApplicationList() {
        return getHostApplicationList(null);
    }

    /**
     * This filters a list of applications by their current status
     *
     * @param apps The list of applications to filter
     * @param state The status to filter the job by
     * @return The list of filtered applications
     */
    public List<ApplicationOnHost> getHostApplicationList(List<ApplicationOnHost> apps, JOB_STATUS state) {
        List<ApplicationOnHost> answer = new ArrayList<>();
        for (ApplicationOnHost app : apps) {
            if (app.getStatus().equals(state)) {
                answer.add(app);
            }
        }
        return answer;
    }

    @Override
    public List<ApplicationOnHost> getHostApplicationList(ApplicationOnHost.JOB_STATUS state) {
        ArrayList<ApplicationOnHost> answer = new ArrayList<>();

        /**
         * squeue has various jobs states that are possible: see:
         * https://slurm.schedmd.com/squeue.html
         *
         * namely: PENDING (PD), RUNNING (R), SUSPENDED (S), STOPPED (ST),
         * COMPLETING (CG), COMPLETED (CD), CONFIGURING (CF), CANCELLED (CA),
         * FAILED (F), TIMEOUT (TO), PREEMPTED (PR), BOOT_FAIL (BF) , NODE_FAIL
         * (NF), REVOKED (RV), and SPECIAL_EXIT (SE)
         */
        String jobState;
        if (state == null) {
            jobState = "";
        } else {
            jobState = "-t " + state.name();
        }

        /*
         * This queries what jobs are currently running, it outputs
         * "JOBID, NAME, TIME, NODELIST (REASON)"
         * "JOBID, JOB_NAME, USER, STATUS, TIME, MAX_TIME, NODELIST (REASON)"
         * One line per job and space separated.
         * 
         * squeue -t RUNNING -l | awk 'NR> 1 {split($0,values,"[ \t\n]+"); 
         * printf values[1] " " ; printf values[2] " "; printf values[4] " "; 
         * printf values[5] " "; printf values[6] " "; printf values[7] " " ; 
         * printf values[8] " "; print values[10]}'
         * -l parameter swapped out with -o "%.30i %.30P %.50j %.30u %.30T %.30M %.30l %.30D %R"
         * as per: https://slurm.schedmd.com/squeue.html. This changes the length 
         * of things such as the job name and helps avoid truncation
         *
         * The output looks like:
         * 
         * 3009 RK-BENCH Kavanagr RUNNING 8:06 ns52
         *        
         */ 
        String maincmd = "squeue " + jobState + " -o \"%.30i %.50j %.30u %.30T %.30M %.20l %R\""
                + " | awk 'NR> 1 {split($0,values,\"[ \\t\\n]+\"); "
                + "printf values[1] \" \"; "
                + "printf values[2] \" \"; "
                + "printf values[3] \" \"; "
                + "printf values[4] \" \"; "
                + "printf values[5] \" \"; "
                + "printf values[6] \" \"; "
                + "printf values[7] \" \"; "                
                + "print values[8]}'";
        ArrayList<String> output = execCmd(maincmd);
        for (String line : output) { //Each line represents a different application
            if (line != null && !line.isEmpty()) {
                line = line.trim();
                String[] items = line.split(" ");
                try {
                    int appId = Integer.parseInt(items[0]);
                    String name = items[1];
                    String status = items[3];
                    long runningTime = parseDurationString(items[4]); //units seconds
                    long maxRuntime = parseDurationString(items[5]); //units seconds
                    long currentTime = System.currentTimeMillis(); //units milliseconds
                    long startTime = currentTime - TimeUnit.SECONDS.toMillis(runningTime); //unit milliseconds
                    GregorianCalendar start = new GregorianCalendar();
                    GregorianCalendar deadline = null;
                    start.setTimeInMillis(startTime);
                    if (maxRuntime != 0) {
                        deadline = new GregorianCalendar();
                        deadline.setTimeInMillis(startTime + TimeUnit.SECONDS.toMillis(maxRuntime));
                    }
                    ArrayList<String> hostStrings = getHostList(items[6]);
                    for (String hostStr : hostStrings) {
                        Host host = getHostByName(hostStr);
                        ApplicationOnHost app = new ApplicationOnHost(appId, name, host);
                        app.setCreated(start);
                        app.setDeadline(deadline);
                        if (state != null) {
                            app.setStatus(state);
                        } else {
                            app.setStatus(status);
                        }
                        answer.add(app);
                    }
                } catch (NumberFormatException ex) {
                    Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                            "Unexpected number format", ex);
                }
            }
        }
        return answer;
    }

    /**
     * This takes a string in the format 0:03 i.e. mins:seconds and converts it
     * into seconds
     *
     * @param duration The string to parse
     * @return The time in seconds the duration string translates to.
     */
    public static long parseDurationString(String duration) {
        long seconds = 0;
        //If this is used to parse maximum runtime then the value may be UNLIMITED, thus this is guarded against
        if (duration == null || duration.isEmpty() || !duration.matches("\\d+(:\\d+)*?") || duration.equals("UNLIMITED")) {
            return 0;
        }
        String[] durationSplit = duration.split(":"); //0:03 i.e. mins:seconds or 1:0:0 i.e. 1 hour
        switch (durationSplit.length) {
            case 1:
                seconds = seconds + Long.parseLong(durationSplit[0]);
                break;
            case 2:
                seconds = seconds + TimeUnit.MINUTES.toSeconds(Long.parseLong(durationSplit[0]));
                seconds = seconds + Long.parseLong(durationSplit[1]);
                break;
            case 3:
                seconds = seconds + TimeUnit.HOURS.toSeconds(Long.parseLong(durationSplit[0]));
                seconds = seconds + TimeUnit.MINUTES.toSeconds(Long.parseLong(durationSplit[1]));
                seconds = seconds + Long.parseLong(durationSplit[2]);
                break;
        }
        return seconds;
    } 
    public HostMeasurement getHostData(Host host) {
        return current.get(host.getHostName());
    }

    @Override
    public List<HostMeasurement> getHostData() {
        ArrayList<HostMeasurement> answer = new ArrayList<>();
        answer.addAll(current.values());
        return answer;
    }

    @Override
    public List<HostMeasurement> getHostData(List<Host> hostList) {
        if (hostList == null) {
            return getHostData();
        }
        ArrayList<HostMeasurement> answer = new ArrayList<>();
        for (Host host : hostList) {
            HostMeasurement value = current.get(host.getHostName());
            if (value != null) {
                answer.add(value);
            }
        }
        return answer;
    }

    @Override
    public VmMeasurement getVmData(VmDeployed vm) {
        for (HostMeasurement measure : current.values()) {
            if (measure.getHost().getHostName().equals(vm.getName())) {
                VmMeasurement answer = new VmMeasurement(vm, measure.getClock());
                answer.setMetrics(measure.getMetrics());
                return answer;
            }
        }
        return null;
    }

    @Override
    public List<VmMeasurement> getVmData() {
        ArrayList<VmMeasurement> answer = new ArrayList<>();
        for (HostMeasurement measure : current.values()) {
            VmMeasurement vmData = new VmMeasurement(
                    getVmByName(measure.getHost().getHostName()),
                    measure.getClock());
            vmData.setMetrics(measure.getMetrics());
            answer.add(vmData);
        }
        return answer;
    }

    @Override
    public List<VmMeasurement> getVmData(List<VmDeployed> vmList) {
        if (vmList == null) {
            return getVmData();
        }
        ArrayList<VmMeasurement> answer = new ArrayList<>();
        for (VmDeployed vm : vmList) {

            HostMeasurement measure = current.get(vm.getName());
            VmMeasurement vmData = new VmMeasurement(
                    getVmByName(measure.getHost().getHostName()),
                    measure.getClock());
            vmData.setMetrics(measure.getMetrics());
            answer.add(vmData);
        }
        return answer;
    }

    /**
     * This provides for the named application all the information that is
     * available.
     *
     * @param application The host to get the measurement data for.
     * @return The host measurement data
     */
    @Override
    public ApplicationMeasurement getApplicationData(ApplicationOnHost application) {
        if (application == null) {
            return null;
        }
        HostMeasurement measure = current.get(application.getAllocatedTo().getHostName());
        ApplicationMeasurement appData = new ApplicationMeasurement(
                application,
                measure.getClock());
        appData.setMetrics(measure.getMetrics());
        appendApplicationData(appData, measure);
        return appData;
    }

    /**
     * This lists for all applications all the metric data on them.
     *
     * @return A list of application measurements
     */
    @Override
    public List<ApplicationMeasurement> getApplicationData() {
        return getApplicationData(getHostApplicationList());
    }

    /**
     * This takes a list of applications and provides all the metric data on
     * them.
     *
     * @param appList The list of applications to get the data from
     * @return A list of application measurements
     */
    @Override
    public List<ApplicationMeasurement> getApplicationData(List<ApplicationOnHost> appList) {
        if (appList == null) {
            appList = getHostApplicationList();
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

    /**
     * This takes an application measurement and adds onto it the metrics for
     * the applications status, along with a count of applications both running
     * and allocated to the host
     *
     * @param appData The application specific data
     * @param measure The host measurement to take the data from
     * @return The application measurement with more data appended into it.
     */
    private ApplicationMeasurement appendApplicationData(ApplicationMeasurement appData, HostMeasurement measure) {
        List<ApplicationOnHost> appsOnThisHost = ApplicationOnHost.filter(getHostApplicationList(), measure.getHost());
        List<ApplicationOnHost> appsRunningOnThisHost = getHostApplicationList(appsOnThisHost, JOB_STATUS.RUNNING);
        //loop through the refreshed data and update the apps job status.
        for (ApplicationOnHost app : appsOnThisHost) {
            if (app.equals(appData.getApplication())) {
                appData.getApplication().setStatus(app.getStatus());
                break;
            }
        }
        appData.addMetric(new MetricValue(APPS_STATUS, APPS_STATUS, appData.getApplication().getStatus().name(), measure.getClock()));
        appData.addMetric(new MetricValue(APPS_ALLOCATED_TO_HOST_COUNT, APPS_ALLOCATED_TO_HOST_COUNT, appsOnThisHost.size() + "", measure.getClock()));
        appData.addMetric(new MetricValue(APPS_RUNNING_ON_HOST_COUNT, APPS_RUNNING_ON_HOST_COUNT, appsRunningOnThisHost.size() + "", measure.getClock()));
        return appData;
    }

    @Override
    public double getLowestHostPowerUsage(Host host) {
        return lowest.get(host.getHostName()).getPower();
    }

    @Override
    public double getHighestHostPowerUsage(Host host) {
        return highest.get(host.getHostName()).getPower();
    }

    @Override
    public double getCpuUtilisation(Host host, int durationSeconds) {
        //TODO note duration in seconds is ignored, fix this.
        return current.get(host.getHostName()).getCpuUtilisation();
    }

    /**
     * Given a key this returns its parameter's value.
     *
     * @param key The key name for the parameter to return
     * @param parseString The string to split and get the property value from
     * @return The value of the parameter else null.
     */
    public static String getValue(String key, String parseString) {
        String[] args = parseString.split(";");
        for (String arg : args) {
            if (arg.split("=")[0].trim().equalsIgnoreCase(key)) {
                return arg.split("=")[1].trim();
            }
        }
        return null;
    }

    /**
     * Given a key this returns its parameter's value.
     *
     * @param key The key name for the parameter to return
     * @param parseString The string to split and get the property value from
     * @return The value of the parameter else null.
     */
    public static String getValue(String key, String[] parseString) {
        for (String arg : parseString) {
            if (arg.split("=")[0].trim().equalsIgnoreCase(key)) {
                return arg.split("=")[1].trim();
            }
        }
        return null;
    }

    /**
     * This executes a command and returns the output as a line of strings.
     *
     * @param cmd The command to execute
     * @return A list of output broken down by line
     * @throws java.io.IOException
     */
    private static ArrayList<String> execCmd(String[] cmd) throws java.io.IOException {
        ArrayList<String> output = new ArrayList<>();
        Process proc = Runtime.getRuntime().exec(cmd);
        java.io.InputStream is = proc.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is);
        String outputLine;
        while (s.hasNextLine()) {
            outputLine = s.nextLine();
            output.add(outputLine);
        }
        return output;
    }

    /**
     * This executes a command and returns the output as a line of strings.
     *
     * @param cmd The command to execute
     * @return A list of output broken down by line
     * @throws java.io.IOException
     */
    private static ArrayList<String> execCmd(String mainCmd) {
        String cmd[] = {"/bin/sh",
            "-c",
            mainCmd};
        try {
            return execCmd(cmd); 
        } catch (IOException ex) {
            Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new ArrayList<>();
    }

    /**
     * This reads from a log file the output of slurm.
     */
    private class SlurmTailer extends TailerListenerAdapter {

        @Override
        public void handle(String line) {
            parse(line);
        }
    }

    private class SlurmPoller implements Runnable {

        private int pollRate = 1;
        private boolean running = true;
        private String hostString = "";

        /**
         * This creates a new polling thread,
         *
         * @param pollRate The rate in seconds of how fast to poll SLURM.
         */
        public SlurmPoller(String hostString, int pollRate) {
            this.hostString = hostString;
            this.pollRate = pollRate;
        }

        @Override
        public void run() {
            while (running) {
                String cmd = "scontrol show node=" + hostString + " -o -d | sed \"s/ /;/g\"";
                System.out.println("Command: " + cmd);
                ArrayList<String> lines = execCmd(cmd);
                for (String line : lines) {
                    System.out.println(line);
                    parse(line);
                }
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(pollRate));
                } catch (InterruptedException ex) {
                    Logger.getLogger(SlurmPoller.class.getName()).log(Level.SEVERE, "The data source was interupted.", ex);
                }
            }
        }

        /**
         * This stops the data gatherer from running.
         */
        public void stop() {
            running = false;
        }

        /**
         * @return the pollRate
         */
        public int getPollRate() {
            return pollRate;
        }

        /**
         * @param pollRate the pollRate to set
         */
        public void setPollRate(int pollRate) {
            this.pollRate = pollRate;
        }
    }

    /**
     * This reads in the Gres string, this string represents Generic Resource
     * Scheduling (GRES). Usually either GPUs or Intel Many Integrated Core
     * (MIC) processors
         *
         * @param values The parsed list of metrics.
         * @param measurement The measurement to add the metrics to
         * @param clock The timestamp for the new metric values
         * @return The newly adjusted measurement
         */
        private HostMeasurement readGresString(String[] values, HostMeasurement measurement, long clock) {
            String gresString = getValue("Gres", values);
        if (gresString.isEmpty()) {
            // This indicates that there is no accelerator
            gresString = "(null)";
        }
            /*
             * Example gresString = "gpu:teslak20:2"
             * or "craynetwork:3,hbm:0"
             * or gpus:2*cpu,disk=50G
             * The original string this is parsed from is: "Gres=gpu:teslak20:2"
             */
            boolean hasGraphicsCard = gresString.contains("gpu");
            boolean hasMic = gresString.contains("mic"); //Many Integrated Core
            boolean hasAccelerator = !gresString.equals("(null)");
            measurement.addMetric(new MetricValue(KpiList.HAS_GPU, KpiList.HAS_GPU, hasGraphicsCard + "", clock));
            measurement.addMetric(new MetricValue(KpiList.HAS_MIC, KpiList.HAS_MIC, hasMic + "", clock));
            measurement.addMetric(new MetricValue(KpiList.HAS_ACCELERATOR, KpiList.HAS_ACCELERATOR, hasAccelerator + "", clock));
        if (hasAccelerator == false) {
            return measurement;
        }
            String[] gresStringSplit = gresString.split(",");
            for (String dataItem : gresStringSplit) {
                boolean gpu = dataItem.contains("gpu");
                boolean mic = dataItem.contains("mic");
                String[] dataItemSplit = dataItem.split(":");
            String acceleratorName;
            int acceleratorCount = 1;
            if (dataItemSplit.length == 3) {
                acceleratorName = dataItemSplit[1]; //strings such as gpu:teslak20:2
                try {
                    acceleratorCount = Integer.parseInt(dataItemSplit[2].trim());
                } catch (NumberFormatException ex) {
                    Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                            "Unexpected number format", ex);
                }
            } else {
                acceleratorName = dataItemSplit[0]; //strings such as mic:2
                    try {
                    acceleratorCount = Integer.parseInt(dataItemSplit[1].trim());
                    } catch (NumberFormatException ex) {
                    Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                            "Unexpected number format", ex);
                    }
                }
                if (gpu) {
                measurement.getHost().addAccelerator(new Accelerator(acceleratorName, acceleratorCount, Accelerator.AcceleratorType.GPU));
                measurement.addMetric(new MetricValue(KpiList.GPU_NAME, KpiList.GPU_NAME, acceleratorName, clock));
                measurement.addMetric(new MetricValue(KpiList.GPU_COUNT, KpiList.GPU_COUNT, acceleratorCount + "", clock));
                } else if (mic) {
                measurement.getHost().addAccelerator(new Accelerator(acceleratorName, acceleratorCount, Accelerator.AcceleratorType.MIC));
                measurement.addMetric(new MetricValue(KpiList.MIC_NAME, KpiList.MIC_NAME, acceleratorName, clock));
                measurement.addMetric(new MetricValue(KpiList.MIC_COUNT, KpiList.MIC_COUNT, acceleratorCount + "", clock));
                }
            }
            return measurement;
        }
        
        /**
         * This reads in the GresUsed string, this string represents Generic
     * Resource Scheduling (GRES). Usually either GPUs or Intel Many Integrated
     * Core (MIC) processors
         *
         * @param values The parsed list of metrics.
         * @param measurement The measurement to add the metrics to
         * @param clock The timestamp for the new metric values
         * @return The newly adjusted measurement
         */        
        private HostMeasurement readGresUsedString(String[] values, HostMeasurement measurement, long clock) {        
            String gresString = getValue("GresUsed", values);
        if (gresString.isEmpty()) {
            return measurement;
        }
            String[] gresStringSplit = gresString.split(",");
            for (String dataItem : gresStringSplit) {
                boolean gpu = dataItem.contains("gpu");
                boolean mic = dataItem.contains("mic");
                String[] dataItemSplit = dataItem.split(":");
                String gpuUsed = "";
                for (String item : dataItemSplit) {
                    if(!item.isEmpty() && Character.isDigit(item.charAt(0))) {
                    try (Scanner scanner = new Scanner(item).useDelimiter("[^\\d]+")) {
                        int used = scanner.nextInt();
                        gpuUsed = used + "";
                    }
                    break;
                    }
                }
                if (gpu) {
                    measurement.addMetric(new MetricValue(KpiList.GPU_USED, KpiList.GPU_USED, gpuUsed, clock));
                } else if (mic) {
                    measurement.addMetric(new MetricValue(KpiList.MIC_USED, KpiList.MIC_USED, gpuUsed, clock));
                }

            }            
            return measurement;        
        }

        /**
     * This reads in generic metrics from SLURM that the data source adaptor is
     * not necessarily expecting.
         *
         * @param values The parsed list of metrics.
         * @param measurement The measurement to add the metrics to
         * @param clock The timestamp for the new metric values
         * @return The newly adjusted measurement
         */
        private HostMeasurement readGenericMetrics(String[] values, HostMeasurement measurement, long clock) {
            //The general case is to add all metric values into the list.
            for (String value : values) {
                String[] valueSplit = value.split("=");
                try {
                    switch (valueSplit.length) {
                        case 2: //Most common case
                            measurement.addMetric(new MetricValue(valueSplit[0].trim(), valueSplit[0].trim(), valueSplit[1].trim(), clock));
                            break;
                        case 1: //In cases such as AllocTRES, leave the metric there but report the empty value
                            measurement.addMetric(new MetricValue(valueSplit[0].trim(), valueSplit[0].trim(), "", clock));
                            break;
                        default: //Cases such as CfgTRES=cpu=32,mem=64408M
                            int params = value.split("=").length / 2;
                            valueSplit = value.split("[=,]");
                            /*
                             * CfgTRES=cpu=32,mem=64408M becomes
                             * [CfgTRES, cpu, 32, mem, 64408M]
                             * Thus end result should be:
                             * [CfgTRES:cpu, 32] i.e. index 0:1 , 2
                             * [CfgTRES:mem, 64408M] i.e. index 0:3 , 5
                             */
                        try {
                            for (int i = 1; i <= params; i++) {
                                String name = valueSplit[0].trim() + ":" + valueSplit[i * 2 - 1].trim();
                                measurement.addMetric(new MetricValue(name, name, valueSplit[i * 2].trim(), clock));
                            }
                        } catch (Exception ex) {
                            System.out.println("Parsing had an issue with : " + value);
                        }
                            break;
                    }
                } catch (Exception ex) {
                Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                        "An error occured while parsing from SLURM", ex);
                }
            }
            return measurement;
        }

    /**
     * Parses a line from slurm and adds the data into the data source adaptor.
     *
     * @param line
     */
    private void parse(String line) {
            try {
                boolean valid = true;
                GregorianCalendar calander = new GregorianCalendar();
                long clock = TimeUnit.MILLISECONDS.toSeconds(calander.getTimeInMillis());
                String[] values = line.split(";");
                //System.out.println(line);
                String watts = getValue("CurrentWatts", values);
                String wattskwh = getValue("ConsumedJoules", values);
                String hostname = getValue("NodeName", values);
            String state = getValue("State", values);
            String coreCount = getValue("CPUTot", values);
            if (hostname.isEmpty()) {
                return;
            }
                String hostId = hostname.replaceAll("[^0-9]", "");
            CircularFifoQueue<SlurmDataSourceAdaptor.CPUUtilisation> lastCpuMeasurements = cpuMeasure.get(hostname);
            if (lastCpuMeasurements == null) {
                //Needs enough information to cover any recent queries of cpu utilisation, thus gather last 10mins of data
                lastCpuMeasurements = new CircularFifoQueue<>((int) TimeUnit.MINUTES.toSeconds(10) / poller.getPollRate());
                cpuMeasure.put(hostname, lastCpuMeasurements);
            }
                Host host = getHostByName(hostname);

            //Check for need to discover host
                if (host == null) {
                    host = new Host(Integer.parseInt(hostId), hostname);
                if (coreCount.matches("-?\\d+(\\.\\d+)?")) {
                    host.setCoreCount(Integer.parseInt(coreCount));
                }
                    hosts.put(hostname, host);
            }
            host.setAvailable(!state.isEmpty() && !state.equals("DOWN*"));
            host.setState(state);
            /**
             * The further metrics from this host are not relevant and may cause
             * parse errors
             */
            if (!host.isAvailable()) {
                return;
                }

            //Note CPU Load = N/A when the node is down, but perhas might occur in some other case. The previous guard should prevent this error.
            String cpuLoad = getValue("CPULoad", values);
            if (!cpuLoad.equals("N/A") && cpuLoad.matches("-?\\d+(\\.\\d+)?")) {
                lastCpuMeasurements.add(new SlurmDataSourceAdaptor.CPUUtilisation(clock, hostname, (Double.valueOf(cpuLoad)) * 100));
            }
                HostMeasurement measurement = new HostMeasurement(host, clock);
                measurement.addMetric(new MetricValue(KpiList.POWER_KPI_NAME, KpiList.POWER_KPI_NAME, watts, clock));
                measurement.addMetric(new MetricValue(KpiList.ENERGY_KPI_NAME, KpiList.ENERGY_KPI_NAME, wattskwh, clock));
                readGresString(values, measurement, clock);
                readGresUsedString(values, measurement, clock);
                readGenericMetrics(values, measurement, clock);
            double cpuUtil = Double.valueOf(cpuLoad) / Double.valueOf(getValue("CPUTot", values));
                valid = valid && validatedAddMetric(measurement, new MetricValue(KpiList.CPU_SPOT_USAGE_KPI_NAME, KpiList.CPU_SPOT_USAGE_KPI_NAME, cpuUtil * 100 + "", clock));
                valid = valid && validatedAddMetric(measurement, new MetricValue(KpiList.CPU_IDLE_KPI_NAME, KpiList.CPU_IDLE_KPI_NAME, ((1 - cpuUtil)) * 100 + "", clock));
                if (!valid) {
                    System.out.println("The measurement taken was invalid");
                    return;
                }

                valid = valid && validatedAddMetric(measurement, new MetricValue(KpiList.MEMORY_AVAILABLE_KPI_NAME, KpiList.MEMORY_AVAILABLE_KPI_NAME, (int) (Double.valueOf(getValue("FreeMem", values)) / 1048576) + "", clock));
                valid = valid && validatedAddMetric(measurement, new MetricValue(KpiList.MEMORY_TOTAL_KPI_NAME, KpiList.MEMORY_TOTAL_KPI_NAME, (int) (Double.valueOf(getValue("RealMemory", values)) / 1048576) + "", clock));

                if (!valid) {
                    return;
                }
                current.put(hostname, measurement);
                if (lowest.get(hostname) == null || measurement.getPower() < lowest.get(hostname).getPower()) {
                    lowest.put(hostname, measurement);
                }
                if (highest.get(hostname) == null || measurement.getPower() > highest.get(hostname).getPower()) {
                    highest.put(hostname, measurement);
                }
            } catch (NumberFormatException ex) {
                //Ignore these errors and carry on. It may just be the header line.
            Logger.getLogger(SlurmDataSourceAdaptor.class.getName()).log(Level.SEVERE,
                    "Unexpected number format", ex);
            }
        }

        /**
     * This ensures that metric values are not added in cases where NaN etc is
     * given as an output from the data source.
         *
         * @param measurement The measurement to add the value to
         * @param value The value to add.
         * @return The measurement with the added metric only in cases where the
         * values correct.
         */
        private boolean validatedAddMetric(Measurement measurement, MetricValue value) {
            if (Double.isNaN(value.getValue()) || Double.isInfinite(value.getValue())) {
                System.out.println("Invalid Measure: " + value.getKey() + " : " + value.getName());
                return false;
            }
            measurement.addMetric(value);
            System.out.println("Adding Metric: " + value.getKey() + " : " + value.getName());
            return true;
    }

    /**
     * This is a CPU utilisation record for the Slurm data source adaptor.
     */
    private class CPUUtilisation {

        private final String hostname;
        private final long clock;
        private final double cpu;

        /**
         * This creates a new CPU Utilisation record
         *
         * @param clock the time when the CPU Utilisation was taken
         * @param hostname the name of the host the measurement is for
         * @param cpu The CPU utilisation.
         */
        public CPUUtilisation(long clock, String hostname, double cpu) {
            this.clock = clock;
            this.hostname = hostname;
            this.cpu = cpu;
        }

        /**
         * The time when this record was taken
         *
         * @return The UTC time for this record.
         */
        public long getClock() {
            return clock;
        }

        /**
         * This returns the percentage of time the CPU was idle.
         *
         * @return 0..1 for how idle the CPU was at a specified time frame.
         */
        public double getCpuIdle() {
            return 1 - cpu;
        }

        /**
         * This returns the percentage of time the CPU was busy.
         *
         * @return 0..1 for how busy the CPU was at a specified time frame.
         */
        public double getCpuBusy() {
            return cpu;
        }

        public String getHostname() {
            return hostname;
        }

        /**
         * This indicates if this CPU utilisation object is older than a
         * specified time.
         *
         * @param time The UTC time to compare to
         * @return If the current item is older than the date specified.
         */
        public boolean isOlderThan(long time) {
            return clock < time;
        }

    }

}
