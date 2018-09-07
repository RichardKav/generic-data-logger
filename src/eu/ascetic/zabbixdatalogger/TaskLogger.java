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

import eu.ascetic.ioutils.GenericLogger;
import eu.ascetic.ioutils.ResultsStore;
import eu.ascetic.zabbixdatalogger.datasource.compsstype.CompssImplementation;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;

/**
 * This writes task information out to disk
 * @author Richard Kavanagh
 */
public class TaskLogger extends GenericLogger<CompssImplementation> {

    SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
    HashMap<String, CompssImplementation> previous = new HashMap<>();
    HashMap<String, Long> previousTime = new HashMap<>();

    public TaskLogger(File file, boolean overwrite) {
        super(file, overwrite);
        saveFile.setDelimeter("\t");
    }
    
    @Override
    public void writeHeader(ResultsStore store) {
        store.add("Time");
        store.append("Time");
        store.append("Name");
        store.append("Executed Count");
        store.append("Average Time");
        store.append("Min Time");
        store.append("Max Time");
        store.append("Spot Average Time");
    }

    @Override
    public void writebody(CompssImplementation item, ResultsStore store) {
        GregorianCalendar cal = new GregorianCalendar();
        store.add(cal.getTimeInMillis());
        store.append(formatter.format(cal.getTime()));
        store.append(item.getName());
        store.append(item.getExecutionCount());
        store.append(item.getAverageTime());
        store.append(item.getMinTime());
        store.append(item.getMaxTime());
        store.append(getSpotRunningTime(item, cal.getTimeInMillis()));
    }
    
    /**
     * This gets the spot average runtime of an application - compss reports the
     * average runtime for all time, so this does the time since the last record was passed.
     * @param item The current compss implementation record undergoing writing to disk
     * @param time The current time, in milliseconds
     * @return The rate at which jobs have been processed
     */
    private double getSpotRunningTime(CompssImplementation item, long time) {
        double answer = 0;
        long currentTime = time / 1000;       
        Long lastTimeValue = previousTime.get(item.getName());
        CompssImplementation previousItem = previous.get(item.getName());
        if (lastTimeValue != null && previousItem != null) {
            double changeInExecutionCount = item.getExecutionCount() - previousItem.getExecutionCount();
            long changeInTime = currentTime - lastTimeValue;
            answer = changeInExecutionCount / changeInTime;
        }
        //make a record of the previous item seen
        previousTime.put(item.getName(), currentTime);
        previous.put(item.getName(), item);
        return answer;
    }
    
}
