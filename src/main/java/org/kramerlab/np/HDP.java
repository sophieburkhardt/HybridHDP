package org.kramerlab.np;

import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.util.StirlingTables;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import cc.mallet.types.FeatureVector;
import cc.mallet.util.Randoms;

public class HDP implements Serializable{

    private static final long serialVersionUID = 682708484262196171L;

    GenericRestaurant root;
    ArrayList<GenericRestaurant> restaurants;
    double b0;
    double b1;
    double a;
    StirlingTables stirling;
    Randoms random;

    boolean separateHyperparameters;
    boolean evaluate;
    boolean useEvalB1;

    double tablesPerTopicSum;
    double[] tablesPerTopic;
    boolean hybrid;

    public HDP(GenericRestaurant root, ArrayList<GenericRestaurant> restaurants,double b0,double b1, StirlingTables stirling, boolean separateHyperparameters){
        this.separateHyperparameters = separateHyperparameters;
        System.out.println("sample separately: "+this.separateHyperparameters);
        this.root = root;
        this.restaurants = restaurants;
        this.b0=b0;
        this.root.setB(b0);
        this.b1=b1;
        this.a=0.0;
        this.setB1(b1);
        this.stirling = stirling;
        this.random = new Randoms();
    }

    public void clearAll(){
        this.restaurants.clear();
        this.root.clear();
    }
    
    public void setHybrid(boolean hybrid){
        this.hybrid=hybrid;
    }

    public void setTablesPerTopic(double[] tablesPerTopic){
        this.tablesPerTopic=tablesPerTopic;
    }

    public void setTablesPerTopicSum(double sum){
        this.tablesPerTopicSum=sum;
    }

    public void setUseEvalB1(boolean use){
        this.useEvalB1 = use;
    }

    public void setEvaluate(boolean evaluate){
        this.evaluate = evaluate;
    }

    public void setB1(double b1){
        this.b1 = b1;
        for(GenericRestaurant restaurant: restaurants){
            restaurant.setB(b1);
        }
    }

    public GenericRestaurant get(int restaurantIndex){
        return restaurants.get(restaurantIndex);
    }
    public double getB1(){
        return this.b1;
    }

    public double getB0(){
        return this.root.getB();
    }

    
    public GenericRestaurant getRoot(){
        return this.root;
    }

    public void remove(int index){
        this.restaurants.remove(index);
    }

    public void add(GenericRestaurant restaurant){
        this.restaurants.add(restaurant);
        restaurant.setB(b1);
    }

    public void add(GenericRestaurant restaurant,int capacity, float floadFactor){
        this.add(restaurant);
    }


    public double[] getProbabilityDistribution(double[] dist,ArrayList<Integer> topics,HashMap<Integer,Integer> indicesForTopics,int restaurantIndex,boolean fix, CalcProb calcProb, int giventopic){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        return getProbabilityDistribution(dist, topics, indicesForTopics,restaurant,fix, calcProb, giventopic);
    }


