package com.example.autobrush31;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView state;
    private final Handler handler=new Handler();

    private LinearLayout.LayoutParams buttonParams(int height){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height);
        p.setMargins(0,10,0,10);
        return p;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,32,32,32);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title=new TextView(this);
        title.setText("自动刷图31");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(true);
        root.addView(title,new LinearLayout.LayoutParams(-1,80));

        state=new TextView(this);
        state.setText("检查无障碍服务…");
        state.setTextSize(16);
        state.setGravity(Gravity.CENTER);
        state.setIncludeFontPadding(true);
        root.addView(state,new LinearLayout.LayoutParams(-1,65));

        Button access=new Button(this);
        access.setText("① 开启无障碍服务");
        access.setTextSize(16);
        access.setSingleLine(true);
        access.setGravity(Gravity.CENTER);
        root.addView(access,buttonParams(64));
        access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button open=new Button(this);
        open.setText("② 打开《迷雾大陆》");
        open.setTextSize(16);
        open.setSingleLine(true);
        open.setGravity(Gravity.CENTER);
        root.addView(open,buttonParams(64));
        open.setOnClickListener(v->AutoConfigLauncher.launch(this));

        Button start=new Button(this);
        start.setText("③ 开始自动刷图");
        start.setTextSize(16);
        start.setSingleLine(true);
        start.setGravity(Gravity.CENTER);
        root.addView(start,buttonParams(68));
        start.setOnClickListener(v->{
            AutoBrushAccessibilityService s=AutoBrushAccessibilityService.instance;
            if(s==null){ state.setText("❌ 无障碍服务没有连接，请先开启后返回本页面"); return; }
            AutoConfigLauncher.launch(this);
            state.setText("▶ 正在切换到游戏…");
            handler.postDelayed(()->{
                AutoBrushAccessibilityService current=AutoBrushAccessibilityService.instance;
                if(current!=null){
                    boolean ok=current.startBrush();
                    if(!ok) state.setText("❌ 启动失败："+AutoBrushAccessibilityService.status);
                }
                finish();
            },2000);
        });

        Button stop=new Button(this);
        stop.setText("停止自动刷图");
        stop.setTextSize(16);
        stop.setSingleLine(true);
        stop.setGravity(Gravity.CENTER);
        root.addView(stop,buttonParams(64));
        stop.setOnClickListener(v->{
            if(AutoBrushAccessibilityService.instance!=null) AutoBrushAccessibilityService.instance.stopBrush();
            state.setText("已停止");
        });

        TextView help=new TextView(this);
        help.setText("\n操作流程\n开启无障碍服务 → 开始自动刷图\n自动打开《迷雾大陆》→ 源初秘境 → 地狱31 → 进入秘境 → 自动探索\n\n提示：按钮之间留有间距，避免不同手机字体缩放时文字重叠。");
        help.setTextSize(15);
        help.setIncludeFontPadding(true);
        help.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.setMargins(0,16,0,0);
        root.addView(help,hp);

        scroll.addView(root);
        setContentView(scroll);
    }

    @Override protected void onResume(){ super.onResume(); refreshStatus(); }
    private void refreshStatus(){
        handler.postDelayed(new Runnable(){ public void run(){
            AutoBrushAccessibilityService s=AutoBrushAccessibilityService.instance;
            if(s!=null) state.setText(s.isRunning()?"▶ "+AutoBrushAccessibilityService.status:"✓ "+AutoBrushAccessibilityService.status);
            else state.setText("❌ 无障碍服务未连接");
            if(!isFinishing()) handler.postDelayed(this,500);
        }},100);
    }
}
