package com.example.autobrush31;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimap planner.
 * Three visual zones are intentionally kept separate:
 *  1) gray/white = confirmed road and the only walkable surface;
 *  2) near-black = unexplored fog, a possible exploration target;
 *  3) dark-gray = non-road terrain, NEVER a target and NEVER walkable.
 * Black/near-black components touching the viewport border are treated as
 * clipped map background rather than an exploration exit.
 */
public final class MapNavigator {
    private MapNavigator() {}
    public static final class Result {
        public boolean foundPlayer, roadDirection;
        public int dx,dy,playerX,playerY,targetX,targetY,pathLength,routeCells;
        public float confidence;
    }

    public static Result analyze(Bitmap s){ return analyze(s,0,0); }

    public static Result analyze(Bitmap s,int prevDx,int prevDy){
        Result o=new Result();
        if(s==null||s.isRecycled())return o;
        int w=s.getWidth(),h=s.getHeight();
        if(w<30||h<30)return o;

        int[] p=findPlayer(s,w,h);
        if(p[0]<0)return o;
        o.foundPlayer=true;
        o.playerX=p[0]; o.playerY=p[1];
        o.confidence=Math.min(1f,p[2]/50f);

        int cell=Math.max(2,Math.min(4,Math.min(w,h)/55));
        int cw=w/cell,ch=h/cell;
        boolean[][] road=new boolean[ch][cw];
        for(int y=0;y<ch;y++)for(int x=0;x<cw;x++)
            road[y][x]=roadCell(s,x*cell+cell/2,y*cell+cell/2,cell);

        int pcx=Math.max(0,Math.min(cw-1,o.playerX/cell));
        int pcy=Math.max(0,Math.min(ch-1,o.playerY/cell));
        // The green player marker can cover the road underneath it. Restore only
        // a tiny footprint; do not turn a large dark area into walkable terrain.
        for(int y=Math.max(0,pcy-1);y<=Math.min(ch-1,pcy+1);y++)
            for(int x=Math.max(0,pcx-1);x<=Math.min(cw-1,pcx+1);x++)
                if(distance(x,y,pcx,pcy)<=1)road[y][x]=true;

        boolean[][] reach=new boolean[ch][cw];
        ArrayDeque<Integer> q=new ArrayDeque<>();
        q.add(pcy*cw+pcx); reach[pcy][pcx]=true;
        while(!q.isEmpty()){
            int id=q.removeFirst(),x=id%cw,y=id/cw;
            for(int k=0;k<4;k++){
                int nx=x+(k==0?1:k==1?-1:0);
                int ny=y+(k==2?1:k==3?-1:0);
                if(nx<0||ny<0||nx>=cw||ny>=ch||reach[ny][nx]||!road[ny][nx])continue;
                reach[ny][nx]=true; q.addLast(ny*cw+nx);
            }
        }

        // First classify near-black components. A real unexplored region is a
        // contiguous near-black area INSIDE the minimap. A component touching the
        // viewport border is not used because that black edge is the moving-window
        // crop, not a new room.
        int[][] fogId=new int[ch][cw];
        List<Integer> fogSize=new ArrayList<>();
        fogSize.add(0);
        int nextFog=0;
        for(int y=1;y<ch-1;y++)for(int x=1;x<cw-1;x++){
            if(fogId[y][x]!=0||!fogCell(s,x*cell+cell/2,y*cell+cell/2,cell))continue;
            nextFog++;
            int size=0; boolean touches=false;
            ArrayDeque<Integer> fq=new ArrayDeque<>();
            fq.add(y*cw+x); fogId[y][x]=nextFog;
            while(!fq.isEmpty()){
                int id=fq.removeFirst(),cx=id%cw,cy=id/cw; size++;
                if(cx<=1||cy<=1||cx>=cw-2||cy>=ch-2)touches=true;
                for(int k=0;k<4;k++){
                    int nx=cx+(k==0?1:k==1?-1:0);
                    int ny=cy+(k==2?1:k==3?-1:0);
                    if(nx<1||ny<1||nx>=cw-1||ny>=ch-1||fogId[ny][nx]!=0)continue;
                    if(fogCell(s,nx*cell+cell/2,ny*cell+cell/2,cell)){
                        fogId[ny][nx]=nextFog; fq.addLast(ny*cw+nx);
                    }
                }
            }
            // Negative size marks a component touching the viewport edge.
            fogSize.add(touches?-size:size);
        }

        // Find ONLY confirmed-road cells adjacent to a sufficiently large interior
        // fog component. Dark-gray non-road terrain cannot pass this gate.
        List<int[]> fronts=new ArrayList<>();
        for(int y=1;y<ch-1;y++)for(int x=1;x<cw-1;x++)if(reach[y][x]){
            for(int k=0;k<4;k++){
                int nx=x+(k==0?1:k==1?-1:0),ny=y+(k==2?1:k==3?-1:0);
                if(nx<1||ny<1||nx>=cw-1||ny>=ch-1||road[ny][nx])continue;
                int fid=fogId[ny][nx];
                if(fid>0 && fid<fogSize.size() && fogSize.get(fid)>=8)
                    fronts.add(new int[]{x,y,nx,ny,fogSize.get(fid)});
            }
        }

        int[] best=null; double bestScore=-1e9;
        for(int[] f:fronts){
            int vx=f[0]-pcx,vy=f[1]-pcy;
            double d=Math.hypot(vx,vy); if(d<1)continue;
            double score=f[4]*0.35 - d*1.2;
            if(prevDx!=0||prevDy!=0){
                double forward=(vx*prevDx+vy*prevDy)/Math.max(1,d);
                score+=forward*55;
                if(vx*prevDx+vy*prevDy<0)score-=300;
            }
            // Prefer a target whose adjacent road corridor continues in that
            // direction. This prevents choosing a side pocket of dark pixels.
            int corridor=reachDir(s,f[0]*cell+cell/2,f[1]*cell+cell/2,
                    Integer.compare(vx,0),Integer.compare(vy,0),cell);
            score+=corridor*18;
            if(score>bestScore){bestScore=score;best=f;}
        }

        if(best!=null){
            int vx=best[0]-pcx,vy=best[1]-pcy;
            o.dx=Integer.compare(vx,0); o.dy=Integer.compare(vy,0);
            // Use the dominant axis for the joystick. Diagonal is only selected
            // when the target is genuinely diagonal.
            if(o.dx!=0&&o.dy!=0){
                if(Math.abs(vx)>=Math.abs(vy)*1.7)o.dy=0;
                else if(Math.abs(vy)>=Math.abs(vx)*1.7)o.dx=0;
            }
            if(corridorExists(s,o.playerX,o.playerY,o.dx,o.dy,cell)){
                o.roadDirection=true;
                o.targetX=best[0]*cell+cell/2;
                o.targetY=best[1]*cell+cell/2;
                o.pathLength=(int)Math.max(1,Math.hypot(vx,vy));
                o.routeCells=Math.min(60,fronts.size());
                return o;
            }
        }

        // No interior fog frontier: stay on confirmed gray road and prefer the
        // previous heading. Never walk into dark-gray terrain or black edge crop.
        int[][] dirs={{1,0},{0,1},{-1,0},{0,-1}};
        int bi=-1,br=0,bs=Integer.MIN_VALUE;
        for(int i=0;i<4;i++){
            int dx=dirs[i][0],dy=dirs[i][1];
            int r=reachDir(s,o.playerX,o.playerY,dx,dy,cell);
            if(r<3)continue;
            int sc=r*20;
            if(dx==prevDx&&dy==prevDy)sc+=800;
            if(dx==-prevDx&&dy==-prevDy)sc-=1200;
            if(sc>bs){bs=sc;bi=i;br=r;}
        }
        if(bi>=0){
            o.dx=dirs[bi][0];o.dy=dirs[bi][1];o.roadDirection=true;
            o.targetX=o.playerX+o.dx*br*cell;
            o.targetY=o.playerY+o.dy*br*cell;
            o.pathLength=br;o.routeCells=br;
        }
        return o;
    }

