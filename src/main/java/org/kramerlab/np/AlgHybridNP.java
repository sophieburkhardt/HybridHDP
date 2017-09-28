/**
 * AlgHybridNP.java
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


package org.kramerlab.np;


import org.apache.commons.cli.*;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import org.kramerlab.np.*;
import org.kramerlab.interfaces.Algorithm;
import org.kramerlab.interfaces.TopicModel;


public class AlgHybridNP implements Algorithm{


    TopicModel model;

    public void setModel(TopicModel model){
        this.model = model;
    }

    
    public TopicModel initialize(CommandLine options,InstanceList data,boolean supervised){
        TopicModel model = null;
        int numDocs = data.size();
        boolean updateConcentration = false;
        if(options.getOptionValue("updateConcentration")!=null){
            updateConcentration = Boolean.valueOf(options.getOptionValue("updateConcentration"));
        }
        double b0,b1;
        if(updateConcentration){
            b0 = 1;
            b1 = 1;
        }else{
            b0 = Double.valueOf(options.getOptionValue("b0"));
            b1 = Double.valueOf(options.getOptionValue("b1"));
        }
        int batchsize = Integer.valueOf(options.getOptionValue("batchSize"));
        boolean samplehyper = Boolean.valueOf(options.getOptionValue("sampleHyper"));
        double beta = Double.valueOf(options.getOptionValue("beta"));
        int k = 1;//Integer.valueOf(options.getOptionValue("k"));
        boolean useOriginalSampler = Boolean.valueOf(options.getOptionValue("useOriginalSampler"));
        int updateIterations = Integer.valueOf(options.getOptionValue("updateIterations"));
        int burninIterations = Integer.valueOf(options.getOptionValue("burninIterations"));
        double kappa=0.6;
        if(options.getOptionValue("kappa")!=null){
            kappa = Double.valueOf(options.getOptionValue("kappa"));
        }
        int interval = 1;
        if(options.getOptionValue("interval")!=null){
            interval = Integer.valueOf(options.getOptionValue("interval"));
        }    
        if(supervised){
            System.out.println("supervised");
            LabelAlphabet alphabet = org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet());
            model = new Hybrid(alphabet,beta,k,b0,b1,false,interval,numDocs,updateIterations,burninIterations,useOriginalSampler,batchsize,kappa);
        }else{
            int maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
            System.out.println("unsupervised");
            model = new Hybrid(org.kramerlab.Main.newLabelAlphabet(maxNumTopics),beta,k,b0,b1,true,interval,numDocs,updateIterations,burninIterations,useOriginalSampler,batchsize,kappa);
        }
        ((Hybrid)model).setSampleHyper(samplehyper);

        this.model = model;
        return model;
    }

    public Options constructOptions(boolean supervised){
        Options options = new Options();
        Option b0Option = new Option("b0",true,"b0");    
        Option b1Option = new Option("b1",true,"b1");    
        Option betaOption = new Option("beta",true,"beta");    
        Option intervalOption = new Option("interval",true,"interval for stirling numbers");    
        Option mhOption = new Option("numMHIterations",true,"number of MH iterations");    
        Option concentrationOption = new Option("updateConcentration",true,"whether or not to update the concentration parameters");   
        Option kOption = new Option("k",true,"k, how many samples to store with the sparse sampler");    
        Option maxOption = new Option("maxTopicNumber",true,"maximum number of topics");
        //Option batchsizeOption = new Option("batchsize",true,"batchsize");
        Option updateIterationOption = new Option("updateIterations",true,"number of update iterations");
        Option burninIterationOption = new Option("burninIterations",true,"number of burnin iterations");
        Option kappaOption = new Option("kappa",true,"kappa");
        Option sampleHyperOption = new Option("sampleHyper",true,"whether to sample the hyper parameters");
        Option useOriginalSamplerOption = new Option("useOriginalSampler",true,"whether or not to use the original sampler");
        options.addOption(b0Option).addOption(b1Option).addOption(betaOption).addOption(kOption).addOption(maxOption).addOption(concentrationOption).addOption(intervalOption).addOption(mhOption).addOption(updateIterationOption).addOption(burninIterationOption).addOption(useOriginalSamplerOption).addOption(kappaOption).addOption(sampleHyperOption);    
        return options;
}

    public String constructBaseString(CommandLine options,String dirBaseString){
        boolean useoriginal = Boolean.valueOf(options.getOptionValue("useOriginalSampler"));
        if(useoriginal){
            dirBaseString+="HybridNP-Original/";
        }else{
            dirBaseString+="HybridNP/";
        }
        return dirBaseString;
    }

    public String constructEndString(CommandLine options,String dirEndString){
        boolean supervised = ((Hybrid)this.model).isSupervised();
        boolean samplehyper = Boolean.valueOf(options.getOptionValue("sampleHyper"));
        double kappa = Double.valueOf(options.getOptionValue("kappa"));
        int maxNumTopics = 0;
        if(supervised){
            maxNumTopics = -1;
        }else{
            maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
        }
        int batchsize = Integer.valueOf(options.getOptionValue("batchSize"));
        if(samplehyper){
            dirEndString="kappa-"+kappa+"/numtopics-"+maxNumTopics+"/batchsize-"+batchsize+"/sampleHyperParameters/";
        }else{
            dirEndString="kappa-"+kappa+"/numtopics-"+maxNumTopics+"/batchsize-"+batchsize+"/";
        }
        return dirEndString;
    }

    
}
