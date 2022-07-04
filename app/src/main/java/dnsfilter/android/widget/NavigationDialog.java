package dnsfilter.android.widget;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import dnsfilter.android.R;

public class NavigationDialog extends Dialog {
    private static final Long SWIPE_ANIMATION_DURATION = 500L;
    private static final Long SWIPE_EVENT_DURATION_THRESHOLD = 100L;
    private Float startDX = 0f;
    private Float totalShiftX = 0f;
    private View containerView = null;

    public NavigationDialog(Context context) {
        super(context);
    }

    public NavigationDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    protected NavigationDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        containerView = findViewById(R.id.navContainer).getRootView();
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startDX = event.getRawX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float shift = startDX - event.getRawX();
                totalShiftX = shift;
                if (shift >= 0) {
                    containerView.setX(-shift);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (SystemClock.uptimeMillis() - event.getDownTime() < SWIPE_EVENT_DURATION_THRESHOLD) {
                    onCancelSwipe();
                    return super.onTouchEvent(event);
                }

                if (totalShiftX > startDX / 4) {
                    containerView.setX(0f);
                    hide();
                } else {
                    onCancelSwipe();
                    return true;
                }
                break;
            default:
                return super.onTouchEvent(event);
        }
        return true;
    }

    private void onCancelSwipe() {
        containerView.animate().x(0f).setDuration(SWIPE_ANIMATION_DURATION).start();
        totalShiftX = 0f;
    }
}
