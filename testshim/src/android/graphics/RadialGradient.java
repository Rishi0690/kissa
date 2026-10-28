package android.graphics;
public class RadialGradient extends Shader { final float x,y,r; final int[] c; final float[] p;
  public RadialGradient(float x, float y, float r, int[] c, float[] p, TileMode t) { this.x=x;this.y=y;this.r=r;this.c=c;this.p=p; }
  @Override public java.awt.Paint awt() {
    if (c.length < 2 || r <= 0) return new java.awt.Color(c[0], true);
    return new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Float(x, y), r, fr(c.length, p), cs(c)); } }
