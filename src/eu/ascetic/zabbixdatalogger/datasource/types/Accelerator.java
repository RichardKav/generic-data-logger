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

import java.io.Serializable;
import java.util.Objects;

/**
 * This represents an accelerator on a physical host
 *
 * @author Richard Kavanagh
 */
public class Accelerator implements Comparable<Accelerator>, Serializable {
    
    private static final long serialVersionUID = 1L;
    private String name = "";
    private AcceleratorType type;
    private int count = 0;

    public enum AcceleratorType {

        GPU, MIC, FPGA
    }

    /**
     * This creates a new accelerator
     *
     * @param name The name of the accelerator
     * @param count The count of accelerators on the physical host
     * @param type The type of accelerator on the host
     */
    public Accelerator(String name, int count, AcceleratorType type) {
        this.type = type;
        this.name = name;
        this.count = count;
    }

    /**
     * This gets the name of the accelerator
     *
     * @return The name of the accelerator
     */
    public String getName() {
        return name;
    }

    /**
     * This sets the name of the accelerator
     *
     * @param name The name of the accelerator
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * This indicates the type of accelerator
     *
     * @return the accelerator
     */
    public AcceleratorType getType() {
        return type;
    }

    /**
     * This sets the type of accelerator
     *
     * @param type the accelerator to set
     */
    public void setType(AcceleratorType type) {
        this.type = type;
    }

    /**
     * This gets the count of accelerators on the physical host
     *
     * @return the count
     */
    public int getCount() {
        return count;
    }

    /**
     * This sets count of accelerators on the physical host
     *
     * @param count the count to set
     */
    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Accelerator) {
            if (this.name.equals(((Accelerator) obj).getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public int compareTo(Accelerator o) {
        return name.compareTo(o.getName());
    }

}
