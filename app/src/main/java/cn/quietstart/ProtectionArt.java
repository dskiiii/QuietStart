package cn.quietstart;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A static status illustration, redrawn only when filtering changes. */
final class ProtectionArt extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active;
    ProtectionArt(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setActive(boolean value){if(active!=value){active=value;invalidate();}}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float unit=Math.min(getWidth()/180f,getHeight()/116f);
        c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(unit,unit);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x0EDAF3CD);c.drawCircle(0,0,51,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);paint.setColor(0x22DAF3CD);c.drawCircle(0,0,50,paint);c.drawCircle(0,0,39,paint);
        paint.setColor(0xFFDAF3CD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.3f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        Path shield=new Path();shield.moveTo(0,-29);shield.lineTo(24,-19);shield.lineTo(22,4);shield.cubicTo(20,19,9,28,0,32);shield.cubicTo(-9,28,-20,19,-22,4);shield.lineTo(-24,-19);shield.close();c.drawPath(shield,paint);
        if(active){Path check=new Path();check.moveTo(-10,0);check.lineTo(-2,8);check.lineTo(12,-8);c.drawPath(check,paint);}
        else{c.drawArc(-10,-9,10,11,-50,280,false,paint);c.drawLine(0,-13,0,-1,paint);}
        paint.setStrokeWidth(1.4f);c.drawLine(60,-21,68,-21,paint);c.drawLine(64,-25,64,-17,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x779FCAA7);c.drawCircle(-62,18,2.5f,paint);c.drawCircle(42,41,1.7f,paint);
        c.restore();
    }
}
