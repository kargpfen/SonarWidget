package org.vaadin.sonarwidget.widgetset.client.ui;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.CanvasPixelArray;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.ImageData;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

public class ImageRenderer {
    private Image image;
    private Canvas canvas;
    private int offset;
    private float maxDepthArea = 0.0f;
    private float range = 0.0f;
    private boolean isDirty = false;
    private DepthData model;
    private Widget parent;
    private int tilewidth;
    private boolean sidescan;
    private boolean overlay;
    private int color;

    public static int COLOR_RED = 1;
    public static int COLOR_GREEN = 2;
    public static int COLOR_BLUE = 4;
    public static int COLOR_INVERSE = 8;
    public static int COLOR_MAPCOLORS = 16;
    public static int COLOR_MORECONTRAST = 32;
    public static int COLOR_LESSCONTRAST = 64;
    public static int COLOR_CONTRASTBOOST = 128;

    public interface Listener {
        void doneLoading(int offset);
    }

    public ImageRenderer(String pic, DepthData model, Widget parent,
            final int offset, int tilewidth,
            final Listener listener) {
        this.image = new Image(pic);
        RootPanel.get().add(image);
        image.setVisible(false);
        this.offset = offset;
        this.model = model;
        this.parent = parent;
        this.tilewidth = tilewidth;
        this.canvas = addCanvas(tilewidth);

        // When image loads start transition animation
        this.image.addLoadHandler(new LoadHandler() {

            @Override
            public void onLoad(LoadEvent event) {

                getCanvas().getElement().getStyle().setOpacity(0);

                float maxDepth = ImageRenderer.this.model
                        .getLowlimit(ImageRenderer.this.offset);
                for (int loop = ImageRenderer.this.offset; loop < ImageRenderer.this.offset
                        + VSonarWidget.tilewidth; loop++) {
                    float lowlimit = ImageRenderer.this.model.getLowlimit(loop);
                    if (maxDepth < lowlimit) {
                        maxDepth = lowlimit;
                    }
                }

                setMaxDepthArea(maxDepth);

                new Timer() {
                    private int alpha = 0;

                    @Override
                    public void run() {

                        getCanvas().getElement().getStyle()
                                .setOpacity(alpha * 0.1);

                        // when animation has reached
                        // zero opacity stop animation timer.
                        if (alpha >= 10) {
                            isDirty = true;
                            listener.doneLoading(offset);
                            this.cancel();
                        }

                        alpha++;
                    }
                }.scheduleRepeating(24);
            }
        });
    }

    private Canvas addCanvas(int canvaswidth) {
        Canvas canvas = Canvas.createIfSupported();

        canvas.setCoordinateSpaceHeight(parent.getElement().getClientHeight());
        canvas.setHeight(parent.getElement().getClientHeight() + "px");
        canvas.setCoordinateSpaceWidth(canvaswidth);
        canvas.setWidth(canvaswidth + "px");
        canvas.getElement().getStyle().setPosition(Position.ABSOLUTE);
        canvas.getElement().getStyle().setLeft(offset, Unit.PX);

        return canvas;
    }

    public Canvas getCanvas() {
        return this.canvas;
    }

    public void setMaxDepthArea(float maxDepth) {
        this.maxDepthArea = maxDepth;
    }

    public float getMaxDepthArea() {
        return this.maxDepthArea;
    }

    public void setRange(float range) {
        if (this.range != range) {
            this.range = range;
            isDirty = true;
        }
    }

    public void setColor(int color) {
        if (this.color != color) {
            isDirty = true;
        }

        this.color = color;
    }

    public void setOverlay(boolean overlay) {
        if (this.overlay != overlay) {
            isDirty = true;
        }

        this.overlay = overlay;
    }

    public void setSidescan(boolean sidescan) {
        if (this.sidescan != sidescan) {
            isDirty = true;
        }

        this.sidescan = sidescan;
    }

    public boolean isVisible(int p1) {
        int left = p1;
        int right = left + parent.getOffsetWidth();

        if (this.offset < left && this.offset + tilewidth < left
                || this.offset > right && this.offset + tilewidth > right) {
            return false;
        }

        return true;
    }

    public boolean isCurrentRenderer(int p) {

        if (this.offset <= p && this.offset + tilewidth >= p) {
            return true;
        }

        return false;
    }

    public void render() {
        if (!isDirty) {
            return;
        }

        Context2d context = canvas.getContext2d();
        canvas.setCoordinateSpaceHeight(parent.getElement().getClientHeight());
        canvas.setHeight(parent.getElement().getClientHeight() + "px");

        normalizeImage(ImageElement.as(image.getElement()));
        colorizeImage(context);
        drawOverlay(offset, context);

        isDirty = false;
    }

    public void clearCanvas() {
        final Context2d context = canvas.getContext2d();
        context.clearRect(0, 0, canvas.getCoordinateSpaceWidth(),
                canvas.getCoordinateSpaceHeight());
    }

