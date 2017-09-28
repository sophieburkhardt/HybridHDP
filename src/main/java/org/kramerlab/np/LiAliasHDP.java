/**
 * LiAliasHDP.java
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
import org.kramerlab.util.MultinomialCalcProb;
import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.AliasUtils2;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;


public class LiAliasHDP extends AbstractAliasHDP{


    ArrayList<HashSet<Integer>> topicsForType;

    public LiAliasHDP(GenericRestaurant root, ArrayList<GenericRestaurant> restaurants,double b0,double b1, StirlingTables stirling, boolean separateHyperparameters){
        super(root,restaurants,b0,b1,stirling,separateHyperparameters);
        this.topicsForType = new ArrayList<HashSet<Integer>>();
    }

    public void initTopicsForType(int numTypes){
        for(int type = 0;type<numTypes;++type){
            this.topicsForType.add(new HashSet<Integer>());
        }
    }

    public void addTopicToType(int type,int topic){
        this.topicsForType.get(type).add(topic);
    }

    public void removeTopicFromType(int type,int topic){
        this.topicsForType.get(type).remove(topic);
    }

    @Override
    public int[] sampleTopicIndex(Map<Integer,Integer> indicesForTopics, int type, CalcProb calcProb ,boolean fix,int restaurantIndex,int k,ArrayList<Integer> activeTopics){
        //if(restaurants.size()>100)System.out.println(restaurants.get(restaurantIndex).numCustomers+" "+ restaurantIndex+" "+restaurants.get(100).numCustomers);
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
        //System.out.println(pnorm/mass);
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
            //            System.out.println(p.length+" "+topics.length);
            int newTopicIndex = indicesForTopics==null?topics[counter/2]:indicesForTopics.get(topics[counter/2]);
            result[0] = newTopicIndex;
            result[1] = 2-counter%2;
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
        //clearSamples();//TEMPORARY
        //System.out.println(Arrays.toString(result));
        return result;
    }

    public int getPIndex(int topic){
        for(int i = 0;i<pTopics.length;++i){
            if(pTopics[i]==topic){
                return i;
            }
        }
        return -1;
    }


    @Override
    public boolean mh(int s, int t, int indexS, int indexT, int us, int ut,CalcProb calcProb,int type,int superIndex,boolean fix){
        if(indexS==-1)return true;//if the old topic was removed, accept whatever new topic was chosen
        //System.out.println(us+" "+ut);
        int numLevels = 2;

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
        if(us==0||!root.hasTopic(s)){//was new root topic
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
        if (ut==0||!root.hasTopic(t)){//the costumer wants to open a new table at root level
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

        //stored value old topic
        if(indexS!=-1){
            if(us==0){
                pValueS=0;
                qValueS=0;
            }else{
                pValueS=pindexS==-1?0:p[pindexS*numLevels+(numLevels-us)];
                qValueS=qMH[indexS*numLevels+(numLevels-us)];
            }
        }else{//if topic s was removed
            pValueS=0;
            qValueS=qMH[q.length-1];
            System.out.println("metro "+qValueS+" "+q.length+" "+(indexT*numLevels+(numLevels-ut)));
        }
        
        //stored value new topic
        if(indexT>=qMH.length/2||ut==0){
            pValueT = 0; //new topic has probability zero here
        }else{
            pValueT = pindexT==-1?0:p[pindexT*numLevels+(numLevels-ut)];
        }
        if(ut==0){
            qValueT = qMH[q.length-1];
        }else{
            qValueT = qMH[indexT*numLevels+(numLevels-ut)];
        }
    
        double nom = (pnorm*pValueS+qnorm*qValueS);
        double denom = (pnorm*pValueT+qnorm*qValueT);
        mult  = nom/(double)denom;
        pi*=mult;
        double sample = random.nextUniform();

        return sample<=pi;
    }


    protected void computeQ(GenericRestaurant restaurant, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix,int restaurantIndex,ArrayList<Integer> activeTopics){
        //this only works for multinomial
        MultinomialCalcProb multProb = (MultinomialCalcProb) calcProb;        
        double qnorm = 0;
        this.q = new double[activeTopics.size()*2];
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
        int counter = 0;
        for(int ind = 0;ind<activeTopics.size();++ind){
            int topic = activeTopics.get(ind);
            if(indicesForTopics!=null&&indicesForTopics.get(topic)==null)System.out.println("missing: "+topic);
            int index = indicesForTopics==null?topic:indicesForTopics.get(topic);
            int arrayIndex = counter*2;
            counter++;
            double mult = multProb.getQProb(type,index);

            if(restaurant.hasTopic(topic)){
                int tt=restaurant.numTablesPerTopic.get(topic);
                int customersPerTopic =restaurant.numCustomersPerTopic.get(topic);
                double oldTable = oldTableProb(topic,tt,customersPerTopic,restaurant.b,numTokens);
                double newTable = 0;
                if(hybrid){
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurant.b,numTokens,this.tablesPerTopic[topic],fix);
                }else{
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurant.b,numTokens,root.numCustomersPerTopic.get(topic),fix);
                }
                q[arrayIndex]=mult*oldTable;
                q[arrayIndex+1]=mult*newTable;//
            
            }
            else if(root.hasTopic(topic)){
                //new subTopic for SuperTopic
                int tpt = this.root.numCustomersPerTopic.get(topic);
                q[arrayIndex+1]=mult*newTopicProb(tpt,rootB,restaurantB,numTokens,fix);
            }else{
                q[arrayIndex+1]=rootB*restaurantB/(rootB+root.numCustomers);
            }
        
            qnorm += q[arrayIndex];
            qnorm += q[arrayIndex+1];
        }

        if(qnorm<0){
            System.out.println("problem at initialization for q "+pnorm+" "+Arrays.toString(q)+" "+restaurant.numTablesPerTopic+" "+restaurant.numCustomersPerTopic);
        }

        //normalize p_dw 
        normalize(q,qnorm);
        //save q
        if(storeQs&&storeJustPerType){
            Map qmap = qnormList.get(0);
            if(qmap==null) qmap = new HashMap<Integer,Double>();
            qmap.put(type,qnorm);
            qnormList.set(0,qmap);
            this.qAll.get(0).put(type,q.clone());//save a deep copy of q
        }else{
            Map qmap = qnormList.get(restaurantIndex);
            if(qmap==null) qmap = new HashMap<Integer,Double>();
            qmap.put(type,qnorm);
            qnormList.set(restaurantIndex,qmap);
            if(storeQs){
                this.qAll.get(restaurantIndex).put(type,q.clone());//save a deep copy of q
            }
        }
    
    }

    



    protected Integer[] computeP(int restaurantIndex, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,int type,boolean fix){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        //this only works for multinomial
        MultinomialCalcProb multProb = (MultinomialCalcProb) calcProb;        
        pnorm = 0;
        //only do existing topics
        Set<Integer> topicsForType = this.topicsForType.get(type);
        Integer[] topics = (Integer[])topicsForType.toArray(new Integer[0]);
        Arrays.sort(topics);
        p = new double[topicsForType.size()*2];
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
        //P_dw
        int counter = 0;
        for(int ind = 0;ind<topics.length;++ind){
            int topic = topics[ind];
            if(indicesForTopics!=null&&indicesForTopics.get(topic)==null)System.out.println("missing: "+topic);
            int index = indicesForTopics==null?topic:indicesForTopics.get(topic);
            int arrayIndex = counter*2;
            counter++;
            double mult = multProb.getPProb(type,index);

            if(restaurant.hasTopic(topic)){
                int tt=restaurant.numTablesPerTopic.get(topic);
                int customersPerTopic =restaurant.numCustomersPerTopic.get(topic);
                double oldTable = oldTableProb(topic,tt,customersPerTopic,restaurant.b,numTokens);
                double newTable = 0;
                if(hybrid){
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurant.b,numTokens,this.tablesPerTopic[topic],fix);
                }else{
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurant.b,numTokens,root.numCustomersPerTopic.get(topic),fix);
                }
                p[arrayIndex]=mult*oldTable;
                p[arrayIndex+1]=mult*newTable;//
            
            }else{
                //new subTopic for SuperTopic
                int tpt = this.root.numCustomersPerTopic.get(topic);
                p[arrayIndex+1]=mult*newTopicProb(tpt,rootB,restaurantB,numTokens,fix);//b1*tpt*tpt/(double)((tpt+1)*(root.numTables+b0));
            }

            if(p[arrayIndex]<0)System.out.println("negative p!!! "+p[arrayIndex]);//+" "+tt+" "+customersPerTopic+" "+mult+" "+oldTable);
            pnorm += p[arrayIndex];
            pnorm += p[arrayIndex+1];
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





    protected void createMoreSamples(int restaurantIndex, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix, int numSamples,ArrayList<Integer> activeTopics){
        computeQ(restaurantIndex, type, indicesForTopics, calcProb,fix,activeTopics);
        //get samples from q
        Stack<Integer> topSamples = new Stack<Integer>();
        Stack<Integer> tabSamples = new Stack<Integer>();
        double[][] a = AliasUtils2.generateAlias(q);
        for(int i=0;i<numSamples;++i){
            double sample = random.nextUniform();
            double coinflip = random.nextUniform();
            int aliasIndex = AliasUtils2.sampleAlias(a,sample,coinflip,q.length);
            int newTopicIndex = aliasIndex/2;
            int tableIndicator = newTopicIndex==root.numTables?0:2-aliasIndex%2;
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



}
