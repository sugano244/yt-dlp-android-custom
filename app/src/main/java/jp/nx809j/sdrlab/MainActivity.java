package jp.nx809j.sdrlab;
import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import java.io.InputStream;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView output;
    private Spinner snrSelect;
    private Button test, probe;
    private Spinner iqFormat;
    private static final int PICK_IQ=4101;
    private String last="";
    private int dp(int d){return Math.round(d*getResources().getDisplayMetrics().density);}
    private TextView label(LinearLayout root,String message,int size){
        TextView t=new TextView(this);t.setText(message);t.setTextSize(size);t.setTextColor(Color.rgb(30,54,70));
        t.setPadding(0,dp(8),0,dp(8));root.addView(t);return t;
    }
    private Button button(LinearLayout root,String name,View.OnClickListener f){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);root.addView(b);b.setOnClickListener(f);return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(32),dp(18),dp(30));
        scroll.setFillViewport(true);scroll.addView(content);setContentView(scroll);
        label(content,"NX809J IQ Field Lab v0.4",24);
        label(content,"記録したIQ信号のCP/FFTを解析します。実放送の受信・映像再生は未対応です。",15);
        label(content,"仮想受信SNR",16);
        snrSelect=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"35 dB","20 dB","10 dB","5 dB","0 dB"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        snrSelect.setAdapter(adapter);content.addView(snrSelect);
        test=button(content,"仮想OFDM復調テスト",v->runTest());
        probe=button(content,"APKだけで受信できるか診断",v->runProbe());
        label(content,"録音済みIQ信号を解析（ISDB-T Mode 3を想定）",17);
        label(content,"8.126984 MS/s / 中心周波数は対象チャンネル / サンプルレート変換機能なし",12);
        iqFormat=new Spinner(this);
        ArrayAdapter<String> iqAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            new String[]{"CU8 (I/Q unsigned 8-bit)","CS16LE (signed I/Q 16-bit)","CF32LE (float32 I/Q)"});
        iqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        iqFormat.setAdapter(iqAdapter);content.addView(iqFormat);
        button(content,"IQファイルを選択して解析",v->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("*/*");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(pick,PICK_IQ);
        });
        button(content,"内蔵テストIQを解析",v->launch(()->"SYNTHETIC / NOT A TV BROADCAST\\n"+
            IqFieldAnalyzer.analyze(IqFieldAnalyzer.syntheticDemo(),IqFieldAnalyzer.Format.CU8).report()));
        output=label(content,"テストまたは診断を選択してください。",13);output.setTextIsSelectable(true);
        button(content,"ログをコピー",v->{
            if(last.isEmpty()){Toast.makeText(this,"ログがありません",Toast.LENGTH_SHORT).show();return;}
            ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("SDR Lab",last));
            Toast.makeText(this,"コピーしました",Toast.LENGTH_SHORT).show();
        });
        button(content,"ログを共有",v->{
            if(last.isEmpty())return;
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,last);startActivity(Intent.createChooser(send,"診断ログを共有"));
        });
        label(content,"未実装: 実RF入力、キャリア/シンボル同期の追従、TMCC、パイロット等化、誤り訂正、MPEG-TS、映像・音声。",12);
    }
    private void publish(String s){runOnUiThread(()->{
        last=s;output.setText(s);test.setEnabled(true);probe.setEnabled(true);
    });}
    private void launch(java.util.concurrent.Callable<String> job){
        test.setEnabled(false);probe.setEnabled(false);output.setText("処理中...");
        worker.execute(()->{
            try{publish(job.call());}
            catch(Exception e){publish("診断失敗: "+e.toString());}
        });
    }
    private void runTest(){
        double[] values={35,20,10,5,0};
        double snr=values[snrSelect.getSelectedItemPosition()];
        launch(()->"SNR: "+snr+" dB\n"+VirtualOfdm.run(3,snr,20261019L));
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_IQ||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        final IqFieldAnalyzer.Format format=IqFieldAnalyzer.Format.values()[iqFormat.getSelectedItemPosition()];
        launch(()->{
            byte[] bytes;
            try(InputStream stream=getContentResolver().openInputStream(uri)){
                bytes=IqFieldAnalyzer.readUpTo(stream);
            }
            return "Local file URI selected via Android document picker.\\n"+
                "Imported bytes: "+bytes.length+" (max "+IqFieldAnalyzer.MAX_BYTES+")\\n"+
                IqFieldAnalyzer.analyze(bytes,format).report();
        });
    }
    private void runProbe(){launch(()->RfReadiness.inspect(this)+"\\n"+Diagnostics.read(this));}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
