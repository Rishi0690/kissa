package android.graphics;
public class LinearGradient extends Shader { final float x0,y0,x1,y1; final int[] c; final float[] p;
  public LinearGradient(float x0, float y0, float x1, float y1, int[] c, float[] p, TileMode t) { this.x0=x0;this.y0=y0;this.x1=x1;this.y1=y1;this.c=c;this.p=p; }
  @Override public java.awt.Paint awt() {
    if (c.length < 2 || (x0 == x1 && y0 == y1)) return new java.awt.Color(c[0], true);
    return new java.awt.LinearGradientPaint(new java.awt.geom.Point2D.Float(x0, y0), new java.awt.geom.Point2D.Float(x1, y1), fr(c.length, p), cs(c)); } }
