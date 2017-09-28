package org.kramerlab.np;

import java.lang.Math;

import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.OnlineModel;
import org.kramerlab.util.DoubleMultinomialCalcProb;
import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.StirlingTablesLarge;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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



public class Hybrid implements OnlineModel,Inferencer{

    HDP hdp;

    boolean evaluate;
    boolean unsupervised;

    int numTypes;
    int numTopics;

    ArrayList<TopicAssignment> topicAssignments;

    double[][] typeTopicCounts;
    double[] tokensPerTopic;
    double[] tablesPerTopic;
    double tablesPerTopicSum;

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

    int k;

    int accepts1;
    int accepts2;
    int rejects1;
    int rejects2;

    int allowed;
    int notAllowed;

    int batchsize;


    InstanceList data;
    int numDocs;
    int numBatches;
    int updateIterations;
    int burninIterations;
    boolean useOriginalSampler;
    double kappa;

    boolean sampleHyper;
    
    public Hybrid(LabelAlphabet alphabet,double beta,int k,double b0,double b1,int interval){
        this(alphabet,beta,k,b0,b1,false,interval,0,10,4,false,2,0.6);
    }

    public Hybrid(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,int numDocs, int updateIterations,int burninIterations,boolean useOriginalSampler,int batchsize,double kappa){
        this.batchsize=batchsize;
        this.kappa=kappa;
        this.useOriginalSampler=useOriginalSampler;
        this.numBatches=0;
        this.numDocs=numDocs;
        this.tablesPerTopicSum=0;
        this.updateIterations=updateIterations;
        this.burninIterations=burninIterations;
        this.stirling = new StirlingTablesLarge(20000,15000,5000,1000,0,interval);
        this.k = k;
        this.activeTopics = new ArrayList<Integer>();
        this.indicesForTopics = new HashMap<Integer,Integer>();
        this.evaluate = false;
        this.b0 = b0;
        this.b1 = b1;
        this.numTopics = alphabet.size();
        this.topicAlphabet = alphabet;
        GenericRestaurant root = new GenericRestaurant();
        for(int topic = 0;topic<numTopics;++topic){
            this.activeTopics.add(topic);
            this.indicesForTopics.put(topic,topic);
            root.add(topic);
        }
        if(useOriginalSampler){
            this.hdp = new HDP(root, new ArrayList<GenericRestaurant>(),b0,b1,stirling,false); 
        }else{
            this.hdp = new ModAliasHDP(root, new ArrayList<GenericRestaurant>(),b0,b1,stirling,false); 
            ((AbstractAliasHDP)this.hdp).setStoreJustPerType(false);
            ((AbstractAliasHDP)this.hdp).setStoreQs(true);
        }
        this.hdp.setHybrid(true);        
        topicAssignments = new ArrayList<TopicAssignment>();
        this.beta = 0.01;
        this.random = new Randoms();
        this.unsupervised = unsupervised;
    }

    public boolean isSupervised(){
        return (!unsupervised);
    }
    
