package android.graphics;
public class Paint {
  public static final int ANTI_ALIAS_FLAG = 1, FILTER_BITMAP_FLAG = 2, DITHER_FLAG = 4;
  public enum Cap { BUTT, ROUND, SQUARE } public enum Join { MITER, ROUND, BEVEL } public enum Style { FILL, STROKE, FILL_AND_STROKE }
  int color = 0xFF000000; float strokeWidth = 0f, miter = 4f; Cap cap = Cap.BUTT; Join join = Join.MITER; Style style = Style.FILL;
  Shader shader; boolean aa = true, filter;
  public Paint() {} public Paint(int flags) {}
  public int getAlpha() { return color >>> 24; } public void setAlpha(int a) { color = (color & 0x00FFFFFF) | ((a & 0xFF) << 24); }
  public int getColor() { return color; } public void setColor(int c) { color = c; }
  public Cap getStrokeCap() { return cap; } public void setStrokeCap(Cap c) { cap = c; }
  public Join getStrokeJoin() { return join; } public void setStrokeJoin(Join j) { join = j; }
  public float getStrokeMiter() { return miter; } public void setStrokeMiter(float m) { miter = m; }
  public float getStrokeWidth() { return strokeWidth; } public void setStrokeWidth(float w) { strokeWidth = w; }
  public Style getStyle() { return style; } public void setStyle(Style s) { style = s; }
  public boolean isAntiAlias() { return aa; } public void setAntiAlias(boolean b) { aa = b; }
  public boolean isFilterBitmap() { return filter; } public void setFilterBitmap(boolean b) { filter = b; }
  ColorFilter cf; public ColorFilter getColorFilter() { return cf; } public ColorFilter setColorFilter(ColorFilter f) { cf = f; return f; } public PathEffect setPathEffect(PathEffect e) { return e; }
  public Shader setShader(Shader s) { shader = s; return s; } public Xfermode setXfermode(Xfermode x) { return x; }
}
