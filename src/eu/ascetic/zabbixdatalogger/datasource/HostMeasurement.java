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
package eu.ascetic.zabbixdatalogger.datasource;

import static eu.ascetic.zabbixdatalogger.datasource.KpiList.ENERGY_KPI_NAME;
import static eu.ascetic.zabbixdatalogger.datasource.KpiList.POWER_KPI_NAME;
import eu.ascetic.zabbixdatalogger.datasource.types.Host;

/**
 * This represents a single snapshot of the data from a data source.
 *
 * @author Richard Kavanagh
 */
public class HostMeasurement extends Measurement {

    private Host host;

    /**
     * This creates a host measurement.
     *
     * @param host The host the measurement is for
     */
    public HostMeasurement(Host host) {
        this.host = host;
    }

    /**
     * This creates a host measurement.
     *
     * @param host The host the measurement is for
     * @param clock The time when the measurement was taken, this is in unix time. 
     * i.e. Calendar.
     */
    public HostMeasurement(Host host, long clock) {
        this.host = host;
        setClock(clock);
    }

    /**
     * The gets the host that the measurement is for.
     * @return The host that the measurement is for.
     */
    public Host getHost() {
        return host;
    }

    /**
     * The sets the host that the measurement is for.
     * @param host The host that the measurement is for.
     */
    public void setHost(Host host) {
        this.host = host;
    }

    @Override
    public String toString() {
        return host.toString() + " Time: " + getClock() + " Metric Count: " + getMetricCount() + " Clock Diff: " + getMaximumClockDifference();
    }

    /**
     * This provides rapid access to power values from a host measurement.
     *
     * @return The power consumed when the measurement was taken.
     */
    public double getPower() {
        return this.getMetric(POWER_KPI_NAME).getValue();
    }

    /**
     * This provides rapid access to energy value from a host measurement.
     *
     * @return The energy consumed when the measurement was taken, going back to
     * an unspecified period of time. To be used like a meter reading that you
     * might give to an energy company.
     */
    public double getEnergy() {
        return this.getMetric(ENERGY_KPI_NAME).getValue();
    }

}
