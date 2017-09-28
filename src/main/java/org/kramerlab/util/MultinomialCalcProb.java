/**
 * MultinomialCalcProb.java
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


package org.kramerlab.util;

import org.kramerlab.interfaces.CalcProb;

public class MultinomialCalcProb implements CalcProb{

    int[] typeTopicCounts;
    int[] tokensPerTopic;
    double beta;
    double betaSum;

    public MultinomialCalcProb(int[] typeTopicCounts, int[] tokensPerTopic,double beta,double betaSum){
        this.typeTopicCounts = typeTopicCounts;
        this.tokensPerTopic = tokensPerTopic;
        this.beta = beta;
        this.betaSum = betaSum;
    }

    public double getProb(int type, int topicIndex){
        double mult =  0;
        if(topicIndex==-1){
            mult = beta/betaSum;
        }else{
            mult = (typeTopicCounts[topicIndex]+beta)/(tokensPerTopic[topicIndex]+betaSum);
        }
        if(mult<0){
            System.out.println("MultinomialCalcProb: "+typeTopicCounts[topicIndex]+" "+tokensPerTopic[topicIndex]);
        }
        return mult;
    }

    public double getPProb(int type,int topicIndex){
double mult =  0;
        if(topicIndex!=-1){
            mult = typeTopicCounts[topicIndex]/(tokensPerTopic[topicIndex]+betaSum);
        }
        if(mult<0){
            System.out.println("MultinomialCalcProb: "+typeTopicCounts[topicIndex]+" "+tokensPerTopic[topicIndex]);
        }
        return mult;
    }
    public double getQProb(int type,int topicIndex){
        double mult =  0;
        if(topicIndex==-1){
            mult = beta/betaSum;
        }else{
            mult = beta/(tokensPerTopic[topicIndex]+betaSum);
        }
        if(mult<0){
            System.out.println("MultinomialCalcProb: "+typeTopicCounts[topicIndex]+" "+tokensPerTopic[topicIndex]);
        }
        return mult;
    }

}
