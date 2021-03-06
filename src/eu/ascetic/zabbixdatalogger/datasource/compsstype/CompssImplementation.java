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
 *
 * This is being developed for the TANGO Project: http://tango-project.eu
 *
 */
package eu.ascetic.zabbixdatalogger.datasource.compsstype;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * This wraps around the programming model's json output data, for a list of
 * implementations that are currently running.
 * @author Richard Kavanagh
 */
public class CompssImplementation extends JsonObjectWrapper {

    private String name;
    
    public CompssImplementation(JSONObject json) {
        super(json);
    }
    
    public CompssImplementation(String name, JSONObject json) {
        super(json);
        this.name = name;        
    }    
    
    /**
     * This parses a json object for a list of compss implementation objects
     * @param items The json object with items in it
     * @return The list of implementation objects from the json
     */
    public static List<CompssImplementation> getCompssImplementation(JSONObject items) {
        ArrayList<CompssImplementation> answer = new ArrayList<>();
        if (items.has("implementations")) {
            JSONObject implementations = items.getJSONObject("implementations");
            for (Iterator iterator = implementations.keys(); iterator.hasNext();) {
                Object key = iterator.next();
                if (key instanceof String && implementations.get((String) key) instanceof JSONObject) {
                    JSONObject implementation = implementations.getJSONObject((String) key);
                    answer.add(new CompssImplementation((String) key, implementation));
                }
            }
            return answer;
        }
        if (items.has("Core")) {
            JSONObject core;
            if (items.get("Core") instanceof JSONObject) {
                core = items.getJSONObject("Core");
                answer = parseCompsImplementationFromCoreObject(core, answer);
            } else if (items.get("Core") instanceof JSONArray) {
                JSONArray coreArray = items.getJSONArray("Core");
                for (int i = 0; i < coreArray.length(); i++) {
                    core = coreArray.getJSONObject(i);
                    answer = parseCompsImplementationFromCoreObject(core, answer);
                }
            }
            return answer;
        }        
        return answer;
    }
    
    /**
     * This parses a core object from the json.
     * @param core The core object to parse
     * @param answer The initial answer that will have additional items added to it
     * as the parsing proceeds. i.e. this method may be used to part process a list of core objects.
     * @return The list that has been appended to.
     */
    private static ArrayList<CompssImplementation> parseCompsImplementationFromCoreObject(JSONObject core, ArrayList<CompssImplementation> answer) {
        if (core.get("Impl") instanceof JSONArray) {
            JSONArray implementations = core.getJSONArray("Impl");
            for (int i = 0; i < core.length();i++) {
                if (implementations.get(i) instanceof JSONObject) {
                    JSONObject implementation = implementations.getJSONObject(i);
                    answer.add(new CompssImplementation(implementation.getString("Signature"), implementation));
                }
            }
            } else if (core.get("Impl") instanceof JSONObject) {
                JSONObject implementation = core.getJSONObject("Impl");
                answer.add(new CompssImplementation(implementation.getString("Signature"), implementation));                
            }
        return answer;
    }
    
    /**
     * An example of an implementation is the following json line:
     * "remote.process_frame_sequential":{"maxTime":112,"executions":2,"avgTime":101,"minTime":90},
     */
    
    /**
     *
     * @return
     */
    public int getMaxTime() {
        if (json.has("maxTime")) {
            return json.getInt("maxTime");
        }
        if (json.has("MaxExecutionTime")) {
            return json.getInt("MaxExecutionTime");
        }        
        //the default assumption is zero.
        return 0;        
    }    

    /**
     *
     * @return
     */
    public int getMinTime() {
        if (json.has("minTime")) {
            return json.getInt("minTime");
        }
        if (json.has("MinExecutionTime")) {
            return json.getInt("MinExecutionTime");
        }           
        //the default assumption is zero.
        return 0;        
    }
    
    /**
     *
     * @return
     */
    public int getAverageTime() {
        if (json.has("avgTime")) {
            return json.getInt("avgTime");
        }
        if (json.has("MeanExecutionTime")) {
            return json.getInt("MeanExecutionTime");
        }    
        //the default assumption is zero.
        return 0;        
    }
    
    /**
     *
     * @return
     */
    public int getExecutionCount() {
        if (json.has("executions")) {
            return json.getInt("executions");
        }
        if (json.has("ExecutedCount")) {
            return json.getInt("ExecutedCount");
        }        
        //the default assumption is zero.
        return 0;        
    }          

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    
}
