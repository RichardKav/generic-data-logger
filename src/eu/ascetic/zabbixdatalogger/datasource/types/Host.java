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
package eu.ascetic.zabbixdatalogger.datasource.types;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

/**
 * This class stores the basic data for physical machine. This represents a host
 * in the Zabbix monitoring tool.
 *
 * An important similar class is!
 *
 * @see eu.ascetic.monitoring.api.datamodel.host
 *
 * @author Richard Kavanagh
 */
public class Host extends MonitoredEntity implements Comparable<Host> {

    private int id = -1;
    private String hostName = "";
    private boolean available = true;
    private String state = "";
    private int coreCount;
    private int ramMb;
    private double diskGb;
    private final HashSet<Accelerator> accelerators = new HashSet<>();

    /**
     * E_i^0: is the "idle power consumption" in Watts (with zero number of VMs
     * running) E_i^c: power consumption of a CPU cycle (or instruction)
     *
     * This value acts as the default idle power consumption for a host and is
     * used when no calibration data is available.
     *
     */
    private double defaultIdlePowerConsumption = 0.0; //i.e. 27.1w for an idle laptop.
    private int defaultIdleRamUsage = 0;

    /**
     * An idea of power consumption scale:
     * http://www.xbitlabs.com/articles/memory/display/ddr3_13.html
     * http://superuser.com/questions/40113/does-installing-larger-ram-means-consuming-more-energy
     * http://www.tomshardware.com/reviews/power-saving-guide,1611-4.html
     */
    /**
     * This creates a new instance of a host
     *
     * @param id The host id
     * @param hostName The host name
     */
    public Host(int id, String hostName) {
        this.id = id;
        this.hostName = hostName;
    }

    /**
     * This is a copy constructor for a host
     * @param id The host id
     * @param hostName The host name
     * @param host The old host to copy information from
     */
    public Host(int id, String hostName, Host host) {
        this.id = id;
        this.hostName = hostName;
        this.available = true;
        this.state = host.getState();
        this.coreCount = host.getCoreCount();
        this.ramMb = host.getRamMb();
        this.diskGb = host.getDiskGb();       
        this.accelerators.addAll(host.getAccelerators());
    }

    /**
     * This returns the host's id.
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * This sets the host's id.
     *
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * This returns the host's name.
     *
     * @return the hostName
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * This sets the hosts name.
     *
     * @param hostName the hostName to set
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * This indicates if the host is currently available.
     *
     * @return the available
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * This sets the flag to state the host is available.
     *
     * @param available the available to set
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * This indicates the state of the host and allows for more complex states
     * rather than just up or down. Such as SLURMs drain and maintenance states.
     * @return 
     */
    public String getState() {
        return state;
    }

    /**
     * This allows the state of the host to be set. This property allows for more 
     * complex states rather than just up or down. Such as SLURMs drain and 
     * maintenance states.
     * @param state 
     */
    public void setState(String state) {
        this.state = state;
    }    

    @Override
    public String toString() {
        return "HostID: " + id + " Host Name: " + hostName + " Available: " + available  
                + " Has Accelerator: " + hasAccelerator() + " Has GPU: " + hasGpu() + " Has MIC: " + hasMic();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Host) {
            Host host = (Host) obj;
            if (hostName != null && host.getHostName() != null) {
                return this.hostName.equals(host.getHostName());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.hostName);
        return hash;
    }

    @Override
    public int compareTo(Host o) {
        return this.getHostName().compareTo(o.getHostName());
    }

    /**
     * This returns the value for the default idle power consumption of a host.
     * This value is recorded and used in cases where calibration has yet to
     * occur.
     *
     * @return the defaultIdlePowerConsumption
     */
    public double getDefaultIdlePowerConsumption() {
        return defaultIdlePowerConsumption;
    }

    /**
     * This sets the value for the default idle power consumption of a host.
     * This value is recorded and used in cases where calibration has yet to
     * occur.
     *
     * @param defaultIdlePowerConsumption the default Idle Power Consumption to
     * set
     */
    public void setDefaultIdlePowerConsumption(double defaultIdlePowerConsumption) {
        this.defaultIdlePowerConsumption = defaultIdlePowerConsumption;
    }

    /**
     * This provides the amount of memory that is used without a VM been placed
     * on the host machine.
     *
     * @return the idleRamUsage The amount of ram used when the host is idle.
     */
    public int getDefaultIdleRamUsage() {
        return defaultIdleRamUsage;
    }

    /**
     * This sets the amount of memory that is used without a VM been placed on
     * the host machine.
     *
     * @param defaultIdleRamUsage The amount of ram used when the host is idle.
     */
    public void setDefaultIdleRamUsage(int defaultIdleRamUsage) {
        this.defaultIdleRamUsage = defaultIdleRamUsage;
    }

