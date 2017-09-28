package org.kramerlab.np;

import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.TopicModel;
import org.kramerlab.interfaces.CalcProb;
import org.kramerlab.util.StirlingTables;
import org.kramerlab.util.MultinomialCalcProb;

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

public class StandardBlockSampler2 extends MyBlockSampler2{

    public StandardBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval){
        this(alphabet,beta,k,b0,b1,unsupervised,interval,10000,5000,5000,1000,b1);
    }
    
    public StandardBlockSampler2(LabelAlphabet alphabet,double beta,int k,double b0,double b1,boolean unsupervised,int interval,int maxN,int maxM,int initN,int initM,double evalb1){
        super(alphabet,beta,k,b0,b1,unsupervised,interval,false,maxN,maxM,initN,initM,evalb1,0.0,1);
        this.hdp = new HDP(new GenericRestaurant(), new ArrayList<GenericRestaurant>(),b0,b1,stirling,false);
        ((HDP)this.hdp).setUseEvalB1(true);
    }

    public void sampleTopicsForOneDoc(FeatureSequence wordTokens,FeatureSequence topicSequence,FeatureVector labels,GenericRestaurant docRestaurant,int docRestaurantIndex){
        int[] topics = topicSequence.getFeatures();
        int docLength = topicSequence.size();
        double[] probabilities = null;
        probabilities = new double[numTopics*2];
        int oldTopic,newTopic,arrayPosition,tableIndicator,type,oldTopicIndex,newTopicIndex,oldTableIndicator;
        double prob;
        int[] currentTypeTopicCounts;

        for(int position = 0;position<docLength;++position){
            type = wordTokens.getIndexAtPosition(position);
            currentTypeTopicCounts = typeTopicCounts[type];
            oldTopic = topics[position];
            oldTopicIndex = indicesForTopics.get(oldTopic);
            oldTableIndicator = 2;
            int tableRemoved = docRestaurant.remove(oldTopic,false);
            if(tableRemoved!=0){
                //the customer is allowed to move
                allowed++;
                if(!evaluate){
                    decreaseTypeTopic(currentTypeTopicCounts,type,oldTopic,oldTopicIndex);
                    if(tableRemoved==1){
                        hdp.getRoot().remove(oldTopic,true);
                        oldTableIndicator = 1;
                    }
                    if(!hdp.getRoot().hasTopic(oldTopic)){
                        //if the topic was completely removed
                        oldTableIndicator = 0;
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
                    probabilities = hdp.getProbabilityDistribution(probabilities,activeTopics,indicesForTopics,docRestaurant,true,calcProb,type);
                    arrayPosition = sampleFromArray(probabilities);
                    tableIndicator = 2-arrayPosition%2;
                    newTopicIndex = arrayPosition/2;
                    newTopic = activeTopics.get(newTopicIndex);
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
                if(tableIndicator<2&&!evaluate){
                    hdp.getRoot().add(newTopic);
                }
                topics[position] = newTopic;
                if(!evaluate){
                    increaseTypeTopic(currentTypeTopicCounts,type,newTopic,newTopicIndex);
                }
            }else{
                notAllowed++;
            }
        }
    }    


}
