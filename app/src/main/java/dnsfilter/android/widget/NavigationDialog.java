package dnsfilter.android.widget;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import dnsfilter.android.R;

public class NavigationDialog extends Dialog {
    private static final String TAG = "Navigation dialog";
    private static final Long SWIPE_ANIMATION_DURATION = 500L;
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
                Log.d(TAG, "Start dX = " + startDX);
                return true;
            case MotionEvent.ACTION_MOVE:
                Log.d(TAG, "raw X event = " + event.getRawX());
                float shift = startDX - event.getRawX();
                totalShiftX = shift;
                Log.d(TAG, "shift = " + shift + " and total shift = " + totalShiftX);
                if (shift >= 0) {
                    containerView.setX(-shift);
                }
                return true;
            case MotionEvent.ACTION_UP:
                Log.d(TAG, "current time = " + System.currentTimeMillis() + " downtime = " + event.getDownTime() +
                        " diff = " + (SystemClock.uptimeMillis() - event.getDownTime()));
                if (SystemClock.uptimeMillis() - event.getDownTime() < 100L) {
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
