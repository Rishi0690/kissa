package com.kissa.shim;
import android.graphics.*;
import androidx.compose.ui.graphics.AndroidImageBitmap_androidKt;
import androidx.compose.ui.graphics.ImageBitmap;
/** Glue so Kotlin tests only see Compose ImageBitmap (Kotlin can't resolve the test-double Bitmap against android.jar). */
public class ShotIO {
    public static ImageBitmap create(int w, int h) { return AndroidImageBitmap_androidKt.asImageBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)); }
    public static ImageBitmap load(String path) { return AndroidImageBitmap_androidKt.asImageBitmap(BitmapFactory.decodeFile(path)); }
    public static void save(ImageBitmap img, String path) {
        try { javax.imageio.ImageIO.write(BitmapAccess.img(AndroidImageBitmap_androidKt.asAndroidBitmap(img)), "png", new java.io.File(path)); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
