package android.graphics;
import java.awt.image.BufferedImage;
/** JVM test double: backs android.graphics.Bitmap with a BufferedImage so Compose DrawScope code can be rendered headless. */
public class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE, RGBA_1010102 }
    public enum CompressFormat { JPEG, PNG, WEBP }
    final BufferedImage img;
    Bitmap(BufferedImage img) { this.img = img; }
    public static Bitmap createBitmap(int w, int h, Config c) { return new Bitmap(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)); }
    public static Bitmap createBitmap(android.util.DisplayMetrics m, int w, int h, Config c) { return createBitmap(w, h, c); }
    public int getWidth() { return img.getWidth(); }
    public int getHeight() { return img.getHeight(); }
    public Config getConfig() { return Config.ARGB_8888; }
    public boolean hasAlpha() { return true; }
    public void setHasAlpha(boolean b) {}
    public void prepareToDraw() {}
    public void recycle() {}
    public Bitmap copy(Config c, boolean m) { return this; }
    public void getPixels(int[] px, int off, int stride, int x, int y, int w, int h) { img.getRGB(x, y, w, h, px, off, stride); }
}
