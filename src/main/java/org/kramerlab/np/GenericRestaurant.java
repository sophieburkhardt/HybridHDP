/**
 * GenericRestaurant.java
 * 
 * Copyright (C) 2017 Sophie Burkhardt
 *
 * The method sampleb() was adapted from https://github.com/blei-lab/hdp which is licensed under GNU General Public License v2.0 and copyright belongs to Chong Wang and David Blei
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

import cc.mallet.util.Randoms;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.io.Serializable;

/**generic restaurant class that just stores the topic counts for one restaurant. It is possible to add and remove customers from this restaurant*/
public class GenericRestaurant implements Serializable{
        

    private static final long serialVersionUID = -7151056068632000886L;

    int numTables;
    int numCustomers;
    HashMap<Integer,Integer> numTablesPerTopic;
    HashMap<Integer,Integer> numCustomersPerTopic;
    Randoms random;
    double b;
    double evalB;


    public GenericRestaurant(){
        this(0.01);
    }

    public GenericRestaurant(double b){
        this(b,10);
    }

    public GenericRestaurant(double b,double evalB){
        this.evalB = evalB;
        this.b=b;
        this.numTables = 0;
        this.numCustomers = 0;
        this.numTablesPerTopic=new HashMap<Integer,Integer>();
        this.numCustomersPerTopic=new HashMap<Integer,Integer>();
        this.random = new Randoms();
    }

    public void setEvalB(double b){
        this.evalB = b;
    }

    public void clear(){
        this.numTables = 0;
        this.numCustomers = 0;
        this.numTablesPerTopic.clear();
        this.numCustomersPerTopic.clear();
    }
    
    public double getB(){
        return this.b;
    }
    
    public void setB(double b){
        this.b=b;
    }

    //what happens if the customer is not allowed to move?
    /** removes a customer for specified topic
        @force if true, only a customer is removed, no table and no checking is done
        @return 0: customer is not allowed to move, 1: a table is removed, 2: a customer is removed no table*/
    public int remove(int topic,boolean force){
        boolean hasOpened = hasOpenedTable(topic);
        boolean isAllowed = false;

        if(hasOpened&&!force){
            isAllowed = isAllowed(topic);
            if(isAllowed){
                removeTable(topic);
                return 1;
            }else{
                return 0;
            }
        }else{
            boolean check=false;
            check = removeCustomer(topic);
            return 2;
        }
    }

    /**adds a customer for specified topic and table indicator*/
    public void add(int topic,int tableIndicator){
        if(tableIndicator<2){
            addNewTable(topic);
        }else{
            addCustomer(topic);
        }
    }

    /**add a new customer that sits at an existing table if one exists*/
    public void add(int topic){
        int count = numCustomersPerTopic.get(topic)==null?0:numCustomersPerTopic.get(topic);
        if(count>0){
            addCustomer(topic);
        }else{
            addNewTable(topic);
        }
    }


    public boolean hasTopic(int topic){
        return this.numTablesPerTopic.get(topic)!=null;
    }

    private boolean isAllowed(int topic){
        //check if there are other customers
        if(this.numCustomersPerTopic.get(topic)>1){
            //check if there are other tables
            if(this.numTablesPerTopic.get(topic)==1){
                //no other tables
                return false;
            }
        }
        return true;
    }


    /**a new table with one costumer is added to the Restaurant,
       if the topic is new at the root level, the global table is also added*/
    private void addNewTable(int topic){
        //change local statistics
        numTables++;
        numCustomers++;
        int count = this.numCustomersPerTopic.get(topic)==null?0:this.numCustomersPerTopic.get(topic);
        this.numCustomersPerTopic.put(topic,count+1);
        if(count==0){
            //new topic for Restaurant
            numTablesPerTopic.put(topic,1);
        }else{
            numTablesPerTopic.put(topic,numTablesPerTopic.get(topic)+1);
        }
    }
        
    /**removes a table and one customer, if there are more customers at this table they are relocated to another table*/
    private void removeTable(int topic){
        numTables--;
        numCustomers--;
        int customersPerTopic = numCustomersPerTopic.get(topic);
        int tablesPerTopic = numTablesPerTopic.get(topic);
        if(customersPerTopic>1){
            numCustomersPerTopic.put(topic,customersPerTopic-1);
        }else{
            numCustomersPerTopic.remove(topic);
        }

        if(tablesPerTopic==1){
            //the last table for this topic
            this.numTablesPerTopic.remove(topic);
        }else{
            numTablesPerTopic.put(topic,tablesPerTopic-1);
        }
    }


    private void addCustomer(int topic){
        numCustomers++;
        int count = this.numCustomersPerTopic.get(topic)==null?0:this.numCustomersPerTopic.get(topic);
        this.numCustomersPerTopic.put(topic,count+1);
    }

    /**returns true if the customer did open the table*/
    private boolean hasOpenedTable(int topic){
        int numCustomersForTopic = this.numCustomersPerTopic.get(topic);
        double risk = numTablesPerTopic.get(topic)/(double)numCustomersForTopic;
        double randomNumber = random.nextUniform();
        if(randomNumber<risk){
            return true;
        }else{
            return false;
        }
    }

    /**removes one Customer but no table, key has to be >1*/
    private boolean removeCustomer(int topic){
        numCustomers--;
        int customersPerTopic = numCustomersPerTopic.get(topic);
        if(customersPerTopic>1){
            numCustomersPerTopic.put(topic,customersPerTopic-1);
        }else{
            numCustomersPerTopic.remove(topic);
        }

        if(customersPerTopic==1){
            //This is just for the problematic case where a customer is forced out and a table gets removed. This only happens for the root restaurant which means that the whole topic is removed
            System.err.println("Remove last customer with table ");// +key+" "+topic+" "+numCustomersPerTopic);
            numTables--;
            numTablesPerTopic.remove(topic);
            return false;
        }
        return true;
    }

    //see blei's HDP implementation (state.cpp sample_first_level_concentration)
    //also see escobar and west p 585
    public void sampleb(){
        double result = b;
        double  shape = 5.0;
        double  scale = 0.1;

        int k = this.numTables;
        int n = this.numCustomers;


        double eta = random.nextBeta(result+1,n);

        double pi = shape+k-1;

        double rate = 1.0/scale-Math.log(eta);

        pi = pi/(pi + rate * n);

        int cc = rbernoulli(pi);

        if (cc==1)
            result = random.nextGamma(shape+k,1.0/rate);
        else
            result = random.nextGamma(shape+k-1,1.0/rate);
        this.b = result;
    }

    public int rbernoulli(double val){
        double randomNumber = random.nextUniform();
        if(randomNumber<val) return 0;
        else return 1;
    }

     
}
