/**
 * AliasUtils2.java
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

import java.util.Arrays;
import java.util.Set;
import java.util.Stack;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;


import cc.mallet.util.Randoms;


/**contains functions for helping with the Alias sampling methods from the KDD2014 paper Li et al.*/
public class AliasUtils2{
    
    /**calculates the alias table for the probability distribution p; note that the description in the KDD paper is wrong*/
    public static  double[][] generateAlias(double[] p){
        double epsilon = 0.000001;
        double[][] a = null;
        double average = 0;
        
        a = new double[p.length][3];
        average = 1/(double) p.length;
        
        Stack<Integer> l_i = new Stack<Integer>();
        Stack<Double> l_p = new Stack<Double>();
        Stack<Integer> h_i = new Stack<Integer>();
        Stack<Double> h_p = new Stack<Double>();
        for(int i = 0;i<p.length;++i){
            //if(p[i]>0+epsilon){
                if(p[i]<=average){
                    l_i.push(i);
                    l_p.push(p[i]);
                }else{
                    h_i.push(i);
                    h_p.push(p[i]);
                }
                //}
        }
        int counter = 0;
        
        while(!l_i.empty()||!h_i.empty()){
            if(h_i.empty()){
                int l_ind = l_i.pop();
                double l_prob = l_p.pop();
                a[counter][0]=l_ind;
                a[counter][1]=l_ind;
                a[counter][2]=l_prob;
                counter++;
            }else if(l_i.empty()){
                int h_ind;
                double h_prob;
                h_ind = h_i.pop();
                h_prob = h_p.pop();
                a[counter][0]=h_ind;
                a[counter][1]=h_ind;
                a[counter][2]=h_prob;
                double val = h_prob-average;
                if(val>average+epsilon){
                    h_i.push(h_ind);
                    h_p.push(val);
                }else{
                    l_i.push(h_ind);
                    l_p.push(val);
                }
            }else{
		int l_ind=-1;
                double l_prob=-1;
                int h_ind;
                double h_prob;
                h_ind = h_i.pop();
                h_prob = h_p.pop();
		l_ind = l_i.pop();
		l_prob = l_p.pop();
                a[counter][0]=l_ind;
                a[counter][1]=h_ind;
                a[counter][2]=l_prob;
                counter++;
                double val = h_prob-(average-l_prob);
                if(val>average+epsilon){
                    h_i.push(h_ind);
                    h_p.push(val);
                }else{
                    l_i.push(h_ind);
                    l_p.push(val);
                }
            }
        }
        /*        if(counter<p.length){
            System.out.println("error!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            double[][] na = new double[counter][3];
            for(int i = 0;i<counter;++i){
                na[i]=a[i];
            }
            a=na;
            }*/
        return a;
    }


    /**
samples a topic from an alias table
a: alias table
sample: random double between 0 and 1 for selecting the bin
coinflip: random double between 0 and 1 for selecting one of the two topics in the selected bin
distLength: the length of the probability distribution
*/
    public static int sampleAlias(double[][] a, double sample,double coinflip,int distLength){
        int bin = (int)(sample*a.length);
        double[] entry = a[bin];
        if(coinflip<distLength*entry[2]){
            return (int)entry[0];

        }else{
            return (int)entry[1];
        }
    }


    
    public static boolean stationaryMetropolisHastings(int[] local_tokensPerTopic, int s, int t, int type,double beta,double betaSum,double[] pdw,double pdw_norm,double[] qw,double qw_norm,double[] alpha,double sample,int[] tokensPerTopic,int[] typeTopicCounts){

        //compute pi
        double pi = 0;
        pi=(local_tokensPerTopic[t]+alpha[t])/(double)(local_tokensPerTopic[s]+alpha[s]);

        pi*=(typeTopicCounts[t]+beta)/(double)(typeTopicCounts[s]+beta);
        pi*=(tokensPerTopic[s]+betaSum)/(double)(tokensPerTopic[t]+betaSum);
        pi*=(pdw_norm*pdw[s]+qw_norm*qw[s])/(double)(pdw_norm*pdw[t]+qw_norm*qw[t]);
        //check whether to accept or not
        //double sample = random.nextUniform();
        if(pi<1){
            if(sample<=pi){
                return true;
            }else{
                return false;
            }
        }else{
            return true;
        }
    }

}
