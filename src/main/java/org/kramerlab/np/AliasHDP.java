/**
 * AliasHDP.java
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

import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.AliasUtils2;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;


public class AliasHDP extends AbstractAliasHDP{//HDP{


    public AliasHDP(GenericRestaurant root, ArrayList<GenericRestaurant> restaurants,double b0,double b1, StirlingTables stirling, boolean separateHyperparameters){
        super(root,restaurants,b0,b1,stirling,separateHyperparameters);
        this.topicSamples = new ArrayList<Map<Integer,Stack<Integer>>>();
        this.tableIndicatorSamples = new ArrayList<Map<Integer,Stack<Integer>>>();
        this.qnormList = new ArrayList<Map<Integer,Double>>();
        qAll = new ArrayList<Map<Integer,double[]>>();
        this.storeQs = false;
        this.storeJustPerType = false;
        if(storeQs&&storeJustPerType){
            topicSamples.add(new HashMap<Integer,Stack<Integer>>());
            tableIndicatorSamples.add(new HashMap<Integer,Stack<Integer>>());
            qnormList.add(new HashMap<Integer,Double>());
        }
    }


    public int[] sampleTopicIndex(Map<Integer,Integer> indicesForTopics, int type, CalcProb calcProb ,boolean fix,int restaurantIndex,int k,ArrayList<Integer> activeTopics){
        int[] result = new int[2];
        Integer[] topics = computeP(restaurantIndex,indicesForTopics,calcProb,type,fix);
        Stack<Integer> topSamples = null;
        if(!storeJustPerType){
            topSamples = topicSamples.get(restaurantIndex).get(type);
        }else{
            topSamples = topicSamples.get(0).get(type);
        }
        if(topSamples==null||topSamples.empty()){
            //if there are no samples stored, recompute
            createMoreSamples(restaurantIndex,type,indicesForTopics,calcProb,fix,k,activeTopics);
        }
        double qnorm = -1;
        if(!storeJustPerType){
            qnorm = qnormList.get(restaurantIndex).get(type);
        }else{
            qnorm = qnormList.get(0).get(type);
        }
        double mass = pnorm+qnorm;
        double rand = random.nextUniform();
        if(rand<pnorm/mass){
            rand*=mass;
            rand/=pnorm;
            //get topic from existing topics
            result[1] = 2;
            int counter = -1;
            while(rand>0){
                counter++;
                rand-=p[counter];
            }    
            int newTopicIndex = indicesForTopics==null?topics[counter]:indicesForTopics.get(topics[counter]);
            result[0] = newTopicIndex;
        }else{
            //get topic from stored samples
            Stack<Integer> tabSamples = null;
            if(!storeJustPerType){
                tabSamples = tableIndicatorSamples.get(restaurantIndex).get(type);
                topSamples = topicSamples.get(restaurantIndex).get(type);
            }else{
                tabSamples = tableIndicatorSamples.get(0).get(type);
                topSamples = topicSamples.get(0).get(type);
            }

            result[0] = topSamples.pop();
            result[1] = tabSamples.pop();
        }
        return result;
    }


    

    protected void computeQ(int restaurantIndex,int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,boolean fix,ArrayList<Integer> activeTopics){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        computeQ(restaurant,type, indicesForTopics, calcProb, fix,restaurantIndex,activeTopics);
    }


    /**compute probability of opening a new table for all topics*/
    protected void computeQ(GenericRestaurant restaurant, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix,int restaurantIndex,ArrayList<Integer> activeTopics){
     
        int numTopics = activeTopics.size();//root.numTables;
        if(fix){
            q = new double[numTopics];        
        }else{
            q = new double[numTopics+1];        
        }
        double restaurantB=0;
        double rootB=0;
        if(evaluate){
            restaurantB=restaurant.evalB;
            rootB = root.b;
        }else{
            restaurantB=restaurant.b;
            rootB = root.b;
        }
        double qnorm = 0;
        int counter = 0;
        int numTokens = restaurant.numCustomers;
        for(int index = 0;index<activeTopics.size();++index){
            Integer topic = activeTopics.get(index);
            int arrayIndex = index;//counter;//!!!
            if(this.root.hasTopic(topic)){
                counter++;
                double mult = calcProb.getProb(type,index);
                if(!restaurant.hasTopic(topic)){
                    //new subTopic for SuperTopic
                    int tpt = this.root.numCustomersPerTopic.get(topic);
                    q[arrayIndex]=mult*newTopicProb(tpt,rootB,restaurantB,numTokens,fix);
                }else{
                    //old subTopic
                    int tt=restaurant.numTablesPerTopic.get(topic)==null?0:restaurant.numTablesPerTopic.get(topic);
                    int customersPerTopic =restaurant.numCustomersPerTopic.get(topic);
                    double newTable = 0;
                    if(hybrid){
                        newTable = newTableProb(topic,tt,customersPerTopic,rootB,restaurantB,numTokens,this.tablesPerTopic[topic],fix);
                    }else{
                        int tpt = this.root.numCustomersPerTopic.get(topic);
                        newTable = newTableProb(topic,tt,customersPerTopic,rootB,restaurantB,numTokens,tpt,fix);
                    }

                    q[arrayIndex]=mult*newTable;//
                }
            }
            qnorm += q[arrayIndex];
        }
        if(!fix){
            //add one more for a new topic
            //u==0, new topic at root level
            q[numTopics]=calcProb.getProb(type,-1);//beta/betaSum;//beta is per word
            q[numTopics]*=rootB*restaurantB/(rootB+root.numCustomers);
            qnorm+=q[numTopics];            

        }

        if(qnorm<0){
            System.out.println("problem at initialization of q "+qnorm+" "+Arrays.toString(q));
        }

        //normalize q 
        normalize(q,qnorm);
        //System.out.println(Arrays.toString(q));
        if(storeQs&&storeJustPerType){
            Map qmap = qnormList.get(0);
            if(qmap==null) qmap = new HashMap<Integer,Double>();
            qmap.put(type,qnorm);
            qnormList.set(0,qmap);
            this.qAll.get(0).put(type,q.clone());//save a deep copy of q
        }else{
            //System.out.println("here");
            Map qmap = qnormList.get(restaurantIndex);
            if(qmap==null) qmap = new HashMap<Integer,Double>();
            qmap.put(type,qnorm);
            qnormList.set(restaurantIndex,qmap);
            if(storeQs){
                this.qAll.get(restaurantIndex).put(type,q.clone());//save a deep copy of q
            }
        }
    }

    

    protected void createMoreSamples(int restaurantIndex, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix, int numSamples,ArrayList<Integer> activeTopics){
        computeQ(restaurantIndex, type, indicesForTopics, calcProb,fix,activeTopics);
        //get samples from q
        Stack<Integer> topSamples = new Stack<Integer>();
        Stack<Integer> tabSamples = new Stack<Integer>();
        double[][] a = AliasUtils2.generateAlias(q);
        for(int i=0;i<numSamples;++i){
            double sample = random.nextUniform();
            double coinflip = random.nextUniform();
            int newTopicIndex = AliasUtils2.sampleAlias(a,sample,coinflip,q.length);
            int tableIndicator = newTopicIndex==root.numTables?0:1;
            topSamples.push(newTopicIndex);
            tabSamples.push(tableIndicator);
        }
        if(!storeJustPerType){
            this.topicSamples.get(restaurantIndex).put(type,topSamples);
            this.tableIndicatorSamples.get(restaurantIndex).put(type,tabSamples);
        }else{
            this.topicSamples.get(0).put(type,topSamples);
            this.tableIndicatorSamples.get(0).put(type,tabSamples);
        }
    }



    /**computes probability distribution for the topics that exist in the given restaurant
       @return the sorted list of unique topics for the given restaurant*/
    protected Integer[] computeP(int restaurantIndex, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,int type,boolean fix){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        //int numTopics = typeTopicCounts.length;
        
        pnorm = 0;
        //only do existing topics
        Set<Integer> topicsForType = restaurant.numTablesPerTopic.keySet();
        Integer[] topics = (Integer[])topicsForType.toArray(new Integer[0]);
        Arrays.sort(topics);
        p = new double[topicsForType.size()];
        //P_dw
        int numTokens = restaurant.numCustomers;
        double restaurantB=1;
        if(evaluate){
            restaurantB=restaurant.evalB;
        }else{
            restaurantB=restaurant.b;
        }

        int counter = 0;
        for(int ind = 0;ind<topics.length;++ind){
            int topic = topics[ind];
            if(indicesForTopics!=null&&indicesForTopics.get(topic)==null)System.out.println("missing: "+topic);
            int index = indicesForTopics==null?topic:indicesForTopics.get(topic);
            int arrayIndex = counter;
            counter++;
            double mult = calcProb.getProb(type,index);
                //old subTopic
                int tt=restaurant.numTablesPerTopic.get(topic);
                int customersPerTopic =restaurant.numCustomersPerTopic.get(topic);
                //if(customersPerTopic>1000)System.out.println("p: "+customersPerTopic+" "+topic+" "+restaurantIndex);
                double oldTable = oldTableProb(topic,tt,customersPerTopic,restaurantB,numTokens);

                p[arrayIndex]=mult*oldTable;

                if(p[arrayIndex]<0)System.out.println("negative p!!! "+p[arrayIndex]+" "+tt+" "+customersPerTopic+" "+mult+" "+oldTable);
            pnorm += p[arrayIndex];
        }

        if(pnorm<0){
            System.out.println("problem at initialization for p "+pnorm+" "+Arrays.toString(topics)+" "+Arrays.toString(p)+" "+restaurant.numTablesPerTopic+" "+restaurant.numCustomersPerTopic);
        }

        //normalize p_dw 
        normalize(p,pnorm);
        //System.out.println(Arrays.toString(p));
        this.pTopics = topics;
        return topics;
    }
        

    /**
checks if the change from the old topic to the selected topic should be accepted or not
*/
    public boolean mh(int s, int t, int indexS, int indexT, int us, int ut,CalcProb calcProb,int type,int superIndex,boolean fix){
        if(indexS==-1)return true;//if the old topic was removed, accept whatever new topic was chosen
        //System.out.println(us+" "+ut);
        int pindexS = this.getPIndex(s);
        int pindexT = this.getPIndex(t);
        GenericRestaurant restaurant = restaurants.get(superIndex);

        double restaurantB=0;
        double rootB=0;
        if(evaluate){
            restaurantB=restaurant.evalB;
            rootB = root.b;
        }else{
            restaurantB=restaurant.b;
            rootB = root.b;
        }
        int numTokens = restaurant.numCustomers;

        double qnorm = -1;
        if(storeJustPerType){
            if(qnormList.get(0).get(type)==null)return true; //the first time we always accept since we don't have probabilities for the random initialization
            qnorm = qnormList.get(0).get(type);
        }else{
            if(qnormList.get(superIndex).get(type)==null)return true; //the first time we always accept since we don't have probabilities for the random initialization
            qnorm = qnormList.get(superIndex).get(type);
        }

        //compute pi
        double pi = 0;
        double piT = 0;
        double piS = 0;
        if(us==0||root.numTablesPerTopic.get(s)==null){//was new root topic
            piS=rootB*restaurantB/(rootB+root.numCustomers);
        }else if(!restaurant.hasTopic(s)){//no other costumer left with same topic
            //new topic
            piS=newTopicProb(root.numCustomersPerTopic.get(s),rootB,restaurantB,numTokens,fix);
        }else{//topic s is still in the document
            if(us==1){
                //new table
                if(hybrid){
                    piS=newTableProb(s,restaurant.numTablesPerTopic.get(s),restaurant.numCustomersPerTopic.get(s),rootB,restaurantB,numTokens,this.tablesPerTopic[s],fix);
                }else{
                    piS=newTableProb(s,restaurant.numTablesPerTopic.get(s),restaurant.numCustomersPerTopic.get(s),rootB,restaurantB,numTokens,root.numCustomersPerTopic.get(s),fix);
                }
            }else if(us==2){
                //old table
                piS=oldTableProb(s,restaurant.numTablesPerTopic.get(s),restaurant.numCustomersPerTopic.get(s),restaurantB,numTokens);
            }else{
                System.out.println("error during metropolis hastings step "+us);
            }
        }
        boolean newTableT = false; 
        if (ut==0||root.numTablesPerTopic.get(t)==null){//the costumer wants to open a new table at root level
            piT=rootB*restaurantB/(rootB+root.numCustomers);
        }else if(!restaurant.hasTopic(t)){//the costumer wants to open a new table at document level
            //check if new or old topic
            //            if(ut!=1)System.out.println("why not 1??");
            if(root.numTablesPerTopic.get(t)==null) System.out.println("mh "+t+" "+root.numTablesPerTopic+" "+restaurant.numTablesPerTopic);
            piT = newTopicProb(root.numCustomersPerTopic.get(t),rootB,restaurantB,numTokens,fix);
        }else{//the costumer wants to sit at an existing table, stirling expr. for old table
            if (ut==1){
                //old topic, stirling expr. for new table
                if(hybrid){
                    piT=newTableProb(t,restaurant.numTablesPerTopic.get(t),restaurant.numCustomersPerTopic.get(t),rootB,restaurantB,numTokens,this.tablesPerTopic[t],fix);
                }else{
                    piT=newTableProb(t,restaurant.numTablesPerTopic.get(t),restaurant.numCustomersPerTopic.get(t),rootB,restaurantB,numTokens,root.numCustomersPerTopic.get(t),fix);
                }
            }else{
                piT=oldTableProb(t,restaurant.numTablesPerTopic.get(t),restaurant.numCustomersPerTopic.get(t),restaurantB,numTokens);
            }
        }

        if(ut!=0){
            piT*=calcProb.getProb(type,indexT);
        }else{
            piT*=calcProb.getProb(type,-1);
        }

        if(indexS!=-1){
            piS*=calcProb.getProb(type,indexS);
        }else{
            piS*=calcProb.getProb(type,-1);
        }
        pi=piT/piS;

        double mult=-1;
        double pValueS,qValueS,pValueT,qValueT;
               
        double[] qMH = null;
        if(storeJustPerType){
            qMH = qAll.get(0).get(type);
        }else{
            qMH = qAll.get(superIndex).get(type);
        }

        if(us==0){
            pValueS=0;
            qValueS=qMH[qMH.length-1];
        }else{
            if(us==1){
                pValueS=0;
                if(indexS>qMH.length-1){
                    //the topic did not exist yet when q was made
                    qValueS=qMH[qMH.length-1];
                }else{
                    qValueS=indexS!=-1?qMH[indexS]:qMH[qMH.length-1];//if the old topic was removed...
                }
            }else{
                //us==2
                qValueS=0;
                if(pindexS!=-1){
                    pValueS=p[pindexS];
                }else{
                    pValueS=0;
                }
            }
        }
        
        if(ut==0){
            pValueT = 0; //new topic has probability zero here
            qValueT = qMH[qMH.length-1];
        }else{
            if(ut==1){
                pValueT=0;
                if(indexT>qMH.length-1){
                    //several topics were added since the last recompute
                    qValueT = qMH[qMH.length-1];
                }else{
                    qValueT = qMH[indexT];
                }
            }else{
                //ut==2
                qValueT=0;
                if(pindexT!=-1){
                    pValueT = p[pindexT];
                }else{
                    pValueT = 0;
                }
            }
        }

        double nom = (pnorm*pValueS+qnorm*qValueS);
        double denom = (pnorm*pValueT+qnorm*qValueT);
        mult  = nom/(double)denom;
        pi*=mult;
        double sample = random.nextUniform();
        return sample<=pi;
    }

}
