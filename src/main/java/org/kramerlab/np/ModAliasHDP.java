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


public class ModAliasHDP extends AbstractAliasHDP{//HDP{

    public ModAliasHDP(GenericRestaurant root, ArrayList<GenericRestaurant> restaurants,double b0,double b1, StirlingTables stirling, boolean separateHyperparameters){
super(root,restaurants,b0,b1,stirling,separateHyperparameters);
        this.q = new HashMap<Integer,double[]>();
        this.qnormList = new HashMap<Integer,Double>();
        this.topicSamples = new HashMap<Integer,Stack<Integer>>();
        this.tableIndicatorSamples = new HashMap<Integer,Stack<Integer>>();
    }


    double discardmass;
    Map<Integer,double[]> q;
    //double[] p;
    Map<Integer,Double> qnormList;
    //double pnorm;

    Map<Integer,Stack<Integer>> topicSamples;//maps the type to the corresponding topic samples
    Map<Integer,Stack<Integer>> tableIndicatorSamples;

    //Integer[] pTopics;

    int qcounter = 0;
    int pcounter = 0;

    public void printRatio(){
        if(pcounter+qcounter==0)System.out.println("0");
        else{
            System.out.println("Ratio: "+pcounter/(double)(qcounter+pcounter));
            pcounter = 0;
            qcounter = 0;
        }
    }

    public void clearSamples(){
        //for(int i = 0;i<topicSamples.size();++i){
            topicSamples.clear();
            tableIndicatorSamples.clear();
            //}
    }


