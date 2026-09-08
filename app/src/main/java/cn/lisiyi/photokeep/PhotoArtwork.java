package cn.lisiyi.photokeep;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector illustration; no network images or access to the user's photos. */
public final class PhotoArtwork extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mountain = new Path(), tick = new Path();
    public PhotoArtwork(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void color(String hex) { paint.setColor(Color.parseColor(hex)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float s = Math.min(getWidth() / 160f, getHeight() / 170f);
        canvas.save(); canvas.translate((getWidth() - 160 * s) / 2, (getHeight() - 170 * s) / 2); canvas.scale(s, s);
        color("#D8E3CC"); canvas.drawCircle(83, 80, 66, paint);
        canvas.save(); canvas.rotate(-13, 74, 88);
        color("#B7C9AD"); canvas.drawRoundRect(30, 30, 120, 146, 9, 9, paint);
        canvas.restore();
        canvas.save(); canvas.rotate(9, 90, 87);
        color("#FCFCF6"); canvas.drawRoundRect(44, 20, 133, 140, 8, 8, paint);
        color("#ADC6B5"); canvas.drawRoundRect(51, 27, 126, 115, 3, 3, paint);
        color("#F0CE85"); canvas.drawCircle(104, 48, 10, paint);
        mountain.reset(); mountain.moveTo(51, 101); mountain.lineTo(75, 61); mountain.lineTo(110, 115); mountain.lineTo(51, 115); mountain.close();
        color("#40755E"); canvas.drawPath(mountain, paint);
        mountain.reset(); mountain.moveTo(85, 115); mountain.lineTo(108, 78); mountain.lineTo(126, 101); mountain.lineTo(126, 115); mountain.close();
        color("#628C65"); canvas.drawPath(mountain, paint);
        color("#D6DECD"); canvas.drawRoundRect(63, 124, 114, 128, 2, 2, paint);
        canvas.restore();
        color("#176B55"); canvas.drawCircle(123, 137, 21, paint);
        color("#FFFFFF"); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        tick.reset(); tick.moveTo(114, 137); tick.lineTo(120, 143); tick.lineTo(132, 131); canvas.drawPath(tick, paint);
        paint.setStyle(Paint.Style.FILL); canvas.restore();
    }
}
