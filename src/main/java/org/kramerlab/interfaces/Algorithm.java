/**
 * Algorithm.java
 * 
 * Copyright (C) 2017 Sophie Burkhardt
 *
 * This file is part of HybridHDP.
 * 
 * HybridHDP is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at
 * your option) any later version.
 * 
 * HybridHDP is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 */


package org.kramerlab.interfaces;

import org.apache.commons.cli.*;
import org.kramerlab.interfaces.*;
import cc.mallet.types.InstanceList;

public interface Algorithm{

    public void setModel(TopicModel model);
    
    public Options constructOptions(boolean supervised);

    public TopicModel initialize(CommandLine options,InstanceList data,boolean supervised);

    public String constructBaseString(CommandLine options,String baseString);

    public String constructEndString(CommandLine options,String endString);
    
}
