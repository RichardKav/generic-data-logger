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
import eu.ascetic.zabbixdatalogger.datasource.types.Host;

/**
 * This represents a single snapshot of the data from a data source.
 * @author Richard Kavanagh
 */
public class ApplicationMeasurement extends Measurement {

    private ApplicationOnHost application;    
  
    /**
     * This creates a application measurement.
     *
     * @param application The application the measurement is for
     */
    public ApplicationMeasurement(ApplicationOnHost application) {
        this.application = application;
    } 
    
    /**
     * This creates a application measurement.
     *
     * @param application The application the measurement is for
     * @param clock The time when the measurement was taken, this is in unix
     * time. i.e. Calendar.
     */
    public ApplicationMeasurement(ApplicationOnHost application, long clock) {
        this.application = application;
        setClock(clock);
    }

    /**
     * The gets the application that the measurement is for.
     *
     * @return The host that the measurement is for.
     */    
    public ApplicationOnHost getApplication() {
        return application;
    }

    public void setApplication(ApplicationOnHost application) {
        this.application = application;
    }

    /**
     * The gets the host of the application that the measurement is for.
     *
     * @return The host of the application that the measurement is for.
     */
    public Host getHost() {
        return application.getAllocatedTo();
    }

    @Override
    public String toString() {
        return application.toString() + " Time: " + getClock() + " Metric Count: " + getMetricCount() + " Clock Diff: " + getMaximumClockDifference();
    }
    
}
