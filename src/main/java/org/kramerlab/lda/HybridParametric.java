package org.kramerlab.lda;

import java.lang.Math;

import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.OnlineModel;
import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.util.DoubleMultinomialCalcProb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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



public class HybridParametric implements OnlineModel,Inferencer{

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

    Alphabet alphabet;
    LabelAlphabet topicAlphabet;
    Randoms random;

    double alpha;

    InstanceList data;
    int numDocs;
    int numBatches;
    int updateIterations;
    int burninIterations;
    boolean useOriginalSampler;
    double kappa;
    int batchsize;

    GenericAlias alias;
    int k;
    HashSet<Integer> sparseTopics;
    
    public HybridParametric(LabelAlphabet alphabet,double beta,double alpha){
        this(alphabet,beta,alpha,false,0,10,4,false,1,0.6);
    }

    public HybridParametric(LabelAlphabet alphabet,double beta,double alpha,boolean unsupervised,int numDocs, int updateIterations,int burninIterations,boolean useOriginalSampler,int batchSize,double kappa){
        this.batchsize = batchSize;
        this.sparseTopics = new HashSet<Integer>();
        this.kappa=kappa;
        this.useOriginalSampler=useOriginalSampler;
        this.numBatches=0;
        this.numDocs=numDocs;
        this.updateIterations=updateIterations;
        this.burninIterations=burninIterations;
        this.evaluate = false;
        this.alpha = alpha;
        this.numTopics = alphabet.size();
        this.topicAlphabet = alphabet;
        topicAssignments = new ArrayList<TopicAssignment>();
        this.beta = 0.01;
        this.random = new Randoms();
        this.unsupervised = unsupervised;
        this.k = 10;
    }

    public void setBatch(InstanceList batch){
        //this.data=batch;
    }

    public double getAlpha(){
        return this.alpha;
    }

    public double getKappa(){
        return this.kappa;
    }
    
    public double getBeta(){
        return this.beta;
    }

    public int getNumTopics(){
        return this.numTopics;
    }

    public void setEvaluate(boolean evaluate){
        this.evaluate = evaluate;
    }

    public void init(int numTypes){
        this.numTypes=numTypes;
        this.typeTopicCounts = new double[numTypes][numTopics];
        this.tokensPerTopic = new double[numTopics];
        this.betaSum = beta*numTypes;
        if(!useOriginalSampler){
            this.alias = new GenericAlias(numTypes,numTopics,this.alpha);
        }
    }

    public void addInstances(InstanceList instances){
        this.alphabet = instances.getDataAlphabet();
        this.numTypes = alphabet.size();
        init(numTypes);
        this.data=instances;
        for(Instance instance:instances){

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
                    topics[position] = topic;

                }

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
        if(!this.useOriginalSampler){
            this.alias.clearSamples();
        }
        this.setBatch(batch);
        this.alphabet = batch.getDataAlphabet();
        this.numBatches+=1;
        int[][] gamma = new int[this.numTypes][this.numTopics];
        int docCounter=0;
        for(Instance instance:batch){
            docCounter++;
            if(docCounter%1000==0)System.out.println("doc: "+docCounter);

            FeatureSequence wordTokens = (FeatureSequence) instance.getData();
            int docLength = wordTokens.size();
            int[] topics = new int[docLength];
            for(int position = 0;position<docLength;++position){
                topics[position]=random.nextInt(numTopics);

            }

            LabelSequence topicSequence =
                new LabelSequence(topicAlphabet, topics);
            topics = topicSequence.getFeatures();


            for(int i = 0;i<this.updateIterations;++i){
                if(this.unsupervised){
                    sampleTopicsForOneDoc(wordTokens,topicSequence);
                }else{
                    sampleTopicsForOneDocSupervised(wordTokens,topicSequence,(FeatureVector)instance.getTarget());
                }
                if(i>this.burninIterations){
                    for(int pos=0;pos<docLength;++pos){
                        int type=wordTokens.getIndexAtPosition(pos);
                        int topic = topics[pos];
                        gamma[type][topic]+=1;
                    }
                }
            }
        }
        //do global update
        int batchLength=batch.size();
        double rho=1.0/(Math.pow(this.numBatches+1,this.kappa));
        this.tokensPerTopic=new double[this.numTopics];
        for(int word=0;word<this.numTypes;++word){
            for(int t = 0;t<this.numTopics;++t){
                //if(gamma[word][t]>0)System.out.println(rho+" "+gamma[word][t]);
                this.typeTopicCounts[word][t]=(1.0-rho)*this.typeTopicCounts[word][t]+rho*gamma[word][t]*this.numDocs/(double)(batchLength*(this.updateIterations-this.burninIterations));
                this.tokensPerTopic[t]+=this.typeTopicCounts[word][t];
            }
        }
    }

