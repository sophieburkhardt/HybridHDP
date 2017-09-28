package org.kramerlab.util;

import java.util.Arrays;

public class StirlingTablesLarge extends StirlingTables{

    int interval = 10;
    int startSparse = 10000;

    public StirlingTablesLarge(int maxN, int maxM, int usedN,int usedM,double a,int interval){
        if(a<0||a>1){
            System.err.println("a has to be between zero and one!");
        }
        System.out.println("large Stirling tables");
        this.maxN = maxN;
        this.maxM = maxM;
        this.usedN=usedN;
        this.usedM=usedM;
        this.a = a;
        this.S_V = new double[maxN+1][maxM+1];
        this.interval = interval;
        fillTable(0,0,usedN,usedM);
        System.out.println("Interval "+interval);
    }


    /**
     @param usedN this is the index up to which we want to fill, not the true value of N*/
    public void fillTable(int startN,int startM,int usedN,int usedM){
        if(usedN<startN) usedN=startN;
        if(usedM<startM) usedM=startM;
        //make sure to increase the table in bigger steps
        if(usedN-startN<10&&usedN-startN>0) usedN=startN+100;
        if(usedM-startM<10&&usedM-startM>0) usedM=startM+100;
        if(usedN>maxN)usedN=maxN;
        if(usedM>maxM)usedM=maxM;
        System.out.println("fill table, n was "+startN+" now "+usedN+" m was "+startM+" now "+usedM);
        int N,M;
        if(startM>0 &&startM<usedM){
            //refill for M upto startN
            //first calculate the starting value for n (there cannot be less customers than tables)
            int firstN = 0;
            if((startM+1)<startSparse){
                firstN = startM+1;
            }else{
                firstN = startSparse;
                int g = (startM+1-startSparse)/interval;
                firstN+=g;
            }
            for(int n = firstN;n<=startN;++n){
                if(n<startSparse){
                    //store the whole table         
                    for(int m = startM+1;m<=n && m<=usedM;++m){
                        S_V[n-2][m-2] = m<n?(n-1-m*a)*S_V[n-3][m-2]:0;
                        S_V[n-2][m-2]+=1;
                        S_V[n-2][m-2]/=1.0/S_V[n-3][m-3]+(n-1-(m-1)*a);
                    }
                }else{
                    //we are in the sparse region
                    int trueN = startSparse+(n-startSparse)*interval;
                    //start from last value and build up 
                    double[][] temp = new double[interval][usedM];
                    //first line is the last stored line
                    for(int mHere = 2;mHere <= usedM;++mHere){
                        temp[0][mHere-2] = S_V[n-3][mHere];
                    }
                    //now compute the rest
                    for(int nHere = 1;nHere<interval;++nHere){
                        int tempTrueN = trueN+nHere;
                        temp[nHere][0] = (1.0+(tempTrueN-1-2*a)*temp[nHere-1][0])/(tempTrueN-1-a);
                        for(int mHere = 3;mHere<=usedM;++mHere){
                            temp[nHere][mHere-2]=mHere<tempTrueN?(tempTrueN-1-mHere*a)*temp[nHere-1][mHere-2]:0;
                            temp[nHere][mHere-2]+=1.0;
                            temp[nHere][mHere-2]/=1.0/temp[nHere-1][mHere-3]+(tempTrueN-1-(mHere-1)*a);
                        }
                    }
                    //new line is based on last line of temp
                    int tempTrueN = trueN+interval;
                    for(int mHere = startM;mHere<=tempTrueN&&mHere<=usedM;++mHere){
                        S_V[n-2][mHere-2] = mHere<tempTrueN?(tempTrueN-1-mHere*a)*temp[interval-1][mHere-2]:0;
                        S_V[n-2][mHere-2]+=1;
                        S_V[n-2][mHere-2]/=1.0/temp[interval-1][mHere-3]+(tempTrueN-1-(mHere-1)*a);
                    }
                }

            }
        }
        //now fill from M=0 after startN

        if(startN==0){
            S_V[0][0] = 1.0/(1.0-a);
            N=3;
            System.out.println(S_V[0][0]+" "+N+" "+a);
        }else{
            N=startN+1;
        }
     
        for (; N<=usedN; N++) {//N=3
            if(N<startSparse){
                S_V[N-2][0] = (1.0+(N-1-2*a)*S_V[N-3][0])/(N-1-a);
                for (M=3; M<=usedM && M<=N; M++) {//M=3
                    S_V[N-2][M-2] = M<N?((N-1-M*a)*S_V[N-3][M-2]):0; 
                    S_V[N-2][M-2] += 1.0;
                    S_V[N-2][M-2]  /= (1.0/S_V[N-3][M-3]+(N-1-(M-1)*a));
                }
            }else{
                //sparse region
                int trueN = startSparse+(N-startSparse)*interval;
                //start from last value and build up 
                double[][] temp = new double[interval][usedM+1];
                //first line is the last stored line
                for(int mHere = 0;mHere < temp[0].length;++mHere){
                    temp[0][mHere] = S_V[N-3][mHere];
                }
                //now compute the rest
                for(int nHere = 1;nHere<interval;++nHere){
                    int tempTrueN = trueN+nHere;
                    temp[nHere][0] = (1.0+(tempTrueN-1-2*a)*temp[nHere-1][0])/(tempTrueN-1-a);
                    for(int mHere = 3;mHere < temp[0].length;++mHere){
                        temp[nHere][mHere-2]=mHere<tempTrueN?(tempTrueN-1-mHere*a)*temp[nHere-1][mHere-2]:0;
                        temp[nHere][mHere-2]+=1.0;
                        temp[nHere][mHere-2]/=1.0/temp[nHere-1][mHere-3]+(tempTrueN-1-(mHere-1)*a);
                    }
                }
                //new line is based on last line of temp
                int tempTrueN = trueN+interval;
                S_V[N-2][0] = (1.0+(tempTrueN-1-2*a)*temp[interval-1][0])/(tempTrueN-1-a);
                for(int mHere = 3;mHere < temp[0].length;++mHere){
                    S_V[N-2][mHere-2] = mHere<tempTrueN?(tempTrueN-1-mHere*a)*temp[interval-1][mHere-2]:0;
                    S_V[N-2][mHere-2]+=1;
                    S_V[N-2][mHere-2]/=1.0/temp[interval-1][mHere-3]+(tempTrueN-1-(mHere-1)*a);
                }
            }
        }
        this.usedN = usedN;
        this.usedM = usedM;
    }


