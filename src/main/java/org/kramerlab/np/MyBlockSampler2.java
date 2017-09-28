package org.kramerlab.np;

import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.TopicModel;
import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.util.MultinomialCalcProb;
import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.StirlingTablesLarge;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import cc.mallet.util.Randoms;
import cc.mallet.types.Instance;
import cc.mallet.types.Alphabet;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.IDSorter;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.topics.TopicAssignment;



public class MyBlockSampler2 implements TopicModel,Inferencer{

    HDP hdp;

    boolean evaluate;
    boolean unsupervised;
    boolean updateConcentration;
    
    int numTypes;
    int numTopics;

    ArrayList<TopicAssignment> topicAssignments;

    int[][] typeTopicCounts;
    int[] tokensPerTopic;

    double beta;
    double betaSum;

    StirlingTables stirling;
    Alphabet alphabet;
    LabelAlphabet topicAlphabet;
    Randoms random;

    ArrayList<Integer> activeTopics;
    HashMap<Integer,Integer> indicesForTopics;

    double b0;
    double b1;
    double evalb1;
    
    int k;

    int accepts1;
    int accepts2;
    int rejects1;
    int rejects2;

    int allowed;
    int notAllowed;

    int numMHIterations;

    public MyBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,int interval,boolean useOriginal){
        this(alphabet,beta,k,b0,b1,false,interval,useOriginal);
    }

    public MyBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,boolean useOriginal){
        this(alphabet,beta,k,b0,b1,unsupervised,interval,useOriginal,10000,5000,5000,1000,b1,0.0,1);
    }
    
    public MyBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,boolean useOriginal,int maxN,int maxM,int startN,int startM,double evalb1,double a,int mhIterations){
        this.evalb1 = evalb1;
        System.out.println("original "+useOriginal);
        this.numMHIterations = mhIterations;
        this.stirling = new StirlingTablesLarge(maxN,maxM,startN,startM,a,interval);
        this.k = k;
        this.activeTopics = new ArrayList<Integer>();
        this.indicesForTopics = new HashMap<Integer,Integer>();
        this.evaluate = false;
        this.b0 = b0;
        this.b1 = b1;
        this.numTopics = alphabet.size();
        this.topicAlphabet = alphabet;
        for(int topic = 0;topic<numTopics;++topic){
            this.activeTopics.add(topic);
            this.indicesForTopics.put(topic,topic);
        }
        if(useOriginal){
            this.hdp = new AliasHDP(new GenericRestaurant(), new ArrayList<GenericRestaurant>(),b0,b1,stirling,false); 
        }else{
            if(!unsupervised){
                this.hdp = new ModAliasHDP2(new GenericRestaurant(), new ArrayList<GenericRestaurant>(),b0,b1,stirling,false,a);
            }else{
                this.hdp = new ModAliasHDP(new GenericRestaurant(), new ArrayList<GenericRestaurant>(),b0,b1,stirling,false);
            }
        }
        ((AbstractAliasHDP)this.hdp).setStoreJustPerType(false);
        ((AbstractAliasHDP)this.hdp).setStoreQs(true);
        ((AbstractAliasHDP)this.hdp).setUseEvalB1(true);
        
        topicAssignments = new ArrayList<TopicAssignment>();
        this.beta = 0.01;
        this.random = new Randoms();
        this.unsupervised = unsupervised;
    }

    public double getB0(){
        return this.b0;
    }

    public double getB1(){
        return this.b1;
    }

    public double getBeta(){
        return this.beta;
    }

    public double getEvalB1(){
        return this.evalb1;
    }

    public int getNumTopics(){
        return this.numTopics;
    }

    public int getK(){
        return this.k;
    }

    public HDP getHDP(){
        return this.hdp;
    }

    public void setEvalB1(double evalb1){
        this.evalb1 = evalb1;
    }
    
    public void setUpdateConcentration(boolean updateConcentration){
        this.updateConcentration = updateConcentration;
        if(updateConcentration){
            ((HDP)this.hdp).setUseEvalB1(false);
        }
    }

    public void setEvaluate(boolean evaluate){
        this.evaluate = evaluate;
    }

    public void init(int numTypes){
        this.typeTopicCounts = new int[numTypes][numTopics];
        this.tokensPerTopic = new int[numTopics];
        this.betaSum = beta*numTypes;
    }

    public void addInstances(InstanceList instances){
        this.alphabet = instances.getDataAlphabet();
        this.numTypes = alphabet.size();
        init(numTypes);
        //int c = 0;
        for(Instance instance:instances){
            //if(c%1000==0&&c>1){
            //  System.out.println("instances added: "+c);
            //}
            //c++;
            GenericRestaurant docRest = new GenericRestaurant();
            FeatureSequence tokens = (FeatureSequence) instance.getData();
            int docLength = tokens.size();
            FeatureVector labelVector = (FeatureVector)instance.getTarget();
            int local_numLabels = unsupervised?numTopics:labelVector.numLocations();
            if(local_numLabels>0){
                LabelSequence topicSequence =
                    new LabelSequence(topicAlphabet, new int[docLength]);
                int[] topics = topicSequence.getFeatures();
                for (int position = 0; position < docLength; position++) {
                    int type = tokens.getIndexAtPosition(position);
                    //the topic can only come from the labels
                    int chosenrank = random.nextInt(local_numLabels);
                    int topic = unsupervised?chosenrank:labelVector.indexAtLocation(chosenrank);
                    increaseTypeTopic(typeTopicCounts[type],type,topic,topic);
                    //typeTopicCounts[type][topic]++;
                    //tokensPerTopic[topic]++;
                    topics[position] = topic;
                    docRest.add(topic);
                    if(docRest.numCustomersPerTopic.get(topic)==1){
                        //System.out.println("hier");
                        hdp.getRoot().add(topic);
                    }
                }
                this.hdp.add(docRest);
                TopicAssignment ta = new TopicAssignment(instance,topicSequence);
                this.topicAssignments.add(ta);
            }
        }
    }



    public void sample(int numIterations){
        for(int iter = 0;iter<numIterations;++iter){
            //((ModAliasHDP)this.hdp).printRatio();
            for(int doc = 0;doc<topicAssignments.size();++doc){
                if(doc%1000==0)System.out.println("doc: "+doc);
                //this.hdp.clearSamples();
                TopicAssignment topicAssignment = topicAssignments.get(doc);
                FeatureSequence wordTokens = (FeatureSequence) topicAssignment.instance.getData();
                FeatureVector labels = (FeatureVector)topicAssignment.instance.getTarget();
                FeatureSequence topics = topicAssignment.topicSequence;
                sampleTopicsForOneDoc(wordTokens,topics,labels,hdp.get(doc),doc);
            }
            System.out.println((rejects2+rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects2)/(double)(rejects1+rejects2+accepts1+accepts2));
            System.out.println(notAllowed/(double)(notAllowed+allowed));
            allowed=0;
            notAllowed=0;
            rejects1=0;
            rejects2=0;
            accepts1=0;
            accepts2=0;
            //System.out.println("update "+this.updateConcentration+" "+iter+" "+(iter+1));
            //if(iter>=10&&(iter+1)%10==0&&this.updateConcentration){
            if(this.updateConcentration){
                this.hdp.sampleb0();
                this.hdp.sampleb1();
                System.out.println("sampled b "+this.hdp.b1);
            }
        }
    }

    public void sampleTopicsForOneDoc(FeatureSequence wordTokens,FeatureSequence topicSequence,FeatureVector labels,GenericRestaurant docRestaurant,int docRestaurantIndex){
    
        //if(unsupervised) evaluate = true;
        //        System.out.println("sample "+docRestaurantIndex);
        //int numLabels = labels.numLocations();
        int[] topics = topicSequence.getFeatures();
        int docLength = topicSequence.size();
        double[] probabilities = null;
        probabilities = new double[numTopics*2];
        int oldTopic,newTopic,arrayPosition,tableIndicator,type,oldTopicIndex,newTopicIndex,oldTableIndicator;
        double prob;
        int[] currentTypeTopicCounts;

        for(int position = 0;position<docLength;++position){
            type = wordTokens.getIndexAtPosition(position);
            //((AbstractAliasHDP)this.hdp).clearSamples();
            currentTypeTopicCounts = typeTopicCounts[type];
            oldTopic = topics[position];
            oldTopicIndex = indicesForTopics.get(oldTopic);
            oldTableIndicator = 2;
            int tableRemoved = docRestaurant.remove(oldTopic,false);
            if(tableRemoved!=0){
                //the customer is allowed to move
                allowed++;
                if(tableRemoved==1) oldTableIndicator = 1;
                if(!hdp.getRoot().hasTopic(oldTopic)){
                    oldTableIndicator = 0;
                }
                if(!evaluate){
                    decreaseTypeTopic(currentTypeTopicCounts,type,oldTopic,oldTopicIndex);
                    //currentTypeTopicCounts[oldTopicIndex]--;
                    //tokensPerTopic[oldTopicIndex]--;
                    if(tableRemoved==1){
                        hdp.getRoot().remove(oldTopic,true);
                        oldTableIndicator = 1;
                    }
                    if(!hdp.getRoot().hasTopic(oldTopic)){
                        oldTableIndicator = 0;
                        /*
                        //if the topic was completely removed
                        System.out.println("topic "+oldTopic+ " was removed");
                        this.numTopics--;
                        ((AbstractAliasHDP)this.hdp).clearSamples();
                        this.activeTopics.remove(oldTopicIndex);
                        //decrease indices of higher topics
                        for(int ind = oldTopicIndex;ind<activeTopics.size();++ind){
                            int top = activeTopics.get(ind);
                            int oldIndex = indicesForTopics.get(top);
                            indicesForTopics.put(top,oldIndex-1);
                        }
                        int[] newTokensPerTopic = new int[tokensPerTopic.length-1];
                        int[][] newTypeTopicCounts = new int[typeTopicCounts.length][typeTopicCounts[0].length-1];
                        int c = 0;
                        for(int top = 0;top<tokensPerTopic.length;++top){
                            if(top<oldTopicIndex){
                                newTokensPerTopic[c] = tokensPerTopic[top];
                                for(int typ = 0;typ<typeTopicCounts.length;++typ){
                                    newTypeTopicCounts[typ][c] = typeTopicCounts[typ][top];
                                }
                                c++;
                            }else if(top>oldTopicIndex){
                                newTokensPerTopic[c] = tokensPerTopic[top];
                                for(int typ = 0;typ<typeTopicCounts.length;++typ){
                                    newTypeTopicCounts[typ][c] = typeTopicCounts[typ][top];                                    
                                }
                                c++;                          
                            }
                        }
                        this.tokensPerTopic = newTokensPerTopic;
                        this.typeTopicCounts = newTypeTopicCounts;
                        //System.out.println("indicesForTopics: "+indicesForTopics+" "+activeTopics);
                        currentTypeTopicCounts = typeTopicCounts[type];
                        oldTopicIndex=-1;
                        */
                    }
                }
                CalcProb calcProb = new MultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);
                if(!evaluate&&!unsupervised){
                    probabilities = hdp.getProbabilityDistributionForLabels(labels,docRestaurant,calcProb,type,evaluate,true);
                    arrayPosition = sampleFromArray(probabilities);
                    int rank = arrayPosition/2;
                    newTopic = labels.indexAtLocation(rank);
                    newTopicIndex = newTopic;
                    tableIndicator = 2-arrayPosition%2;
                }else{
                    newTopicIndex = -1;
                    newTopic = -1;
                    tableIndicator = -1;
                    for(int mhIter = 0;mhIter<numMHIterations;++mhIter){
                        int[] samplingResult = ((AbstractAliasHDP)hdp).sampleTopicIndex(indicesForTopics,type,calcProb,true,docRestaurantIndex,this.k,activeTopics);
                        tableIndicator = samplingResult[1];
                        newTopicIndex = samplingResult[0];
                        newTopic = activeTopics.get(newTopicIndex);
                        //oldTableIndicator = docRestaurant.sampleOldTableConfiguration(oldTopic);
                        //if(!evaluate){
                        boolean accept = ((AbstractAliasHDP)hdp).mh(oldTopic,newTopic,oldTopicIndex,newTopicIndex,oldTableIndicator,tableIndicator,calcProb,type,docRestaurantIndex,true);
                        if(!accept){
                            if(tableIndicator==1){
                                rejects1++;
                            }else if(tableIndicator==2){
                                rejects2++;
                            }
                            newTopicIndex = oldTopicIndex;
                            tableIndicator = oldTableIndicator;
                            newTopic = oldTopic;
                        }else{
                            if(tableIndicator==1){
                                accepts1++;
                            }else if(tableIndicator==2){
                                accepts2++;
                            }
                            //prepare for next round
                            oldTopic=newTopic;
                            oldTopicIndex=newTopicIndex;
                            oldTableIndicator = tableIndicator;
                        }
                    }
                        //}
                }
                if(newTopicIndex>numTopics-1||!this.hdp.getRoot().hasTopic(newTopic)){
                    //new supertopic
                    tableIndicator = 0;
                    //TODO more changes
                }
                if(tableIndicator==2&&!docRestaurant.hasTopic(newTopic)){
                    tableIndicator = 1;
                }
                docRestaurant.add(newTopic,tableIndicator);
                if(tableIndicator<2&&!evaluate){
                    hdp.getRoot().add(newTopic);
                }
                topics[position] = newTopic;
                if(!evaluate){
                    increaseTypeTopic(currentTypeTopicCounts,type,newTopic,newTopicIndex);
                    //currentTypeTopicCounts[newTopicIndex]++;
                    //tokensPerTopic[newTopicIndex]++;
                }
            }else{
                notAllowed++;
            }
        }
    }    


    public void decreaseTypeTopic(int[] currentTypeTopicCounts,int type,int topic,int topicIndex){
        currentTypeTopicCounts[topicIndex]--;
        tokensPerTopic[topicIndex]--;
    }

    public void increaseTypeTopic(int[] currentTypeTopicCounts,int type,int topic,int topicIndex){
        currentTypeTopicCounts[topicIndex]++;
        tokensPerTopic[topicIndex]++;
    }


    public double[] getSampledDistribution(Instance instance,int numIterations,int thinning,int burnin){
        //if(this.hdp instanceof AbstractAliasHDP)((AbstractAliasHDP)this.hdp).clearSamples();
        double[] result = new double[numTopics];
        double norm = 0;
        FeatureSequence tokenSequence =
            (FeatureSequence) instance.getData();
        int docLength = tokenSequence.getLength();
        GenericRestaurant docRestaurant = new GenericRestaurant();
        docRestaurant.setEvalB(this.evalb1);
        int[] topics = new int[docLength];
        //initializeAliasInferencer(topics,numIterations);
        for(int position = 0;position<docLength;++position){
            int randomTopic = activeTopics.get(random.nextInt(numTopics));
            while(!this.hdp.root.hasTopic(randomTopic)){
                randomTopic = activeTopics.get(random.nextInt(numTopics));
            }
            topics[position]=randomTopic;
            docRestaurant.add(topics[position]);
        }
        /*int[] allLabels = new int[numTopics];
          for(int i = 0;i<allLabels.length;++i){
          allLabels[i]=i;
          }
          FeatureVector labels = new FeatureVector(topicAlphabet,allLabels);*/
        LabelSequence topicSequence =
            new LabelSequence(topicAlphabet, topics);
        topics = topicSequence.getFeatures();

        this.hdp.add(docRestaurant);

        for(int i = 0;i<numIterations;++i){            
            this.sampleTopicsForOneDoc(tokenSequence,topicSequence,null,docRestaurant,this.hdp.restaurants.size()-1);
            //System.out.println(Arrays.toString(topics));
            if(i%thinning==0&&i>burnin){
                //System.out.println(i+" "+Arrays.toString(topics)+" "+this.hdp.restaurants.size());
                for(int pos=0;pos<docLength;++pos){
                    int top = topics[pos];
                    if(top>result.length-1){
                        System.out.println("HERE "+result.length+" "+this.numTopics+" "+top);
                    }
                    result[top]+=1;
                    norm+=1;
                }
                /*
                  for(int pos=0;pos<docLength;++pos){
                  int type = tokenSequence.getIndexAtPosition(pos);

                  for(int top = 0;top<numTopics;++top){

                  double val = (typeTopicCounts[type][top]+beta)/(tokensPerTopic[top]+betaSum)*hdp.getTopicProbabilityMass(docRestaurant,top);
                  result[top]+=val;
                  norm+=val;
                  }*/
                //            }

                /*int[] localTokensPerDTopic = new int[numSuperTopics];
                  for(int position=0;position<docLength;++position){
                  localTokensPerDTopic[dtopics[position]]++;
                  }*/
                /*double totalMass = 0;
                  for(int topic =  0;topic<numTopics;++topic){
                  totalMass += hdp.getTopicProbabilityMass(docRestaurant,topic);
                  }

                  //analogously to MyDependencyLDA
                  for(int label = 0;label<numTopics;++label){
                  //for(int topicIndex = 0;topicIndex<numSubTopics;++topicIndex){
                  //  int topic = activeSubTopics.get(topicIndex);
                  double labelval=0;
                  for(int position = 0;position<docLength;++position){
                  int type = tokenSequence.getIndexAtPosition(position);


                  double mass = hdp.getTopicProbabilityMass(docRestaurant,label);
                  double val = (typeTopicCounts[type][label]+beta)/(tokensPerTopic[label]+betaSum)*mass;
                  labelval+=val;
                  }
                  if(docLength>0){
                  labelval/=docLength;
                  }else{
                  labelval = 0.01;
                  }
                  result[label]+=labelval;
                  norm+=labelval;
                  //}
                  }
                */
            }
        }
        //normalize
        for(int label = 0;label<numTopics;++label){
            result[label]/=norm;
        }
        this.hdp.remove(this.hdp.restaurants.size()-1); //remove the document after evaluation
        //System.out.println(Arrays.toString(result));
        return result;
    }


    public IDSorter[] topWords(int topic){
        IDSorter[] topWords = new IDSorter[numTypes];
        for(int word = 0;word<numTypes;++word){
            topWords[word] = new IDSorter(word,typeTopicCounts[word][topic]);
        }
        Arrays.sort(topWords);
        return topWords;
    }

    public void printTopWords(String filename,int numWords,int threshold){
   try{
       //get real label frequencies
       int[] realFreqs = new int[numTopics];
       
       int sumRealFreqs = 1;
       if(!unsupervised){
           sumRealFreqs = 0;
           for(int i = 0;i<this.topicAssignments.size();++i){
               TopicAssignment ta = this.topicAssignments.get(i);
               FeatureVector labels = (FeatureVector)ta.instance.getTarget();
               int numL = labels.numLocations();
               for(int j = 0;j<numL;++j){
                   int l = labels.indexAtLocation(j);
                   realFreqs[l]++;
                   sumRealFreqs++;
               }
           }
       }
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            int allsum = 0;
            int tabsum = 0;
            for(int subTopicIndex = 0;subTopicIndex<numTopics;++subTopicIndex){
                allsum+=tokensPerTopic[subTopicIndex];
                tabsum+=this.hdp.getRoot().numCustomersPerTopic.get(subTopicIndex)==null?0:this.hdp.getRoot().numCustomersPerTopic.get(subTopicIndex);
            }
            for(int subTopicIndex = 0;subTopicIndex<numTopics;++subTopicIndex){
                int num2 = this.hdp.getRoot().numCustomersPerTopic.get(subTopicIndex)==null?0:this.hdp.getRoot().numCustomersPerTopic.get(subTopicIndex);
                bw.write((realFreqs[subTopicIndex]/(double)sumRealFreqs)+" "+(num2/(double)tabsum)+"\n");
            }
            /*
            for(int subTopicIndex = 0;subTopicIndex<numTopics;++subTopicIndex){
                int num = tokensPerTopic[subTopicIndex];
                int num2 = this.hdp.getRoot().numCustomersPerTopic.get(subTopicIndex);
                bw.write(topicAlphabet.lookupObject(subTopicIndex)+"("+(num/(double)allsum)+" "+(num2/(double)tabsum)+")"+": ");
                IDSorter[] topWords = topWords(subTopicIndex);
                for(int wordIndex = 0;wordIndex<numWords;++wordIndex){
                    int word = topWords[wordIndex].getID();
                    if(topWords[wordIndex].getWeight()>0){
                        bw.write((String)this.alphabet.lookupObject(word)+" ");
                    }
                }
                bw.write("\n");
                }*/
            bw.flush();
            bw.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public Inferencer getInferencer(){
        return this;
    }

    public int sampleFromArray(double[] probs){
        double randomValue = random.nextUniform();
        int index = -1;
        while(randomValue>0){
            index++;
            randomValue-=probs[index];
        }
        return index;
    }

    public double getTestingLoglikelihood(InstanceList data){
        //this.unsupervised=false;
        //System.out.println("evaluate: "+evaluate);
        this.evaluate=true;
        rejects1=0;
        rejects2=0;
        accepts1=0;
        accepts2=0;
    
        //if(this.hdp instanceof AbstractAliasHDP){
        //((AbstractAliasHDP)this.hdp).clearSamples();
        //}
        double likelihood = 0;
        int numTokens = 0;
        for(Instance instance: data){
            FeatureSequence tokenSequence =
                (FeatureSequence) instance.getData();
            int docLength = tokenSequence.getLength();
            numTokens+=docLength;
            /*GenericRestaurant docRestaurant = new GenericRestaurant();
            int[] topics = new int[docLength];
            //initializeAliasInferencer(topics,numIterations);
            for(int position = 0;position<docLength;++position){
                topics[position]=activeTopics.get(random.nextInt(numTopics));
                docRestaurant.add(topics[position]);
            }
            int[] allLabels = new int[numTopics];
            for(int i = 0;i<allLabels.length;++i){
                allLabels[i]=i;
            }
            FeatureVector labels = new FeatureVector(topicAlphabet,allLabels);
            LabelSequence topicSequence =
                new LabelSequence(topicAlphabet, topics);
            topics = topicSequence.getFeatures();

            this.hdp.add(docRestaurant);
            for(int i = 0;i<10;++i){
                //sample a few times
                this.sampleTopicsForOneDoc(tokenSequence,topicSequence,labels,docRestaurant,this.hdp.restaurants.size()-1);
                }*/
            double[] distribution = this.getSampledDistribution(instance,10,1,4);
            double docLikelihood = getDocLikelihood(distribution,tokenSequence);
            likelihood+=docLikelihood;
            //this.hdp.remove(this.hdp.restaurants.size()-1);
            //System.out.println(this.hdp.restaurants.size()+" "+ ((AbstractAliasHDP)this.hdp).topicSamples.size());
        }
        //this.unsupervised=true;
        this.evaluate=false;
        //if(this.hdp instanceof AbstractAliasHDP){
        //  ((AbstractAliasHDP)this.hdp).clearSamples();
        //}
        //System.out.println("Evaluation: "+(rejects2+rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects2)/(double)(rejects1+rejects2+accepts1+accepts2));
        rejects1=0;
        rejects2=0;
        accepts1=0;
        accepts2=0;
        return likelihood/numTokens;
    }

    public double getDocLikelihood(FeatureSequence topics,FeatureSequence wordTokens){
        double docLikelihood = 0;
        double[] docProbs = new double[tokensPerTopic.length];
        for(int pos = 0;pos<wordTokens.size();++pos){
            int topic = indicesForTopics.get(topics.getIndexAtPosition(pos));
            docProbs[topic]+=1;
        }
        for(int top = 0;top<docProbs.length;++top){
            docProbs[top]+=0.01;
            docProbs[top]/=((tokensPerTopic.length*0.01)+wordTokens.size());
        }
        for(int pos = 0;pos<wordTokens.size();++pos){
            int type = wordTokens.getIndexAtPosition(pos);
            double typePerp = 0;
            for(int top = 0;top<docProbs.length;++top){
                double val = docProbs[top]*(beta+typeTopicCounts[type][top])/(double)(tokensPerTopic[top]+betaSum);
                typePerp+=val;
            }
            docLikelihood+=Math.log(typePerp);
        }
        return docLikelihood;
    }

    public double getDocLikelihood(double[] distribution,FeatureSequence wordTokens){
        double docLikelihood = 0;
        for(int pos = 0;pos<wordTokens.size();++pos){
            int type = wordTokens.getIndexAtPosition(pos);
            double typePerp = 0;
            for(int top = 0;top<distribution.length;++top){
                double val = distribution[top]*(beta+typeTopicCounts[type][top])/(double)(tokensPerTopic[top]+betaSum);
                typePerp+=val;
            }
            docLikelihood+=Math.log(typePerp);
        }
        return docLikelihood;
    }


    public double getTrainingLoglikelihood(){
        double loglikelihood = 0;
        double tokenSum = 0;
        for(TopicAssignment ta:this.topicAssignments){
            double docPerp = 0;
            FeatureSequence wordTokens = (FeatureSequence) ta.instance.getData();
            tokenSum+=wordTokens.size();
            FeatureSequence topics = ta.topicSequence;
            double[] docProbs = new double[tokensPerTopic.length];
            for(int pos = 0;pos<wordTokens.size();++pos){
                int topic = indicesForTopics.get(topics.getIndexAtPosition(pos));
                docProbs[topic]+=1;
            }
            for(int top = 0;top<docProbs.length;++top){
                docProbs[top]+=0.01;
                docProbs[top]/=((tokensPerTopic.length*0.01)+wordTokens.size());
            }
            /*int[] typeCounts = new int[numTypes];
            for(int pos = 0;pos<wordTokens.size();++pos){
                int type = wordTokens.getIndexAtPosition(pos);
                //int topic = topics.getIndexAtPosition(pos);
                typeCounts[type]+=1;
                }*/
            for(int pos = 0;pos<wordTokens.size();++pos){
                int type = wordTokens.getIndexAtPosition(pos);
                double typePerp = 0;
                for(int top = 0;top<docProbs.length;++top){
                    double val = docProbs[top]*(beta+typeTopicCounts[type][top])/(double)(tokensPerTopic[top]+betaSum);
                    typePerp+=val;
                }
                docPerp+=Math.log(typePerp);
            }
            loglikelihood+=docPerp;
        }
        //perplexity/=tokenSum;
        return loglikelihood/tokenSum;
    }

}