    /**
     * This gets the maximum amount of cores this host has.
     *
     * @return The core count this host has physically available.
     */    
    public int getCoreCount() {
        return coreCount;
    }

    /**
     * This sets the maximum amount of cpus this host has.
     *
     * @param coreCount The core count this host has physically available.
     */    
    public void setCoreCount(int coreCount) {
        if (coreCount < 0) {
            throw new IllegalArgumentException("The amount of cores must not be less than zero.");
        }        
        this.coreCount = coreCount;
    }
    
    /**
     * This gets the maximum amount of ram this host has.
     *
     * @return The ram this host has physically available.
     */
    public int getRamMb() {
        return ramMb;
    }

    /**
     * This sets the maximum amount of ram this host has.
     *
     * @param ramMb The ram this host has physically available.
     */
    public void setRamMb(int ramMb) {
        if (ramMb < 0) {
            throw new IllegalArgumentException("The amount of memory must not be less than zero.");
        }
        this.ramMb = ramMb;
    }

    /**
     * This gets the amount of disk space this host has available.
     *
     * @return The disk space this host has available.
     */
    public double getDiskGb() {
        return diskGb;
    }

    /**
     * This sets the amount of disk space this host has available.
     *
     * @param diskGb The disk space this host has available.
     */
    public void setDiskGb(double diskGb) {
        if (diskGb < 0) {
            throw new IllegalArgumentException("The amount of disk size must not be less than zero.");
        }
        this.diskGb = diskGb;
    }
    
    /**
     * Indicates if this host has an accelerator or not
     * @return if the host has any type of accelerator or not 
     */
    public synchronized boolean hasAccelerator () {
        return !accelerators.isEmpty();
    }
    
    /**
     * Indicates if this host has an GPU as an accelerator or not
     * @return If the host has a GPU or not
     */
    public synchronized boolean hasGpu () {
        for (Accelerator current : accelerators) {
            if (current.getType().equals(Accelerator.AcceleratorType.GPU))
                return true;
        }
        return false;
    }
    
    /**
     * Indicates if this host has a Intel Many Integrated Core (MIC) processor 
     * as an accelerator or not
     * @return if the host has a Intel Many Integrated Core (MIC) processor 
     */
    public synchronized boolean hasMic () {
        for (Accelerator current : accelerators) {
            if (current.getType().equals(Accelerator.AcceleratorType.MIC))
                return true;
        }
        return false;
    }
    
    /**
     * Provides the count of GPUs attached to the host
     * @return The count of GPUs attached to the host
     */
    public synchronized int getGpuCount () {
        int count = 0;
        for (Accelerator current : accelerators) {
            if (current.getType().equals(Accelerator.AcceleratorType.GPU))
                count = count + current.getCount();
        }
        return count;
    }
    
    /**
     * Provides the count of Intel Many Integrated Core (MIC) processor attached to the host
     * @return The count of Intel Many Integrated Core (MIC) processor attached to the host
     */
    public synchronized int getMicCount () {
        int count = 0;
        for (Accelerator current : accelerators) {
            if (current.getType().equals(Accelerator.AcceleratorType.MIC))
                count = count + current.getCount();
        }
        return count;
    }    
    
    /**
     * Adds an accelerator to the physical host
     * @param accelerator Indicates which accelerator to add to the host.
     */
    public synchronized void addAccelerator(Accelerator accelerator) {
        if (!accelerators.contains(accelerator)) {
            this.accelerators.add(accelerator);
        }
    }
    
    /**
     * Adds an accelerator to the physical host
     * @param accelerator Indicates which accelerator to add to the host.
     */
    public synchronized void addAccelerator(Accelerator.AcceleratorType accelerator) {
        this.accelerators.add(new Accelerator("",1, accelerator));
    }    
    
    /**
     * Adds an accelerator to the physical host
     * @param accelerator Indicates which accelerator to add to the host.
     */
    public synchronized void addAccelerator(HashSet<Accelerator> accelerator) {
        this.accelerators.addAll(accelerator);
    } 
    
    /**
     * Removes an accelerator to the physical host
     * @param accelerator Indicates which accelerator to remove from the host.
     */
    public synchronized void removeAccelerator(Accelerator accelerator) {
        this.accelerators.remove(accelerator);
    }
    
    /**
     * Removes an accelerator to the physical host
     * @param accelerator Indicates which accelerator to remove from the host.
     */
    public synchronized void removeAccelerator(HashSet<Accelerator> accelerator) {
        this.accelerators.removeAll(accelerator);
    }
    
    /**
     * This provides the list of accelerators for this physical host.
     * @return The list of accelerators this host has.
     */
    public synchronized HashSet<Accelerator> getAccelerators() {
        return (HashSet<Accelerator>) accelerators.clone();
    }
    

}
