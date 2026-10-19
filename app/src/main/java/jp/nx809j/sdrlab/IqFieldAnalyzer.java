package jp.nx809j.sdrlab;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Offline, standard-PARAMETER-based Mode-3 OFDM field analyzer.
 * Does NOT decode TMCC, pilot cells, FEC, TS, or any video. Not a receiver.
 * No RF access. Input is local IQ file via Android system file picker.
 */
public final class IqFieldAnalyzer {
    public static final int FFT_SIZE = 8192;
    public static final int SAMPLES = 65536;
    public static final int MAX_BYTES = SAMPLES * 8;
    private static final int ACTIVE = 5617; // approximate 13-segment Mode 3 occupied span
    private IqFieldAnalyzer() {}

    public enum Format { CU8, CS16LE, CF32LE }

    public static final class Result {
        public final int sampleCount, cpLength, start;
        public final double confidence, fractionalCfo, bandContrastDb;
        public final String format;
        Result(int count,int cp,int start,double confidence,double cfo,double contrast,String format) {
            this.sampleCount=count;this.cpLength=cp;this.start=start;
            this.confidence=confidence;this.fractionalCfo=cfo;this.bandContrastDb=contrast;this.format=format;
        }
        public boolean likelyOfdm() { return confidence >= 0.55 && bandContrastDb >= 1.0; }
        public String report(){
            return String.format(Locale.ROOT,
                "=== Recorded IQ / Mode-3 FFT field analysis ===\n"+
                "Source: local IQ file, format=%s; samples=%d\n"+
                "Assumed sample rate: 8,126,984.127 samples/s (NOT verified)\n"+
                "FFT=%d; active occupied span ~%d bins\n"+
                "Candidate CP=%d samples (ratio 1/%d)\n"+
                "Candidate symbol start=%d samples\n"+
                "CP normalized correlation=%.5f\n"+
                "Fractional CFO=%.5f subcarrier spacings (ambiguous outside +/-0.5)\n"+
                "Central-band contrast vs edge=%.2f dB\n"+
                "CP + bandwidth signature: %s\n"+
                "A signature is NOT proof of ISDB-T; other OFDM formats can look similar.\n"+
                "No TMCC, pilot equalization, FEC, MPEG-TS, descrambling, audio, or video.\n"+
                "Only prerecorded or synthetic input; no live RF capture.\n",
                format,sampleCount,FFT_SIZE,ACTIVE,cpLength,FFT_SIZE/cpLength,
                start,confidence,fractionalCfo,bandContrastDb,likelyOfdm()?"POSSIBLE OFDM":"INCONCLUSIVE");
        }
    }

    public static byte[] readUpTo(InputStream input) throws IOException {
        if(input==null) throw new IOException("input stream unavailable");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(131072);
        byte[] block = new byte[8192];
        int n;
        while(buffer.size() < MAX_BYTES && (n=input.read(block,0,Math.min(block.length,MAX_BYTES-buffer.size())))!=-1){
            if(n>0)buffer.write(block,0,n);
        }
        return buffer.toByteArray();
    }
    private static double[] decode(byte[] bytes,Format format,boolean imag){
        int bpc = format==Format.CU8?2:format==Format.CS16LE?4:8;
        int count = Math.min(SAMPLES, bytes.length/bpc);
        if(count < 3*(FFT_SIZE+256)) throw new IllegalArgumentException("IQ file too short: need 3+ OFDM symbols");
        double[] data=new double[count];
        for(int i=0;i<count;i++){
            int at=i*bpc+(imag?bpc/2:0);
            if(format==Format.CU8)data[i]=((bytes[at]&255)-127.5)/128.0;
            else if(format==Format.CS16LE){
                int v=(bytes[at]&255)|((bytes[at+1]&255)<<8);
                data[i]=(short)v/32768.0;
            }else{
                int bits=(bytes[at]&255)|((bytes[at+1]&255)<<8)|
                    ((bytes[at+2]&255)<<16)|((bytes[at+3]&255)<<24);
                float f=Float.intBitsToFloat(bits);
                if(!Float.isFinite(f))throw new IllegalArgumentException("non-finite IQ sample");
                data[i]=f;
            }
        }
        return data;
    }

