package com.ycuwq.datepicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import java.util.List;


/**
 * 滚动选择器
 * Created by yangchen on 2017/12/12.
 */
@SuppressWarnings("FieldCanBeLocal")
public class WheelPicker<T> extends View {
	private final String TAG = getClass().getSimpleName();

	/**
	 * 数据集合
	 */
	private List<T> mDataList;
	/**
	 * 选择的Text的颜色
	 */
	private int mTextColor = Color.BLACK;

	private int mTextSize = 80;

	private Paint mPaint;

	private int mTextMaxWidth, mTextMaxHeight;
	private int mHalfVisibleItemCount = 2;

	private int mItemSpace = 8;

	private int mItemHeight;

	private int mCurrentItemPosition;

	private Rect mDrawnRect;

	private Rect mChooseRect;

	private int mItemDrawX, mItemDrawY;

	private Scroller mScroller;

	private int mTouchSlop;

	private VelocityTracker mTracker;

	private int mTouchDownY;
	/**
	 * Y轴Scroll滚动的位移
	 */
	private int mScrollOffsetY;

	/**
	 * 最后手指Down事件的Y轴坐标，用于计算拖动距离
	 */
	private int mLastDownY;

	/**
	 * 是否循环读取
	 */
	private boolean mIsCyclic = true;

	/**
	 * 最大可以Fling的距离
	 */
	private int mMaxFlingY, mMinFlingY;
	/**
	 * 滚轮滑动时的最小/最大速度
	 */
	private int mMinimumVelocity = 50, mMaximumVelocity = 10000;

	private Handler mHandler = new Handler();

	private Runnable mScrollerRunnable = new Runnable() {
		@Override
		public void run() {
			if (mScroller.computeScrollOffset()) {
				int scrollerCurrY = mScroller.getCurrY();
				if (mIsCyclic) {
					int visibleItemCount = 2 * mHalfVisibleItemCount + 1;
					//判断超过上下限直接令其恢复到初始坐标的值
					if (scrollerCurrY > visibleItemCount * mItemHeight) {
						scrollerCurrY = scrollerCurrY % (mDataList.size() * mItemHeight);
					} else if (scrollerCurrY < -(visibleItemCount + mDataList.size()) * mItemHeight) {
						scrollerCurrY = (scrollerCurrY % mDataList.size() * mItemHeight) + mDataList.size() * mItemHeight;
					}
				}
				mScrollOffsetY = scrollerCurrY;
				postInvalidate();
				mHandler.postDelayed(this, 16);
			}
		}
	};

	public WheelPicker(Context context) {
		this(context, null);
	}

