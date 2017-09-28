/**
 * LiBlockSampler2.java
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

import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.TopicModel;
import org.kramerlab.util.StirlingTables;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import cc.mallet.util.Randoms;
import cc.mallet.types.Instance;
import cc.mallet.types.Alphabet;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.IDSorter;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.topics.TopicAssignment;


public class LiBlockSampler2 extends MyBlockSampler2{

    public LiBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,int mhIterations){
        this(alphabet,beta,k,b0,b1,unsupervised,interval,mhIterations,10000,5000,5000,1000,b1);
    }
    
    public LiBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,int mhIterations,int maxN,int maxM,int initN,int initM,double evalb1){
        super(alphabet,beta,k,b0,b1,unsupervised,interval,false,maxN,maxM,initN,initM,evalb1,0.0,1);
        this.numMHIterations = mhIterations;
        System.out.println("MH iterations: "+numMHIterations);
        this.hdp = new LiAliasHDP(new GenericRestaurant(), new ArrayList<GenericRestaurant>(),b0,b1,stirling,false); 
        ((LiAliasHDP)this.hdp).setStoreJustPerType(false);
        ((LiAliasHDP)this.hdp).setStoreQs(true);
        ((AbstractAliasHDP)this.hdp).setUseEvalB1(true);
    }



    public void init(int numTypes){
        super.init(numTypes);
        ((LiAliasHDP)this.hdp).initTopicsForType(numTypes);
    }

    public void decreaseTypeTopic(int[] currentTypeTopicCounts,int type,int topic,int topicIndex){
        super.decreaseTypeTopic(currentTypeTopicCounts,type,topic,topicIndex);
        if(currentTypeTopicCounts[topicIndex]==0){
            ((LiAliasHDP)this.hdp).removeTopicFromType(type,topic);
        }
    }

    public void increaseTypeTopic(int[] currentTypeTopicCounts,int type,int topic,int topicIndex){
        super.increaseTypeTopic(currentTypeTopicCounts,type,topic,topicIndex);
        ((LiAliasHDP)this.hdp).addTopicToType(type,topic);
    }




}
