/**
 * Copyright 2018 University of Leeds
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
package eu.ascetic.zabbixdatalogger;

import eu.ascetic.zabbixdatalogger.datasource.CollectDInfluxDbDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.CollectdDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.CompssDatasourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.DataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.HostMeasurement;
import eu.ascetic.zabbixdatalogger.datasource.SlurmDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.TangoEnvironmentDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.TangoRemoteProcessingDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.ZabbixDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.ZabbixDirectDbDataSourceAdaptor;
import eu.ascetic.zabbixdatalogger.datasource.compsstype.CompssImplementation;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;
import eu.ascetic.zabbixdatalogger.datasource.types.VmDeployed;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.logging.Level;

/**
 * This application logs out the raw data that is received from Zabbix, CollectD 
 * or SLURM.
 */
public class Logger {

    private static boolean running = true;

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            System.out.println("Please provide as the first argument the name of "
                    + "the host or virtual machine to monitor, such as testnode1.");
            System.exit(0);
        }
        String hostname = args[0];
        HashSet<String> strArgs = new HashSet<>();
        strArgs.addAll(Arrays.asList(args));
        MeasurementLogger logger = new MeasurementLogger(new File("Dataset_" + hostname + ".txt"), false);
        TaskLogger tasklogger = null;
        new Thread(logger).start();
        if (!(strArgs.contains("silent") || strArgs.contains("s"))) {
            System.out.println("This application will run continually until the word "
                    + "'quit' is written.)");
            System.out.println("It is currently logging data out for: " + hostname);
            System.out.println("This is being output to the file: Dataset_" + hostname + ".txt");
            QuitWatcher quitWatcher = new QuitWatcher();
            new Thread(quitWatcher).start();
        }
        DataSourceAdaptor adaptor;
        if ((strArgs.contains("json") || strArgs.contains("j"))) {
            adaptor = new ZabbixDataSourceAdaptor();
        } else if ((strArgs.contains("zabbix") || strArgs.contains("z"))) {
            adaptor = new ZabbixDirectDbDataSourceAdaptor();            
        } else if (strArgs.contains("influx")  || strArgs.contains("i")) {
            adaptor = new CollectDInfluxDbDataSourceAdaptor();
        } else if ((strArgs.contains("compss") || strArgs.contains("c"))) {
            adaptor = new CompssDatasourceAdaptor();
        } else if ((strArgs.contains("collectd") || strArgs.contains("d"))) {
            adaptor = new CollectdDataSourceAdaptor();          
            try {
                Thread.sleep(20000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.SEVERE, null, ex);
            }
            for (Host hostToList : adaptor.getHostList()) {
                System.out.println(hostToList.getHostName());
            }
        } else if ((strArgs.contains("slurm") || strArgs.contains("s"))) {
            adaptor = new SlurmDataSourceAdaptor();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if ((strArgs.contains("tango") || strArgs.contains("t"))) {
            adaptor = new TangoEnvironmentDataSourceAdaptor();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if ((strArgs.contains("remote") || strArgs.contains("r"))) {
            adaptor = new TangoRemoteProcessingDataSourceAdaptor();
            //Adding hostname avoids conflicts if multiple instances run at once
            tasklogger = new TaskLogger(new File("Dataset_compss_"+ hostname + ".txt"), false);
            new Thread(tasklogger).start();            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.SEVERE, null, ex);
            }               
        } else { //Zabbix is the default
            adaptor = new ZabbixDirectDbDataSourceAdaptor();
        }
        Host host = adaptor.getHostByName(hostname);
        VmDeployed vm = null;
        if (host == null) {
            vm = adaptor.getVmByName(hostname);
        }
        while (running) {
            HostMeasurement measurement = adaptor.getHostData(host);
            if (adaptor instanceof TangoRemoteProcessingDataSourceAdaptor && tasklogger != null) {
                for (CompssImplementation impl : ((TangoRemoteProcessingDataSourceAdaptor)adaptor).getCompssImplementation()) {
                    tasklogger.printToFile(impl);
                }
            }
            if (host != null && measurement != null) {               
                logger.printToFile(measurement);
            } else if (vm != null) {
                logger.printToFile(adaptor.getVmData(vm));
            } else {
                running = false;
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.INFO, "The resource named was not found");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(Logger.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        logger.stop();
    }

    /**
     * This looks for input from the console so that the application can be told
     * when to quit.
     */
    /**
     * This looks for input from the console so that the application can be told
     * when to quit.
     */
    private static class QuitWatcher implements Runnable {

        @Override
        public void run() {
            while (running) {
                Scanner scanner = new Scanner(System.in);
                String cmd = scanner.hasNext() ? scanner.next() : null;
                if (cmd != null && cmd.equals("quit")) {
                    running = false;
                }
            }
        }

    }
}