    public double[] getProbabilityDistribution(double[] dist,ArrayList<Integer> topics,HashMap<Integer,Integer> indicesForTopics,GenericRestaurant restaurant,boolean fix, CalcProb calcProb, int giventopic){
        int numTopics = topics.size();
        int topicIndex, arrayIndex, tpt, tt=0, customersPerTopic=0;
        double mult, sum, oldprob=0, newprob=0;
        sum = 0;
        double restaurantB = 0;
        double rootB = 0;
        if(evaluate&&useEvalB1){
            restaurantB = restaurant.evalB;
            rootB = root.b;
        }else{
            restaurantB = restaurant.b;
            rootB = root.b;
        }
        int numTokens = restaurant.numCustomers;
        for(Integer topic: topics){
            topicIndex = indicesForTopics.get(topic);
            arrayIndex = topicIndex*2;
            mult = calcProb.getProb(giventopic,topicIndex);
            if(restaurant.hasTopic(topic)){
                //subtopic exists in restaurant
                tt = restaurant.numTablesPerTopic.get(topic);
                customersPerTopic = restaurant.numCustomersPerTopic.get(topic);
                oldprob =oldTableProb(topic,tt,customersPerTopic,restaurantB,numTokens);
                if(hybrid){
                    newprob = newTableProb(topic,tt,customersPerTopic,rootB,restaurantB,numTokens,this.tablesPerTopic[topic],fix);
                }else{
                    tpt = this.root.numCustomersPerTopic.get(topic);
                    newprob = newTableProb(topic,tt,customersPerTopic,rootB,restaurantB,numTokens,tpt,fix);
                }
                dist[arrayIndex] = mult * oldprob;
                dist[arrayIndex + 1] = mult * newprob;
            }else if(this.root.hasTopic(topic)){
                //subtopic is new in restaurant
                dist[arrayIndex] = 0; //old table not possible
                if(this.hybrid){
                    newprob = newTopicProb(this.tablesPerTopic[topic],rootB,restaurantB,numTokens,fix);
                }else{
                    tpt = this.root.numCustomersPerTopic.get(topic);
                    newprob = newTopicProb(tpt,rootB,restaurantB,numTokens,fix);
                }
                dist[arrayIndex + 1] = mult * newprob;
            }else{
                dist[arrayIndex] = 0;
                dist[arrayIndex + 1] = 0;
            }
            sum+=dist[arrayIndex];
            sum+=dist[arrayIndex+1];
        }
        if(!fix){
            arrayIndex = 2*numTopics;
            mult = calcProb.getProb(giventopic,-1);//prior.getTopicProbabilityMass(rest,giventopic);
            double rnc = this.hybrid?this.tablesPerTopicSum:this.root.numCustomers;
            dist[arrayIndex] = mult*rootB*restaurantB/(rootB+this.root.numCustomers);
            sum+=dist[arrayIndex];
        }
        normalize(dist,sum);
        return dist;
    }

    public double[] getProbabilityDistributionForLabels(FeatureVector labels, int restaurantIndex, CalcProb calcProb, int type,boolean evaluate,boolean fix){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        return getProbabilityDistributionForLabels(labels, restaurant, calcProb, type,evaluate,fix);
    }

    public double[] getProbabilityDistributionForLabels(FeatureVector labels, GenericRestaurant restaurant, CalcProb calcProb,int type,boolean evaluate,boolean fix){
        int numLabels = labels.numLocations();
        double[] dist = new double[numLabels*2];
        double sum = 0;
        double mult, oldprob, newprob;
        int arrayIndex, labelIndex;
        int label, tt, customersPerTopic,tpt;
        double restaurantB = 0;
        double rootB = 0;
        int numTokens = restaurant.numCustomers;
        if(evaluate&&useEvalB1){
            restaurantB = restaurant.evalB;
            rootB = root.b;
        }else{
            restaurantB = restaurant.b;
            rootB = root.b;
        }

        for(int rank = 0;rank<numLabels;++rank){
            label = labels.indexAtLocation(rank);
            arrayIndex = rank*2;
            labelIndex = label;//indicesForTopics.get(label);
            mult = calcProb.getProb(type,labelIndex);
            if(restaurant.hasTopic(label)){
                //subtopic exists in restaurant
                tt = restaurant.numTablesPerTopic.get(label);
                customersPerTopic = restaurant.numCustomersPerTopic.get(label);
                oldprob = oldTableProb(label,tt,customersPerTopic,restaurantB,numTokens);
                if(hybrid){
                    newprob = newTableProb(label,tt,customersPerTopic,rootB,restaurantB,numTokens,this.tablesPerTopic[label],fix);
                }else{
                    tpt = this.root.numCustomersPerTopic.get(label);
                    newprob = newTableProb(label,tt,customersPerTopic,rootB,restaurantB,numTokens,tpt,a,restaurant.numTables,fix);
                }
                if(evaluate){
                    dist[arrayIndex] = mult * oldprob;
                    dist[arrayIndex + 1] = mult * newprob;
                }else{
                    dist[arrayIndex] = mult * oldprob/(oldprob+newprob);
                    dist[arrayIndex + 1] = mult * newprob/(oldprob+newprob);
                }
            }else if(this.root.hasTopic(label)){
                //subtopic is new in restaurant
                dist[arrayIndex] = 0; //old table not possible
                tpt = this.root.numCustomersPerTopic.get(label);
                newprob = newTopicProb(tpt,rootB,restaurantB,numTokens,a,restaurant.numTables,fix);
                if(evaluate){
                    dist[arrayIndex + 1] = mult * newprob;
                }else{
                    dist[arrayIndex + 1] = mult;
                }
            }else{
                //if the label is absent, treat it as a new root topic so it is still possible to sample this label
                mult = calcProb.getProb(type,-1);
                dist[arrayIndex] = 0;
                dist[arrayIndex+1] = mult*rootB*restaurantB/(rootB+this.root.numCustomers);
            }
            sum+=dist[arrayIndex];
            sum+=dist[arrayIndex+1];
        }
        normalize(dist,sum);
        return dist;
    }

