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