    /** Confirmed road: medium/bright neutral gray only. Dark-gray terrain is excluded. */
    private static boolean roadCell(Bitmap s,int cx,int cy,int cell){
        int good=0,n=0;
        for(int y=Math.max(0,cy-cell);y<=Math.min(s.getHeight()-1,cy+cell);y++)
            for(int x=Math.max(0,cx-cell);x<=Math.min(s.getWidth()-1,cx+cell);x++){
                int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000;
                int sp=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                // Road is gray-white. Bright colored UI/art and dark terrain are not road.
                if(lum>=70&&lum<=190&&sp<=48)good++;
                n++;
            }
        return n>0&&good/(float)n>=0.30f;
    }

    /** Deep black fog only. Dark-gray non-road must never satisfy this. */
    private static boolean fogCell(Bitmap s,int cx,int cy,int cell){
        return deepBlackRatio(s,cx,cy,cell)>=0.62f;
    }

    private static float deepBlackRatio(Bitmap s,int cx,int cy,int cell){
        int n=0,d=0;
        for(int y=Math.max(0,cy-cell);y<=Math.min(s.getHeight()-1,cy+cell);y++)
            for(int x=Math.max(0,cx-cell);x<=Math.min(s.getWidth()-1,cx+cell);x++){
                int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
                int lum=(299*r+587*g+114*b)/1000;
                int sp=Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
                if(lum<22&&sp<38)d++;
                n++;
            }
        return n==0?0:d/(float)n;
    }

