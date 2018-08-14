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
package eu.ascetic.zabbixdatalogger.datasource.types;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * This class represents an energy user of the Tango project and in particular
 * an application running on a physical host.
 *
 * @author Richard Kavanagh
 */
public class ApplicationOnHost extends MonitoredEntity implements Comparable<ApplicationOnHost> { 

    public enum JOB_STATUS {

        PENDING, RUNNING, SUSPENDED, STOPPED, COMPLETING, COMPLETED, CONFIGURING, 
        CANCELLED, FAILED, TIMEOUT, PREEMPTED, BOOT_FAIL, NODE_FAIL, REVOKED, SPECIAL_EXIT
    }
    
    private static final Map<String, JOB_STATUS> JOB_STATUS_MAPPING
            = new HashMap<>();

    /*
     * namely: PENDING (PD), RUNNING (R), SUSPENDED (S), STOPPED (ST),
     * COMPLETING (CG), COMPLETED (CD), CONFIGURING (CF), CANCELLED (CA),
     * FAILED (F), TIMEOUT (TO), PREEMPTED (PR), BOOT_FAIL (BF) , NODE_FAIL
     * (NF), REVOKED (RV), and SPECIAL_EXIT (SE)
     */
    static {
        JOB_STATUS_MAPPING.put("PENDING", JOB_STATUS.PENDING);
        JOB_STATUS_MAPPING.put("RUNNING", JOB_STATUS.RUNNING);
        JOB_STATUS_MAPPING.put("SUSPENDED", JOB_STATUS.SUSPENDED);
        JOB_STATUS_MAPPING.put("STOPPED", JOB_STATUS.STOPPED);
        JOB_STATUS_MAPPING.put("COMPLETING", JOB_STATUS.COMPLETING);
        JOB_STATUS_MAPPING.put("COMPLETED", JOB_STATUS.COMPLETED);
        JOB_STATUS_MAPPING.put("CONFIGURING", JOB_STATUS.CONFIGURING);
        JOB_STATUS_MAPPING.put("CANCELLED", JOB_STATUS.CANCELLED);  
        JOB_STATUS_MAPPING.put("FAILED", JOB_STATUS.FAILED);      
        JOB_STATUS_MAPPING.put("TIMEOUT", JOB_STATUS.TIMEOUT);      
        JOB_STATUS_MAPPING.put("PREEMPTED", JOB_STATUS.PREEMPTED);      
        JOB_STATUS_MAPPING.put("BOOT_FAIL", JOB_STATUS.BOOT_FAIL);      
        JOB_STATUS_MAPPING.put("NODE_FAIL", JOB_STATUS.NODE_FAIL);      
        JOB_STATUS_MAPPING.put("REVOKED", JOB_STATUS.REVOKED);      
        JOB_STATUS_MAPPING.put("SPECIAL_EXIT", JOB_STATUS.SPECIAL_EXIT);      
        JOB_STATUS_MAPPING.put(null, null);
        JOB_STATUS_MAPPING.put("", null);
    }

    private int id;
    private String name;
    private Host allocatedTo;
    private Calendar created;
    private Calendar deadline;
    private JOB_STATUS status;
    private JSONObject properties; //helps better describe the application

    /**
     * Creates an instance of an application which is to be allocated power
     * consumption
     *
     * @param id The id of the application
     * @param name The name of the application
     * @param allocatedTo The host the application is allocated to
     */
    public ApplicationOnHost(int id, String name, Host allocatedTo) {
        this.id = id;
        this.name = name;
        this.allocatedTo = allocatedTo;
        created = new GregorianCalendar(); //assumes created date of now()
    }

    /**
     * Creates an instance of an application which is to be allocated power
     * consumption
     *
     * @param id The id of the application
     * @param name The name of the application
     * @param allocatedTo The host the application is allocated to
     * @param created The time the application was started
     */
    public ApplicationOnHost(int id, String name, Host allocatedTo, Calendar created) {
        this.id = id;
        this.name = name;
        this.allocatedTo = allocatedTo;
        this.created = created;
    }

    /**
     * This gets the id associated with this application (process id number).
     *
     * @return The process id number of the application.
     */
    public int getId() {
        return id;
    }

    /**
     * This sets the process id associated with this application.
     *
     * @param id The application.
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * This gets the name this application is known by.
     *
     * @return The applications name
     */
    public String getName() {
        return name;
    }

    /**
     * This sets the name this application is known by
     *
     * @param name The applications name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * This sets which host this application is allocated to.
     *
     * @param allocatedTo The host this app is allocated to
     */
    public void setAllocatedTo(Host allocatedTo) {
        this.allocatedTo = allocatedTo;
    }

    /**
     * This gets the date the application was started.
     *
     * @return The start time of the application.
     */
    public Calendar getCreated() {
        return created;
    }

    /**
     * This sets the date the application was instantiated.
     *
     * @param created The boot time of the application.
     */
    public void setCreated(Calendar created) {
        this.created = created;
    }