    public double getTopicProbabilityMass(GenericRestaurant restaurant,int topic,boolean fix){
        double mass = 0;
        double restaurantB = 0;
        if(evaluate&&useEvalB1){
            restaurantB = restaurant.evalB;
        }else{
            restaurantB=restaurant.b;
        }
        int numTokens = restaurant.numCustomers;
        if(restaurant.hasTopic(topic)){
            int tt = restaurant.numTablesPerTopic.get(topic);
            int customersPerTopic = restaurant.numCustomersPerTopic.get(topic);
            double prob = oldTableProb(topic,tt,customersPerTopic,restaurantB,numTokens);
            mass += prob;
            if(hybrid){
                prob = newTableProb(topic,tt,customersPerTopic,root.b,restaurantB,numTokens,this.tablesPerTopic[topic],fix);
            }else{
                int tpt = this.root.numCustomersPerTopic.get(topic)==null?1:this.root.numCustomersPerTopic.get(topic);//technically this should not be null but in some cases the topic was temporarily removed while sampling the super topic (see NPFastDependencyLDA2 sampleSuperTopic)
                prob = newTableProb(topic,tt,customersPerTopic,root.b,restaurantB,numTokens,tpt,fix);
            }
            mass += prob;
                            
        }else{
            int tpt = this.root.numCustomersPerTopic.get(topic)==null?1:this.root.numCustomersPerTopic.get(topic);//technically this should not be null but in some cases the topic was temporarily removed while sampling the super topic (see NPFastDependencyLDA2 sampleSuperTopic)
            double prob = newTopicProb(tpt,root.b,restaurantB,numTokens,fix);
            mass = prob;
        }//else{
        // System.out.println("unknown topic "+topic+" "+this.root.numTablesPerTopic);    
        //}
        return mass;
    }


    public double oldTableProb(int topic, int tt, int customersPerTopic,double b,int numTokens){
        return stirling.stirling_U(customersPerTopic,tt)*(customersPerTopic-tt+1)/(double)(customersPerTopic+1)/(double)(b+numTokens);//old table
     
    }

    /**see hca likesub.c method: doctableindicatorprob() (multiply with b which is not mentioned in the paper but present in the code)*/
    public double newTableProb(int topic, int tt, int customersPerTopic,double b0,double b1,int numTokens,int tpt,boolean fixed){
        return newTableProb(topic,tt,customersPerTopic,b0,b1,numTokens,tpt,0,0,fixed);
        //return b1*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*tpt/(b0+root.numCustomers)/(1.0+tpt);
    }

    public double newTableProb(int topic, int tt, int customersPerTopic,double b0,double b1,int numTokens,int tpt,double a,int nt,boolean fixed){
        //this version includes a!=0
        if(fixed){
            return (b1+a*nt)*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*(tpt-a)/(root.numCustomers-root.numTables*a)/(1.0+tpt);
        }else{
            return (b1+a*nt)*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*(tpt-a)/(b0+root.numCustomers)/(1.0+tpt);
        }
    }

    
    public double newTableProb(int topic, int tt, int customersPerTopic,double b0,double b1,int numTokens,double tpt,boolean fixed){
        //this version includes an estimate of the number of tables per topic tpt rather than an exact count
        return newTableProb(topic,tt,customersPerTopic,b0,b1,numTokens,tpt,0,0,fixed);
        //return b1*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*tpt/(b0+this.tablesPerTopicSum)/(1.0+tpt);
    }

