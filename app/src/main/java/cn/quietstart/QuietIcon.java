package cn.quietstart;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native vector icons; no bitmap assets or animation work. */
final class QuietIcon extends Drawable {
    private final String kind;
    private final int color;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    QuietIcon(String kind,int color){this.kind=kind;this.color=color;}
    @Override public void draw(Canvas canvas){
        canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case "back":line(canvas,14,6,8,12,14,18);break;
            case "next":line(canvas,9,7,14,12,9,17);break;
            case "settings":
                for(int i=0;i<8;i++){canvas.save();canvas.rotate(i*45,12,12);canvas.drawLine(12,3,12,5,paint);canvas.restore();}
                canvas.drawCircle(12,12,7,paint);canvas.drawCircle(12,12,2.5f,paint);break;
            case "shield":
                shield(canvas);line(canvas,8,12,11,15,16,9);break;
            case "spark":
                line(canvas,12,3,14.5f,9.5f,21,12,14.5f,14.5f,12,21,9.5f,14.5f,3,12,9.5f,9.5f,12,3);break;
            case "apps":
                for(int x:new int[]{4,14})for(int y:new int[]{4,14})canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,paint);break;
            case "leaf":
                Path leaf=new Path();leaf.moveTo(5,18);leaf.cubicTo(0,7,12,3,20,4);leaf.cubicTo(21,15,14,22,5,18);canvas.drawPath(leaf,paint);
                line(canvas,4,21,14,11);break;
            case "sliders":
                for(int y:new int[]{6,12,18})canvas.drawLine(4,y,20,y,paint);
                for(int[] xy:new int[][]{{9,6},{16,12},{8,18}}){paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawCircle(xy[0],xy[1],2.4f,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(color);canvas.drawCircle(xy[0],xy[1],2.4f,paint);}break;
            case "info":
                canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,11,12,17,paint);canvas.drawPoint(12,7,paint);break;
            case "download":
                line(canvas,12,3,12,15);line(canvas,7,10,12,15,17,10);line(canvas,4,16,4,21,20,21,20,16);break;
            case "pulse":line(canvas,2,12,7,12,10,5,14,19,17,12,22,12);break;
            case "lock":
                canvas.drawRoundRect(5,10,19,21,3,3,paint);canvas.drawArc(8,2,16,16,180,180,false,paint);canvas.drawLine(12,14,12,17,paint);break;
            default:
                canvas.drawRoundRect(5,3,19,21,3,3,paint);canvas.drawLine(9,8,15,8,paint);canvas.drawLine(9,12,15,12,paint);canvas.drawLine(9,16,13,16,paint);
        }
        canvas.restore();
    }
    private void shield(Canvas c){Path p=new Path();p.moveTo(12,2);p.lineTo(21,6);p.lineTo(20,13);p.cubicTo(19,18,15,21,12,22);p.cubicTo(9,21,5,18,4,13);p.lineTo(3,6);p.close();c.drawPath(p,paint);}
    private void line(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