    /**
     * Gets the deadline of the application if it has one, null is returned if
     * no deadline is set.
     *
     * @return The deadline of the application, null if not set.
     */
    public Calendar getDeadline() {
        return deadline;
    }

    /**
     * This sets the deadline of the application
     *
     * @param deadline The new deadline of the application, to discard the
     * deadline the value that should be passed is null.
     */
    public void setDeadline(Calendar deadline) {
        this.deadline = deadline;
    }

    /**
     * Indicates if this application has a deadline set
     *
     * @return True only if a deadline for the application has been set.
     */
    public boolean hasDeadline() {
        return deadline != null;
    }

    /**
     * If a deadline is set the progress to completion can be determined.
     *
     * @return The percentage of progress through the application alloted time.
     * -1 if this no deadline has been set.
     */
    public double getProgress() {
        if (!hasDeadline()) {
            return -1;
        }
        long start = TimeUnit.MILLISECONDS.toSeconds(created.getTimeInMillis());
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long end = TimeUnit.MILLISECONDS.toSeconds(deadline.getTimeInMillis());
        long maxDuration = end - start;
        if (maxDuration <= 0) {
            return -1;
        }
        long progress = now - start;
        if (progress <= 0) {
            return -1;
        }
        return ((double) progress / (double) maxDuration) * 100d;
    }

    /**
     * This provides details of how long the application has been running in
     * seconds.
     *
     * @return The current length of time the application has been running.
     */
    public long getDuration() {
        if (!hasDeadline()) {
            return -1;
        }
        long start = TimeUnit.MILLISECONDS.toSeconds(created.getTimeInMillis());
        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return now - start;
    }

    /**
     * This provides details of an applications maximum duration
     *
     * @return The maximum duration of the application in seconds if a deadline
     * is set. returns -1 if no deadline is set.
     */
    public long getMaxDuration() {
        if (!hasDeadline()) {
            return -1;
        }
        long start = TimeUnit.MILLISECONDS.toSeconds(created.getTimeInMillis());
        long end = TimeUnit.MILLISECONDS.toSeconds(deadline.getTimeInMillis());
        return end - start;
    }

    /**
     * This indicates which host this application is allocated to.
     *
     * @return the allocatedTo The host this app is allocated to
     */
    public Host getAllocatedTo() {
        return allocatedTo;
    }

    /**
     * This gets the jobs status
     *
     * @return The status of this job when it was last measured.
     */
    public JOB_STATUS getStatus() {
        return status;
    }

    /**
     * This sets the status of this job
     *
     * @param status The new status of this job
     */
    public void setStatus(JOB_STATUS status) {
        this.status = status;
    }

    /**
     * This sets the status of this job
     *
     * @param status The new status of this job
     */
    public void setStatus(String status) {
        this.status = getAdaptationType(status);
    }
    
    /**
     * This provides the mapping between the string representation of a response
     * type and the adaptation type.
     *
     * @param responseType The name of the rule.
     * @return The Adaptation type required.
     */
    public static JOB_STATUS getAdaptationType(String responseType) {
        JOB_STATUS answer = JOB_STATUS_MAPPING.get(responseType);
        if (answer == null) {
            for (Map.Entry<String,JOB_STATUS> item : JOB_STATUS_MAPPING.entrySet()) {
                if (item.getKey() != null && item.getKey().startsWith(responseType)) {
                    return item.getValue();
                }
            }
        }
        if (answer == null) {
            Logger.getLogger(ApplicationOnHost.class.getName()).log(Level.SEVERE, "The response type was not found: {0}", responseType);
        }
        return answer;
    }    

