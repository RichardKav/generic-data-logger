/**
 * Copyright 2014 University of Leeds
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

import eu.ascetic.ioutils.GenericLogger;
import eu.ascetic.ioutils.ResultsStore;
import eu.ascetic.zabbixdatalogger.datasource.Measurement;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;

/**
 * This class logs out measurement data to disk.
 * @author Richard Kavanagh
 */
public class MeasurementLogger extends GenericLogger<Measurement> {

    private final ArrayList<String> metricNames = new ArrayList<>();

    public MeasurementLogger(File file, boolean overwrite) {
        super(file, overwrite);
        saveFile.setDelimeter("\t");
    }

    /**
     * This writes a measurement out to disk for the purpose of auditing what is
     * going on. It by default overwrites the previous file
     *
     * @param store The results store to save data to
     * @param measurements The measurement to write to file
     */
    @Override
    public void saveToDisk(ResultsStore store, Collection<Measurement> measurements) {
        try {
            if (!store.getResultsFile().exists() || metricNames.isEmpty()) {
                /**
                 * Write out the header if the file does not exist or is if the
                 * file is been appended to for the first time. i.e. the headers
                 * may be in a different order.
                 */
                for (Measurement measurement : measurements) {
                    metricNames.addAll(measurement.getMetricNameList());
                    writeHeader(store);
                    /**
                     * This gets the first item out of the measurements list, so
                     * the header can be written.
                     */
                    break;  
                }

            }
            for (Measurement measurement : measurements) {
                writebody(measurement, store);
                store.saveMemoryConservative();
            }
        } catch (Exception ex) {
            //logging is important but should not stop the main thread from running!
            java.util.logging.Logger.getLogger(MeasurementLogger.class.getName()).log(Level.SEVERE, "An error occurred when saving an item to disk", ex);
        }
    }

    private void metricNamesAdd(Measurement measurement) {
        for (String metricName : measurement.getMetricNameList()) {
            if (!metricNames.contains(metricName)) {
                metricNames.add(metricName);
            }
        }
    }

    @Override
    public void writeHeader(ResultsStore store) {
        store.add("Time Stamp Data");
        for (String name : metricNames) {
            store.append(name);
        }
    }

    @Override
    public void writebody(Measurement item, ResultsStore store) {
        if (item.getMetricNameList().size() > metricNames.size()) {
            //Reprint Column Headers and write out new data items
            metricNamesAdd(item);
            writeHeader(store);
        }
        store.add(item.getClock());
        for (String name : metricNames) {
            if (item.metricExists(name)) {
                store.append(item.getMetric(name).getValueAsString());
            } else {
                store.append("");
            }
        }
    }
}
