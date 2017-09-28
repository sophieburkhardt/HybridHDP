package org.kramerlab.lda;

import org.kramerlab.util.AliasUtils2;
import org.kramerlab.interfaces.CalcProb;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Stack;



import cc.mallet.util.Randoms;

public class GenericAlias{

    ArrayList<Stack<Integer>> topicSamples;
    ArrayList<double[]> qw;
    ArrayList<Double> qnorm;
    double[] p;
    double pnorm;

    int numTypes;
    int numTopics;

    double alpha;

    Randoms random;
    
    public GenericAlias(int numTypes,int numTopics,double alpha){
        this.alpha = alpha;
        this.random = new Randoms();
        topicSamples = new ArrayList<Stack<Integer>>();
        qw = new ArrayList<double[]>();
        qnorm = new ArrayList<Double>();
        this.numTypes = numTypes;
        this.numTopics = numTopics;
        for(int i = 0;i<numTypes;++i){
            topicSamples.add(new Stack<Integer>());
            qw.add(new double[numTopics]);
            qnorm.add(0.0);
        }
    }

    public void clearSamples(){
        for(Stack<Integer> samples: topicSamples){
            samples.clear();
        }
    }
    
    public int sampleTopic(int type,ArrayList<Integer> sparseTopics,int[] localTokensPerTopic,CalcProb calcProb,int numSamples){
        int newTopic = -1;
        computeP(type,sparseTopics,localTokensPerTopic,calcProb);
        Stack<Integer> topSamples = topicSamples.get(type);
        if(topSamples.empty()){
            createMoreSamples(numSamples,type,calcProb);
        }
        double qn = qnorm.get(type);
        double mass = qn+pnorm;
        double rand = random.nextUniform();
        if(rand<pnorm/mass){
            rand*=mass;
            rand/=pnorm;
            int index=-1;
            while(rand>0){
                index++;
                rand-=p[index];
            }
            newTopic = sparseTopics.get(index);
        }else{
            newTopic = topSamples.pop();
        }
        return newTopic;
    }

    public void computeP(int type,ArrayList<Integer> sparseTopics,int[] localTokensPerTopic,CalcProb calcProb){
        this.p = new double[sparseTopics.size()];
        int index=0;
        this.pnorm = 0;
        for(Integer top:sparseTopics){
            this.p[index]=localTokensPerTopic[top]*calcProb.getProb(type,top);
            pnorm+=this.p[index];
            index++;
        }
        normalize(p,pnorm);
    }

    public void computeQ(int type,CalcProb calcProb){
        double[] q = this.qw.get(type);
        Arrays.fill(q,0);
        double sum = 0;
        for(int top = 0;top<numTopics;++top){
            q[top] = this.alpha*calcProb.getProb(type,top);
            sum+=q[top];
        }
        this.qnorm.set(type,sum);
        normalize(q,sum);
    }

    public void createMoreSamples(int numSamples,int type,CalcProb calcProb){
        computeQ(type,calcProb);
        double[] q = qw.get(type);
        double[][] a = AliasUtils2.generateAlias(q);
        for(int i=0;i<numSamples;++i){
            double sample = random.nextUniform();
            double coinflip = random.nextUniform();
            int newTopic = AliasUtils2.sampleAlias(a,sample,coinflip,q.length);
            topicSamples.get(type).push(newTopic);
        }
    }

    public void normalize(double[] dist,double sum){
        for(int i = 0;i<dist.length;++i){
            dist[i]/=sum;
        }
    }
    
}