    public static Result analyze(byte[] bytes,Format format){
        final double[] real=decode(bytes,format,false),imag=decode(bytes,format,true);
        int n=real.length;
        double best=-1,bestR=0,bestI=0;
        int bestPos=0,bestGuard=0;
        for(int guard:new int[]{256,512,1024,2048}){
            int scan=Math.min(FFT_SIZE+guard,n-FFT_SIZE-guard);
            if(scan<=1)continue;
            double cr=0,ci=0,e1=0,e2=0;
            for(int j=0;j<guard;j++){
                int k=j+FFT_SIZE;
                cr+=real[j]*real[k]+imag[j]*imag[k];
                ci+=real[j]*imag[k]-imag[j]*real[k];
                e1+=real[j]*real[j]+imag[j]*imag[j];
                e2+=real[k]*real[k]+imag[k]*imag[k];
            }
            for(int pos=0;pos<scan;pos++){
                double normalized=Math.hypot(cr,ci)/(Math.sqrt(e1*e2)+1e-20);
                double energy=e1+e2;
                // A true long guard also matches shorter subsets. Prefer the
                // longest candidate when confidence is effectively tied.
                if(energy>1e-6 && (normalized>best+0.002 ||
                    (normalized>=best-0.002 && guard>bestGuard))){
                    best=normalized;bestPos=pos;bestGuard=guard;bestR=cr;bestI=ci;
                }
                if(pos+1>=scan)break;
                int old=pos,add=pos+guard,oldLag=old+FFT_SIZE,newLag=add+FFT_SIZE;
                cr+=real[add]*real[newLag]+imag[add]*imag[newLag]-
                    real[old]*real[oldLag]-imag[old]*imag[oldLag];
                ci+=real[add]*imag[newLag]-imag[add]*real[newLag]-
                    real[old]*imag[oldLag]+imag[old]*real[oldLag];
                e1+=real[add]*real[add]+imag[add]*imag[add]-
                    real[old]*real[old]-imag[old]*imag[old];
                e2+=real[newLag]*real[newLag]+imag[newLag]*imag[newLag]-
                    real[oldLag]*real[oldLag]-imag[oldLag]*imag[oldLag];
            }
        }
        if(bestGuard==0)throw new IllegalArgumentException("No symbols to analyze");
        int start=bestPos+bestGuard;
        double epsilon=Math.atan2(bestI,bestR)/(2*Math.PI);
        double[] xr=new double[FFT_SIZE],xi=new double[FFT_SIZE];
        for(int j=0;j<FFT_SIZE;j++){
            double phase=-2*Math.PI*epsilon*j/FFT_SIZE;
            double cs=Math.cos(phase),sn=Math.sin(phase);
            double a=real[start+j],b=imag[start+j];
            xr[j]=a*cs-b*sn;xi[j]=a*sn+b*cs;
        }
        fft(xr,xi);
        double central=0,outer=0;
        int nCentral=0,nOuter=0;
        int half=ACTIVE/2;
        for(int i=0;i<FFT_SIZE;i++){
            int bin=i<=FFT_SIZE/2?i:i-FFT_SIZE;
            double power=xr[i]*xr[i]+xi[i]*xi[i];
            if(Math.abs(bin)<=half) { central+=power; nCentral++; }
            else {outer+=power;nOuter++;}
        }
        double contrast=10*Math.log10(((central+1e-20)/nCentral)/((outer+1e-20)/nOuter));
        return new Result(n,bestGuard,start,Math.max(0,best),epsilon,contrast,format.name());
    }

    private static void fft(double[] real,double[] imag){
        int n=real.length;
        for(int i=1,j=0;i<n;i++){
            int bit=n>>1;
            while((j&bit)!=0){j^=bit;bit>>=1;}
            j^=bit;
            if(i<j){
                double t=real[i];real[i]=real[j];real[j]=t;
                t=imag[i];imag[i]=imag[j];imag[j]=t;
            }
        }
        for(int len=2;len<=n;len<<=1){
            double angle=-2*Math.PI/len;
            double wrBase=Math.cos(angle),wiBase=Math.sin(angle);
            for(int start=0;start<n;start+=len){
                double wr=1,wi=0;
                for(int j=0;j<len/2;j++){
                    int p=start+j,q=p+len/2;
                    double ar=real[q]*wr-imag[q]*wi,ai=real[q]*wi+imag[q]*wr;
                    real[q]=real[p]-ar;imag[q]=imag[p]-ai;
                    real[p]+=ar;imag[p]+=ai;
                    double next=wr*wrBase-wi*wiBase;
                    wi=wr*wiBase+wi*wrBase;wr=next;
                }
            }
        }
    }

    /** Deterministic 5-symbol, CP=1024, Mode-3-like synthetic IQ recording. */
    public static byte[] syntheticDemo(){
        int guard=1024,symbol=FFT_SIZE+guard,count=symbol*5;
        byte[] out=new byte[count*2];
        Random rng=new Random(198701);
        double amplitude=0.66;
        for(int frame=0;frame<5;frame++){
            double[] r=new double[FFT_SIZE],im=new double[FFT_SIZE];
            for(int k=-ACTIVE/2;k<=ACTIVE/2;k++){
                int b=k<0?k+FFT_SIZE:k;
                if(k==0)continue;
                r[b]=(rng.nextBoolean()?1:-1)/Math.sqrt(2);
                im[b]=(rng.nextBoolean()?1:-1)/Math.sqrt(2);
            }
            ifft(r,im);
            for(int p=0;p<symbol;p++){
                int b=p<guard? FFT_SIZE-guard+p : p-guard;
                double ar=r[b]*amplitude*85,ai=im[b]*amplitude*85;
                int index=(frame*symbol+p)*2;
                out[index]=(byte)(Math.max(0,Math.min(255,Math.round(ar+127.5))));
                out[index+1]=(byte)(Math.max(0,Math.min(255,Math.round(ai+127.5))));
            }
        }
        return out;
    }
    private static void ifft(double[] r,double[] i){
        // FFT of conjugated data, conjugate result, divide by N.
        for(int n=0;n<i.length;n++)i[n]=-i[n];
        fft(r,i);
        for(int n=0;n<i.length;n++){r[n]/=i.length;i[n]=-i[n]/i.length;}
        // FFT convention scales typical QPSK-OFDM time signal to ~1/sqrt(N).
        // Gain here can be adjusted in demo; this does not change the physical RF chain.
        double gain=Math.sqrt(FFT_SIZE);
        for(int n=0;n<i.length;n++){r[n]*=gain;i[n]*=gain;}
    }
}
