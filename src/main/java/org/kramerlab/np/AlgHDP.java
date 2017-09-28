package org.kramerlab.np;


import org.apache.commons.cli.*;
import cc.mallet.types.InstanceList;
import org.kramerlab.np.*;
import org.kramerlab.interfaces.Algorithm;
import org.kramerlab.interfaces.TopicModel;


public class AlgHDP implements Algorithm{


    TopicModel model;

    public void setModel(TopicModel model){
        this.model = model;
    }

    
    public TopicModel initialize(CommandLine options,InstanceList data,boolean supervised){
        TopicModel model = null;
        boolean updateConcentration = true;
        if(options.getOptionValue("updateConcentration")!=null){
            updateConcentration = Boolean.valueOf(options.getOptionValue("updateConcentration"));
            System.out.println("update concentration "+updateConcentration);
        }
        int maxN,maxM,initN,initM;
        maxN = 25000;
        maxM = 5000;
        initN = 5000;
        initM = 1000;
        if(options.getOptionValue("maxN")!=null){
            maxN = Integer.valueOf(options.getOptionValue("maxN"));
        }
        if(options.getOptionValue("maxM")!=null){
            maxM = Integer.valueOf(options.getOptionValue("maxM"));
        }
        if(options.getOptionValue("initN")!=null){
            initN = Integer.valueOf(options.getOptionValue("initN"));
        }        
        if(options.getOptionValue("initM")!=null){
            initM = Integer.valueOf(options.getOptionValue("initM"));
        }
        double b0,b1;
        if(updateConcentration){
            b0 = 1;
            b1 = 1;
        }else{
            b0 = Double.valueOf(options.getOptionValue("b0"));
            b1 = Double.valueOf(options.getOptionValue("b1"));
        }
        double evalb1 = b1;
        if(options.getOptionValue("evalb1")!=null){
            evalb1 = Double.valueOf(options.getOptionValue("evalb1"));
            System.out.println("Eval b1: "+evalb1);
        }
        double beta = Double.valueOf(options.getOptionValue("beta"));
        int k = Integer.valueOf(options.getOptionValue("k"));
        int mhIterations = Integer.valueOf(options.getOptionValue("numMHIterations"));
        int interval = 1;
        if(options.getOptionValue("interval")!=null){
            interval = Integer.valueOf(options.getOptionValue("interval"));
        }    
        int maxTopicNumber = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
        double a = 0.0;
        if(options.getOptionValue("a")!=null){
            a = Double.valueOf(options.getOptionValue("a"));
        }
	String sampler = options.getOptionValue("whichSampler");
	if(sampler.equals("li")){
	    if(!supervised){
		int maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
		System.out.println("unsupervised");
		model = new LiBlockSampler2(org.kramerlab.Main.newLabelAlphabet(maxNumTopics),beta,k,b0,b1,true,interval,mhIterations,maxN,maxM,initN,initM,evalb1);
	    }else{
		model = new LiBlockSampler2(org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet()),beta,k,b0,b1,false,interval,mhIterations,maxN,maxM,initN,initM,evalb1);
		//System.out.println("supervised version currently not available");
	    }
	}else if(sampler.equals("myalias")){

	    if(!supervised){
		int maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
		System.out.println("unsupervised");
		model = new MyBlockSampler2(org.kramerlab.Main.newLabelAlphabet(maxNumTopics),beta,k,b0,b1,true,interval,true,maxN,maxM,initN,initM,evalb1,a,mhIterations);
		//((BlockSampler2)model).setUpdateConcentration(updateConcentration);
	    }else{
		model = new MyBlockSampler2(org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet()),beta,k,b0,b1,false,interval,true,maxN,maxM,initN,initM,evalb1,a,mhIterations);
		//System.out.println("supervised version currently not available");
	    }

        }else if(sampler.equals("myalias2")){
	    if(!supervised){
		int maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
		System.out.println("unsupervised");
		model = new MyBlockSampler2(org.kramerlab.Main.newLabelAlphabet(maxNumTopics),beta,k,b0,b1,true,interval,false,maxN,maxM,initN,initM,evalb1,a,mhIterations);
		//((BlockSampler2)model).setUpdateConcentration(updateConcentration);
	    }else{
		model = new MyBlockSampler2(org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet()),beta,k,b0,b1,false,interval,false,maxN,maxM,initN,initM,evalb1,a,mhIterations);
		//System.out.println("supervised version currently not available");
	    }
	}else{
            if(!supervised){
                int maxNumTopics = Integer.valueOf(options.getOptionValue("maxTopicNumber"));
                System.out.println("unsupervised");
                model = new StandardBlockSampler2(org.kramerlab.Main.newLabelAlphabet(maxNumTopics),beta,k,b0,b1,true,interval,maxN,maxM,initN,initM,evalb1);
                //((BlockSampler2)model).setUpdateConcentration(updateConcentration);
            }else{
                model = new StandardBlockSampler2(org.kramerlab.Main.makeLabelAlphabet(data.getTargetAlphabet()),beta,k,b0,b1,false,interval,maxN,maxM,initN,initM,evalb1);
                //System.out.println("supervised version currently not available");
            }
	}
        ((MyBlockSampler2)model).setUpdateConcentration(updateConcentration);
        ((MyBlockSampler2)model).setEvalB1(evalb1);
	this.model = model;
        return model;
    }

    public Options constructOptions(boolean supervised){
        Options options = new Options();
        Option aOption = new Option("a",true,"a");    
        Option b0Option = new Option("b0",true,"b0");    
        Option useOriginalSampler = new Option("useOriginalSampler",true,"whether or not to use the original sampler (for alias version only)");
	Option whichSampler = new Option("whichSampler",true,"which sampler to use (li,myalias,myalias2)");    
        Option intervalOption = new Option("interval",true,"interval for stirling numbers");    
        Option mhOption = new Option("numMHIterations",true,"number of MH iterations");    
        Option concentrationOption = new Option("updateConcentration",true,"whether or not to update the concentration parameters");   
        Option maxNOption = new Option("maxN",true,"maximum N for Stirling tables");    
        Option maxMOption = new Option("maxM",true,"maximum M for Stirling tables");    
        Option initNOption = new Option("initN",true,"initial N for Stirling tables");    
        Option initMOption = new Option("initM",true,"initial M for Stirling tables");

        Option b1Option = new Option("b1",true,"b1");
        Option evalb1Option = new Option("evalb1",true,"b1 during evaluation");    
        Option betaOption = new Option("beta",true,"beta");    
        Option kOption = new Option("k",true,"k");    
        Option maxOption = new Option("maxTopicNumber",true,"maximum number of topics");
        options.addOption(b0Option).addOption(b1Option).addOption(betaOption).addOption(kOption).addOption(maxOption).addOption(concentrationOption).addOption(intervalOption).addOption(mhOption).addOption(useOriginalSampler).addOption(whichSampler).addOption(evalb1Option).addOption(aOption);    
        return options;
    }

    public String constructBaseString(CommandLine options,String dirBaseString){
	String sampler = options.getOptionValue("whichSampler");
        if(sampler.equals("li")){
            dirBaseString+="Li-HDP/";
        }else if(sampler.equals("myalias")){
            dirBaseString+="LA-HDP/";
        }else if(sampler.equals("myalias2")){
            dirBaseString+="LA2-HDP/";
        }else{
            dirBaseString+="HDP/";
        }
        return dirBaseString;
    }

    public String constructEndString(CommandLine options,String dirEndString){
        int mhIterations = Integer.valueOf(options.getOptionValue("numMHIterations"));
        boolean updateConcentration = true;
        if(options.getOptionValue("updateConcentration")!=null){
            updateConcentration = Boolean.valueOf(options.getOptionValue("updateConcentration"));
        }

        MyBlockSampler2 mod = (MyBlockSampler2) this.model;
        int numIterations = Integer.valueOf(options.getOptionValue("numIterations"));  
        if(updateConcentration){
            dirEndString = "updateConcentration/"+mod.getK()+"-"+mod.getBeta()+"-"+mod.getNumTopics()+"/"+String.valueOf(numIterations)+"/mh-"+mhIterations+"/";
        }else{
            dirEndString = mod.getB0()+"-"+mod.getB1()+"/"+mod.getK()+"-"+mod.getBeta()+"-"+mod.getNumTopics()+"/"+String.valueOf(numIterations)+"/evalb1-"+mod.getEvalB1()+"/mh-"+mhIterations+"/";
        }
        double a = 0.0;
        if(options.getOptionValue("a")!=null){
            a = Double.valueOf(options.getOptionValue("a"));
            dirEndString+="a-"+a+"/";
        }

        return dirEndString;
    }

    
}
