package android.graphics;
public class Shader { public enum TileMode { CLAMP, REPEAT, MIRROR, DECAL }
  public java.awt.Paint awt() { return java.awt.Color.MAGENTA; }
  static float[] fr(int n, float[] pos) {
    float[] f = new float[n];
    for (int i = 0; i < n; i++) f[i] = pos != null ? pos[i] : (n == 1 ? 0f : (float) i / (n - 1));
    for (int i = 1; i < n; i++) if (f[i] <= f[i - 1]) f[i] = Math.min(1f, f[i - 1] + 0.0001f);
    return f; }
  static java.awt.Color[] cs(int[] c) { java.awt.Color[] o = new java.awt.Color[c.length]; for (int i = 0; i < c.length; i++) o[i] = new java.awt.Color(c[i], true); return o; } }
