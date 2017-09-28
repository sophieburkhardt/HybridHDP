/**
 * Main.java
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

package org.kramerlab;

import cc.mallet.types.InstanceList;
import cc.mallet.types.Instance;
import cc.mallet.util.Randoms;
import cc.mallet.types.Alphabet;
import cc.mallet.types.LabelAlphabet;

import java.nio.charset.StandardCharsets;

import org.kramerlab.util.ImportExample;
import org.kramerlab.util.StirlingTablesLarge;
import org.kramerlab.interfaces.TopicModel;
import org.kramerlab.interfaces.OnlineModel;
import org.kramerlab.interfaces.Inferencer;
import org.kramerlab.interfaces.Algorithm;


import org.kramerlab.np.MyBlockSampler2;
import org.kramerlab.np.Hybrid;
import org.kramerlab.np.AbstractAliasHDP;
import org.kramerlab.np.HDP;
import org.kramerlab.lda.HybridParametric;
import org.kramerlab.np.AlgHybridNP;
import org.kramerlab.lda.AlgHybridP;
import org.kramerlab.np.AlgHDP;
//import org.kramerlab.lda.AlgLDA;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;


import org.apache.commons.cli.*;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;


import java.util.Iterator;
import java.util.ArrayList;
import java.util.Arrays;

public class Main{

    public static void main(String[] args){
        try{
            String name = "config.txt";
            String lineN = args[0];
            String[] toParse = new String[4];
            toParse[0] = "-f";
            toParse[1] = name;
            toParse[2] = "-l";
            toParse[3] = lineN;
            //read filename and linenumber where parameters are stored
            Options mainOptions = new Options().addOption("f",true,"filename where parameters are stored").addOption("l",true,"line number where parameters are in the file");
            CommandLineParser mainParser = new DefaultParser();
            CommandLine mainLine = mainParser.parse(mainOptions, toParse);
            String fileName = mainLine.getOptionValue("f");
            int lineNumber = Integer.valueOf(mainLine.getOptionValue("l"));
            String[] parLine = readLine(fileName,lineNumber);
//read in the selected algorithm from retrieved line
            OptionGroup algorithmTypeGroup = new OptionGroup().addOption(new Option("hdp",false,"HDP")).addOption(new Option("hyb_hdp",false,"Hybrid HDP")).addOption(new Option("hyb_lda",false,"Hybrid LDA"));
            algorithmTypeGroup.setRequired(true);
            Option supervisedOption = new Option("supervised",true,"whether or not the algorithm is supervised");
            Options methodOptions = new Options().addOptionGroup(algorithmTypeGroup).addOption(supervisedOption);
            CommandLineParser algParser = new ExtendedDefaultParser(true);
            CommandLine algLine = algParser.parse(methodOptions, parLine);
            String selectedAlgorithm = algorithmTypeGroup.getSelected();
            String superv = algLine.getOptionValue("supervised");
            boolean supervised = true;
            if (algLine.hasOption("supervised")){
                supervised = Boolean.valueOf(superv);
                //System.out.println("Algorithm is supervised "+supervised);
            }else{
                System.err.println("supervised option not specified");
            }
            //get the options for the selected algorithm and parse them
            Algorithm alg = getAlgorithm(selectedAlgorithm);
            Options options = constructOptions(alg, supervised);
            CommandLineParser paraParser = new ExtendedDefaultParser(true);
            CommandLine parsedOptions = paraParser.parse(options, parLine);
            runAlgorithm(alg, parsedOptions, supervised);

        }catch(ParseException e){
            e.printStackTrace();
        }
    }

    public static Algorithm getAlgorithm(String alg){
        if(alg.equals("lda")){
            return null;//new AlgLDA();
        }else if(alg.equals("hdp")){
            return new AlgHDP();
        }else if(alg.equals("hyb_hdp")){
            return new AlgHybridNP();
        }else if(alg.equals("hyb_lda")){
            return new AlgHybridP();
        }
        return null;
    }


    public static Options constructOptions(Algorithm alg,boolean supervised){
        Options options = null;
        //get algorithm specific options
        options = alg.constructOptions(supervised);
        //add general options
        Options generalOptions = generalOptions();
        for(Option opt: generalOptions.getOptions()){
            options.addOption(opt);
        }
        return options;
    }

    public static String[] readLine(String filename,int lineNumber){
        BufferedReader br=null;
        String line=null;
        int count = 0;
        try{
            br = new BufferedReader(new FileReader(filename));
            while(count<lineNumber){
                line=br.readLine();
                count++;
            }
        }catch(FileNotFoundException e1){
            e1.printStackTrace();
        }catch(IOException e2){
            e2.printStackTrace();
        }
        String[] pars = line.split(" ");

        return pars;
    }

    public static void runAlgorithm(Algorithm alg, CommandLine options, boolean supervised){
        TopicModel model = null;
        String datasetName = options.getOptionValue("datasetName");
        String dirBaseString="allresults/"+datasetName+"/";
        String dirEndString=null;
        Boolean readDirectly = false;
        if(options.getOptionValue("readInstancelist")!=null){
            readDirectly = Boolean.valueOf(options.getOptionValue("readInstancelist"));
            System.out.println("deserialize "+readDirectly);
        }
        String dataFilename = options.getOptionValue("dataFilename");
        String testDataFilename = options.getOptionValue("testDataFilename");
        System.out.println("filename "+dataFilename+" dataset "+datasetName+" ");
        int numIterations;
        //read data
        String inputType = options.getOptionValue("inputType");
        
        InstanceList data = null;
        if(readDirectly){
            data = InstanceList.load(new File(dataFilename));
            System.out.println("number documents "+data.size());
        }else{
	    ImportExample ie = new ImportExample();
            data = readData(ie,dataFilename, supervised,inputType);
            //data.save(new File(datasetName+"-train.instancelist"));
        }
        InstanceList trainingData = null;
        InstanceList testingData = null;

        if(testDataFilename!=null){
            trainingData = data;
            if(readDirectly){
                testingData = InstanceList.load(new File(testDataFilename));
            }else{
		ImportExample ie = new ImportExample();
                testingData = readData(ie,testDataFilename,supervised,inputType);
                //trainingData.save(new File(datasetName+"-train.instancelist"));
                //testingData.save(new File(datasetName+"-test.instancelist"));
            }
        }
        System.out.println("number of labels: "+data.getTargetAlphabet().size());
        System.out.println("number of features: "+data.getDataAlphabet().size());
        if(trainingData!=null){
            System.out.println("number of training data: "+trainingData.size());
            System.out.println("number of testing data: "+testingData.size());
        }
        if(options.getOptionValue("readExisting")!=null){
            //read existing model
            System.out.println("reading existing model...");
            String readFilename = options.getOptionValue("modelFilename");
            try{
                model = read(new File(readFilename));
            }catch(Exception e){
                e.printStackTrace();
            }
        }else{
            model = alg.initialize(options,data,supervised);
        }
        dirBaseString=alg.constructBaseString(options,dirBaseString);
        dirEndString = alg.constructEndString(options,dirEndString);

        if(options.getOptionValue("run")!=null){
            Integer run = Integer.valueOf(options.getOptionValue("run"));
            if(run>1)data.shuffle(new Randoms());
            dirEndString = dirEndString+"run-"+run+"/";
        }
        //batch, incremental, incremental batch ?
        if(options.getOptionValue("batch")!=null){
            String outputDirectory = dirBaseString+"justbatch/"+dirEndString;
            createDirectory(outputDirectory);
            
            //divide into training and testing data
            if(options.getOptionValue("trainingPercentage")!=null&&testingData==null){
                double trainingPercentage = Double.valueOf(options.getOptionValue("trainingPercentage"));
                int split = (int)(data.size()*trainingPercentage);
                trainingData = data.subList(0,split);
                testingData = data.subList(split,data.size());
            }
            else{
                trainingData = data;
            }
            //normal batch training
            trainBatch(model,options,trainingData,testingData,outputDirectory,supervised);
        }else  if(options.getOptionValue("incremental")!=null){
            //divide into training and testing data
            if(options.getOptionValue("trainingPercentage")!=null&&testingData==null){
                double trainingPercentage = Double.valueOf(options.getOptionValue("trainingPercentage"));
                int split = (int)(data.size()*trainingPercentage);
                trainingData = data.subList(0,split);
                testingData = data.subList(split,data.size());
            }
            String outputDirectory = dirBaseString+"online/"+dirEndString;
            createDirectory(outputDirectory);
            int batchsize = Integer.valueOf(options.getOptionValue("batchSize"));
            
            trainOnline((OnlineModel)model,options,data,batchsize,outputDirectory,supervised);
        }else  if(options.getOptionValue("incremental_fixed")!=null){
            //divide into training and testing data
            if(options.getOptionValue("trainingPercentage")!=null&&testingData==null){
                double trainingPercentage = Double.valueOf(options.getOptionValue("trainingPercentage"));
                int split = (int)(data.size()*trainingPercentage);
                trainingData = data.subList(0,split);
                testingData = data.subList(split,data.size());
            }
            String outputDirectory = dirBaseString+"online/"+dirEndString;
            createDirectory(outputDirectory);
            int batchsize = Integer.valueOf(options.getOptionValue("batchSize"));
            
            trainOnlineFixedTestsetECML((OnlineModel)model,options,trainingData,testingData,batchsize,outputDirectory,supervised);
        }
    }

    public static Options generalOptions(){
        Options options = new Options();
        //Option serializeOption = new Option("serialize",true,"serialize model to file");
        Option readOption = new Option("readInstancelist",true,"whether or not to read the serialized instancelist directly");
        Option subsampleOption = new Option("subsample",true,"whether to use the full test dataset or just a sample from it");    
        Option writeOption = new Option("writeDistributions",true,"write distributions to file");
        Option dataFileOption = new Option("dataFilename",true,"filename for data");
        Option testdataFileOption = new Option("testDataFilename",true,"filename for testingdata");
        Option inputTypeOption = new Option("inputType",true,"input type, e.g. json or txt");
        Option datasetOption = new Option("datasetName",true,"name of dataset");
        Option percOption = new Option("trainingPercentage",true,"percentage of data set to use for training in case of batch training");
        Option numIterOption = new Option("numIterations",true,"number of iterations for training");
        Option numTopOption = new Option("numTopWords",true,"number of words to display per topic");
        Option evalInterval = new Option("evaluationInterval",true,"after how many training steps to evaluate");
        Option evalIterOption = new Option("numEvalIterations",true,"number of iterations for evaluation");
        Option thinningOption = new Option("thinning",true,"number of thinning iterations at evaluation");
        Option burninOption = new Option("burnin",true,"number of burn-in iterations at evaluation");
        Option wordThresholdOption = new Option("wordThreshold",true,"only display words that occur more often than this");
	Option printOption = new Option("print",true,"whether or not to print the resulting topics to file");    
	Option saveOption = new Option("saveModel",true,"whether or not to save the model to file");    
	Option saveFilenameOption = new Option("saveFilename",true,"filename where to save the model, only necessary when saveModel is true");    
	Option readFilenameOption = new Option("modelFilename",true,"filename to read existing model from");    
        Option startAtOption = new Option("startAt",true,"where to start evaluating (for online experiments)");
        Option runOption = new Option("run",true,"run number, if larger than 1, dataset will be randomized");
        Option batchsizeOption = new Option("batchSize", true, "batch size for online learning");
        //options.add(serializeOption);
        options.addOption(evalIterOption);
        options.addOption(thinningOption);
        options.addOption(burninOption);
        options.addOption(startAtOption);
        options.addOption(subsampleOption);
        options.addOption(readOption);
        options.addOption(batchsizeOption);
        options.addOption(writeOption);
        options.addOption(testdataFileOption);
        options.addOption(runOption);
        options.addOption(dataFileOption);
	options.addOption(inputTypeOption);
        options.addOption(datasetOption);
        options.addOption(percOption);
        options.addOption(numIterOption);
        options.addOption(numTopOption);
        options.addOption(evalInterval);
        options.addOption(wordThresholdOption);
	options.addOption(printOption);
        options.addOption(saveOption);
        options.addOption(saveFilenameOption);
        options.addOption(readFilenameOption);
        OptionGroup trainTypeGroup = new OptionGroup().addOption(new Option("batch",true,"to train model in batch mode")).addOption(new Option("incremental",true,"train model incrementally, with prequential evaluation")).addOption(new Option("incrementalBatch",true,"train in batch mode with incrementally increasing size of training data")).addOption(new Option("incremental_fixed",true,"train incrementally with a fixed testset")).addOption(new Option("readExisting",true,"read existing model from file"));   
        trainTypeGroup.setRequired(true); 
        options.addOptionGroup(trainTypeGroup);
        return options;
    }

    public static TopicModel read (File f) throws Exception {

        TopicModel topicModel = null;

        ObjectInputStream ois = new ObjectInputStream (new FileInputStream(f));
        topicModel = (TopicModel) ois.readObject();
        ois.close();

        return topicModel;
    }

    
    public static void save(TopicModel model, String filename){
        try {
            ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(filename));
            oos.writeObject(model);
            oos.close();
        } catch (IOException e) {
            System.err.println("Problem serializing Model to file " +
                               filename + ": " + e);
        }
    }

    public static InstanceList readData(ImportExample ie,String filename, boolean supervised,String inputType){
        //ImportExample ie = new ImportExample();
        InstanceList il = null;
	if(inputType!=null&&inputType.equals("json")){
	    String outputName = "jsonOutput";
	    transformJSONData(filename,outputName);
	    filename = outputName;
	}
        if(supervised){
            il = ie.readFromFile(filename);
        }else{
            il = ie.readFromFile(filename);//temporary
        }
        System.out.println("number of features: "+il.getDataAlphabet().size());
        return il;//.subList(0,1000);//just for debugging TODO: change
    }

    public static void createDirectory(String directory){
        File dirfile=new File(directory);
        if(!dirfile.exists()){
            try{
                Files.createDirectories(Paths.get(directory));
            }catch(IOException e){
                e.printStackTrace();
            }
        }
        System.out.println("directory: "+directory);
    }

    public static void transformJSONData(String inputFilename, String outputFilename){
        try{
            JSONTokener tokener=new JSONTokener(new BufferedReader(new InputStreamReader(new FileInputStream(inputFilename), "UTF-8")));
            JSONArray jsonArray=new JSONArray(tokener);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename),"UTF-8"));
	    jsonArray = (JSONArray)((JSONObject)((JSONObject)jsonArray.get(0)).get("hits")).get("hits");
            for (int i=0; i < jsonArray.length(); i++) {
                JSONObject object=jsonArray.getJSONObject(i);
		Iterator iter = object.keys();
		/*while(iter.hasNext()){
		    System.out.println(iter.next());
		    }*/
		if(object.has("fields")){
                //write instance to file
                //get source array
                JSONObject source = (JSONObject)object.get("fields");
                //get text
                String text = source.getString("content.content_text").replace("\n"," ");
                //get label_id
                String id = "something";//object.getString("_id");
                //System.out.println(text+" " +id);
                bw.write(i + "\t" + id +"\t" + text+"\n");
		}
            }
            bw.flush();
            bw.close();
	}catch(IOException e){
            e.printStackTrace();
        }catch(JSONException e){
            e.printStackTrace();
        }

    }

    public static ArrayList evaluate(TopicModel model,InstanceList testingData,String outputDirectory,boolean writeDistributions,InstanceList trainset,int numIterations,int thinning, int burnin){
        int numMeasures = 2;
        Double[] results = new Double[numMeasures]; //for storing micro- and macro-averaged AUC
        int[][] trueVals = new int[testingData.size()][];
        double[][] prediction = new double[testingData.size()][];
        try{
            BufferedWriter distWriter = new BufferedWriter(new FileWriter(outputDirectory+"/distributions.txt"));
            distWriter.write("\n");
            distWriter.flush();
            distWriter.close();
        }catch(Exception e){
            e.printStackTrace();
        }

        int numLabels =testingData.getTargetAlphabet().size();
        

        if(model instanceof MyBlockSampler2){
            ((MyBlockSampler2)model).setEvaluate(true);
            HDP hdp = ((MyBlockSampler2)model).getHDP();
            if(hdp instanceof AbstractAliasHDP){
                ((AbstractAliasHDP)hdp).clearSamples();
            }
        }
        
        long start = System.currentTimeMillis();
        Inferencer ti=model.getInferencer();
        System.out.println("number of test documents "+testingData.size());
        for(int i=0;i<testingData.size();++i){
            //if(i%100==0){
                //System.out.println("number of evaluated documents: "+i);
              //}
            Instance testinst = testingData.get(i);
            double[] dist=ti.getSampledDistribution(testinst,numIterations,thinning,burnin);
            int[] truth = org.kramerlab.evaluation.Evaluation.getTruthInt(testinst);
            trueVals[i]=truth;
            prediction[i]=dist;
            if(writeDistributions){
                try{
                    BufferedWriter distWriter = new BufferedWriter(new FileWriter(outputDirectory+"/distributions.txt",true));
                    distWriter.write(Arrays.toString(dist)+"\n");
                    distWriter.flush();
                    distWriter.close();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }

        }
        long end = System.currentTimeMillis();
        try{
            
            BufferedWriter timeWriter = new BufferedWriter(new FileWriter(outputDirectory+"/evalTime.txt"));
            timeWriter.write(end-start+"\n");
            timeWriter.flush();
            timeWriter.close();
        }catch(Exception e){
            e.printStackTrace();
        }
        


        if(model instanceof MyBlockSampler2){
            ((MyBlockSampler2)model).setEvaluate(false);
        }
        //micro AUC
        weka.core.Instances resinst = meka.core.Metrics.curveDataMicroAveraged(trueVals,prediction);
        double microAUC = weka.classifiers.evaluation.ThresholdCurve.getROCArea(resinst);

        results[0]=microAUC;
        //macro AUC
        double macroAUC = org.kramerlab.evaluation.Evaluation.P_macroAUROC(trueVals,prediction);
        results[1]=macroAUC;

        System.out.println("Micro-averaged AUC "+results[0]);
        System.out.println("Macro-averaged AUC "+results[1]);
        ArrayList resList = new ArrayList();
        for(int i = 0;i<results.length;++i){
            resList.add(results[i]);
        }
        return resList;//results;
        
    }

    public static void trainOnline(OnlineModel model,CommandLine options,InstanceList data,int batchsize,String outputDirectory,boolean supervised){
        System.out.println("train online");
        printEvalTime(outputDirectory+"runtime.txt",0,0,-1);
        String[] measureNames = new String[]{"Micro-averaged AUC", "Macro-averaged AUC"};

        int numTopWords = Integer.valueOf(options.getOptionValue("numTopWords"));
        int wordThreshold = Integer.valueOf(options.getOptionValue("wordThreshold"));
        int evalIter = 200;
        int thinning = 1;
        int burnin = 100;
        if(options.getOptionValue("numEvalIterations")!=null){
            evalIter=Integer.valueOf(options.getOptionValue("numEvalIterations"));
        }
        if(options.getOptionValue("thinning")!=null){
            thinning=Integer.valueOf(options.getOptionValue("thinning"));
        }
        if(options.getOptionValue("burnin")!=null){
            burnin=Integer.valueOf(options.getOptionValue("burnin"));
        }
        int startAt = 0;
        if(options.getOptionValue("startAt")!=null){
             startAt = Integer.valueOf(options.getOptionValue("startAt"));
        }
        boolean print = Boolean.valueOf(options.getOptionValue("print"));
        boolean parallel = Boolean.valueOf(options.getOptionValue("parallelEvaluation"));
        boolean writeDistributions = Boolean.valueOf(options.getOptionValue("writeDistributions"));
        String saveFilename = null;
        boolean saveModel = Boolean.valueOf(options.getOptionValue("saveModel"));
        if(saveModel) saveFilename = options.getOptionValue("saveFilename");
        int instanceCounter = 0;
        boolean started = false;
        try{
            BufferedWriter empty = new BufferedWriter(new FileWriter(outputDirectory+"topics.txt"));
            empty.flush();
            empty.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    
        while(instanceCounter<data.size()-(2*batchsize)){
                    
            System.out.println(instanceCounter);
            InstanceList batch = null;
            if(data.size()-instanceCounter>=batchsize){
                batch = data.subList(instanceCounter,instanceCounter+batchsize);
            }else{
                batch = data.subList(instanceCounter,data.size());
            }
            long start = System.currentTimeMillis();
            if(started){
                model.updateBatch(batch);
            }else{
                model.addInstances(batch);
                try{
                    model.sample(100);
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
            long end = System.currentTimeMillis();
            printEvalTime(outputDirectory+"runtime.txt",start,end,(instanceCounter+batchsize));
            if(instanceCounter>=startAt){

                if(print) model.printTopWords(outputDirectory+"topics.txt",numTopWords,wordThreshold);
                
                InstanceList testingData = data.subList(instanceCounter+batchsize,instanceCounter+(2*batchsize));
                ArrayList<Double> measures = evaluate(model,testingData,outputDirectory,writeDistributions,null,evalIter,thinning,burnin);
                if(saveModel) save(model,saveFilename);
                if(!started||instanceCounter==startAt){
                    //delete previous file contents
                    int c = 0;
                    for(Double measure: measures){
                        String name = measureNames[c];
                        c++;
                        try{
                            BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt"));
                            bw.write("");
                            bw.flush();
                            bw.close();
                        }catch(IOException e){
                            e.printStackTrace();
                        }
                    }

                }
                //write results to file
                int c=0;
                for(Double measure: measures){
                    String name = measureNames[c];
                    c++;
                    try{
                        BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt",true));
                        bw.write((instanceCounter+batchsize) +" "+measure);
                        bw.write("\n");
                        bw.flush();
                        bw.close();
                    }catch(IOException e){
                        e.printStackTrace();
                    }
                }
            }
            started = true;
            instanceCounter+=batchsize;
        }
    }



    public static void trainOnlineFixedTestset(OnlineModel model,CommandLine options,InstanceList data,InstanceList testdata,int batchsize,String outputDirectory,boolean supervised){
        System.out.println("train with fixed test set");
        int numIterations = Integer.valueOf(options.getOptionValue("numIterations"));
        int evalInterval = Integer.valueOf(options.getOptionValue("evaluationInterval"));
        printEvalTime(outputDirectory+"runtime.txt",0,0,-1);    
        int numTopWords = Integer.valueOf(options.getOptionValue("numTopWords"));
        int wordThreshold = Integer.valueOf(options.getOptionValue("wordThreshold"));
        boolean print = Boolean.valueOf(options.getOptionValue("print"));
        boolean writeDistributions = Boolean.valueOf(options.getOptionValue("writeDistributions"));
        String saveFilename = null;
        boolean saveModel = Boolean.valueOf(options.getOptionValue("saveModel"));
        boolean subsample = Boolean.valueOf(options.getOptionValue("subsample"));
        if(saveModel) saveFilename = options.getOptionValue("saveFilename");
        int evalIter = 200;
        int thinning = 1;
        int burnin = 100;
        if(options.getOptionValue("numEvalIterations")!=null){
            evalIter=Integer.valueOf(options.getOptionValue("numEvalIterations"));
        }
        if(options.getOptionValue("thinning")!=null){
            thinning=Integer.valueOf(options.getOptionValue("thinning"));
        }
        if(options.getOptionValue("burnin")!=null){
            burnin=Integer.valueOf(options.getOptionValue("burnin"));
        }

        boolean started = false;
        int numBatches=0;
        /*InstanceList initbatch = data.subList(0,batchsize);
        for(int i = 0;i<10;++i){
            model.updateBatch(initbatch);
            }*/
        try{
        BufferedWriter empty = new BufferedWriter(new FileWriter(outputDirectory+"topics.txt"));
        empty.flush();
        empty.close();
        }catch(Exception e){
            e.printStackTrace();
        }
        for(int iter=0;iter<numIterations;++iter){
            int instanceCounter = 0;

            while(instanceCounter<data.size()-(batchsize)){            
                System.out.println(instanceCounter);
    
                InstanceList batch = data.subList(instanceCounter,instanceCounter+batchsize);
                long start = System.currentTimeMillis();    
                //if(started){
                model.updateBatch(batch);
                //}else{
                //model.addInstances(batch);
                //try{
                //  model.sample(1);
                //}catch(IOException e){
                //  e.printStackTrace();
                //}
                //}
                long end = System.currentTimeMillis();
                printEvalTime(outputDirectory+"runtime.txt",start,end,(instanceCounter+batchsize));
                if((numBatches+1)%evalInterval==0){//instanceCounter==0||
                    ArrayList<Double> measures = null;
                    if(subsample){
                        measures = evaluate(model,testdata.sampleWithReplacement(new Randoms(), 1000),outputDirectory,writeDistributions,null,evalIter,thinning,burnin);
                    }else{
                        measures = evaluate(model,testdata,outputDirectory,writeDistributions,null,evalIter,thinning,burnin);
                        }
                    if(print) model.printTopWords(outputDirectory+"topics.txt",numTopWords,wordThreshold);
                    if(saveModel) save(model,saveFilename);
                    String[] measureNames = new String[]{"Micro-averaged AUC", "Macro-averaged AUC"};
                    if(!started){
                        //delete previous file contents
                        if(supervised){
                            int c = 0;
                            for(Double measure: measures){
                                String name = measureNames[c];
                                ++c;
                                try{
                                    BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt"));
                                    bw.write("");
                                    bw.flush();
                                    bw.close();
                                }catch(IOException e){
                                    e.printStackTrace();
                                }
                            }
                        }else{
                            //TODO
                        }
                    }
                    int c = 0;
                    //write results to file
                    if(supervised){
                        for(Double measure: measures){
                            String name = measureNames[c];
                            c++;
                            try{
                                BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt",true));
                                bw.write((instanceCounter+batchsize) +" "+measure);
                                bw.write("\n");
                                bw.flush();
                                bw.close();
                            }catch(IOException e){
                                e.printStackTrace();
                            }
                        }
                    }
                    started = true;
                }
                instanceCounter+=batchsize;
                numBatches++;
            }
        }
    }


    public static void trainOnlineFixedTestsetECML(OnlineModel model,CommandLine options,InstanceList data,InstanceList testdata,int batchsize,String outputDirectory,boolean supervised){
        System.out.println("train with fixed test set");
        int numIterations = Integer.valueOf(options.getOptionValue("numIterations"));
        printEvalTime(outputDirectory+"runtime.txt",0,0,-1);    
        int numTopWords = Integer.valueOf(options.getOptionValue("numTopWords"));
        int wordThreshold = Integer.valueOf(options.getOptionValue("wordThreshold"));
        boolean print = Boolean.valueOf(options.getOptionValue("print"));
        boolean parallel = Boolean.valueOf(options.getOptionValue("parallelEvaluation"));
        boolean writeDistributions = Boolean.valueOf(options.getOptionValue("writeDistributions"));
        String saveFilename = null;
        boolean saveModel = Boolean.valueOf(options.getOptionValue("saveModel"));
        int evalInterval=Integer.valueOf(options.getOptionValue("evaluationInterval"));
        if(saveModel) saveFilename = options.getOptionValue("saveFilename");
        if(model instanceof Hybrid){
            ((Hybrid)model).init(data.getDataAlphabet().size());
        }
        if(model instanceof HybridParametric){
            ((HybridParametric)model).init(data.getDataAlphabet().size());
        }
     
        try{
            BufferedWriter likWriter = new BufferedWriter(new FileWriter(outputDirectory+"/loglikelihood.txt"));
            likWriter.write("");
            likWriter.flush();
            likWriter.close();
        }catch(Exception e){
            e.printStackTrace();
        }

        boolean started = false;
        for(int iter=0;iter<numIterations;++iter){
            int instanceCounter = 0;
            System.out.println(iter);
    
            InstanceList batch = data.sampleWithReplacement(new Randoms(), batchsize);
            long start = System.currentTimeMillis();    
            
            model.updateBatch(batch);
            
            long end = System.currentTimeMillis();
            printEvalTime(outputDirectory+"runtime.txt",start,end,iter);    
            if((iter+1)%evalInterval==0){
                if(print) model.printTopWords(outputDirectory+"topics.txt",numTopWords,wordThreshold);
                if(model instanceof Hybrid || model instanceof HybridParametric){
                    double loglikeli = 0;
                    if(model instanceof Hybrid){
                        loglikeli = ((Hybrid)model).getTestingLoglikelihood(testdata);
                    }else if(model instanceof HybridParametric){
                        loglikeli = ((HybridParametric)model).getTestingLoglikelihood(testdata);
                    }
                    System.out.println("loglikelihood "+loglikeli);
                    try{
                        BufferedWriter likWriter = new BufferedWriter(new FileWriter(outputDirectory+"/loglikelihood.txt",true));
                        likWriter.write(iter+" "+loglikeli+"\n");
                        likWriter.flush();
                        likWriter.close();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public static void trainBatch(TopicModel model,CommandLine options,InstanceList trainingData,InstanceList testingData,String outputDirectory,boolean supervised){
        boolean subsample = Boolean.valueOf(options.getOptionValue("subsample"));
        boolean measureLikelihood = false;
        if(options.getOptionValue("likelihood")!=null){
            measureLikelihood = Boolean.valueOf(options.getOptionValue("likelihood"));
            System.out.println("measure likelihood "+measureLikelihood);
        }
        int evalIter = 200;
        int thinning = 1;
        int burnin = 100;
        if(options.getOptionValue("numEvalIterations")!=null){
            evalIter=Integer.valueOf(options.getOptionValue("numEvalIterations"));
        }
        if(options.getOptionValue("thinning")!=null){
            thinning=Integer.valueOf(options.getOptionValue("thinning"));
        }
        if(options.getOptionValue("burnin")!=null){
            burnin=Integer.valueOf(options.getOptionValue("burnin"));
        }

        try{
            if(model instanceof MyBlockSampler2 && !supervised){
                try{
                    BufferedWriter distWriter = new BufferedWriter(new FileWriter(outputDirectory+"/loglikelihood.txt"));
                    distWriter.flush();
                    distWriter.close();
                    distWriter = new BufferedWriter(new FileWriter(outputDirectory+"/testloglikelihood.txt"));
                    distWriter.flush();
                    distWriter.close();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            printEvalTime(outputDirectory+"runtime.txt",0,0,-1);    
            int numIterations = Integer.valueOf(options.getOptionValue("numIterations"));
            int numTopWords = Integer.valueOf(options.getOptionValue("numTopWords"));
            int wordThreshold = Integer.valueOf(options.getOptionValue("wordThreshold"));
            //TODO: only read in when using a supervised method
            int evalInterval = -1;
            evalInterval = Integer.valueOf(options.getOptionValue("evaluationInterval"));
            boolean print = Boolean.valueOf(options.getOptionValue("print"));
            boolean parallel = Boolean.valueOf(options.getOptionValue("parallelEvaluation"));
            boolean writeDistributions = Boolean.valueOf(options.getOptionValue("writeDistributions"));
            String saveFilename = null;
            boolean saveModel = Boolean.valueOf(options.getOptionValue("saveModel"));
            if(saveModel) saveFilename = options.getOptionValue("saveFilename");
            boolean started = false;
            model.addInstances(trainingData);
            for(int iter = 0;iter<numIterations;++iter){
                System.out.println("iteration "+iter);
                long start = System.currentTimeMillis();
                model.sample(1);
                long end = System.currentTimeMillis();
                printEvalTime(outputDirectory+"runtime.txt",start,end,iter);    
                try {
                    if(model instanceof MyBlockSampler2 && !supervised){
                        BufferedWriter likelihoodWriter = new BufferedWriter(new FileWriter(outputDirectory+"/loglikelihood.txt",true));
                        double like = ((MyBlockSampler2)model).getTrainingLoglikelihood();
                        System.out.println("likelihood "+like);
                        likelihoodWriter.write((iter)+" "+like+"\n");
                        likelihoodWriter.flush();
                        likelihoodWriter.close();
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
                String[] measureNames = new String[]{"Micro-averaged AUC", "Macro-averaged AUC"};
                if(!supervised&&(iter+1)%evalInterval==0&&testingData!=null&&testingData.size()>0){
                    if(print){
                        model.printTopWords(outputDirectory+"topics.txt",numTopWords,wordThreshold);
                        
                    }

                    try {
                        if(model instanceof MyBlockSampler2 && !supervised){
                            BufferedWriter likelihoodWriter = new BufferedWriter(new FileWriter(outputDirectory+"/testloglikelihood.txt",true));
                            double like = ((MyBlockSampler2)model).getTestingLoglikelihood(testingData);
                            System.out.println("likelihood "+like);
                            likelihoodWriter.write(iter+" "+like+"\n");
                            likelihoodWriter.flush();
                            likelihoodWriter.close();
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }

                }
                if(supervised&&(iter+1)%evalInterval==0){
                    if(print){
                        model.printTopWords(outputDirectory+"topics.txt",numTopWords,wordThreshold);

                    }

                    if(saveModel&&saveFilename!=null) save(model,saveFilename);
                    if(saveModel&&saveFilename==null) save(model,outputDirectory+"model.ser");
                    //evaluate model here
                    
                        if(!measureLikelihood){
                            ArrayList<Double> measures =null;
                            if(subsample){
                                measures = evaluate(model,testingData.sampleWithReplacement(new Randoms(), 1000),outputDirectory,writeDistributions,trainingData,evalIter,thinning,burnin);
                            }else{
                                measures = evaluate(model,testingData,outputDirectory,writeDistributions,trainingData,evalIter,thinning,burnin);
                            }
                            if(!started){
                                //delete previous file contents
                                int c = 0;
                                for(Double measure: measures){
                                    String name = measureNames[c];
                                    ++c;
                                    try{
                                        BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt"));
                                        bw.write("");
                                        bw.flush();
                                        bw.close();
                                    }catch(IOException e){
                                        e.printStackTrace();
                                    }
                                }

                            }
                            //write results to file
                            int c = 0;
                            for(Double measure: measures){
                                String name = measureNames[c];
                                ++c;
                                try{
                                    BufferedWriter bw = new BufferedWriter(new FileWriter(outputDirectory+name+".txt",true));
                                    bw.write(iter +" "+measure);
                                    bw.write("\n");
                                    bw.flush();
                                    bw.close();
                                }catch(IOException e){
                                    e.printStackTrace();
                                }
                            }
                        }
                    
                    started = true;
                }
            }
        
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void printEvalTime(String filename,long start,long end,int iter){
        try{
            if(iter==-1){
                BufferedWriter bwruntime = new BufferedWriter(new FileWriter(filename));
                bwruntime.close();
            }else{
                long runtime = end-start;
                BufferedWriter bwruntime = new BufferedWriter(new FileWriter(filename,true));
                bwruntime.write(iter+" "+String.valueOf(runtime)+"\n");
                bwruntime.flush();
                bwruntime.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }    
        
    }
    public static LabelAlphabet newLabelAlphabet (int numTopics) {
	LabelAlphabet ret = new LabelAlphabet();
	for (int i = 0; i < numTopics; i++)
	    ret.lookupIndex("topic"+i);
	return ret;
    }
    /**function to get a label Alphabet from a normal Alphabet*/
    public static LabelAlphabet makeLabelAlphabet(Alphabet alphabet){
        LabelAlphabet la = new LabelAlphabet();
        for(int i=0;i<alphabet.size();++i){
            la.lookupIndex(alphabet.lookupObject(i),true);
        }
        return la;
    }
    
    
}
