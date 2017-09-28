package org.kramerlab.util;

import java.util.Arrays;
import java.io.Serializable;

/**
computes fractions of stirling numbers for the block sampler for HDP
see "A Bayesian View of the Poisson-Dirichlet Process" by Buntine and Hutter*/

public class StirlingTables implements Serializable{

    private static final long serialVersionUID = 6594105819499469709L;

    int maxN;
    int maxM;
    int usedN;
    int usedM;
    double a;
    double[][] S_V;

    public StirlingTables(){
    }

    public StirlingTables(int maxN, int maxM, int usedN,int usedM,double a){
        if(a<0||a>1){
            System.err.println("a has to be between zero and one!");
        }
        this.maxN = maxN;
        this.maxM = maxM;
        this.usedN=usedN;
        this.usedM=usedM;
        this.a = a;
        this.S_V = new double[maxN+1][maxM+1];
        fillTable(0,0,usedN,usedM);
    }

    public void fillTable(int startN,int startM,int usedN,int usedM){
        if(usedN<startN) usedN=startN;
        if(usedM<startM) usedM=startM;
        //make sure to increase the table in bigger steps
        if(usedN-startN<10&&usedN-startN>0) usedN=startN+100;
        if(usedM-startM<10&&usedM-startM>0) usedM=startM+100;
        int N,M;
        if(startM>0 &&startM<usedM){
            //refill for M upto startN
            for(int n = startM+1;n<=startN;++n){//note that n>=m!
                for(int m = startM+1;m<=n && m<=usedM;++m){
                    S_V[n-2][m-2] = m<n?(n-1-m*a)*S_V[n-3][m-2]:0;
                    S_V[n-2][m-2]+=1;
                    S_V[n-2][m-2]/=1.0/S_V[n-3][m-3]+(n-1-(m-1)*a);
                }
            }
        }
        //now fill from M=0 after startN

        if(startN==0){
            S_V[0][0] = 1.0/(1.0-a);
            N=3;
        }else{
            N=startN+1;
        }

        for (; N<=usedN; N++) {//N=3
            S_V[N-2][0] = (1.0+(N-1-2*a)*S_V[N-3][0])/(N-1-a);
            for (M=3; M<=usedM && M<=N; M++) {//M=3
                S_V[N-2][M-2] = M<N?((N-1-M*a)*S_V[N-3][M-2]):0; 
                S_V[N-2][M-2] += 1.0;
                S_V[N-2][M-2]  /= (1.0/S_V[N-3][M-3]+(N-1-(M-1)*a));
            }
        }
        this.usedN = usedN;
        this.usedM = usedM;
    }

    public double S_U(int n,int m){
        if(m==1) return n-a;
        if(m<=1) return -1;
        double sv = S_V(n,m);
        if(sv==0)System.out.println("StirlingTables: sv is zero "+n+" "+m);
        return n-m*a+1.0/sv;
    }

    public double S_UV(int n,int m){
        double sv;
        if(m==1) return Double.NEGATIVE_INFINITY;
        if(m==n+1) return 1;
        sv = S_V(n,m);
        if(sv==0)System.out.println("StirlingTables: sv is zero "+n +" "+m);
        return (n-m*a)*sv+1.0;
    }

    public double S_V(int n, int m){
        if(m>n) return 0;
        if(m==1) return n;
        if(m<=usedM&&n<=usedN){
            return S_V[n-2][m-2];
        }else{
            if(m<=maxM&&n<=maxN){
                fillTable(usedN,usedM,n,m);
                return S_V[n-2][m-2];
            }else{
                System.out.println("maximum table value reached!!! m:"+m+" n: "+n);
                return -1;
            }
        }
    }


    public double stirling_UV(int N,int M){
        double result =  S_UV(N,M);
        if(result!=-1){
            return result;
        }else{
            System.out.println("overflow stirling table UV "+N+" "+M);
            return -1;
        }
    }
    public double stirling_U(int N,int M){
        if(M>N) return 0;
        if(M==1) return N;
        if(N==0&&M==0) return 0;
        double result =  S_U(N,M);
        if(result!=-1&&result!=Double.POSITIVE_INFINITY){
            return result;
        }else{
            System.out.println("overflow stirling table U "+N+" "+M+" "+result);
            return result;
        }
    }



    public static void main(String[] args){
        StirlingTables table = new StirlingTables(10100,1100,100,1000,0.5);
        System.out.println(table.S_V(1,1));
        System.out.println(table.S_V(10000,10));
        System.out.println(table.S_V(10000,100));
        System.out.println(table.S_V(10000,1000));
    }
}