    public double S_V(int n, int m,int nind){
        if(m>n) return 0;
        if(m==1) return n;
        if(m<=usedM&&nind<=usedN){
            return S_V[nind-2][m-2];
        }else{
            if(m<=maxM&&nind<=maxN){
                fillTable(usedN,usedM,nind,m);
                return S_V[nind-2][m-2];
            }else{
                System.out.println("StirlingTableLarge: maximum table value reached!!! m:"+m+" n: "+n);
                return -1;
            }
        }
    }



    public double stirling_UV(int N,int M){
        int prunedM = M;
        if(M>maxM) prunedM = maxM;
        if(N>=startSparse){
            int c = (N-startSparse)/interval;
            int ind = c+startSparse;
            if((c*interval+startSparse)<prunedM)ind+=1;
            double sv;
            if(prunedM==1) return Double.NEGATIVE_INFINITY;
            if(prunedM==N+1) return 1;
            sv = S_V(N,prunedM,ind);
            if(sv==0){
                System.out.println("StirlingTables UV: sv is zero "+N +" "+prunedM+" "+ind+" "+usedN+" "+usedM+" "+S_V[ind-2][M-2]+" "+M);
                if(S_V[ind-2][prunedM-2]==0){
                    System.out.println(Arrays.toString(S_V[ind-2]));
                }
                //throw new java.lang.Exception();
            }
            return (N-prunedM*a)*sv+1.0;
        }
        return super.stirling_UV(N,prunedM);
    }


    public double stirling_U(int N,int M){
        int prunedM = M;
        if(M>maxM) prunedM = maxM;
        if(N>=startSparse){
            int c = (N-startSparse)/interval;
            int ind = c+startSparse;
            if((c*interval+startSparse)<prunedM)ind+=1;
            if(prunedM==1) return N-a;
            if(prunedM<=1) return -1;
            double sv = S_V(N,prunedM,ind);
            if(sv==0){
                System.out.println("StirlingTables U: sv is zero "+N+" "+prunedM+" "+ind+" "+usedN+" "+usedM+" "+M);
                //throw new java.lang.Exception();
            }
            return N-prunedM*a+1.0/sv;
        }
        return super.stirling_U(N,prunedM);
    }


}
