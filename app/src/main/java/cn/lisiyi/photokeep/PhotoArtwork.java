package cn.lisiyi.photokeep;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small vector illustration drawn from theme colors; no network images or access to the user's photos. */
public final class PhotoArtwork extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    public PhotoArtwork(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void color(int res) { paint.setColor(getContext().getColor(res)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float s = Math.min(getWidth() / 160f, getHeight() / 150f);
        canvas.save(); canvas.translate((getWidth() - 160 * s) / 2, (getHeight() - 150 * s) / 2); canvas.scale(s, s);
        paint.setStyle(Paint.Style.FILL);
        // Back print, tilted away.
        canvas.save(); canvas.rotate(-10, 70, 70);
        color(R.color.pk_outline_variant); canvas.drawRoundRect(22, 22, 112, 122, 10, 10, paint);
        canvas.restore();
        // Front print with a simple landscape.
        canvas.save(); canvas.rotate(6, 80, 70);
        color(R.color.pk_surface_container_lowest); canvas.drawRoundRect(36, 14, 128, 120, 10, 10, paint);
        color(R.color.pk_secondary_container); canvas.drawRoundRect(44, 22, 120, 100, 4, 4, paint);
        color(R.color.pk_accent_warm); canvas.drawCircle(102, 40, 8, paint);
        path.reset(); path.moveTo(44, 90); path.lineTo(68, 56); path.lineTo(96, 100); path.lineTo(44, 100); path.close();
        color(R.color.pk_primary); canvas.drawPath(path, paint);
        path.reset(); path.moveTo(80, 100); path.lineTo(100, 72); path.lineTo(120, 92); path.lineTo(120, 100); path.close();
        paint.setAlpha(150); canvas.drawPath(path, paint); paint.setAlpha(255);
        canvas.restore();
        // Cloud with a confirmation tick: the matched copy in OneDrive.
        color(R.color.pk_primary);
        canvas.drawCircle(104, 120, 15, paint); canvas.drawCircle(124, 112, 19, paint); canvas.drawCircle(142, 124, 13, paint);
        canvas.drawRoundRect(92, 118, 152, 139, 10.5f, 10.5f, paint);
        color(R.color.pk_on_primary); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset(); path.moveTo(112, 124); path.lineTo(120, 131); path.lineTo(134, 117); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
}
