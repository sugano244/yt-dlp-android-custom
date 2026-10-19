package jp.nx809j.sdrlab;

import java.util.Arrays;
import java.util.Locale;

/**
 * Fictional, register-level virtual ISDB-T frontend board for research.
 * This is not an emulation of any vendor IC, kernel tuner driver, or actual RF.
 * Input is synthetic or prerecorded baseband IQ; no antenna, PLL, ADC or radio.
 */
public final class VirtualFullsegBoard {
    public static final int REG_CHIP_ID = 0x0000;
    public static final int REG_VERSION = 0x0004;
    public static final int REG_CONTROL = 0x0008;
    public static final int REG_STATUS = 0x000C;
    public static final int REG_UHF_CHANNEL = 0x0010;
    public static final int REG_CENTER_KHZ = 0x0014;
    public static final int REG_SAMPLE_HZ = 0x0018;
    public static final int REG_FFT_SIZE = 0x001C;
    public static final int REG_CP_SAMPLES = 0x0020;
    public static final int REG_CP_CORRELATION = 0x0024;
    public static final int REG_CFO_PPM_SUBCARRIER = 0x0028;
    public static final int REG_BAND_CONTRAST_MDB = 0x002C;
    public static final int REG_SOURCE = 0x0030;
    public static final int REG_RF_PRESENT = 0x0034;
    public static final int REG_IRQ_MASK = 0x0038;
    public static final int REG_IRQ_STATUS = 0x003C;
    public static final int REG_TS_BYTES = 0x0040;
    public static final int REG_ERROR = 0x0044;

    public static final int CHIP_ID = 0x56534631; // "VSF1": virtual, not hardware
    public static final int CTRL_POWER = 1;
    public static final int CTRL_TUNE = 2;
    public static final int CTRL_RESET = 4;

    public static final int ST_POWERED = 1;
    public static final int ST_CONFIGURED = 2;
    public static final int ST_CP_DETECTED = 4;
    public static final int ST_TMCC_LOCK = 8; // never set in v0.5
    public static final int ST_TS_VALID = 16; // never set in v0.5
    public static final int ST_NO_RF = 32; // physical radio always absent

    public static final int IRQ_TUNE = 1;
    public static final int IRQ_CP = 2;
    public static final int IRQ_ERROR = 4;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_INPUT = 1;
    public static final int ERROR_NO_CP = 2;
    public static final int ERROR_BAD_IQ = 3;
    public static final int ERROR_BAD_CONFIG = 4;

    private static final int[] REGISTER_ADDRESSES = {
        REG_CHIP_ID,REG_VERSION,REG_CONTROL,REG_STATUS,
        REG_UHF_CHANNEL,REG_CENTER_KHZ,REG_SAMPLE_HZ,REG_FFT_SIZE,
        REG_CP_SAMPLES,REG_CP_CORRELATION,REG_CFO_PPM_SUBCARRIER,
        REG_BAND_CONTRAST_MDB,REG_SOURCE,REG_RF_PRESENT,REG_IRQ_MASK,
        REG_IRQ_STATUS,REG_TS_BYTES,REG_ERROR
    };
    private static final String[] REGISTER_NAMES = {
        "CHIP_ID","VERSION","CONTROL","STATUS",
        "UHF_CHANNEL","CENTER_KHZ","SAMPLE_HZ","FFT_SIZE",
        "CP_SAMPLES","CP_CORR_x1e6","CFO_x1e6","BAND_CONTRAST_mDb",
        "SOURCE","RF_PRESENT","IRQ_MASK","IRQ_STATUS","TS_BYTES","ERROR"
    };

    public enum Origin { NONE, SYNTHETIC, RECORDED }
    private int channel = 27;
    private boolean powered = false, configured = false, cpDetected = false;
    private int control = 0, irqMask = 7, irqStatus = 0, error = ERROR_NONE;
    private byte[] input;
    private IqFieldAnalyzer.Format iqFormat;
    private Origin origin = Origin.NONE;
    private IqFieldAnalyzer.Result lastResult;
    private String note = "未選局。物理RF入力なし。";