    public int[] sampleTopicIndex(Map<Integer,Integer> indicesForTopics, int type, CalcProb calcProb ,boolean fix,int restaurantIndex,int k,ArrayList<Integer> activeTopics){
        GenericRestaurant restaurant = null;
        /*    if(this.hybrid){
            restaurant = this.restaurants.get(0);
        }else{
            restaurant = this.restaurants.get(restaurantIndex);
            }*/
        restaurant = this.restaurants.get(restaurantIndex);
        //System.out.println("clear");
        //this.clearSamples();
        //if(restaurants.size()>100)System.out.println(restaurants.get(restaurantIndex).numCustomers+" "+ restaurantIndex+" "+restaurants.get(100).numCustomers);
        int[] result = new int[2];
        
        Stack<Integer> topSamples = topicSamples.get(type);
        //System.out.println(topSamples);
        /*if(!storeJustPerType){
            topSamples = topicSamples.get(restaurantIndex).get(type);
        }else{
            topSamples = topicSamples.get(0).get(type);
            }*/
        if(topSamples==null||topSamples.empty()){
            //if there are no samples stored, recompute
            //System.out.println("create"+type);
            //System.out.flush();
            createMoreSamples(restaurantIndex,type,indicesForTopics,calcProb,fix,k,activeTopics);
        }
        Integer[] topics = computeP(restaurantIndex,indicesForTopics,calcProb,type,fix);
        double qnorm = qnormList.get(type);
        /*if(!storeJustPerType){
            qnorm = qnormList.get(restaurantIndex).get(type);
        }else{
            qnorm = qnormList.get(0).get(type);
            }*/
        double mass = pnorm+qnorm-discardmass;
        double rand = random.nextUniform();
        boolean debug=false;
        if(debug){
            double[] comp1 = new double[activeTopics.size()];
            double[] probabilities = super.getProbabilityDistribution(new double[activeTopics.size()*2],activeTopics,(HashMap)indicesForTopics,restaurant,true,calcProb,type);
            double[] comp = new double[activeTopics.size()];
            double[] qhere = this.q.get(type);
            for(int i = 0;i<activeTopics.size();++i){
                comp[i] = qhere[i]*qnorm;
            }
            double d = 0;
            for(int t = 0;t<topics.length;++t){
                d+=comp[topics[t]];
                comp[topics[t]]=0;                
                comp[topics[t]]+=p[t*2]*pnorm;
                comp[topics[t]]+=p[t*2+1]*pnorm;
            }
            if(d<discardmass-0.0000001||d>discardmass+0.0000001){
                System.out.println("discardmass mismatch "+d+" "+discardmass);
            }
            String test = "";
            for(int i = 0;i<activeTopics.size();++i){
                test+=(i+" "+comp[i]+" ");
                comp[i]/=qnorm+pnorm-discardmass;
                comp1[i]=probabilities[i*2]+probabilities[i*2+1];
                if(comp[i]<comp1[i]-0.000000001||comp[i]>comp1[i]+0.0000000001){
                    System.out.println(Arrays.toString(comp1)+"-"+Arrays.toString(comp)+" "+mass+" "+Arrays.toString(qhere)+" "+Arrays.toString(p));
                    System.out.println(Arrays.toString(topics)+" "+test);
                }
            }
            //System.out.println(Arrays.toString(comp1)+"-"+Arrays.toString(comp)+" "+mass+" "+Arrays.toString(qhere)+" "+Arrays.toString(p));
        }
        //System.out.println((pnorm/mass)+" "+((qnorm-discardmass)/mass)+" "+(qnorm-discardmass)+" "+p.length+" "+this.q.get(type).length);
        //System.out.println(pnorm+" "+qnorm+" "+discardmass+" "+(pnorm/mass));
        if(rand<pnorm/mass){
            pcounter+=1;
            rand*=mass;
            rand/=pnorm;
            //get topic from existing topics
    
            int counter = -1;
            while(rand>0){
                counter++;
                rand-=p[counter];
            }    
            //            System.out.println(p.length+" "+topics.length);
            int newTopicIndex = indicesForTopics==null?topics[counter/2]:indicesForTopics.get(topics[counter/2]);
            result[0] = newTopicIndex;
            result[1] = 2-counter%2;
            //if(result[1]==1)System.out.println("1111 "+result[0]+" "+result[1]);
        }else{
            qcounter+=1;
            //get topic from stored samples
            Stack<Integer> tabSamples = tableIndicatorSamples.get(type);
            topSamples = topicSamples.get(type);
            
            /*if(!storeJustPerType){
                tabSamples = tableIndicatorSamples.get(restaurantIndex).get(type);
                topSamples = topicSamples.get(restaurantIndex).get(type);
            }else{
                tabSamples = tableIndicatorSamples.get(0).get(type);
                topSamples = topicSamples.get(0).get(type);
                }*/

            result[0] = topSamples.pop();
            result[1] = tabSamples.pop();
            boolean found = false;
            int count=0;
            while(!found){
                //System.out.println(found);
                //discard sample if it occurs in restaurant
                if(restaurant.numTablesPerTopic.get(result[0])!=null){
                    count+=1;
                    if(count>10){
                        result = this.directlySample(type,restaurant);
                        break;
                    }
                    //System.out.println("reject");
                    //this.clearSamples();
                    if(topSamples.empty()){
                        //if there are no samples stored, recompute
                        createMoreSamples(restaurantIndex,type,indicesForTopics,calcProb,fix,k,activeTopics);
                        tabSamples = tableIndicatorSamples.get(type);
                        topSamples = topicSamples.get(type);
                    }
                    result[0] = topSamples.pop();
                    result[1] = tabSamples.pop();
                }else{
                    found = true;
                }
            }
    
        }
        //clearSamples();//TEMPORARY
        //System.out.println(Arrays.toString(result));
        return result;
    }

    public int[] directlySample(int type,GenericRestaurant restaurant){
        int[] result=new int[2];
        result[1]=1;
        double[] qType = this.q.get(type);    
        double normType=this.qnormList.get(type);
        double sample = this.random.nextUniform()*(normType-this.discardmass);
        for(int i = 0;i<qType.length;++i){
            if(!restaurant.hasTopic(i)){
                sample-=qType[i]*normType;
                if(sample<0){
                    result[0]=i;
                    break;
                }
            }
        }

        return result;
    }
    

    protected void computeQ(int restaurantIndex,int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,boolean fix,ArrayList<Integer> activeTopics){
        GenericRestaurant restaurant = null;
        //if(this.hybrid){
        //  restaurant = restaurants.get(0);
        //}else{
        restaurant = restaurants.get(restaurantIndex);
            //}
        computeQ(restaurant,type, indicesForTopics, calcProb, fix,restaurantIndex,activeTopics);
    }


