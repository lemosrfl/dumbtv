package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Wide, launcher-matched artwork for Fire TV's Amazon Apps grid tile. */
final class AppsGridBanner extends Drawable {
    private static final int[] COLORS = {
            0xFF4D8EFF, 0xFF36C6B3, 0xFFFFB454,
            0xFF9B79F2, 0xFFFF647C, 0xFF61B8F0,
            0xFF58C98D, 0xFF6E87B8, 0xFFB278D8
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        canvas.save();
        canvas.translate(b.left, b.top);
        float sx = b.width() / 372f;
        float sy = b.height() / 209f;
        canvas.scale(sx, sy);

        paint.setShader(new LinearGradient(0, 0, 372, 209,
                new int[] {0xFF172131, 0xFF202D40, 0xFF192338}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 372, 209, paint);
        paint.setShader(null);

        // Nine small rounded app cards form a clear grid without borrowing Amazon branding.
        float left = 53, top = 48, side = 31, gap = 10;
        for (int i = 0; i < COLORS.length; i++) {
            int col = i % 3, row = i / 3;
            float x = left + col * (side + gap), y = top + row * (side + gap);
            paint.setColor(COLORS[i]);
            canvas.drawRoundRect(x, y, x + side, y + side, 7, 7, paint);
            paint.setColor(0x44FFFFFF);
            canvas.drawRoundRect(x + 5, y + 5, x + side - 5, y + 10, 2, 2, paint);
        }

        paint.setColor(0xFF6EA5FF);
        canvas.drawRoundRect(178, 66, 181, 143, 1.5f, 1.5f, paint);
        paint.setColor(0xFFF4F7FC);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(21);
        canvas.drawText("MEUS", 198, 96, paint);
        paint.setTextSize(19);
        canvas.drawText("APLICATIVOS", 198, 123, paint);

        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override
    public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }

    @Override
    public int getOpacity() { return Color.TRANSPARENT; }

    @Override
    public int getIntrinsicWidth() { return 372; }

    @Override
    public int getIntrinsicHeight() { return 209; }
}
