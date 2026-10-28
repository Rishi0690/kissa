package android.graphics;
import java.awt.geom.*;
public class Path {
  public enum Direction { CW, CCW } public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD } public enum Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }
  public Path2D.Float p = new Path2D.Float(); FillType ft = FillType.WINDING; float cx, cy;
  public Path() {}
  public void moveTo(float x, float y) { p.moveTo(x, y); cx = x; cy = y; }
  public void lineTo(float x, float y) { ensure(); p.lineTo(x, y); cx = x; cy = y; }
  public void quadTo(float a, float b, float x, float y) { ensure(); p.quadTo(a, b, x, y); cx = x; cy = y; }
  public void cubicTo(float a, float b, float c, float d, float x, float y) { ensure(); p.curveTo(a, b, c, d, x, y); cx = x; cy = y; }
  public void rMoveTo(float x, float y) { moveTo(cx + x, cy + y); } public void rLineTo(float x, float y) { lineTo(cx + x, cy + y); }
  public void rQuadTo(float a, float b, float x, float y) { quadTo(cx + a, cy + b, cx + x, cy + y); }
  public void rCubicTo(float a, float b, float c, float d, float x, float y) { cubicTo(cx + a, cy + b, cx + c, cy + d, cx + x, cy + y); }
  private void ensure() { if (p.getCurrentPoint() == null) p.moveTo(cx, cy); }
  public void close() { if (p.getCurrentPoint() != null) p.closePath(); }
  public void reset() { p = new Path2D.Float(); cx = cy = 0; } public void rewind() { reset(); }
  public boolean isEmpty() { return p.getCurrentPoint() == null; } public boolean isConvex() { return false; }
  public FillType getFillType() { return ft; } public void setFillType(FillType f) { ft = f; p.setWindingRule(f == FillType.EVEN_ODD || f == FillType.INVERSE_EVEN_ODD ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO); }
  public void addOval(RectF r, Direction d) { p.append(new Ellipse2D.Float(r.left, r.top, r.width(), r.height()), false); }
  public void addRect(RectF r, Direction d) { p.append(new Rectangle2D.Float(r.left, r.top, r.width(), r.height()), false); }
  public void addRoundRect(RectF r, float[] radii, Direction d) { p.append(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), radii[0] * 2, radii[1] * 2), false); }
  public void addArc(RectF r, float start, float sweep) { p.append(new Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, Arc2D.OPEN), false); }
  public void arcTo(RectF r, float start, float sweep, boolean force) { p.append(new Arc2D.Float(r.left, r.top, r.width(), r.height(), -start, -sweep, Arc2D.OPEN), !force && p.getCurrentPoint() != null); Point2D c = p.getCurrentPoint(); if (c != null) { cx = (float) c.getX(); cy = (float) c.getY(); } }
  public void addPath(Path o, float dx, float dy) { p.append(o.p.createTransformedShape(AffineTransform.getTranslateInstance(dx, dy)), false); }
  public void computeBounds(RectF out, boolean exact) { Rectangle2D b = p.getBounds2D(); out.set((float) b.getMinX(), (float) b.getMinY(), (float) b.getMaxX(), (float) b.getMaxY()); }
  public void transform(Matrix m) { p.transform(m.awt()); }
  public boolean op(Path a, Path b, Op op) { Area x = new Area(a.p), y = new Area(b.p);
    switch (op) { case UNION: x.add(y); break; case INTERSECT: x.intersect(y); break; case XOR: x.exclusiveOr(y); break; case DIFFERENCE: x.subtract(y); break; default: y.subtract(x); x = y; }
    p = new Path2D.Float(x); return true; }
}