    /**
     * This returns the time in seconds that have passed since this application
     * was started.
     *
     * @return The time in seconds since boot. This returns -1 if the created
     * date is unknown.
     */
    public long getTimeFromStart() {
        if (created == null) {
            return -1;
        }
        long currentTime = new GregorianCalendar().getTimeInMillis();
        long bootTimeMilliSecs = created.getTimeInMillis();
        return TimeUnit.MILLISECONDS.toSeconds(currentTime - bootTimeMilliSecs);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ApplicationOnHost) {
            ApplicationOnHost app = (ApplicationOnHost) obj;
            return name.equals(app.getName()) && 
                    id == app.getId() && 
                    this.getAllocatedTo().equals(app.getAllocatedTo());
        }
        return false;

    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 23 * hash + this.id;
        hash = 23 * hash + Objects.hashCode(this.name);
        hash = 23 * hash + Objects.hashCode(this.allocatedTo);
        return hash;
    }

    @Override
    public int compareTo(ApplicationOnHost application) {
    int nameddirection = name.compareTo(application.getName());
    if (nameddirection != 0) {
        return nameddirection;
    }
    int idDirection = Integer.compare(id, application.getId());
    if (idDirection != 0){
        return idDirection;
    }
    int allocatedToDirection = allocatedTo.compareTo(application.getAllocatedTo());
        return allocatedToDirection;
    }

    @Override
    public String toString() {
        SimpleDateFormat formatter = new SimpleDateFormat("dd-mm-yyyy hh:mm:ss");  
        return "name: " + name
                + " id: " + id
                + " host : " + allocatedTo
                + " started: " + formatter.format(created.getTime());
    }

    /**
     * This casts a application on host collection into a monitored entity
     * collection.
     *
     * @param applications The application collection to cast into its parent
     * type.
     * @return The collection of applications in its super type. This is backed
     * by a hashset.
     */
    public static Collection<MonitoredEntity> castToMonitoredEntity(Collection<ApplicationOnHost> applications) {
        Collection<MonitoredEntity> answer = new HashSet<>();
        answer.addAll(applications);
        return answer;
    }

    /**
     * This casts a application list into a monitored entity list.
     *
     * @param applications The application on host list to cast into its parent
     * type.
     * @return The list of applications in its super type. This is backed by an
     * array list.
     */
    public static List<MonitoredEntity> castToEnergyUser(List<ApplicationOnHost> applications) {
        List<MonitoredEntity> answer = new ArrayList<>();
        answer.addAll(applications);
        return answer;
    }

    /**
     * This takes a list of applications and returns only the list on a named
     * host.
     *
     * @param applications The list of all applications
     * @param host The host to find its list of applications for
     * @return The list of applications on the named host
     */
    public static List<ApplicationOnHost> filter(List<ApplicationOnHost> applications, Host host) {
        List<ApplicationOnHost> answer = new ArrayList<>();
        for (ApplicationOnHost application : applications) {
            if (application.allocatedTo != null && application.allocatedTo.equals(host)) {
                answer.add(application);
            }
        }
        return answer;
    }

    /**
     * This takes a list of applications and returns only the list on
     * applications listed that have the same name and instance id. This
     * therefore means if an application runs across several hosts the list can
     * be filtered to find all the application on host instances, for a given
     * application.
     *
     * @param applications The list of all applications
     * @param name The name of the application
     * @param id The unique id of the application's instance, if this is less than
     * 0 then the value is ignored from the filtering.
     * @return The list of applications on host instances for a named
     * application
     */
    public static List<ApplicationOnHost> filter(List<ApplicationOnHost> applications, String name, int id) {
        List<ApplicationOnHost> answer = new ArrayList<>();
        for (ApplicationOnHost application : applications) {
            if (application.getName().equals(name) && (application.getId() == id || id < 0)) {
                answer.add(application);
            }
        }
        return answer;
    }
    
    /**
     * @return the properties
     */
    public JSONObject getProperties() {
        return properties;
    }

    /**
     * @param properties the properties to set
     */
    public void setProperties(JSONObject properties) {
        this.properties = properties;
    }
    
    /**
     * Adds a string based property to the application on host. Although this 
     * doesn't change the energy model, it allows properties to be added which 
     * can then change how any decision is made when performing adaptation.
     * @param key The key for the property
     * @param value The value for the property
     */
    public void addProperty(String key, String value) {
        if (properties == null) {
            properties = new JSONObject();
        }         
        properties.put(key, value);
    }
    
     /**
     * Adds a string based property to the application on host. Although this 
     * doesn't change the energy model, it allows properties to be added which 
     * can then change how any decision is made when performing adaptation.
     * @param key The key for the property
     * @param value The value for the property
     */
    public void addProperty(String key, Object value) {
        if (properties == null) {
            properties = new JSONObject();
        }          
        properties.put(key, value);
    }   
    
    /**
     * This gets the string representation of a named property
     * @param key The key value of the property to return
     * @return The string representation of a named property
     */
    public String getPropertyAsString(String key) {
        if (properties == null) {
            return null;
        }
        if (properties.get(key) instanceof String) {
            return properties.getString(key);
        }
        //Backup option to use toString method
        return properties.get(key).toString();
    }
    
    /**
     * This gets the Integer representation of a named property
     * @param key The key value of the property to return
     * @return The Integer representation of a named property
     */
    public Integer getPropertyAsInteger(String key) {
        if (properties == null) {
            return null;
        }
        if (properties.get(key) instanceof Integer) {
            return properties.getInt(key);
        }
        //Backup option to return null
        return null;
    }
    
    /**
     * This gets the double representation of a named property
     * @param key The key value of the property to return
     * @return The double representation of a named property
     */
    public Double getPropertyAsDouble(String key) {
        if (properties == null) {
            return null;
        }
        if (properties.get(key) instanceof Double) {
            return properties.getDouble(key);
        }
        //Backup option to return null
        return null;
    }      
    
    /**
     * This gets the string representation of a named property
     * @param key The key value of the property to return
     * @return The string representation of a named property. Null if no properties
     * are set
     */
    public Object getProperty(String key) {
        if (properties == null) {
            return null;
        }        
        return properties.get(key);
    }
    
    /**
     * This indicates if this application on a host has a particular key value
     * pair stored
     * @param key The key to search for
     * @return If a value is stored for a given property.
     */
    public boolean hasProperty(String key) {
        if (properties == null) {
            return false;
        }
        return properties.has(key);
    }
    
}
