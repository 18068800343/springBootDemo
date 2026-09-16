package com.example.autobrush31;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView state;
    private final Handler handler=new Handler();

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams buttonParams(int heightDp){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(heightDp));
        p.setMargins(0,dp(7),0,dp(7));
        return p;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245,245,245));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(22),dp(22),dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245,245,245));

        TextView title=new TextView(this);
        title.setText("自动刷图31");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(true);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(68)));

        state=new TextView(this);
        state.setText("检查无障碍服务…");
        state.setTextSize(16);
        state.setGravity(Gravity.CENTER);
        state.setIncludeFontPadding(true);
        root.addView(state,new LinearLayout.LayoutParams(-1,dp(58)));

        Button access=new Button(this); access.setText("① 开启无障碍服务"); style(access,16); root.addView(access,buttonParams(52));
        access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        Button overlay=new Button(this); overlay.setText("② 开启悬浮寻路调试"); style(overlay,16); root.addView(overlay,buttonParams(52));
        overlay.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));}
            catch(Throwable t){startActivity(new Intent(Settings.ACTION_SETTINGS));}
        });

        Button open=new Button(this); open.setText("③ 打开《迷雾大陆》"); style(open,16); root.addView(open,buttonParams(52));
        open.setOnClickListener(v->AutoConfigLauncher.launch(this));

        Button start=new Button(this); start.setText("④ 开始自动刷图"); style(start,16); root.addView(start,buttonParams(56));
        start.setOnClickListener(v->{
            AutoBrushAccessibilityService s=AutoBrushAccessibilityService.instance;
            if(s==null){ state.setText("❌ 无障碍服务没有连接，请先开启后返回本页面"); return; }
            AutoConfigLauncher.launch(this);
            state.setText("▶ 正在打开《迷雾大陆》…");
            handler.postDelayed(()->{
                AutoBrushAccessibilityService current=AutoBrushAccessibilityService.instance;
                if(current!=null){
                    boolean ok=current.startBrush();
                    if(!ok) state.setText("❌ 启动失败："+AutoBrushAccessibilityService.status);
                }
                finish();
            },1800);
        });

        Button stop=new Button(this); stop.setText("停止自动刷图"); style(stop,16); root.addView(stop,buttonParams(52));
        stop.setOnClickListener(v->{
            if(AutoBrushAccessibilityService.instance!=null) AutoBrushAccessibilityService.instance.stopBrush();
            state.setText("已停止");
        });

        TextView help=new TextView(this);
        help.setText("操作流程\n开启无障碍服务 → 开启悬浮寻路调试 → 开始自动刷图\n自动打开《迷雾大陆》 → 源初秘境 → 地狱31 → 进入秘境 → 自动探索\n\n悬浮窗会实时显示实际小地图、人物位置、目标点、当前移动方向和识别置信度；可拖动窗口。\n\n如果不需要调试，可不授权悬浮窗，自动刷图仍可运行。\n");
        help.setTextSize(15);
        help.setIncludeFontPadding(true);
        help.setGravity(Gravity.LEFT);
        help.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.setMargins(0,dp(12),0,0);
        root.addView(help,hp);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void style(Button b,float size){
        b.setTextSize(size);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(true);
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
