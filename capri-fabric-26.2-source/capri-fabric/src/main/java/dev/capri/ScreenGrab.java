package dev.capri;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Grabs the finished game frame and turns it into a small base64 JPEG. Nothing is written to disk. */
public final class ScreenGrab {
    private ScreenGrab() {}

    public static void capture(int maxWidth, Consumer<String> ok, Consumer<String> fail) {
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget target = mainTarget(mc);
            Screenshot.takeScreenshot(target, (NativeImage image) -> {
                final int w;
                final int h;
                final int[] px;
                try {
                    w = image.getWidth();
                    h = image.getHeight();
                    px = image.getPixelsABGR();
                } finally {
                    image.close();
                }
                // Encode off the render thread so the game doesn't hitch.
                CompletableFuture.runAsync(() -> {
                    try {
                        ok.accept(encode(px, w, h, maxWidth));
                    } catch (Throwable t) {
                        fail.accept(t.toString());
                    }
                });
            });
        } catch (Throwable t) {
            fail.accept(t.toString());
        }
    }

    /**
     * The main render target moved between versions: Minecraft.getMainRenderTarget() in 26.1,
     * GameRenderer.mainRenderTarget() in 26.2. Reflection keeps this working on both.
     */
    private static RenderTarget mainTarget(Minecraft mc) throws ReflectiveOperationException {
        Object gameRenderer = null;
        try {
            Field f = Minecraft.class.getField("gameRenderer");
            gameRenderer = f.get(mc);
        } catch (ReflectiveOperationException ignored) {
            // fall through to the other lookups
        }
        if (gameRenderer != null) {
            try {
                Method m = gameRenderer.getClass().getMethod("mainRenderTarget");
                return (RenderTarget) m.invoke(gameRenderer);
            } catch (NoSuchMethodException ignored) {
                // try the older location below
            }
        }
        Method m = Minecraft.class.getMethod("getMainRenderTarget");
        return (RenderTarget) m.invoke(mc);
    }

    private static String encode(int[] abgr, int w, int h, int maxWidth) throws Exception {
        int[] rgb = new int[w * h];
        for (int i = 0; i < rgb.length; i++) {
            int p = abgr[i];
            rgb[i] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
        }
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, w, h, rgb, 0, w);

        BufferedImage out = src;
        if (maxWidth > 0 && w > maxWidth) {
            int nw = maxWidth;
            int nh = Math.max(1, (int) ((long) h * maxWidth / w));
            out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.7f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            }
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } finally {
            writer.dispose();
        }
    }
}
