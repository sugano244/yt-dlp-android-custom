package jp.nx809j.sdrlab;
public final class OfdmTest {
    public static void main(String[] args){
        VirtualOfdm.Result clear=VirtualOfdm.run(3,35,20261019L);
        VirtualOfdm.Result noisy=VirtualOfdm.run(3,0,20261019L);
        System.out.println("SNR 35: "+clear);
        System.out.println("SNR 0: "+noisy);
        if(clear.errors!=0||noisy.errors<100)throw new AssertionError("Unexpected synthetic BER");
        System.out.println("PASS: synthetic signal FFT/IFFT and BER response");
    }
}