    public void sampleTopicsForOneDocSupervised(FeatureSequence wordTokens,FeatureSequence topicSequence,FeatureVector labelVector){
        int type,oldTopic,newTopic,newTopicIndex;
        double prob,sum;
        int[] topics = topicSequence.getFeatures();
        int numLabels = labelVector.numLocations();    
        double[] currentTypeTopicCounts;
        double[] probabilities = new double[numLabels];
        int docLength = wordTokens.size();
        for(int position = 0;position<docLength;++position){
            type = wordTokens.getIndexAtPosition(position);
            currentTypeTopicCounts = typeTopicCounts[type];
            CalcProb calcProb = new DoubleMultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);
            sum=0;
            for(int labelIndex = 0;labelIndex<numLabels;++labelIndex){
                int label = labelVector.indexAtLocation(labelIndex);
                probabilities[labelIndex]=calcProb.getProb(type,label);
                sum+=probabilities[labelIndex];
            }
            normalize(probabilities,sum);
            newTopicIndex = sampleFromArray(probabilities);
            newTopic = labelVector.indexAtLocation(newTopicIndex);
            topics[position]=newTopic;
        }
    }

    public void sampleTopicsForOneDoc(FeatureSequence wordTokens,FeatureSequence topicSequence){
        int[] topics = topicSequence.getFeatures();
        int docLength = topicSequence.size();
        double[] probabilities = null;
        probabilities = new double[numTopics];
        int[] localTokensPerTopic = new int[numTopics];
        if(!useOriginalSampler){
            sparseTopics.clear();
        }
        for(int pos = 0;pos<docLength;++pos){
            localTokensPerTopic[topics[pos]]++;
            sparseTopics.add(topics[pos]);
        }
        int type,oldTopic,newTopic;
        double prob,sum;
    
        double[] currentTypeTopicCounts;

        for(int position = 0;position<docLength;++position){
            //if(evaluate)System.out.println("here");
            type = wordTokens.getIndexAtPosition(position);
            currentTypeTopicCounts = typeTopicCounts[type];
            oldTopic = topics[position];
            CalcProb calcProb = new DoubleMultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);
            newTopic = -1;
            if(this.useOriginalSampler){
                sum=0;
                for(int top = 0;top<this.numTopics;++top){
                    probabilities[top]=calcProb.getProb(type,top)*(localTokensPerTopic[top]+this.alpha);
                    sum+=probabilities[top];
                }
                normalize(probabilities,sum);

                newTopic = sampleFromArray(probabilities);
            }else{
                newTopic = this.alias.sampleTopic(type,new ArrayList(sparseTopics),localTokensPerTopic,calcProb,this.k);
            }

            topics[position] = newTopic;
        }
    }    


    public double[] getSampledDistribution(Instance instance,int numIterations,int thinning,int burnin){
        if(!this.useOriginalSampler){
            this.alias.clearSamples();
        }

        double[] result = new double[numTopics];
        double norm = 0;
        FeatureSequence tokenSequence =
            (FeatureSequence) instance.getData();
        int docLength = tokenSequence.getLength();
        int[] topics = new int[docLength];
        //initializeAliasInferencer(topics,numIterations);
        for(int position = 0;position<docLength;++position){
            topics[position]=random.nextInt(numTopics);

        }

        LabelSequence topicSequence =
            new LabelSequence(topicAlphabet, topics);
        topics = topicSequence.getFeatures();

        for(int i = 0;i<numIterations;++i){            
            //System.out.println("getSampled"+i);
            this.sampleTopicsForOneDoc(tokenSequence,topicSequence);
            //System.out.println(Arrays.toString(topics));
            if(i%thinning==0&&i>burnin){
                //System.out.println(Arrays.toString(topics));
                int[] localTokensPerTopic = new int[numTopics];
                for(int pos=0;pos<docLength;++pos){
                    int topic = topics[pos];
                    localTokensPerTopic[topic]++;
                }
                double[] currentTypeTopicCounts = null;
                int type = -1;
                for(int pos=0;pos<docLength;++pos){
                    type = tokenSequence.getIndexAtPosition(pos);
                    currentTypeTopicCounts = typeTopicCounts[type];
                    CalcProb calcProb = new DoubleMultinomialCalcProb(currentTypeTopicCounts,tokensPerTopic,beta,betaSum);
                    for(int topic = 0;topic<numTopics;++topic){
                        double val = calcProb.getProb(type,topic)*(localTokensPerTopic[topic]+this.alpha);
                        result[topic]+=val;
                        norm+=val;
                    }
                }
                /*
                  //old version
                  for(int pos=0;pos<docLength;++pos){
                    int topic = topics[pos];
                    result[topic]+=1.0;
                    norm+=1.0;    
                    }*/
            }
        }
        //normalize
        for(int label = 0;label<numTopics;++label){
            result[label]/=norm;
        }
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
            //System.out.println(Arrays.toString(probs)+" "+randomValue+" "+probs[index]);
        }
        return index;
    }

    public double getTestingLoglikelihood(InstanceList data){
        this.evaluate=true;
        double likelihood = 0;
        int numTokens = 0;
        int count = 0;
        for(Instance instance: data){
            count+=1;

            FeatureSequence tokenSequence =
                (FeatureSequence) instance.getData();
            int docLength = tokenSequence.getLength();
            numTokens+=docLength;
            double[] distribution = this.getSampledDistribution(instance,10,1,4);
            double docLikelihood = getDocLikelihood(distribution,tokenSequence);
            likelihood+=docLikelihood;
        }
        this.evaluate=false;
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
                int topic = topics.getIndexAtPosition(pos);
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
                docPerp+=Math.log(typePerp);
            }
            loglikelihood+=docPerp;
        }
        return loglikelihood/tokenSum;
    }

    public void normalize(double[] probs,double sum){
        for(int i = 0;i<probs.length;++i){
            probs[i]/=sum;
        }
    }
    
}
