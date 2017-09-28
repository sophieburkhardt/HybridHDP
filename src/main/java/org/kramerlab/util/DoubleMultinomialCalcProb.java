package org.kramerlab.util;

import org.kramerlab.interfaces.CalcProb;

public class DoubleMultinomialCalcProb implements CalcProb{

    double[] typeTopicCounts;
    double[] tokensPerTopic;
    double beta;
    double betaSum;

    public DoubleMultinomialCalcProb(double[] typeTopicCounts, double[] tokensPerTopic,double beta,double betaSum){
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
        return mult;
    }

    public double getPProb(int type,int topicIndex){
double mult =  0;
        if(topicIndex!=-1){
            mult = typeTopicCounts[topicIndex]/(tokensPerTopic[topicIndex]+betaSum);
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
