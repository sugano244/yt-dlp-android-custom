package jp.nx809j.sdrlab;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import java.io.File;

public final class Diagnostics {
    private Diagnostics(){}
    public static String read(Context context){
        StringBuilder out=new StringBuilder("NX809J SDR Lab device diagnostic (read-only)\n");
        out.append("Model: ").append(Build.MODEL).append("\n");
        out.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
        out.append("Device: ").append(Build.DEVICE).append("\n");
        out.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("\nPotential TV-related Android features:\n");
        int found=0;
        FeatureInfo[] features=context.getPackageManager().getSystemAvailableFeatures();
        if(features!=null)for(FeatureInfo f:features){
            String n=f.name;if(n!=null&&(n.contains("tv")||n.contains("tuner")||n.contains("broadcast"))){out.append(n).append("\n");found++;}
        }
        if(found==0)out.append("(none exposed to this app)\n");
        out.append("\nExposed /dev entries containing dvb, tuner, radio:\n");
        try {
            String[] names=new File("/dev").list();
            int devices=0;
            if(names!=null)for(String n:names){
                if(n.contains("dvb")||n.contains("radio")||n.contains("tuner")){out.append(n).append("\n");devices++;}
            }
            if(devices==0)out.append("(none visible; sandboxing can hide devices)\n");
        }catch(Exception e){out.append("Not accessible: ").append(e).append("\n");}
        out.append("\nMediaCodec decoder capabilities (video/mpeg2, video/avc):\n");
        try{
            for(MediaCodecInfo c:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                if(c.isEncoder())continue;
                for(String type:c.getSupportedTypes()){
                    if(type.equalsIgnoreCase("video/mpeg2")||type.equalsIgnoreCase("video/avc"))out.append(c.getName()).append(": ").append(type).append("\n");
                }
            }
        }catch(Exception e){out.append("Codec enumeration failed: ").append(e).append("\n");}
        out.append("\nThis cannot reveal undocumented RF capabilities, and does not establish terrestrial reception.\n");
        return out.toString();
    }
}