	public WheelPicker(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs,0);
	}

	public WheelPicker(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WheelPicker);
		a.recycle();
		initPaint();
		mDrawnRect = new Rect();
		mChooseRect = new Rect();
		mScroller = new Scroller(context);
		ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = configuration.getScaledTouchSlop();

	}


	public void setDataList(@NonNull List<T> dataList) {
		mDataList = dataList;
		if (dataList.size() == 0) {
			return;
		}
		computeTextSize();
		mCurrentItemPosition = 0;
	}

	public void computeTextSize() {
		mTextMaxWidth = mTextMaxHeight = 0;
		if (mDataList.size() == 0) {
			return;
		}
		mTextMaxWidth = (int) mPaint.measureText(mDataList.get(mDataList.size() - 1).toString());
		Paint.FontMetrics metrics = mPaint.getFontMetrics();
		mTextMaxHeight = (int) (metrics.bottom - metrics.top);
	}

	private void initPaint() {
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.LINEAR_TEXT_FLAG);
		mPaint.setStyle(Paint.Style.FILL);
		mPaint.setTextAlign(Paint.Align.CENTER);
		mPaint.setColor(mTextColor);
		mPaint.setTextSize(mTextSize);
	}

	/**
	 * 显示的个数等于上下两边Item的个数+ 中间的ITem
	 * @return
	 */
	private int getVisibleItemCount() {
		return mHalfVisibleItemCount * 2 + 1;
	}

	/**
	 *  计算实际的大小
	 * @param specMode 测量模式
	 * @param specSize 测量的大小
	 * @param size     需要的大小
	 * @return 返回的数值
	 */
	private int measureSize(int specMode, int specSize, int size) {
		if (specMode == MeasureSpec.EXACTLY) {
			return specSize;
		} else {
			return Math.min(specSize, size);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int specWidthSize = MeasureSpec.getSize(widthMeasureSpec);
		int specWidthMode = MeasureSpec.getMode(widthMeasureSpec);
		int specHeightSize = MeasureSpec.getSize(heightMeasureSpec);
		int specHeightMode = MeasureSpec.getMode(heightMeasureSpec);

		int width = mTextMaxWidth;
		int height = mTextMaxHeight * getVisibleItemCount() + mItemSpace * (mHalfVisibleItemCount - 1);

		width += getPaddingLeft() + getPaddingRight();
		height += getPaddingTop() + getPaddingBottom();
		setMeasuredDimension(measureSize(specWidthMode, specWidthSize, width),
				measureSize(specHeightMode, specHeightSize, height));
	}

	/**
	 * 计算Fling极限
	 * 如果为Cyclic模式则为Integer的极限值，如果正常模式，则为一整个数据集的上下限。
	 */
	private void computeFlingLimitY() {
		int currentItemOffset = mCurrentItemPosition * mItemHeight;
		mMinFlingY = mIsCyclic ? Integer.MIN_VALUE :
				- mItemHeight * (mDataList.size() - 1) + currentItemOffset;
		mMaxFlingY = mIsCyclic ? Integer.MAX_VALUE : currentItemOffset;
	}
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		mDrawnRect.set(getPaddingLeft(), getPaddingTop(),
				getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
		mItemHeight = mDrawnRect.height() / getVisibleItemCount();
		mItemDrawX = mDrawnRect.centerX();
		mItemDrawY = (int) ((mItemHeight - (mPaint.ascent() + mPaint.descent())) / 2);
		//中间的Item边框
		mChooseRect.set(getPaddingLeft(), mItemHeight * mHalfVisibleItemCount,
				getWidth() - getPaddingRight(), mItemHeight + mItemHeight * mHalfVisibleItemCount);
		computeFlingLimitY();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int drawnDataStartPos = - mScrollOffsetY / mItemHeight;
		mPaint.setColor(mTextColor);
		mPaint.setStyle(Paint.Style.FILL);
		//首尾各多绘制一个用于缓冲
		for (int drawDataPos = drawnDataStartPos - mHalfVisibleItemCount - 1;
            drawDataPos <= drawnDataStartPos + mHalfVisibleItemCount + 1; drawDataPos ++) {
			int pos = drawDataPos;
			if (mIsCyclic) {
				if (drawDataPos < 0) {
					pos = mDataList.size() + drawDataPos;
				} else if (drawDataPos > mDataList.size() - 1) {
					pos = drawDataPos - mDataList.size();
				}
			} else {
				if (drawDataPos < 0 || drawDataPos > mDataList.size() - 1) {
					continue;
				}
			}
			if (pos < 0 || pos > mDataList.size() -1) {
				continue;
			}
			T t = mDataList.get(pos);
			int drawY = mItemDrawY + (drawDataPos + mHalfVisibleItemCount) * mItemHeight + mScrollOffsetY;

			canvas.drawText(t.toString(), mItemDrawX, drawY, mPaint);
		}
		mPaint.setStyle(Paint.Style.STROKE);
		canvas.drawRect(mChooseRect, mPaint);

	}


	@Override
	public boolean onTouchEvent(MotionEvent event) {

		if (mTracker == null) {
			mTracker = VelocityTracker.obtain();
		}
		mTracker.addMovement(event);

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (!mScroller.isFinished()) {
					mScroller.abortAnimation();
				}
				mTracker.clear();
				mTouchDownY = mLastDownY = (int) event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				if (Math.abs(mTouchDownY - event.getY()) < mTouchSlop) {
					break;
				}
				float move = event.getY() - mLastDownY;
				mScrollOffsetY += move;
				mLastDownY = (int) event.getY();
				invalidate();
				break;
			case MotionEvent.ACTION_UP:
				mTracker.computeCurrentVelocity(1000, mMaximumVelocity);
				int velocity = (int) mTracker.getYVelocity();
				if (Math.abs(velocity) > mMinimumVelocity) {
					mScroller.fling(0, mScrollOffsetY, 0, (int) mTracker.getYVelocity(),
							0, 0, mMinFlingY, mMaxFlingY);
					mScroller.setFinalY(mScroller.getFinalY() +
							computeDistanceToEndPoint(mScroller.getFinalY() % mItemHeight));
				} else {
					mScroller.startScroll(0, mScrollOffsetY, 0,
							computeDistanceToEndPoint(mScrollOffsetY % mItemHeight));
				}
				if (!mIsCyclic) {
					if (mScroller.getFinalY() > mMaxFlingY) {
						mScroller.setFinalY(mMaxFlingY);
					} else if (mScroller.getFinalY() < mMinFlingY) {
						mScroller.setFinalY(mMinFlingY);
					}
				}
				mHandler.post(mScrollerRunnable);
				mTracker.recycle();
				mTracker = null;
				break;
		}
		return true;
	}

	private int computeDistanceToEndPoint(int remainder) {
		if (Math.abs(remainder) > mItemHeight / 2)
			if (mScrollOffsetY < 0)
				return -mItemHeight - remainder;
			else
				return mItemHeight - remainder;
		else
			return -remainder;
	}



	@Override
	public boolean performClick() {
		return super.performClick();
	}
}