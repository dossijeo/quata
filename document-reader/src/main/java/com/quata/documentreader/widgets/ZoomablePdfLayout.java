package com.quata.documentreader.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;

public class ZoomablePdfLayout extends FrameLayout {
    private final ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float translationX = 0f;
    private float lastX = 0f;
    private float lastY = 0f;
    private boolean horizontalDrag = false;

    public ZoomablePdfLayout(Context context) {
        this(context, null);
    }

    public ZoomablePdfLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomablePdfLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(true);
        setClipToPadding(true);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (scale <= 1f) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                horizontalDrag = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - lastX);
                float dy = Math.abs(event.getY() - lastY);
                horizontalDrag = dx > dy && dx > 8f;
                if (horizontalDrag) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            default:
                horizontalDrag = false;
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (scale > 1f && event.getPointerCount() == 1) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    if (horizontalDrag || Math.abs(dx) > Math.abs(dy)) {
                        translationX = clampTranslation(translationX + dx);
                        applyTransform();
                        horizontalDrag = true;
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    horizontalDrag = false;
                    return true;
                default:
                    break;
            }
        }
        return event.getPointerCount() > 1 || super.onTouchEvent(event);
    }

    private void setScaleAround(float nextScale, float focusX) {
        float previousScale = scale;
        scale = Math.max(1f, Math.min(nextScale, 5f));
        if (scale == 1f) {
            translationX = 0f;
        } else {
            float contentFocusX = (focusX - translationX) / previousScale;
            translationX = clampTranslation(focusX - contentFocusX * scale);
        }
        applyTransform();
    }

    private float clampTranslation(float value) {
        float maxShift = getWidth() * (scale - 1f);
        if (maxShift <= 0f) {
            return 0f;
        }
        return Math.max(-maxShift, Math.min(0f, value));
    }

    private void applyTransform() {
        if (getChildCount() == 0) {
            return;
        }
        android.view.View child = getChildAt(0);
        child.setPivotX(0f);
        child.setPivotY(0f);
        child.setScaleX(scale);
        child.setScaleY(scale);
        child.setTranslationX(translationX);
        child.setTranslationY(0f);
    }

    private final class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            setScaleAround(scale * detector.getScaleFactor(), detector.getFocusX());
            return true;
        }
    }
}
