package ryancheng.bitmaputil;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Create time: 2016/4/7.
 */
public class PaintImageView extends View {
    static final String TAG = "PeekThroughImageView";
    private Paint paint = null;
    private List<Path> paths = new ArrayList<>();
    private Bitmap srcBmp, blurBmp;
    private RectF bmpRect;
    private float bmpScale;
    private static final int MOSAIC_SHADOW_COLOR = 0x40808080;
    private static final int MOSAIC_BLOCK_SIZE = 10;
    private static final int FROG_SHADOW_COLOR = 0x80808080;
    private static final float FROG_BITMAP_SCALE = 0.4f;
    private static final float FROG_RADIUS = 25f;
    private Mode mode = Mode.PAINT;
    public enum Mode {
        MOSAIC, PAINT, FROG
    }

    public PaintImageView(Context context) {
        super(context);
        init();
    }

    public PaintImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PaintImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PaintImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStrokeWidth(30);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        bmpRect = new RectF();
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        float x = motionEvent.getX();
        float y = motionEvent.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            Path path = new Path();
            paths.add(path);
            path.moveTo(x, y);
        } else if (action == MotionEvent.ACTION_MOVE) {
            paths.get(paths.size() - 1).lineTo(x, y);
        }else if (action == MotionEvent.ACTION_UP) {
            paths.get(paths.size() - 1).lineTo(x, y);
        }
        invalidate();
        return true;
    }

    public void setBitmap(Bitmap bitmap) {
        srcBmp = bitmap;
        if (blurBmp != null) {
            blurBmp.recycle();
            blurBmp = null;
        }
        cleanPath();
    }

    public void cleanPath() {
        for (Path path : paths) {
            path.reset();
        }
        paths.clear();
        invalidate();
    }

    public void setMode(Mode mode) {
        Log.d(TAG, "setMode:" + mode);
        this.mode = mode;
        if (blurBmp != null) {
            blurBmp.recycle();
            blurBmp = null;
        }
        cleanPath();
    }

    public Bitmap saveBitmap() {
        Bitmap dst = Bitmap.createBitmap(srcBmp.getWidth(), srcBmp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dst);
        RectF rectF = new RectF(0, 0, srcBmp.getWidth(), srcBmp.getHeight());
        if (mode == Mode.PAINT) {
            c.drawBitmap(srcBmp, null, rectF, paint);
            c.save();
            c.scale(1 / bmpScale, 1 / bmpScale);
            c.translate(-bmpRect.left, -bmpRect.top);
            for (Path path : paths) {
                if (!path.isEmpty()) {
                    c.drawPath(path, paint);
                }
            }
            c.restore();
        } else if (mode == Mode.MOSAIC) {
            c.drawBitmap(srcBmp, null, rectF, paint);
            c.save();
            c.scale(1 / bmpScale, 1 / bmpScale);
            c.translate(-bmpRect.left, -bmpRect.top);
            for (Path path : paths) {
                if (!path.isEmpty()) {
                    c.drawPath(path, paint);
                }
            }
            c.restore();
        } else {
            c.drawBitmap(blurBmp, null, rectF, paint);
            c.save();
            c.scale(1 / bmpScale, 1 / bmpScale);
            c.translate(-bmpRect.left, -bmpRect.top);
            for (Path path : paths) {
                if (!path.isEmpty()) {
                    c.drawPath(path, paint);
                }
            }
            c.restore();
        }
        return dst;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (srcBmp == null) {
            return;
        }
        calBitmapRect();
        if (mode == Mode.PAINT) {
            canvas.drawBitmap(srcBmp, null, bmpRect, paint);
            paint.setShader(null);
        } else if (mode == Mode.MOSAIC) {
            if (blurBmp == null) {
                Bitmap screen = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(screen);
                blurBmp = mosaic(srcBmp, MOSAIC_BLOCK_SIZE, MOSAIC_SHADOW_COLOR);
                c.drawBitmap(blurBmp, null, bmpRect, paint);
                Shader shader = new BitmapShader(screen,
                        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint.setShader(shader);
            }
            canvas.drawBitmap(srcBmp, null, bmpRect, paint);
        } else {
            if (blurBmp == null) {
                Bitmap screen = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(screen);
                c.drawBitmap(srcBmp, null, bmpRect, paint);
                Shader shader = new BitmapShader(screen,
                        Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                paint.setShader(shader);
                blurBmp = blur(getContext(), srcBmp, FROG_BITMAP_SCALE, FROG_SHADOW_COLOR, FROG_RADIUS);
            }
            canvas.drawBitmap(blurBmp, null, bmpRect, paint);
        }
        for (Path path : paths) {
            if (!path.isEmpty()) {
                canvas.drawPath(path, paint);
            }
        }
    }

    private void calBitmapRect() {
        int bw = srcBmp.getWidth();
        int bh = srcBmp.getHeight();
        int sw = getWidth();
        int sh = getHeight();
        //图片宽高比
        float r = 1f * bw / bh;
        //控件宽高比
        float rb = 1f * sw / sh;
        if (r < rb) {
            //左右留白
            bmpScale = 1f * sh / bh;
            float w = r * sh;
            bmpRect.left = (sw - w) / 2;
            bmpRect.right = sw - bmpRect.left;
            bmpRect.top = 0;
            bmpRect.bottom = sh;
        } else {
            //上下留白
            bmpScale = 1f * sw / bw;
            float h = sw / r;
            bmpRect.left = 0;
            bmpRect.right = sw;
            bmpRect.top = (sh - h) / 2;
            bmpRect.bottom = sh - bmpRect.top;
        }
    }

    public static Bitmap mosaic(Bitmap src, int blockSize, int shadowColor) {
        int bitmapWidth = src.getWidth();
        int bitmapHeight = src.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                Bitmap.Config.ARGB_8888);//创建画布
        int row = bitmapWidth / blockSize;// 获得列的切线
        int col = bitmapHeight / blockSize;// 获得行的切线
        int[] block = new int[blockSize * blockSize];
        for (int i = 0; i <= row; i++) {
            for (int j = 0; j <= col; j++) {
                int length = block.length;
                int flag = 0;// 是否到边界标志
                if (i == row && j != col) {
                    length = (bitmapWidth - i * blockSize) * blockSize;
                    if (length == 0) {
                        break;// 边界外已经没有像素
                    }
                    src.getPixels(block, 0, blockSize, i * blockSize, j
                                    * blockSize, bitmapWidth - i * blockSize,
                            blockSize);

                    flag = 1;
                } else if (i != row && j == col) {
                    length = (bitmapHeight - j * blockSize) * blockSize;
                    if (length == 0) {
                        break;// 边界外已经没有像素
                    }
                    src.getPixels(block, 0, blockSize, i * blockSize, j
                            * blockSize, blockSize, bitmapHeight - j
                            * blockSize);
                    flag = 2;
                } else if (i == row && j == col) {
                    length = (bitmapWidth - i * blockSize)
                            * (bitmapHeight - j * blockSize);
                    if (length == 0) {
                        break;// 边界外已经没有像素
                    }
                    src.getPixels(block, 0, blockSize, i * blockSize, j
                                    * blockSize, bitmapWidth - i * blockSize,
                            bitmapHeight - j * blockSize);

                    flag = 3;
                } else {
                    src.getPixels(block, 0, blockSize, i * blockSize, j
                            * blockSize, blockSize, blockSize);//取出像素数组
                }

                int r = 0, g = 0, b = 0, a = 0;
                for (int k = 0; k < length; k++) {
                    r += Color.red(block[k]);
                    g += Color.green(block[k]);
                    b += Color.blue(block[k]);
                    a += Color.alpha(block[k]);
                }
                int color = Color.argb(a / length, r / length, g / length, b
                        / length);//求块内所有颜色的平均值
                for (int k = 0; k < length; k++) {
                    block[k] = color;
                }
                if (flag == 1) {
                    bitmap.setPixels(block, 0, bitmapWidth - i * blockSize,
                            i * blockSize, j
                                    * blockSize, bitmapWidth - i * blockSize,
                            blockSize);
                } else if (flag == 2) {
                    bitmap.setPixels(block, 0, blockSize, i * blockSize, j
                            * blockSize, blockSize, bitmapHeight - j
                            * blockSize);
                } else if (flag == 3) {
                    bitmap.setPixels(block, 0, blockSize, i * blockSize, j
                                    * blockSize, bitmapWidth - i * blockSize,
                            bitmapHeight - j * blockSize);
                } else {
                    bitmap.setPixels(block, 0, blockSize, i * blockSize, j
                            * blockSize, blockSize, blockSize);
                }

            }
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(shadowColor);
        return bitmap;
    }

    static Bitmap blur(Context ctx, Bitmap image, float scale, int shadowColor, float radius) {
        int width = Math.round(image.getWidth() * scale);
        int height = Math.round(image.getHeight() * scale);

        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        Canvas canvas = new Canvas(inputBitmap);
        canvas.drawColor(shadowColor);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(ctx);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(radius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        inputBitmap.recycle();
        return outputBitmap;
    }
}