    public void setBatch(InstanceList batch){
        //this.data=batch;
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

    public int getNumTopics(){
        return this.numTopics;
    }

    public int getK(){
        return this.k;
    }

    public void setSampleHyper(boolean hyper){
        this.sampleHyper = hyper;
    }

    public void setEvaluate(boolean evaluate){
        this.evaluate = evaluate;
    }

    public void init(int numTypes){
        this.numTypes=numTypes;
        this.typeTopicCounts = new double[numTypes][numTopics];
        this.tokensPerTopic = new double[numTopics];
        this.tablesPerTopic= new double[numTopics];
        for(int i = 0;i<tablesPerTopic.length;++i){
            tablesPerTopic[i]=1.0;//initialize to nonzero value
        }
        this.hdp.setTablesPerTopic(this.tablesPerTopic);
        this.betaSum = beta*numTypes;
        //System.out.println("init"+this.tablesPerTopic[0]);
        //TODO: initialize with random values
    }

    public void addInstances(InstanceList instances){
        this.alphabet = instances.getDataAlphabet();
        this.numTypes = alphabet.size();
        init(numTypes);
        this.data = instances;
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
                    //increaseTypeTopic(typeTopicCounts[type],type,topic,topic);
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
        for(int i = 0;i<numIterations;++i){
            this.numBatches = 0;
            int doc = 0;
            InstanceList batch=null;
            while(doc<data.size()){
                if(data.size()-doc>batchsize){
                    batch = data.subList(doc,doc+batchsize);
                }else{
                    batch = data.subList(doc,data.size());
                }
                System.out.println("batch length "+batch.size());//+" doc "+doc+" of "+data.size());
                updateBatch(batch);
                doc+=batchsize;
            }
        }
    }

    public void updateBatch(InstanceList batch){
        //System.out.println(Arrays.toString(((AbstractAliasHDP)this.hdp).tablesPerTopic));
        //System.out.println(((AbstractAliasHDP)this.hdp).tablesPerTopicSum);
        this.setBatch(batch);
        this.alphabet = batch.getDataAlphabet();
        this.numBatches+=1;
        int[][] gamma = new int[this.numTypes][this.numTopics];
        int[] gammaTables = new int[this.numTopics];
        int docCounter=0;
        for(Instance instance:batch){
            docCounter++;
            if(docCounter%1000==0)System.out.println("doc: "+docCounter);
            //this.hdp.clearSamples();
            FeatureSequence wordTokens = (FeatureSequence) instance.getData();
            int docLength = wordTokens.size();
            GenericRestaurant restaurant = new GenericRestaurant();
            this.hdp.add(restaurant);
            int[] topics = new int[docLength];
            for(int position = 0;position<docLength;++position){
                topics[position]=activeTopics.get(random.nextInt(numTopics));
                restaurant.add(topics[position]);
            }

            LabelSequence topicSequence =
                new LabelSequence(topicAlphabet, topics);
            topics = topicSequence.getFeatures();


            for(int i = 0;i<this.updateIterations;++i){
                if(this.unsupervised){
                    //System.out.println(restaurant.numCustomers+" "+restaurant.numTables);
                    sampleTopicsForOneDoc(wordTokens,topicSequence,restaurant,docCounter-1);
                }else{
                    sampleTopicsForOneDocSupervised(wordTokens,topicSequence,(FeatureVector)instance.getTarget(),restaurant);
                }
                if(i>this.burninIterations){
                    for(Map.Entry<Integer,Integer> entry:restaurant.numTablesPerTopic.entrySet()){
                        int topic = entry.getKey();
                        int count = entry.getValue();
                        gammaTables[topic]+=count;
                    }
                    for(int pos=0;pos<docLength;++pos){
                        int type=wordTokens.getIndexAtPosition(pos);
                        int topic = topics[pos];
                        gamma[type][topic]+=1;
                    }
                }
            }
        }
        //System.out.println((rejects2+rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects2)/(double)(rejects1+rejects2+accepts1+accepts2));
        //System.out.println(notAllowed/(double)(notAllowed+allowed));
        allowed=0;
        notAllowed=0;
        rejects1=0;
        rejects2=0;
        accepts1=0;
        accepts2=0;
        //do global update
        int batchLength=batch.size();
        double rho=1.0/(Math.pow(this.numBatches+1,this.kappa));
        this.tokensPerTopic=new double[this.numTopics];
        for(int word=0;word<this.numTypes;++word){
            for(int t = 0;t<this.numTopics;++t){
                this.typeTopicCounts[word][t]=(1.0-rho)*this.typeTopicCounts[word][t]+rho*gamma[word][t]*this.numDocs/(double)(batchLength*(this.updateIterations-this.burninIterations));
                this.tokensPerTopic[t]+=this.typeTopicCounts[word][t];
            }
        }
        this.tablesPerTopicSum=0;
        //System.out.println(this.tablesPerTopic[0]);
        //System.out.println(gammaTables[0]);
        for(int t = 0;t<this.numTopics;++t){
            this.tablesPerTopic[t]=(1.0-rho)*this.tablesPerTopic[t]+rho*gammaTables[t]*this.numDocs/(double)(batchLength*(this.updateIterations-this.burninIterations));
            this.tablesPerTopicSum+=this.tablesPerTopic[t];
        }
        this.hdp.setTablesPerTopicSum(this.tablesPerTopicSum);
        if(this.sampleHyper){
            this.hdp.sampleb0();
            this.hdp.sampleb1();
            System.out.println("B1: "+this.hdp.getB1()+" B0: "+this.hdp.getB0());
        }

        int countCust = 0;
        int countTabs = 0;
        for(int i = this.hdp.restaurants.size()-1;i>=0;--i){
            //countCust+=this.hdp.get(i).numCustomers;
            //countTabs+=this.hdp.get(i).numTables;
            this.hdp.remove(i);
        }
        //System.out.println(countCust+" "+countTabs);
        if(this.hdp instanceof AbstractAliasHDP){
            ((AbstractAliasHDP)this.hdp).clearSamples();
        }
    }

    public void sampleTopicsForOneDocSupervised(FeatureSequence wordTokens,FeatureSequence topicSequence,FeatureVector labelVector,GenericRestaurant restaurant){
        int type,oldTopic,newTopic,newTopicIndex,index,newTableIndicator;
        double prob,sum;
        int[] topics = topicSequence.getFeatures();
        int numLabels = labelVector.numLocations();    
        double[] currentTypeTopicCounts;
        //double[] probabilities = new double[numLabels];
        int docLength = wordTokens.size();
        for(int position = 0;position<docLength;++position){
            type = wordTokens.getIndexAtPosition(position);
            oldTopic = topics[position];
            currentTypeTopicCounts = typeTopicCounts[type];
            CalcProb calcProb = new DoubleMultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);
            int tableRemoved = restaurant.remove(oldTopic,false);
            if(tableRemoved!=0){
                //the customer is allowed to move
                double[] dist = this.hdp.getProbabilityDistributionForLabels(labelVector,restaurant,calcProb,type,false,true);

                index = sampleFromArray(dist);
                newTopicIndex = index/2;
                newTableIndicator = 2-index%2;
                newTopic = labelVector.indexAtLocation(newTopicIndex);
                if(newTableIndicator==2&&!restaurant.hasTopic(newTopic)){
                    newTableIndicator = 1;
                }
                restaurant.add(newTopic,newTableIndicator);

                topics[position]=newTopic;
            }
        }
    }

    
    public void sampleTopicsForOneDoc(FeatureSequence wordTokens,FeatureSequence topicSequence,GenericRestaurant docRestaurant,int docRestaurantIndex){
        int[] topics = topicSequence.getFeatures();
        int docLength = topicSequence.size();
        double[] probabilities = null;
        if(this.useOriginalSampler){                
            probabilities = new double[numTopics*2];
        }
        int oldTopic,newTopic,arrayPosition,tableIndicator,type,oldTopicIndex,newTopicIndex,oldTableIndicator;
        double prob;
        double[] currentTypeTopicCounts;

        for(int position = 0;position<docLength;++position){
            //if(evaluate)System.out.println("here");
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

                CalcProb calcProb = new DoubleMultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);

                newTopicIndex = -1;
                newTopic = -1;
                tableIndicator = -1;
                if(this.useOriginalSampler){                
                    probabilities = hdp.getProbabilityDistribution(probabilities,activeTopics,indicesForTopics,docRestaurant,true,calcProb,type);
                    arrayPosition = sampleFromArray(probabilities);
                    tableIndicator = 2-arrayPosition%2;
                    newTopicIndex = arrayPosition/2;
                    newTopic = activeTopics.get(newTopicIndex);
    
                }else{
                    int[] samplingResult = ((AbstractAliasHDP)hdp).sampleTopicIndex(indicesForTopics,type,calcProb,true,docRestaurantIndex,this.k,activeTopics);
                    tableIndicator = samplingResult[1];
                    newTopicIndex = samplingResult[0];
                    newTopic = activeTopics.get(newTopicIndex);
                    boolean accept = ((AbstractAliasHDP)hdp).mh(oldTopic,newTopic,oldTopic,newTopic,oldTableIndicator,tableIndicator,calcProb,type,0,true);
                    
                }

                if(newTopicIndex>numTopics-1){
                    //new supertopic
                    tableIndicator = 0;
                    //TODO more changes
                }
                if(tableIndicator==2&&!docRestaurant.hasTopic(newTopic)){
                    tableIndicator = 1;
                }
                docRestaurant.add(newTopic,tableIndicator);
                topics[position] = newTopic;
            }else{
                notAllowed++;
            }
        }
    }    


    public double[] getSampledDistribution(Instance instance,int numIterations,int thinning,int burnin){
        if(!this.useOriginalSampler)((AbstractAliasHDP)this.hdp).clearSamples();
        double[] result = new double[numTopics];
        double norm = 0;
        FeatureSequence tokenSequence =
            (FeatureSequence) instance.getData();
        int docLength = tokenSequence.getLength();
        GenericRestaurant docRestaurant = new GenericRestaurant();
        int[] topics = new int[docLength];
        //initializeAliasInferencer(topics,numIterations);
        for(int position = 0;position<docLength;++position){
            topics[position]=activeTopics.get(random.nextInt(numTopics));
            docRestaurant.add(topics[position]);
        }

        LabelSequence topicSequence =
            new LabelSequence(topicAlphabet, topics);
        topics = topicSequence.getFeatures();
        this.hdp.add(docRestaurant);

        for(int i = 0;i<numIterations;++i){            
            //System.out.println("getSampled"+i);
            this.sampleTopicsForOneDoc(tokenSequence,topicSequence,docRestaurant,this.hdp.restaurants.size()-1);
            //System.out.println(Arrays.toString(topics));
            if(i%thinning==0&&i>burnin){
                //System.out.println(Arrays.toString(topics));
                for(int pos=0;pos<docLength;++pos){
                    int topic = topics[pos];
                    result[topic]+=1.0;
                    norm+=1.0;    
                }
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
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            for(int subTopicIndex = 0;subTopicIndex<numTopics;++subTopicIndex){
                double num = tokensPerTopic[subTopicIndex];
                bw.write(topicAlphabet.lookupObject(subTopicIndex)+"("+num+")"+": ");
                IDSorter[] topWords = topWords(subTopicIndex);
                for(int wordIndex = 0;wordIndex<numWords;++wordIndex){
                    int word = topWords[wordIndex].getID();
                    if(topWords[wordIndex].getWeight()>0){
                        bw.write((String)this.alphabet.lookupObject(word)+" ");
                    }
                }
                bw.write("\n");
            }
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
        this.evaluate=true;
        rejects1=0;
        rejects2=0;
        accepts1=0;
        accepts2=0;
        double likelihood = 0;
        int numTokens = 0;
        int count = 0;
        for(Instance instance: data){
            count+=1;

            FeatureSequence tokenSequence =
                (FeatureSequence) instance.getData();
            int docLength = tokenSequence.getLength();
            //System.out.println(count+" "+docLength);
            numTokens+=docLength;

            double[] distribution = this.getSampledDistribution(instance,10,1,4);
            
            double docLikelihood = getDocLikelihood(distribution,tokenSequence);
            //System.out.println(docLikelihood);
            likelihood+=docLikelihood;
        }
        this.evaluate=false;
        //System.out.println("Evaluation: "+(rejects2+rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects1)/(double)(rejects1+rejects2+accepts1+accepts2)+" "+(rejects2)/(double)(rejects1+rejects2+accepts1+accepts2));
        rejects1=0;
        rejects2=0;
        accepts1=0;
        accepts2=0;
        return likelihood/numTokens;
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
        return loglikelihood/tokenSum;
    }

}
