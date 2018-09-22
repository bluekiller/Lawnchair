/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.views;

import static android.support.v4.graphics.ColorUtils.compositeColors;
import static android.support.v4.graphics.ColorUtils.setAlphaComponent;

import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.util.AttributeSet;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;

/**
 * Scrim used for all-apps and shelf in Overview
 * In transposed layout, it behaves as a simple color scrim.
 * In portrait layout, it draws a rounded rect such that
 *    From normal state to overview state, the shelf just fades in and does not move
 *    From overview state to all-apps state the shelf moves up and fades in to cover the screen
 */
public class ShelfScrimView extends ScrimView {

    private final float PROGRESS_WORKSPACE = 1f;

    // In transposed layout, we simply draw a flat color.
    protected boolean mDrawingFlatColor;

    // For shelf mode
    protected int mEndAlpha;
    protected int mThresholdAlpha;
    protected float mRadius;
    protected float mMaxScrimAlpha;
    private final Paint mPaint;

    // Max vertical progress after which the scrim stops moving.
    protected float mMoveThreshold;
    protected float mCalcThreshold;
    // Minimum visible size of the scrim.
    private int mMinSize;

    protected float mScrimMoveFactor = 0;
    private int mShelfColor;
    private int mRemainingScreenColor;

    private final Path mTempPath = new Path();
    private final Path mRemainingScreenPath = new Path();
    private boolean mRemainingScreenPathValid = false;

    public ShelfScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxScrimAlpha = OVERVIEW.getWorkspaceScrimAlpha(mLauncher);

        mEndAlpha = Color.alpha(mEndScrim);
        mThresholdAlpha = Themes.getAttrInteger(context, R.attr.allAppsInterimScrimAlpha);
        mRadius = mLauncher.getResources().getDimension(R.dimen.shelf_surface_radius);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Just assume the easiest UI for now, until we have the proper layout information.
        mDrawingFlatColor = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRemainingScreenPathValid = false;
    }

    @Override
    public void reInitUi() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mDrawingFlatColor = dp.isVerticalBarLayout();

        mMinSize = dp.shelfBarSizePx + dp.getInsets().bottom;
        if (!mDrawingFlatColor) {
            float swipeLength = OverviewState.getDefaultSwipeHeight(mLauncher);
            mCalcThreshold = 1 - swipeLength / mLauncher.getAllAppsController().getShiftRange();
            mRemainingScreenPathValid = false;
            updateColors();
        }
        updateDragHandleAlpha();
        invalidate();
    }

    @Override
    public void updateColors() {
        super.updateColors();
        if (mDrawingFlatColor) {
            return;
        }

        if (mHide) {
            mMoveThreshold = mCalcThreshold;
        } else if (mProgress >= PROGRESS_WORKSPACE) {
            if (mLauncher.isInOverview() || Utilities.getLawnchairPrefs(getContext())
                    .getDockGradientStyle()) {
                mMoveThreshold = mCalcThreshold;
            } else {
                mMoveThreshold = PROGRESS_WORKSPACE;
            }
        }

        if (mProgress > mMoveThreshold) {
            mScrimMoveFactor = 1;

            if (mProgress >= 1) {
                mShelfColor = 0;
            } else {
                int alpha = Math.round(mThresholdAlpha * ACCEL_2.getInterpolation(
                        (1 - mProgress) / (1 - mMoveThreshold)));
                mShelfColor = setAlphaComponent(mEndScrim, alpha);
            }

            mRemainingScreenColor = 0;
        } else if (mProgress <= 0) {
            mScrimMoveFactor = 0;
            mShelfColor = mCurrentFlatColor;
            mRemainingScreenColor = 0;

        } else {
            mScrimMoveFactor = mProgress / mMoveThreshold;
            mRemainingScreenColor = setAlphaComponent(mScrimColor,
                    Math.round((1 - mScrimMoveFactor) * mMaxScrimAlpha * 255));

            // Merge the remainingScreenColor and shelfColor in one to avoid overdraw.
            int alpha = mEndFlatColorAlpha - Math
                    .round((mEndFlatColorAlpha - mThresholdAlpha) * mScrimMoveFactor);
            mShelfColor = setAlphaComponent(
                    compositeColors(mEndFlatColor > 0 ? mEndFlatColor : mEndScrim,
                            mRemainingScreenColor), alpha);
        }
        mDragHandle.setCaretProgress(mProgress);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float translate = drawBackground(canvas);

        if (mDragHandle != null) {
            canvas.translate(0, -translate);
            mDragHandle.draw(canvas);
            canvas.translate(0, translate);
        }
    }

    private float drawBackground(Canvas canvas) {
        if (mDrawingFlatColor) {
            onDrawFlatColor(canvas);
            if (mCurrentFlatColor != 0) {
                canvas.drawColor(mCurrentFlatColor);
            }
            return 0;
        }

        if (mShelfColor == 0) {
            return 0;
        } else if (mScrimMoveFactor <= 0) {
            onDrawFlatColor(canvas);
            canvas.drawColor(mShelfColor);
            return getHeight();
        }

        float minTop = getHeight() - mMinSize;
        if (mProgress < mMoveThreshold) {
            if (mCalcThreshold < mProgress) {
                mScrimMoveFactor = 1f;
            } else {
                mScrimMoveFactor = mProgress / mCalcThreshold;
            }
        }
        float top = minTop * mScrimMoveFactor - (mDragHandle.isHidden() ? mDragHandleSize : 0);

        // Draw the scrim over the remaining screen if needed.
        if (mRemainingScreenColor != 0) {
            if (!mRemainingScreenPathValid) {
                mTempPath.reset();
                // Using a arbitrary '+10' in the bottom to avoid any left-overs at the
                // corners due to rounding issues.
                mTempPath.addRoundRect(0, minTop, getWidth(), getHeight() + mRadius + 10,
                        mRadius, mRadius, Direction.CW);

                mRemainingScreenPath.reset();
                mRemainingScreenPath.addRect(0, 0, getWidth(), getHeight(), Direction.CW);
                mRemainingScreenPath.op(mTempPath, Op.DIFFERENCE);
            }

            float offset = minTop - top;
            canvas.translate(0, -offset);
            mPaint.setColor(mRemainingScreenColor);
            canvas.drawPath(mRemainingScreenPath, mPaint);
            canvas.translate(0, offset);
        }

        mPaint.setColor(mShelfColor);
        onDrawRoundRect(canvas, 0, top, getWidth(), getHeight() + mRadius,
                mRadius, mRadius, mPaint);
        return minTop - (mDragHandle.isHidden() ? mDragHandleSize : 0) - top;
    }

    @Override
    protected void onDrawRoundRect(Canvas canvas, float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        canvas.drawRoundRect(0, top, getWidth(), getHeight() + mRadius,
                mRadius, mRadius, mPaint);
    }
}
