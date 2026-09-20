package cn.quietstart.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;

/** Lives only in the test APK. A real separate-package UI for accessibility checks. */
public final class AdFixtureActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private TextView status;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        root=new FrameLayout(this);setContentView(root);
        status=new TextView(this);status.setText("WAITING");status.setTextSize(24);
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);sp.bottomMargin=dp(80);root.addView(status,sp);
        String mode=getIntent().getStringExtra("mode");if(mode==null)mode="explicit";
        final String selected=mode;
        if("burst".equals(mode))handler.postDelayed(()->show(selected),100);else show(mode);
    }
    private void show(String mode){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(280),dp(220),Gravity.CENTER);root.addView(box,bp);
        if("sibling".equals(mode)){TextView marker=new TextView(this);marker.setText("广告");box.addView(marker);}
        Button button=new Button(this);button.setText("ambiguous".equals(mode)?"关闭":"sibling".equals(mode)?"×":"关闭广告");
        button.setOnClickListener(v->{status.setText("CLICKED:"+mode);root.removeView(box);});
        box.addView(button,new LinearLayout.LayoutParams(dp(110),dp(48)));
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
