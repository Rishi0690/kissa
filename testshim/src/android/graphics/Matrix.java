package android.graphics;
import java.awt.geom.AffineTransform;
public class Matrix { public final float[] v = {1,0,0, 0,1,0, 0,0,1};
  public void reset() { float[] i = {1,0,0,0,1,0,0,0,1}; System.arraycopy(i, 0, v, 0, 9); }
  public void setTranslate(float x, float y) { reset(); v[2] = x; v[5] = y; }
  public void getValues(float[] o) { System.arraycopy(v, 0, o, 0, 9); }
  public void setValues(float[] i) { System.arraycopy(i, 0, v, 0, 9); }
  public AffineTransform awt() { return new AffineTransform(v[0], v[3], v[1], v[4], v[2], v[5]); } }
