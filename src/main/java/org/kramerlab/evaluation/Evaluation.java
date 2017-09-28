package org.kramerlab.evaluation;

import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.FeatureVector;

import java.util.Arrays;

public class Evaluation{


/** Calculate AUROC: Area Under the ROC curve. */
    public static double P_macroAUROC(int Y[][], double P[][]) {
        // works with missing
        int L = Y[0].length;
        int nonZeroLabels = L;
        double AUC[] = new double[L];
        for(int j = 0; j < L; j++) {
            int[] yj = getCol(Y, j);
            if(allZeroOrMissing(yj)){
                nonZeroLabels--;
                continue;
            }
            weka.classifiers.evaluation.ThresholdCurve curve = new weka.classifiers.evaluation.ThresholdCurve();
            weka.core.Instances result = curve.getCurve(meka.core.MLUtils.toWekaPredictions(yj, getCol(P, j)));
            AUC[j] = weka.classifiers.evaluation.ThresholdCurve.getROCArea(result);

        }
        double sumAll = 0;
        for(int i = 0;i<AUC.length;++i){
            sumAll+=AUC[i];
        }
        sumAll/=nonZeroLabels;
        return sumAll;
    }
/**
     * To check if all values for this label are zero or missing
     * 
     * @return If all labels are either missing or zero
     */
    public static boolean allZeroOrMissing(int[] real) {
        for (int i = 0; i < real.length; i++) {
            if (real[i] != 0 && real[i]!=-1) {
                return false;
            }
        }
        return true;
    }



    /**
     * from Meka MatrixUtils
     * GetCol - return the k-th column of M (as a vector).
     */
    public static double[] getCol(double[][] M, int k) {
        double[] col_k = new double[M.length];
        for (int i = 0; i < M.length; i++) {
            col_k[i] = M[i][k];
        }
        return col_k;
    }

    public static int[] getCol(int[][] M, int k) {
        int[] col_k = new int[M.length];
        for (int i = 0; i < M.length; i++) {
            col_k[i] = M[i][k];
        }
        return col_k;
    }



    public static boolean[] getTruth(Instance instance){
        int numlabels = instance.getTargetAlphabet().size();
        boolean[] truth_vals = new boolean[numlabels];
        FeatureVector labels = ((FeatureVector)instance.getTarget());
        int[] labelIndices = labels.getIndices();

        for(int j=0;j<labelIndices.length;++j){
            truth_vals[labelIndices[j]]=true;
        }
        return truth_vals;   
    }


    public static int[] getTruthInt(Instance instance){
        int numlabels = instance.getTargetAlphabet().size();
        int[] truth_vals = new int[numlabels];
        FeatureVector labels = ((FeatureVector)instance.getTarget());
        int[] labelIndices = labels.getIndices();

        for(int j=0;j<labelIndices.length;++j){
            truth_vals[labelIndices[j]]=1;
        }
        return truth_vals;   
    }


}
