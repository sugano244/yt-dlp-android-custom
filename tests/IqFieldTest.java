package jp.nx809j.sdrlab;
import java.util.Random;
public final class IqFieldTest {
    public static void main(String[] args){
        byte[] iq=IqFieldAnalyzer.syntheticDemo();
        IqFieldAnalyzer.Result detection=IqFieldAnalyzer.analyze(iq,IqFieldAnalyzer.Format.CU8);
        System.out.println(detection.report());
        if(detection.confidence<0.85)throw new AssertionError("Missing CP on synthetic recording");
        if(detection.cpLength!=1024)throw new AssertionError("Wrong GI on synthetic recording: "+detection.cpLength);
        byte[] random=new byte[iq.length];
        new Random(4432).nextBytes(random);
        IqFieldAnalyzer.Result noise=IqFieldAnalyzer.analyze(random,IqFieldAnalyzer.Format.CU8);
        System.out.println("Noise correlation: "+noise.confidence);
        if(noise.confidence>0.40)throw new AssertionError("False positive on random noise");
        try {
            IqFieldAnalyzer.analyze(new byte[32],IqFieldAnalyzer.Format.CU8);
            throw new AssertionError("Short input was accepted");
        }catch(IllegalArgumentException expected){System.out.println("Short capture rejected");}
        System.out.println("PASS");
    }
}
