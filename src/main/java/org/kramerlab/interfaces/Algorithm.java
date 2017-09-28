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