    private static boolean corridorExists(Bitmap s,int px,int py,int dx,int dy,int cell){
        int good=0;
        for(int d=1;d<=7;d++)if(roadCell(s,px+dx*d*cell,py+dy*d*cell,cell))good++;
        return good>=3;
    }

    private static int reachDir(Bitmap s,int px,int py,int dx,int dy,int cell){
        if(dx==0&&dy==0)return 0;
        int r=0;
        for(int d=1;d<=20;d++){
            if(roadCell(s,px+dx*d*cell,py+dy*d*cell,cell))r=d;
            else if(d>4)break;
        }
        return r;
    }

    private static int distance(int x,int y,int a,int b){return Math.abs(x-a)+Math.abs(y-b);}

    private static int[] findPlayer(Bitmap s,int w,int h){
        boolean[] m=new boolean[w*h],seen=new boolean[w*h];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int c=s.getPixel(x,y),r=Color.red(c),g=Color.green(c),b=Color.blue(c);
            m[y*w+x]=g>=70&&g>=r+10&&g>=b+4;
        }
        int bx=-1,by=-1,best=-1;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++)if(m[y*w+x]&&!seen[y*w+x]){
            ArrayDeque<Integer>q=new ArrayDeque<>();q.add(y*w+x);seen[y*w+x]=true;
            int n=0,sx=0,sy=0,minx=x,maxx=x,miny=y,maxy=y;
            while(!q.isEmpty()){
                int id=q.removeFirst(),cx=id%w,cy=id/w;n++;sx+=cx;sy+=cy;
                minx=Math.min(minx,cx);maxx=Math.max(maxx,cx);miny=Math.min(miny,cy);maxy=Math.max(maxy,cy);
                for(int k=0;k<4;k++){
                    int nx=cx+(k==0?1:k==1?-1:0),ny=cy+(k==2?1:k==3?-1:0);
                    if(nx<1||ny<1||nx>=w-1||ny>=h-1)continue;
                    int ni=ny*w+nx;
                    if(m[ni]&&!seen[ni]){seen[ni]=true;q.addLast(ni);}
                }
            }
            int area=(maxx-minx+1)*(maxy-miny+1);
            if(n<20||area>6000)continue;
            int score=n*3-Math.abs((maxx-minx)-(maxy-miny))*2;
            if(score>best){best=score;bx=sx/n;by=sy/n;}
        }
        return new int[]{bx,by,Math.max(0,best/3)};
    }
}