    public double newTableProb(int topic, int tt, int customersPerTopic,double b0,double b1,int numTokens,double tpt,double a,int nt,boolean fixed){
        //this version includes an estimate of the number of tables per topic tpt rather than an exact count
        //this version includes a!=0
        if(fixed){
            return (b1+a*nt)*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*(tpt-a)/(root.numCustomers-root.numTables*a)/(1.0+tpt);
        }else{
            return (b1+a*nt)*stirling.stirling_UV(customersPerTopic,tt+1)*(tt+1)/(double)(customersPerTopic+1)/(double)(b1+numTokens)*tpt*(tpt-a)/(b0+root.numCustomers)/(1.0+tpt);
        }
    }


    
    public double newTopicProb(int tpt,double b0, double b1,int numTokens,boolean fixed){
        //basic version for a=0
        return newTopicProb(tpt,b0,b1,numTokens,0,0,fixed);
        //return b1 * tpt * tpt / (double)((tpt + 1) * (this.root.numCustomers + b0))/(double)(b1+numTokens);
    }

    public double newTopicProb(int tpt,double b0, double b1,int numTokens,double a, int nt,boolean fixed){
        //this version includes a!=0
        //fixed is true if the number of topics is fixed so normalization is different
        if(fixed){
            return (b1+a*nt) * (tpt-a) * tpt / (double)((tpt + 1) * (this.root.numCustomers -this.root.numTables*a))/(double)(b1+numTokens);
        }else{
            return (b1+a*nt) * (tpt-a) * tpt / (double)((tpt + 1) * (this.root.numCustomers + b0))/(double)(b1+numTokens);
        }
    }

    
    public double newTopicProb(double tpt,double b0, double b1,int numTokens,boolean fixed){
        //this version includes an estimate of the number of tables per topic tpt rather than an exact count
        return newTopicProb(tpt,b0,b1,numTokens,0,0,fixed);
        //  return b1 * tpt * tpt / (double)((tpt + 1) * (this.tablesPerTopicSum + b0))/(double)(b1+numTokens);
    }

    public double newTopicProb(double tpt,double b0, double b1,int numTokens,double a, int nt,boolean fixed){
        if(fixed){
            return (b1+a*nt) * (tpt-a) * tpt / (double)((tpt + 1) * (this.root.numCustomers -this.root.numTables*a))/(double)(b1+numTokens);
        }else{
            return (b1+a*nt) * (tpt-a) * tpt / (double)((tpt + 1) * (this.root.numCustomers + b0))/(double)(b1+numTokens);
        }
    }

    public void normalize(double[] dist, double norm){
        //normalize
        for(int i = 0;i<dist.length;++i){
            dist[i]/=norm;
        }
    }


    public void sampleb0(){
        root.sampleb();
    }

    public void sampleb1(){
        if(this.separateHyperparameters){
            sampleb1Separately();
        }else{
            sampleb1Combined();
        }
    }


    public void sampleb1Separately(){
        for(GenericRestaurant restaurant: restaurants){
            restaurant.sampleb();
        }
    }

    public double sampleb1Combined(){
        //System.out.println("sampleb1 "+root.numCustomers+" "+tablesPerTopicSum);
        double result = b1;
        double  shape = 1.0;
        double  scale = 1.0;

        int n = 0;
        if(hybrid){
            int sum=0;
            int sumCusts = 0;
        for (int d = 0; d < restaurants.size(); d++)
            {
                int restTables = restaurants.get(d).numTables;
                int docLength = restaurants.get(d).numCustomers;
                sum+=restTables;
                sumCusts+=docLength;
            }
        //System.out.println("sumDocLength "+sumCusts+" tables "+sum);
    
            n = sum;
        }else{
            n = root.numCustomers;
        }
        double rate, sum_log_w, sum_s;
        for (int step = 0; step < 20; step++)
            {
                sum_log_w = 0.0;
                sum_s = 0.0;
                for (int d = 0; d < restaurants.size(); d++)
                    {
                        int docLength = restaurants.get(d).numCustomers;
                        if(docLength>0){
                            sum_log_w += Math.log(random.nextBeta(result + 1, docLength));
                            sum_s += (double)rbernoulli(docLength / (docLength + result));
                        }
                    }
                rate = 1.0 / scale - sum_log_w;
                //System.out.println(step+" "+(shape+n-sum_s)+" "+1.0/rate+" "+ sum_s);
                result = random.nextGamma(shape + n - sum_s, 1.0 / rate);
                //System.out.println(step+" "+result);
            }
        this.b1 = result;
        this.setB1(result);
        return result;
    }





    public int rbernoulli(double val){
        double randomNumber = random.nextUniform();
        if(randomNumber<val) return 0;
        else return 1;
    }



}