    /**compute probability of opening a new table for all topics*/
    protected void computeQ(GenericRestaurant restaurant, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix,int restaurantIndex,ArrayList<Integer> activeTopics){
        //System.out.println("compute q");
        int numTopics = activeTopics.size();//root.numTables;
        double[] q;
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
        int numTokens = 10;//restaurant.numCustomers;
        for(int index = 0;index<activeTopics.size();++index){
            Integer topic = activeTopics.get(index);
            int arrayIndex = index;
            if(this.root.hasTopic(topic)){
                //int index = indicesForTopics==null?topic:indicesForTopics.get(topic);
                counter++;
                double mult = calcProb.getProb(type,index);
                //new subTopic for SuperTopic
                double newTopicProb=0;
                if(this.hybrid){
                    newTopicProb=newTopicProb(this.tablesPerTopic[topic],rootB,restaurantB,numTokens,fix);
                }else{
                    int tpt = this.root.numCustomersPerTopic.get(topic);
                    newTopicProb=newTopicProb(tpt,rootB,restaurantB,numTokens,fix);
                }
                q[arrayIndex]=mult*newTopicProb;
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
        //System.out.println("put q into map");
        this.q.put(type,q);
        this.qnormList.put(type,qnorm);
    }

    

    protected void createMoreSamples(int restaurantIndex, int type, Map<Integer,Integer> indicesForTopics, CalcProb calcProb, boolean fix, int numSamples,ArrayList<Integer> activeTopics){
        //System.out.println("create more samples");
        computeQ(restaurantIndex, type, indicesForTopics, calcProb,fix,activeTopics);
        //get samples from q
        Stack<Integer> topSamples = new Stack<Integer>();
        Stack<Integer> tabSamples = new Stack<Integer>();
        double[] qh = q.get(type);
        double[][] a = AliasUtils2.generateAlias(qh);
        for(int i=0;i<numSamples;++i){
            double sample = random.nextUniform();
            double coinflip = random.nextUniform();
            int newTopicIndex = AliasUtils2.sampleAlias(a,sample,coinflip,qh.length);
            int tableIndicator = newTopicIndex==root.numTables?0:1;
            topSamples.push(newTopicIndex);
            tabSamples.push(tableIndicator);
        }
        this.topicSamples.put(type,topSamples);
        this.tableIndicatorSamples.put(type,tabSamples);

    }



    /**computes probability distribution for the topics that exist in the given restaurant
       @return the sorted list of unique topics for the given restaurant*/
    protected Integer[] computeP(int restaurantIndex, Map<Integer,Integer> indicesForTopics, CalcProb calcProb,int type,boolean fix){
        GenericRestaurant restaurant = null;
        restaurant = this.get(restaurantIndex);
        discardmass = 0;    
        pnorm = 0;
        double[] q = this.q.get(type);
        double qnorm = qnormList.get(type);
        //only do existing topics
        Set<Integer> topicsForType = restaurant.numTablesPerTopic.keySet();
        Integer[] topics = (Integer[])topicsForType.toArray(new Integer[0]);
        Arrays.sort(topics);
        p = new double[topicsForType.size()*2];
        //P_dw
        int numTokens = 10;//restaurant.numCustomers;
        double restaurantB=restaurant.b;
        if(evaluate){
            restaurantB=restaurant.evalB;
        }else{
            restaurantB=restaurant.b;
        }

        int counter = 0;
        for(int ind = 0;ind<topics.length;++ind){
            int topic = topics[ind];
            if(this.root.hasTopic(topic)){
                if(indicesForTopics!=null&&indicesForTopics.get(topic)==null)System.out.println("missing: "+topic);
                int index = indicesForTopics==null?topic:indicesForTopics.get(topic);
                if(qnorm>0){
                    discardmass+=q[index]*qnorm;
                }
                int arrayIndex = counter*2;
                counter++;
                double mult = calcProb.getProb(type,index);

                //old subTopic
                int tt=restaurant.numTablesPerTopic.get(topic);
                int customersPerTopic =restaurant.numCustomersPerTopic.get(topic);

                double oldTable = oldTableProb(topic,tt,customersPerTopic,restaurantB,numTokens);
                double newTable=0;
                if(hybrid){
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurantB,numTokens,this.tablesPerTopic[topic],fix);
                }else{
                    newTable = newTableProb(topic,tt,customersPerTopic,root.b,restaurantB,numTokens,root.numCustomersPerTopic.get(topic),fix);
                }

                p[arrayIndex]=mult*oldTable;
                p[arrayIndex+1]=mult*newTable;


                //}
                if(p[arrayIndex]<0)System.out.println("negative p!!! "+p[arrayIndex]+" "+tt+" "+customersPerTopic+" "+mult+" "+oldTable);
                pnorm += p[arrayIndex];
                pnorm += p[arrayIndex+1];
            }
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
        
    public int getPIndex(int topic){
        for(int i = 0;i<pTopics.length;++i){
            if(pTopics[i]==topic){
                return i;
            }
        }
        return -1;
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

        double restaurantB=restaurant.b;
        double rootB=root.b;
        if(evaluate){
            restaurantB=restaurant.evalB;
        }else{
            restaurantB=restaurant.b;
        }
        int numTokens = 10;//restaurant.numCustomers;
        double qnorm = -1;
        if(qnormList.get(type)==null)return true; //the first time we always accept since we don't have probabilities for the random initialization
        qnorm = qnormList.get(type);
        

        //compute pi
        double pi = 0;
        double piT = 0;
        double piS = 0;
        boolean check1=false,check2=false,check3=false;
        if(us==0||root.numTablesPerTopic.get(s)==null){//was new root topic
            check1=true;
            if(this.hybrid){
                piS=rootB*restaurantB/(rootB+this.tablesPerTopicSum);
            }else{
                piS=rootB*restaurantB/(rootB+root.numCustomers);
            }
        }else if(!restaurant.hasTopic(s)){//no other costumer left with same topic
            check2=true;
            //new topic
            if(this.hybrid){
                piS=newTopicProb(this.tablesPerTopic[s],rootB,restaurantB,numTokens,fix);
            }else{
                piS=newTopicProb(root.numCustomersPerTopic.get(s),rootB,restaurantB,numTokens,fix);
            }
        }else{//topic s is still in the document
            check3=true;
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

            if(this.hybrid){
                piT=rootB*restaurantB/(rootB+this.tablesPerTopicSum);
            }else{
                piT=rootB*restaurantB/(rootB+root.numCustomers);
            }
        }else if(!restaurant.hasTopic(t)){//the costumer wants to open a new table at document level

            //check if new or old topic
            if(root.numTablesPerTopic.get(t)==null) System.out.println("mh "+t+" "+root.numTablesPerTopic+" "+restaurant.numTablesPerTopic);
            if(this.hybrid){
                piT = newTopicProb(this.tablesPerTopic[t],rootB,restaurantB,numTokens,fix);
            }else{
                piT = newTopicProb(root.numCustomersPerTopic.get(t),rootB,restaurantB,numTokens,fix);
            }
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
               
        double[] qMH = q.get(type);


        if(us==0){
            pValueS=0;
            qValueS=qMH[qMH.length-1];
        }else{
            if(us==1){
                //differenciate between existing and new topics
                if(pindexS!=-1){
                    pValueS = p[pindexS*2+(2-us)];
                    qValueS=0;
                }else{
                    pValueS=0;
                    //}
                    if(indexS>qMH.length-1){
                        //the topic did not exist yet when q was made
                        qValueS=qMH[qMH.length-1];
                    }else{
                        qValueS=indexS!=-1?qMH[indexS]:qMH[qMH.length-1];//if the old topic was removed...
                    }
                }
            }else{
                //us==2
                qValueS=0;
                if(pindexS!=-1){
                    pValueS=p[pindexS*2];
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
                //differenciate between existing and new topics
                if(pindexT!=-1){
                    pValueT = p[pindexT*2+(2-ut)];
                    qValueT=0;
                }else{
                    pValueT=0;
                    //}
                    if(indexT>qMH.length-1){
                        //several topics were added since the last recompute
                        qValueT = qMH[qMH.length-1];
                    }else{
                        qValueT = qMH[indexT];
                    }
            }
            }else{
                //ut==2
                qValueT=0;
                if(pindexT!=-1){
                    pValueT = p[pindexT*2];
                }else{
                    pValueT = 0;
                }
            }
        }

        double nom = (pnorm*pValueS+(qnorm)*qValueS);
        double denom = (pnorm*pValueT+(qnorm)*qValueT);
        mult  = nom/(double)denom;
        pi*=mult;
        double sample = random.nextUniform();

        return sample<=pi;
    }


}
