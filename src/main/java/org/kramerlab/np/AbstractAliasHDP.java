/**
 * AbstractAliasHDP.java
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

import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.AliasUtils2;
import org.kramerlab.interfaces.CalcProb;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;


public abstract class AbstractAliasHDP extends HDP{


    /**whether to store the q distributions for each type and super topic*/
    boolean storeQs;
    /**whether to store the q distributions by type, but not per (doc) restaurant (when using this for documents they are traversed one by one, so saving is not necessary), only effective when storeQs is set to true, otherwise will be ignored*/
    boolean storeJustPerType; 
    /**only required once (for evaluation), since it does not have to be stored if there is no MH-step*/
    double[] q;
    ArrayList<Map<Integer,double[]>> qAll;

    double[] p;
    ArrayList<Map<Integer,Double>> qnormList;
    double pnorm;

    ArrayList<Map<Integer,Stack<Integer>>> topicSamples;//maps the type to the corresponding topic samples
    ArrayList<Map<Integer,Stack<Integer>>> tableIndicatorSamples;

    Integer[] pTopics;


    public AbstractAliasHDP(GenericRestaurant root, ArrayList<GenericRestaurant> restaurants,double b0,double b1, StirlingTables stirling, boolean separateHyperparameters){
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




    /**if set to true, also sets storeQs to true*/
    public void setStoreJustPerType(boolean storeJustPerType){
        this.storeJustPerType = storeJustPerType;
        if(storeJustPerType){
            this.setStoreQs(true);
            topicSamples.add(new HashMap<Integer,Stack<Integer>>());
            tableIndicatorSamples.add(new HashMap<Integer,Stack<Integer>>());
            qnormList.add(new HashMap<Integer,Double>());
        }
    }


    public void setStoreQs(boolean storeQs){
        this.storeQs = storeQs;
        if(storeQs){
            if(storeJustPerType){
                //only save for current restaurant (document)
                qAll.add(new HashMap<Integer,double[]>());
            }else{
                for(int i = 0;i<topicSamples.size();++i){
                    //for each super topic
                    qAll.add(new HashMap<Integer,double[]>());
                }
            }
        }
    }

    public void removeTopicFromQs(int topicIndex){
        for(Map<Integer,double[]> qMap: qAll){
            for(Integer type: qMap.keySet()){
                double[] q = qMap.get(type);
                if(q.length>topicIndex){
                    double[] newQ = new double[q.length-1];
                    System.arraycopy(q,0,newQ,0,topicIndex);
                    System.arraycopy(q,topicIndex+1,newQ,topicIndex,q.length-topicIndex-1);
                    qMap.put(type,newQ);
                }
            }
        }
    }


    @Override
    public void remove(int index){
        super.remove(index);
        if(!storeJustPerType){
            this.qnormList.remove(index);
            this.topicSamples.remove(index);
            this.tableIndicatorSamples.remove(index);
            if(storeQs){
                qAll.remove(index);
            }
        }
    }

    public void add(GenericRestaurant restaurant, int capacity, float loadFactor){
        super.add(restaurant);
        if(!storeJustPerType){
            this.qnormList.add(new HashMap<Integer,Double>(capacity,loadFactor));
            this.topicSamples.add(new HashMap<Integer,Stack<Integer>>(capacity,loadFactor));
            this.tableIndicatorSamples.add(new HashMap<Integer,Stack<Integer>>(capacity,loadFactor));
            this.qAll.add(new HashMap<Integer,double[]>(capacity,loadFactor));
        }
    
    }

    @Override
    public void add(GenericRestaurant restaurant){
        this.add(restaurant,16,0.75f);
    }

    public void clearSamples(){
        for(int i = 0;i<topicSamples.size();++i){
            topicSamples.get(i).clear();
            tableIndicatorSamples.get(i).clear();
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

    public int getPIndex(int topic){
        for(int i = 0;i<p.length;++i){
            if(pTopics[i]==topic){
                return i;
            }
        }
        return -1;
    }

    

    protected void computeQ(int restaurantIndex,int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,boolean fix,ArrayList<Integer> activeTopics){
        GenericRestaurant restaurant = restaurants.get(restaurantIndex);
        computeQ(restaurant,type, indicesForTopics, calcProb, fix,restaurantIndex,activeTopics);
    }


    /**compute probability of opening a new table for all topics*/
    protected abstract void computeQ(GenericRestaurant restaurant, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix,int restaurantIndex,ArrayList<Integer> activeTopics);

    

    protected abstract void createMoreSamples(int restaurantIndex, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix, int numSamples,ArrayList<Integer> activeTopics);



    /**computes probability distribution for the topics that exist in the given restaurant
       @return the sorted list of unique topics for the given restaurant*/
    protected abstract Integer[] computeP(int restaurantIndex, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,int type,boolean fix);


    /**
checks if the change from the old topic to the selected topic should be accepted or not
*/
    public abstract boolean mh(int s, int t, int indexS, int indexT, int us, int ut,CalcProb calcProb,int type,int superIndex,boolean fix);



}
