package jp.nx809j.sdrlab;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Educational synthetic OFDM loopback; NOT a real ISDB-T full-seg receiver. */
public final class VirtualOfdm {
    static final int N = 8192, SEGMENTS = 13, PER_SEGMENT = 432, ACTIVE = SEGMENTS * PER_SEGMENT, CP = N / 8;
    private VirtualOfdm() {}
    public static final class Result {
        public final long bits, errors, millis;
        public final double evm;
        Result(long b,long e,long m,double v){bits=b;errors=e;millis=m;evm=v;}
        public String toString() {
            return String.format(Locale.ROOT,
                "13-segment SYNTHETIC OFDM loopback (NOT broadcast)\nFFT: %d, CP: %d, QPSK carriers: %d\nErrors: %d/%d, BER: %.8f\nEVM: %.3f%%, time: %d ms\nNo real RF, TMCC, FEC, MPEG-TS, video or broadcast decoding.",
                N,CP,ACTIVE,errors,bits,bits==0?0:(double)errors/bits,evm,millis);
        }
    }
    public static Result run(int symbols,double snrDb,long seed) {
        if(symbols<1||symbols>30||snrDb< -20||snrDb>90) throw new IllegalArgumentException("test configuration");
        long start=System.nanoTime(); Random rng=new Random(seed);
        double[] r=new double[N],im=new double[N],txr=new double[N+CP],txi=new double[N+CP];
        byte[] br=new byte[ACTIVE],bi=new byte[ACTIVE];
        double unit=1.0/Math.sqrt(2.0), errEnergy=0, idealEnergy=0; long bitErrors=0;
        for(int frame=0;frame<symbols;frame++){
            Arrays.fill(r,0);Arrays.fill(im,0);
            for(int k=0;k<ACTIVE;k++){
                br[k]=(byte)rng.nextInt(2);bi[k]=(byte)rng.nextInt(2);
                int index=carrier(k);r[index]=br[k]==0?unit:-unit;im[index]=bi[k]==0?unit:-unit;
            }
            fft(r,im,true);
            for(int j=0;j<CP;j++){txr[j]=r[N-CP+j];txi[j]=im[N-CP+j];}
            for(int j=0;j<N;j++){txr[CP+j]=r[j];txi[CP+j]=im[j];}
            double power=0;
            for(int j=0;j<txr.length;j++) power+=txr[j]*txr[j]+txi[j]*txi[j];
            double sigma=Math.sqrt((power/txr.length)/(2*Math.pow(10,snrDb/10)));
            for(int j=0;j<N;j++){
                r[j]=txr[CP+j]+rng.nextGaussian()*sigma;
                im[j]=txi[CP+j]+rng.nextGaussian()*sigma;
            }
            fft(r,im,false);
            for(int k=0;k<ACTIVE;k++){
                int index=carrier(k);
                boolean b0=r[index]>=0,b1=im[index]>=0;
                if(b0!=(br[k]==0))bitErrors++;
                if(b1!=(bi[k]==0))bitErrors++;
                double dr=(br[k]==0?unit:-unit)-r[index],di=(bi[k]==0?unit:-unit)-im[index];
                errEnergy+=dr*dr+di*di;idealEnergy+=1;
            }
        }
        return new Result((long)symbols*ACTIVE*2,bitErrors,(System.nanoTime()-start)/1_000_000L,100*Math.sqrt(errEnergy/idealEnergy));
    }
    private static int carrier(int k){
        int c=k-ACTIVE/2;if(c>=0)c++;
        return c>=0?c:N+c;
    }
    static void fft(double[] a,double[] b,boolean inverse) {
        for(int i=1,j=0;i<N;i++){
            int bit=N>>>1;
            while((j&bit)!=0){j^=bit;bit>>>=1;}
            j^=bit;
            if(i<j){double t=a[i];a[i]=a[j];a[j]=t;t=b[i];b[i]=b[j];b[j]=t;}
        }
        for(int len=2;len<=N;len<<=1){
            double angle=(inverse?2:-2)*Math.PI/len;
            double wrBase=Math.cos(angle),wiBase=Math.sin(angle);
            for(int first=0;first<N;first+=len){
                double wr=1,wi=0;
                for(int j=0;j<len/2;j++){
                    int p=first+j,q=p+len/2;
                    double ar=a[q]*wr-b[q]*wi,ai=a[q]*wi+b[q]*wr;
                    a[q]=a[p]-ar;b[q]=b[p]-ai;a[p]+=ar;b[p]+=ai;
                    double next=wr*wrBase-wi*wiBase;wi=wr*wiBase+wi*wrBase;wr=next;
                }
            }
        }
        if(inverse)for(int i=0;i<N;i++){a[i]/=N;b[i]/=N;}
    }
}