    private void normalizeImage(ImageElement source) {
        Context2d context = canvas.getContext2d();
        int width = context.getCanvas().getOffsetWidth();
        int height = context.getCanvas().getClientHeight();

        int sourceHeight = source.getHeight();
        int depthRangeStart = 0;
        int prevDepthRange = 0;
        float scaling = 1;

        clearCanvas();

        // draw canvas in sections cut in depth ranges.
        for (int x = 0; x < width; x++) {
            int depthRange = height;

            if (this.range != 0.0f) {
                depthRange = (int) (((float) height)
                        * model.getLowlimit(offset + x) / this.range);
            }

            // scaling has changed so draw section here
            if (prevDepthRange != depthRange && x > 0) {
                if (sidescan) {
                    context.drawImage(source, depthRangeStart, 0, x
                            - depthRangeStart, sourceHeight, depthRangeStart,
                            (height - (height / scaling)) / 2, x
                                    - depthRangeStart, height / scaling);
                } else {
                    context.drawImage(source, depthRangeStart, 0, x
                            - depthRangeStart, sourceHeight, depthRangeStart,
                            0, x
                            - depthRangeStart, height / scaling);
                }

                depthRangeStart = x;
            }

            scaling = (float) height / (float) depthRange;
            prevDepthRange = depthRange;
        }

        // draw tail section
        if (sidescan && depthRangeStart != width) {
            context.drawImage(source, depthRangeStart, 0, width
                    - depthRangeStart, sourceHeight, depthRangeStart,
                    (height - (height / scaling)) / 2, width - depthRangeStart,
                    height / scaling);
        } else if (depthRangeStart != width) {
            context.drawImage(source, depthRangeStart, 0, width
                    - depthRangeStart, sourceHeight, depthRangeStart, 0, width
                    - depthRangeStart, height / scaling);
        }
    }

    private void colorizeImage(Context2d context) {
        int colormask = color;
        if (colormask == 0) {
            return;
        }

        int shift = 1;
        if ((colormask & COLOR_CONTRASTBOOST) != 0) {
            shift = 2;
        }

        ImageData data = context.getImageData(0, 0, context.getCanvas()
                .getOffsetWidth(), parent.getElement().getClientHeight());
        CanvasPixelArray array = data.getData();
        for (int x = 0; x < array.getLength(); x += 4) {
            int red = array.get(x);
            int green = array.get(x + 1);
            int blue = array.get(x + 2);
            // int alpha = array.get(x+3);

            red = (colormask & COLOR_RED) != 0 ? red : 0;
            green = (colormask & COLOR_GREEN) != 0 ? green : 0;
            blue = (colormask & COLOR_BLUE) != 0 ? blue : 0;

            if ((colormask & COLOR_INVERSE) != 0) {
                red = 255 - red;
                green = 255 - green;
                blue = 255 - blue;
            }

            if ((colormask & COLOR_MORECONTRAST) != 0) {
                red <<= shift;
                green <<= shift;
                blue <<= shift;

                red = Math.min(255, red);
                green = Math.min(255, green);
                blue = Math.min(255, blue);
            }

            if ((colormask & COLOR_LESSCONTRAST) != 0) {
                red >>= shift;
                green >>= shift;
                blue >>= shift;
            }

            if ((colormask & COLOR_MAPCOLORS) != 0) {
                red &= 0xC0;
                green &= 0x60;
                blue &= 0x3F;
            }

            array.set(x, red & 0xFF);
            array.set(x + 1, green & 0xFF);
            array.set(x + 2, blue & 0xFF);
            // array.set(x+3, alpha);
        }

        context.putImageData(data, 0, 0);
    }

    public float getRange() {
        return this.range;
    }

    public int mapToDepth(int x, int y) {
        float lowlimit = model.getLowlimit(x);

        if (this.range == 0.0f) {
            return y;
        }

        int mappedY = (int) (y * lowlimit / this.range);

        if (sidescan) {
            mappedY = (int) (mappedY + (canvas.getOffsetHeight() - canvas
                    .getOffsetHeight() * lowlimit / this.range) / 2);
        }

        return mappedY;
    }

    /**
     * Overlay is drawn on top of depth image. Only red depth line is
     * implemented so far.
     * 
     * @param offset
     * @param context
     *            draw context
     */
    private void drawOverlay(int offset, final Context2d context) {
        if (!overlay) {
            return;
        }

        int width = context.getCanvas().getOffsetWidth();
        double[] points = new double[width];
        double[] mirrorpoints = null;

        if (sidescan) {
            mirrorpoints = new double[width];
        }

        for (int loop = 0; loop < width; loop++) {
            float depth = model.getDepth(loop + offset);
            float lowlimit = model.getLowlimit(loop + offset);

            double yPos = parent.getElement().getClientHeight() * depth
                    / lowlimit;
            yPos = mapToDepth(loop + this.offset, (int) yPos);

            if (sidescan) {
                yPos = (parent.getElement().getClientHeight() / 2) * depth
                        / lowlimit + parent.getElement().getClientHeight() / 2;
                yPos = mapToDepth(loop + this.offset, (int) yPos);
                mirrorpoints[loop] = parent.getElement().getClientHeight() / 2
                        - (yPos - parent.getElement().getClientHeight() / 2);
            }

            points[loop] = yPos;
        }

        drawLine(context, points);
        if (mirrorpoints != null) {
            drawLine(context, mirrorpoints);
        }
    }

    private void drawLine(Context2d context, double[] points) {
        context.setStrokeStyle("red");
        context.beginPath();

        for (int loop = 0; loop < points.length; loop++) {
            double yPos = points[loop];

            if (loop == 0) {
                context.moveTo(loop, yPos);
            } else {
                context.lineTo(loop, yPos);
            }
        }

        context.stroke();
    }
}
