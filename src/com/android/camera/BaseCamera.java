/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
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

package com.android.camera;

import com.android.camera.ui.HeadUpDisplay;

import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;

import java.util.List;

/**
 * Base class for camera implementations.
 */
public abstract class BaseCamera extends NoSearchActivity implements View.OnClickListener,
        ShutterButton.OnShutterButtonListener, SurfaceHolder.Callback, Switcher.OnSwitchListener {
    
    private static final String TAG = "BaseCamera";
    
    protected android.hardware.Camera mCameraDevice;
    protected HeadUpDisplay mHeadUpDisplay;
    protected Parameters mParameters;
    protected Menu mOptionsMenu;
    
    protected static final int ZOOM_STOPPED = 0;
    protected static final int ZOOM_START = 1;
    protected static final int ZOOM_STOPPING = 2;

    protected int mZoomState = ZOOM_STOPPED;
    protected int mTargetZoomValue;
    
    protected boolean mSmoothZoomSupported;
    protected int mZoomValue;  // The current zoom value.
    protected int mZoomMax;
    protected GestureDetector mGestureDetector;
    protected final ZoomListener mZoomListener = new ZoomListener();
    
    protected boolean mPausing = false;
    protected boolean mPreviewing = false; // True if preview is started.
        
    protected abstract void onZoomValueChanged(int index);
    
    protected void initializeZoom() {
        if (!mParameters.isZoomSupported()) return;

        // Maximum zoom value may change after preview size is set. Get the
        // latest parameters here.
        mZoomMax = mParameters.getMaxZoom();
        mSmoothZoomSupported = mParameters.isSmoothZoomSupported();
        mGestureDetector = new GestureDetector(this, new ZoomGestureListener());

        mCameraDevice.setZoomChangeListener(mZoomListener);
    }

    protected float[] getZoomRatios() {
        List<Integer> zoomRatios = mParameters.getZoomRatios();
        if (mParameters.get("taking-picture-zoom") != null) {
            // HTC camera zoom
            float result[] = new float[mZoomMax + 1];
            for (int i = 0, n = result.length; i < n; ++i) {
                result[i] = 1 + i * 0.2f;
            }
            return result;
        } else if (zoomRatios != null) {
            float result[] = new float[zoomRatios.size()];
            for (int i = 0, n = result.length; i < n; ++i) {
                result[i] = (float) zoomRatios.get(i) / 100f;
            }
            return result;

        }

        float[] result = new float[1];
        result[0] = 0.0f;
        return result;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (!super.dispatchTouchEvent(m) && mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(m);
        }
        return true;
    }

    private class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Perform zoom only when preview is started and snapshot is not in
            // progress.
            if (mPausing || !mPreviewing || mHeadUpDisplay == null) {
                return false;
            }

            if (mZoomValue < mZoomMax) {
                // Zoom in to the maximum.
                mZoomValue = mZoomMax;
            } else {
                mZoomValue = 0;
            }

            mHeadUpDisplay.setZoomIndex(mZoomValue);
            return true;
        }
    }
    
    private final class ZoomListener implements android.hardware.Camera.OnZoomChangeListener {
        public void onZoomChange(int value, boolean stopped, android.hardware.Camera camera) {
            Log.v(TAG, "Zoom changed: value=" + value + ". stopped=" + stopped);
            mZoomValue = value;
            // Keep mParameters up to date. We do not getParameter again in
            // takePicture. If we do not do this, wrong zoom value will be set.
            mParameters.setZoom(value);
            // We only care if the zoom is stopped. mZooming is set to true when
            // we start smooth zoom.
            if (stopped && mZoomState != ZOOM_STOPPED) {
                if (value != mTargetZoomValue) {
                    mCameraDevice.startSmoothZoom(mTargetZoomValue);
                    mZoomState = ZOOM_START;
                } else {
                    mZoomState = ZOOM_STOPPED;
                }
            }
        }
    }
}
