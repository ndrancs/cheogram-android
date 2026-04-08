package com.cheogram.android;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

// Works around a long-standing Android bug where ViewPager's onInterceptTouchEvent /
// onTouchEvent can trip over a stale mActivePointerId: a MOVE arrives whose pointer
// set no longer contains the id cached from the earlier DOWN, so findPointerIndex
// returns -1 and getX(-1) throws IllegalArgumentException from native code. Returning
// false means "not intercepting / not consuming", which lets children handle the
// event and lets the pager recover on the next clean DOWN.
public class SafeViewPager extends androidx.viewpager.widget.ViewPager {
	public SafeViewPager(Context context) {
		super(context);
	}

	public SafeViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		try {
			return super.onInterceptTouchEvent(ev);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		try {
			return super.onTouchEvent(ev);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