    public synchronized void setInput(byte[] iq, IqFieldAnalyzer.Format format, Origin kind) {
        if (kind == null || format == null || iq == null || iq.length==0)
            throw new IllegalArgumentException("IQ source missing");
        if (kind == Origin.NONE) throw new IllegalArgumentException("unsupported source kind");
        if (iq.length > IqFieldAnalyzer.MAX_BYTES)
            throw new IllegalArgumentException("IQ data over capacity");
        input = Arrays.copyOf(iq,iq.length);
        iqFormat = format;origin = kind;
        invalidateAcquisition();
        note = "IQ入力登録: " + kind + ", " + iq.length + " bytes。物理RF受信ではありません。";
    }
    public synchronized void disconnectInput() {
        input=null;iqFormat=null;origin=Origin.NONE;invalidateAcquisition();
        note="入力を切断しました。";
    }
    private void invalidateAcquisition(){
        cpDetected=false;lastResult=null;configured=false;error=ERROR_NONE;
        irqStatus=0;
    }
    public synchronized int read(int address) {
        switch(address){
            case REG_CHIP_ID: return CHIP_ID;
            case REG_VERSION: return 0x00050000;
            case REG_CONTROL: return control;
            case REG_STATUS: return (powered?ST_POWERED:0) |
                (configured?ST_CONFIGURED:0) | (cpDetected?ST_CP_DETECTED:0) | ST_NO_RF;
            case REG_UHF_CHANNEL: return channel;
            case REG_CENTER_KHZ: return 473143+6000*(channel-13);
            case REG_SAMPLE_HZ: return 8126984; // assumed baseband sample rate
            case REG_FFT_SIZE: return 8192; // Mode 3 only
            case REG_CP_SAMPLES: return lastResult!=null?lastResult.cpLength:0;
            case REG_CP_CORRELATION: return lastResult!=null?
                safeInt(lastResult.confidence*1000000):0;
            case REG_CFO_PPM_SUBCARRIER: return lastResult!=null?
                safeInt(lastResult.fractionalCfo*1000000):0;
            case REG_BAND_CONTRAST_MDB: return lastResult!=null?
                safeInt(lastResult.bandContrastDb*1000):0;
            case REG_SOURCE: return origin.ordinal();
            case REG_RF_PRESENT: return 0; // always absent
            case REG_IRQ_MASK: return irqMask;
            case REG_IRQ_STATUS: return irqStatus & irqMask;
            case REG_TS_BYTES: return 0; // transport stream is not implemented
            case REG_ERROR: return error;
            default:throw new IllegalArgumentException(String.format(Locale.ROOT,
                "Unknown register: 0x%04X",address));
        }
    }
    private static int safeInt(double value){
        if(Double.isNaN(value)||Double.isInfinite(value)) return 0;
        return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,Math.round(value)));
    }
    public synchronized void write(int address,int value) {
        switch(address){
            case REG_CONTROL:
                if((value & CTRL_RESET)!=0) {
                    reset();
                    if((value & ~CTRL_RESET)==0)return;
                    value &= ~CTRL_RESET;
                }
                powered = (value & CTRL_POWER)!=0;
                control = powered?CTRL_POWER:0;
                if(!powered){
                    invalidateAcquisition();
                    note="電源OFF。物理RF受信機能なし。";
                }else if((value & CTRL_TUNE)!=0){
                    tune();
                }else{
                    note="仮想チップ電源ON。RF回路はエミュレーションのみ。";
                }
                return;
            case REG_UHF_CHANNEL:
                if(value<13||value>52) {
                    error=ERROR_BAD_CONFIG;irqStatus |= IRQ_ERROR;
                    throw new IllegalArgumentException("UHF physical channel must be 13..52");
                }
                channel=value;invalidateAcquisition();note="チャンネル設定: "+value+"（未選局）";
                return;
            case REG_IRQ_MASK:
                irqMask=value&7;return;
            case REG_IRQ_STATUS:
                irqStatus &= ~value;return; // W1C bits
            default:
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Read-only or unknown register: 0x%04X",address));
        }
    }
    private void reset(){
        channel=27;powered=false;configured=false;cpDetected=false;
        control=0;irqMask=7;irqStatus=0;error=ERROR_NONE;lastResult=null;
        note="ソフトウェアリセット完了。記録IQは再利用可能。";
    }
    private void tune() {
        configured = true;cpDetected=false;lastResult=null;error=ERROR_NONE;
        irqStatus |= IRQ_TUNE;
        if(input==null){
            error=ERROR_NO_INPUT;irqStatus |= IRQ_ERROR;
            note="仮想選局完了。ただし実電波もIQ入力もなく、OFDM同期不可。";
            return;
        }
        try{
            lastResult=IqFieldAnalyzer.analyze(input,iqFormat);
            cpDetected=lastResult.confidence>=0.80 && lastResult.bandContrastDb>=2.0;
            if(cpDetected)irqStatus |= IRQ_CP;
            else {error=ERROR_NO_CP;irqStatus |= IRQ_ERROR;}
            note=cpDetected
                ? "記録/合成IQのOFDM CP同期候補を検出。TMCC/TSは未復号、実放送受信なし。"
                : "入力IQからOFDM CP同期候補を確認できません。";
        }catch(RuntimeException exception){
            error=ERROR_BAD_IQ;irqStatus |= IRQ_ERROR;
            note="IQ解析失敗: "+exception.getClass().getSimpleName()+": "+exception.getMessage();
        }
    }
    public synchronized String report() {
        StringBuilder b=new StringBuilder();
        b.append("=== Virtual Full-Seg Board v0.5 ===\n")
         .append("Custom software register model, NOT commercial tuner silicon.\n")
         .append("RF_PRESENT=0 always. No antenna, ADC, tuner IC or actual PLL.\n")
         .append("Channel center is informational; no analog RF retuning.\n")
         .append("Physical CH=").append(channel).append(" center=").append(read(REG_CENTER_KHZ))
         .append(" kHz; nominal IQ=8126984 samples/s.\n")
         .append("Input=").append(origin).append(", powered=").append(powered)
         .append(", configured=").append(configured).append(", CP candidate=").append(cpDetected).append("\n")
         .append(note).append("\n")
         .append("TMCC_LOCK=0; TS_VALID=0; TS_BYTES=0 (NOT IMPLEMENTED).\n")
         .append("Register bus: software memory mapping, no physical I2C/USB.\n\n");
        for(int i=0;i<REGISTER_ADDRESSES.length;i++)
            b.append(String.format(Locale.ROOT,"0x%04X %-21s 0x%08X (%d)\n",
                REGISTER_ADDRESSES[i],REGISTER_NAMES[i],read(REGISTER_ADDRESSES[i]),
                read(REGISTER_ADDRESSES[i])));
        if(lastResult!=null)b.append("\n").append(lastResult.report());
        return b.toString();
    }
}
