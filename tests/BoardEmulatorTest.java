package jp.nx809j.sdrlab;
import java.util.Random;

public final class BoardEmulatorTest {
    private static void check(boolean ok,String label){
        if(!ok)throw new AssertionError(label);
    }
    public static void main(String[] args) {
        VirtualFullsegBoard b = new VirtualFullsegBoard();
        check(b.read(VirtualFullsegBoard.REG_CHIP_ID)==VirtualFullsegBoard.CHIP_ID,"chip ID");
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_NO_RF)!=0,"no real RF");
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_TS_VALID)==0,"no fake TS");
        b.write(VirtualFullsegBoard.REG_UHF_CHANNEL,13);
        check(b.read(VirtualFullsegBoard.REG_CENTER_KHZ)==473143,"ch 13 center");
        b.write(VirtualFullsegBoard.REG_UHF_CHANNEL,52);
        check(b.read(VirtualFullsegBoard.REG_CENTER_KHZ)==707143,"ch 52 center");

        b.write(VirtualFullsegBoard.REG_CONTROL,VirtualFullsegBoard.CTRL_POWER|VirtualFullsegBoard.CTRL_TUNE);
        check(b.read(VirtualFullsegBoard.REG_ERROR)==VirtualFullsegBoard.ERROR_NO_INPUT,"missing input");
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_CP_DETECTED)==0,"no input shouldn't lock");
        check((b.read(VirtualFullsegBoard.REG_IRQ_STATUS)&VirtualFullsegBoard.IRQ_ERROR)!=0,"error irq");
        b.write(VirtualFullsegBoard.REG_IRQ_STATUS,VirtualFullsegBoard.IRQ_ERROR);
        check((b.read(VirtualFullsegBoard.REG_IRQ_STATUS)&VirtualFullsegBoard.IRQ_ERROR)==0,"W1C IRQ");

        byte[] synthetic=IqFieldAnalyzer.syntheticDemo();
        b.setInput(synthetic,IqFieldAnalyzer.Format.CU8,VirtualFullsegBoard.Origin.SYNTHETIC);
        b.write(VirtualFullsegBoard.REG_CONTROL,VirtualFullsegBoard.CTRL_POWER|VirtualFullsegBoard.CTRL_TUNE);
        System.out.println(b.report());
        check(b.read(VirtualFullsegBoard.REG_ERROR)==VirtualFullsegBoard.ERROR_NONE,"synthetic CP acquired");
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_CP_DETECTED)!=0,"CP sync detected");
        check(b.read(VirtualFullsegBoard.REG_CP_SAMPLES)==1024,"CP duration detected");
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_TMCC_LOCK)==0,"no TMCC fabricated");
        check(b.read(VirtualFullsegBoard.REG_TS_BYTES)==0,"no TS fabricated");
        check(b.read(VirtualFullsegBoard.REG_RF_PRESENT)==0,"no fake radio");

        b.write(VirtualFullsegBoard.REG_IRQ_MASK,VirtualFullsegBoard.IRQ_ERROR);
        check(b.read(VirtualFullsegBoard.REG_IRQ_STATUS)==0,"masked IRQ");
        b.write(VirtualFullsegBoard.REG_IRQ_MASK,7);

        b.setInput(new byte[32],IqFieldAnalyzer.Format.CU8,VirtualFullsegBoard.Origin.RECORDED);
        b.write(VirtualFullsegBoard.REG_CONTROL,VirtualFullsegBoard.CTRL_POWER|VirtualFullsegBoard.CTRL_TUNE);
        check(b.read(VirtualFullsegBoard.REG_ERROR)==VirtualFullsegBoard.ERROR_BAD_IQ,"bad capture");

        byte[] noise = new byte[synthetic.length];
        new Random(1229).nextBytes(noise);
        b.setInput(noise,IqFieldAnalyzer.Format.CU8,VirtualFullsegBoard.Origin.RECORDED);
        b.write(VirtualFullsegBoard.REG_CONTROL,VirtualFullsegBoard.CTRL_POWER|VirtualFullsegBoard.CTRL_TUNE);
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_CP_DETECTED)==0,"noise false lock");

        boolean denied=false;
        try{b.write(VirtualFullsegBoard.REG_RF_PRESENT,1);}
        catch(IllegalArgumentException e){denied=true;}
        check(denied,"RF PRESENT immutable");

        b.write(VirtualFullsegBoard.REG_CONTROL,VirtualFullsegBoard.CTRL_RESET);
        check((b.read(VirtualFullsegBoard.REG_STATUS)&VirtualFullsegBoard.ST_POWERED)==0,"reset power");
        check(b.read(VirtualFullsegBoard.REG_UHF_CHANNEL)==27,"reset channel");
        System.out.println("PASS: virtual RF/Tuner register bus, IRQ, tuning, CP, NO_RF invariants.");
    }
}
