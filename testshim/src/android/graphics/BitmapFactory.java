package android.graphics;
public class BitmapFactory {
    public static Bitmap decodeFile(String path) {
        try { java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.File(path));
              java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
              b.getGraphics().drawImage(src, 0, 0, null); return new Bitmap(b);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
