package app.remotessh;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class IconView extends View {
  public IconView(Context context) {
    this(context, "computer", Color.BLACK);
  }

  public String icon;
  public int color;
  public boolean alignBottomEnd;
  private final Paint p = new Paint(3);
  private final Path folder = new Path();

  public IconView(Context c, String icon, int color) {
    super(c);
    this.icon = icon;
    this.color = color;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    float size = Math.min(getWidth(), getHeight()) * .48f;
    canvas.translate(alignBottomEnd ? getWidth()-size-4*getResources().getDisplayMetrics().density : (getWidth()-size)/2,
        alignBottomEnd ? getHeight()-size : (getHeight()-size)/2);
    canvas.scale(size / 24, size / 24);
    p.setColor(color);
    p.setStrokeWidth(1.8f);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    switch (icon) {
      case "shield":
      case "shieldCheck":
        folder.reset(); folder.moveTo(12, 3); folder.lineTo(20, 6); folder.lineTo(19, 14);
        folder.quadTo(17, 19, 12, 22); folder.quadTo(7, 19, 5, 14); folder.lineTo(4, 6); folder.close();
        canvas.drawPath(folder, p);
        if (icon.equals("shieldCheck")) { line(canvas, 8, 12, 11, 15); line(canvas, 11, 15, 16, 9); }
        break;
      case "lock":
        canvas.drawRoundRect(5, 10, 19, 21, 3, 3, p);
        canvas.drawArc(8, 3, 16, 16, 180, 180, false, p);
        line(canvas, 12, 14, 12, 17);
        break;
      case "bolt":
        folder.reset(); folder.moveTo(14, 2); folder.lineTo(5, 14); folder.lineTo(11, 14);
        folder.lineTo(10, 22); folder.lineTo(20, 10); folder.lineTo(13, 10); folder.close();
        canvas.drawPath(folder, p);
        break;
      case "check":
        line(canvas, 5, 12, 10, 17); line(canvas, 10, 17, 20, 6);
        break;
      case "back":
        line(canvas, 20, 12, 4, 12);
        line(canvas, 4, 12, 11, 5);
        line(canvas, 4, 12, 11, 19);
        break;
      case "more":
        p.setStyle(Paint.Style.FILL);
        for (int y = 5; y <= 19; y += 7) canvas.drawCircle(12, y, 1.6f, p);
        break;
      case "folder":
        folder.reset();
        folder.moveTo(3, 8);
        folder.lineTo(3, 5);
        folder.lineTo(10, 5);
        folder.lineTo(13, 8);
        folder.lineTo(21, 8);
        folder.lineTo(21, 20);
        folder.lineTo(3, 20);
        folder.close();
        canvas.drawPath(folder, p);
        line(canvas, 3, 10, 21, 10);
        break;
      case "menu":
        line(canvas, 4, 7, 20, 7);
        line(canvas, 4, 12, 16, 12);
        line(canvas, 4, 17, 20, 17);
        break;
      case "new":
        canvas.drawRoundRect(4, 5, 19, 21, 3, 3, p);
        line(canvas, 12, 12, 21, 3);
        line(canvas, 18, 3, 21, 6);
        break;
      case "arrow":
        line(canvas, 12, 20, 12, 4);
        line(canvas, 5, 11, 12, 4);
        line(canvas, 12, 4, 19, 11);
        break;
      case "stop":
        p.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(6, 6, 18, 18, 2, 2, p);
        break;
      case "close":
        line(canvas, 6, 6, 18, 18);
        line(canvas, 18, 6, 6, 18);
        break;
      case "plus":
        line(canvas, 12, 4, 12, 20);
        line(canvas, 4, 12, 20, 12);
        break;
      case "chevron":
        line(canvas, 8, 5, 15, 12);
        line(canvas, 15, 12, 8, 19);
        break;
      case "copy":
        canvas.drawRoundRect(8, 8, 20, 21, 2, 2, p);
        line(canvas, 15, 4, 4, 4);
        line(canvas, 4, 4, 4, 16);
        break;
      case "terminal":
        line(canvas, 5, 7, 10, 12);
        line(canvas, 10, 12, 5, 17);
        line(canvas, 13, 17, 20, 17);
        break;
      case "moon":
        canvas.drawArc(4, 3, 21, 20, 45, 270, false, p);
        canvas.drawArc(9, 0, 25, 16, 90, 150, false, p);
        break;
      default:
        canvas.drawRoundRect(3, 4, 21, 17, 2, 2, p);
        line(canvas, 12, 17, 12, 21);
        line(canvas, 8, 21, 16, 21);
    }
    canvas.restore();
  }

  private void line(Canvas c, float a, float b, float x, float y) {
    c.drawLine(a, b, x, y, p);
  }
}
