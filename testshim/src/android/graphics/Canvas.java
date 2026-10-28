package android.graphics;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayDeque;
public class Canvas {
  public enum VertexMode { TRIANGLES, TRIANGLE_STRIP, TRIANGLE_FAN }
  final Graphics2D g; final ArrayDeque<Object[]> stack = new ArrayDeque<>();
  public Canvas() { this(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)); }
  public void setBitmap(Bitmap b) {}
  public Canvas(Bitmap b) { g = b.img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); }
  public int save() { stack.push(new Object[]{ g.getTransform(), g.getClip() }); return stack.size(); }
  public int saveLayer(float l, float t, float r, float b, Paint p, int f) { return save(); }
  public void restore() { Object[] s = stack.pop(); g.setTransform((AffineTransform) s[0]); g.setClip((Shape) s[1]); }
  public void translate(float x, float y) { g.translate(x, y); } public void scale(float x, float y) { g.scale(x, y); }
  public void rotate(float d) { g.rotate(Math.toRadians(d)); } public void skew(float x, float y) { g.shear(x, y); }
  public void concat(Matrix m) { g.transform(m.awt()); }
  public boolean clipPath(Path p, Region.Op op) { if (op == Region.Op.INTERSECT) g.clip(p.p); return true; }
  public boolean clipPath(Path p) { g.clip(p.p); return true; }
  public boolean clipRect(float l, float t, float r, float b, Region.Op op) { if (op == Region.Op.INTERSECT) g.clip(new Rectangle2D.Float(l, t, r - l, b - t)); return true; }
  private void paint(Shape s, Paint p) {
    Composite old = g.getComposite();
    if (p.shader != null) { g.setPaint(p.shader.awt()); float a = p.getAlpha() / 255f; if (a < 1f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, a))); }
    else g.setPaint(new Color(p.color, true));
    if (p.style == Paint.Style.STROKE) {
      int cap = p.cap == Paint.Cap.ROUND ? BasicStroke.CAP_ROUND : p.cap == Paint.Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT;
      int join = p.join == Paint.Join.ROUND ? BasicStroke.JOIN_ROUND : p.join == Paint.Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER;
      g.setStroke(new BasicStroke(Math.max(p.strokeWidth, 0.5f), cap, join, Math.max(1f, p.miter))); g.draw(s);
    } else g.fill(s);
    g.setComposite(old);
  }
  public void drawPath(Path path, Paint p) { paint(path.p, p); }
  public void drawRect(float l, float t, float r, float b, Paint p) { paint(new Rectangle2D.Float(l, t, r - l, b - t), p); }
  public void drawOval(float l, float t, float r, float b, Paint p) { paint(new Ellipse2D.Float(l, t, r - l, b - t), p); }
  public void drawCircle(float x, float y, float r, Paint p) { paint(new Ellipse2D.Float(x - r, y - r, 2 * r, 2 * r), p); }
  public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) { paint(new RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2), p); }
  public void drawArc(float l, float t, float r, float b, float start, float sweep, boolean center, Paint p) { paint(new Arc2D.Float(l, t, r - l, b - t, -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), p); }
  public void drawLine(float x1, float y1, float x2, float y2, Paint p) { Paint.Style s = p.style; p.style = Paint.Style.STROKE; paint(new Line2D.Float(x1, y1, x2, y2), p); p.style = s; }
  public void drawPoint(float x, float y, Paint p) {}
  private Composite alphaOn(Paint p) { Composite old = g.getComposite(); float a = p == null ? 1f : p.getAlpha() / 255f; if (a < 0.999f) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, a))); return old; }
  public void drawBitmap(Bitmap b, float x, float y, Paint p) { Composite o = alphaOn(p); AffineTransform t = g.getTransform(); g.translate(x, y); g.drawImage(b.img, 0, 0, null); g.setTransform(t); g.setComposite(o); }
  public void drawBitmap(Bitmap b, Rect s, Rect d, Paint p) { Composite o = alphaOn(p); g.drawImage(b.img, d.left, d.top, d.right, d.bottom, s.left, s.top, s.right, s.bottom, null); g.setComposite(o); }
  public void drawVertices(VertexMode m, int c, float[] v, int vo, float[] t, int to, int[] col, int co, short[] i, int io, int ic, Paint p) {}
}
