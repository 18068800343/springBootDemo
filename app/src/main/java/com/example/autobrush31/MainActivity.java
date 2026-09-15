package com.example.autobrush31;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView state;
    private final Handler handler=new Handler();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(35,45,35,35);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title=new TextView(this); title.setText("自动刷图31"); title.setTextSize(29); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,90));
        state=new TextView(this); state.setText("检查无障碍服务…"); state.setTextSize(17); state.setGravity(Gravity.CENTER);
        root.addView(state,new LinearLayout.LayoutParams(-1,80));

        Button access=new Button(this); access.setText("① 开启无障碍");
        access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(access,new LinearLayout.LayoutParams(-1,65));

        Button open=new Button(this); open.setText("② 打开游戏");
        open.setOnClickListener(v->AutoConfigLauncher.launch(this));
        root.addView(open,new LinearLayout.LayoutParams(-1,65));

        Button start=new Button(this); start.setText("③ 开始自动刷图");
        start.setOnClickListener(v->{
            AutoBrushAccessibilityService s=AutoBrushAccessibilityService.instance;
            if(s==null){ state.setText("❌ 无障碍服务没有连接，请先开启后返回本页面"); return; }
            boolean ok=s.startBrush();
            state.setText(ok ? "▶ 已启动：正在点击源初秘境…" : "❌ 启动失败："+AutoBrushAccessibilityService.status);
        });
        root.addView(start,new LinearLayout.LayoutParams(-1,70));

        Button stop=new Button(this); stop.setText("停止");
        stop.setOnClickListener(v->{
            if(AutoBrushAccessibilityService.instance!=null) AutoBrushAccessibilityService.instance.stopBrush();
            state.setText("已停止");
        });
        root.addView(stop,new LinearLayout.LayoutParams(-1,65));

        TextView help=new TextView(this);
        help.setText("\n流程：源初秘境 → 地狱31 → 进入 → 小地图自动探索\n\n先开启无障碍，再打开游戏，最后点击开始。\n运行状态会自动刷新；首次建议只测试一局。");
        help.setTextSize(15); root.addView(help);
        setContentView(root);
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
