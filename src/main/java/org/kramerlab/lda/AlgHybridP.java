/**
 * AlgHybridP.java
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


package org.kramerlab.lda;


import org.apache.commons.cli.*;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import org.kramerlab.lda.*;
import org.kramerlab.interfaces.Algorithm;
import org.kramerlab.interfaces.TopicModel;


public class AlgHybridP implements Algorithm{


    TopicModel model;

    public void setModel(TopicModel model){
        this.model = model;
    }

    
    public TopicModel initialize(CommandLine options,InstanceList data,boolean supervised){
        TopicModel model = null;
        int numDocs = data.size();
        int batchSize = Integer.valueOf(options.getOptionValue("batchSize"));
        double alpha = Double.valueOf(options.getOptionValue("alpha"));
        double beta = Double.valueOf(options.getOptionValue("beta"));
        int k = 1;//Integer.valueOf(options.getOptionValue("k"));
        boolean useOriginalSampler = Boolean.valueOf(options.getOptionValue("useOriginalSampler"));
        int updateIterations = Integer.valueOf(options.getOptionValue("updateIterations"));
        int burninIterations = Integer.valueOf(options.getOptionValue("burninIterations"));
        double kappa=0.6;
        if(options.getOptionValue("kappa")!=null){
            kappa = Double.valueOf(options.getOptionValue("kappa"));
        }
        int numTopics = Integer.valueOf(options.getOptionValue("t"));
        if(supervised){
            LabelAlphabet alphabet = org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet());
            model = new HybridParametric(alphabet,beta,alpha,false,numDocs,updateIterations,burninIterations,useOriginalSampler,batchSize,kappa);
        }else{
            model = new HybridParametric(org.kramerlab.Main.newLabelAlphabet(numTopics),beta,alpha,true,numDocs,updateIterations,burninIterations,useOriginalSampler,batchSize,kappa);
        }
        return model;
    }

    public Options constructOptions(boolean supervised){
        Options options = new Options();
        Option alphaOption = new Option("alpha",true,"alpha");    
        Option betaOption = new Option("beta",true,"beta");    
        Option mhOption = new Option("numMHIterations",true,"number of MH iterations");    

        Option kOption = new Option("k",true,"k, how many samples to store with the sparse sampler");    
        Option maxOption = new Option("t",true,"number of topics");
        Option updateIterationOption = new Option("updateIterations",true,"number of update iterations");
        Option burninIterationOption = new Option("burninIterations",true,"number of burnin iterations");
        Option kappaOption = new Option("kappa",true,"kappa");
        Option useOriginalSamplerOption = new Option("useOriginalSampler",true,"whether or not to use the original sampler");
        options.addOption(alphaOption).addOption(betaOption).addOption(kOption).addOption(maxOption).addOption(mhOption).addOption(updateIterationOption).addOption(burninIterationOption).addOption(useOriginalSamplerOption).addOption(kappaOption);    
        return options;
    }

    public String constructBaseString(CommandLine options,String dirBaseString){
        boolean useoriginal = Boolean.valueOf(options.getOptionValue("useOriginalSampler"));
        if(useoriginal){
            dirBaseString+="HybridP-Original/";
        }else{
            dirBaseString+="HybridP/";
        }
        return dirBaseString;
    }

    public String constructEndString(CommandLine options,String dirEndString){
        int numTopics = Integer.valueOf(options.getOptionValue("t"));
        int batchsize1 = Integer.valueOf(options.getOptionValue("batchSize"));
        double kappa=0.6;
        if(options.getOptionValue("kappa")!=null){
            kappa = Double.valueOf(options.getOptionValue("kappa"));
        }    
        dirEndString="kappa-"+kappa+"/numtopics-"+numTopics+"/batchsize-"+batchsize1+"/";
        return dirEndString;
    }

    
}
