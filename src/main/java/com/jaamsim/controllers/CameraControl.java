/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2012 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.jaamsim.controllers;

import com.jaamsim.math.Plane;
import com.jaamsim.math.Quaternion;
import com.jaamsim.math.Ray;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec3d;
import com.jaamsim.math.Vec4d;
import com.jaamsim.render.CameraInfo;
import com.jaamsim.render.RenderUtils;
import com.jaamsim.render.Renderer;
import com.jaamsim.render.WindowInteractionListener;
import com.jaamsim.ui.FrameBox;
import com.jaamsim.ui.View;
import com.jogamp.newt.event.MouseEvent;
import com.sandwell.JavaSimulation.ChangeWatcher;

public class CameraControl implements WindowInteractionListener {

	private static final double ZOOM_FACTOR = 1.1;
	// Scale from pixels dragged to radians rotated
	private static final double ROT_SCALE_X = 0.005;
	private static final double ROT_SCALE_Z = 0.005;

	private Renderer _renderer;
	private int _windowID;
	private View _updateView;

	private int _windowPosSetsToIgnore = 4;

	private ChangeWatcher.Tracker _viewTracker;

	private static class PolarInfo {
		double rotZ; // The spherical coordinate that rotates around Z (in radians)
		double rotX; // Ditto for X
		double radius; // The distance the camera is from the view center
		Vec3d viewCenter;
	}

	public CameraControl(Renderer renderer, View updateView) {
		_renderer = renderer;
		_updateView = updateView;

		_viewTracker = _updateView.getChangeTracker();
	}

	@Override
	public void mouseDragged(WindowInteractionListener.DragInfo dragInfo) {

		// Give the RenderManager first crack at this
		if (RenderManager.inst().handleDrag(dragInfo)) {
			RenderManager.inst().queueRedraw();
			return; // Handled
		}

		if (dragInfo.controlDown()) {
			return;
		}
		if (!_updateView.isMovable() || _updateView.isScripted()) {
			return;
		}

		PolarInfo pi = getPolarCoordsFromView();

		if (dragInfo.shiftDown()) {
			// handle rotation
			handleRotation(pi, dragInfo.x, dragInfo.y, dragInfo.dx, dragInfo.dy, dragInfo.button);
		} else {
			handlePan(pi, dragInfo.x, dragInfo.y, dragInfo.dx, dragInfo.dy, dragInfo.button);
		}

		updateCamTrans(pi, true);
	}

	private void handleRotation(PolarInfo pi, int x, int y, int dx, int dy,
	                            int button) {

		pi.rotZ -= dx * ROT_SCALE_Z;
		pi.rotX -= dy * ROT_SCALE_X;

		if (pi.rotX < 0) pi.rotX = 0;
		if (pi.rotX > Math.PI) pi.rotX = Math.PI;

		if (pi.rotZ < 0) pi.rotZ += 2*Math.PI;
		if (pi.rotZ > 2*Math.PI) pi.rotZ -= 2*Math.PI;
	}

	private void handlePan(PolarInfo pi, int x, int y, int dx, int dy,
	                            int button) {

		Renderer.WindowMouseInfo info = _renderer.getMouseInfo(_windowID);
		if (info == null) return;

		if (_updateView.isFollowing() || _updateView.isScripted()) {
			return; // We can not pan while following an object
		}

		//Cast a ray into the XY plane both for now, and for the previous mouse position
		Ray currRay = RenderUtils.getPickRayForPosition(info.cameraInfo, x, y, info.width, info.height);
		Ray prevRay = RenderUtils.getPickRayForPosition(info.cameraInfo, x - dx, y - dy, info.width, info.height);

		double currDist = Plane.XY_PLANE.collisionDist(currRay);
		double prevDist = Plane.XY_PLANE.collisionDist(prevRay);
		if (currDist < 0 || prevDist < 0 ||
		    currDist == Double.POSITIVE_INFINITY ||
		    prevDist == Double.POSITIVE_INFINITY)
		{
			// We're either parallel to or beneath the XY plane, bail out
			return;
		}

		Vec4d currIntersect = currRay.getPointAtDist(currDist);
		Vec4d prevIntersect = prevRay.getPointAtDist(prevDist);

		Vec4d diff = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
		diff.sub3(currIntersect, prevIntersect);

		pi.viewCenter.sub3(diff);

	}

	@Override
	public void mouseWheelMoved(int windowID, int x, int y, int wheelRotation) {

		if (!_updateView.isMovable() || _updateView.isScripted()) {
			return;
		}

		PolarInfo pi = getPolarCoordsFromView();

		int rot = wheelRotation;

		if (rot > 0) {
			for (int i = 0; i < rot; ++i) {
				pi.radius = pi.radius / ZOOM_FACTOR;
			}
		} else
		{
			rot *= -1;
			for (int i = 0; i < rot; ++i) {
				pi.radius = pi.radius * ZOOM_FACTOR;
			}
		}

		updateCamTrans(pi, true);
	}

	@Override
	public void mouseClicked(int windowID, int x, int y, int button, int modifiers) {
		if (!RenderManager.isGood()) { return; }

		RenderManager.inst().hideExistingPopups();
		if (button  == 3) {
			// Hand this off to the RenderManager to deal with
			RenderManager.inst().popupMenu(windowID);
		}
		if (button == 1 && (modifiers & WindowInteractionListener.MOD_CTRL) == 0) {
			RenderManager.inst().handleSelection(windowID);
		}
	}

	@Override
	public void mouseMoved(int windowID, int x, int y) {
		if (!RenderManager.isGood()) { return; }

		RenderManager.inst().mouseMoved(windowID, x, y);
	}

	@Override
	public void rawMouseEvent(MouseEvent me) {
	}

	@Override
	public void mouseEntry(int windowID, int x, int y, boolean isInWindow) {
		if (!RenderManager.isGood()) { return; }

		if (isInWindow && RenderManager.inst().isDragAndDropping()) {
			RenderManager.inst().createDNDObject(windowID, x, y);
		}
	}

	private void updateCamTrans(PolarInfo pi, boolean updateInputs) {

		Vec4d zOffset = new Vec4d(0, 0, pi.radius, 1.0d);

		Quaternion rot = Quaternion.Rotation(pi.rotZ, Vec4d.Z_AXIS);
		rot.mult(Quaternion.Rotation(pi.rotX, Vec4d.X_AXIS), rot);

		Transform finalTrans = new Transform(pi.viewCenter);

		finalTrans.merge(new Transform(Vec4d.ORIGIN, rot, 1), finalTrans);
		finalTrans.merge(new Transform(zOffset), finalTrans);


		if (updateInputs) {
			updateViewPos(finalTrans.getTransRef(), pi.viewCenter);
		}

		// Finally update the renders camera info
		CameraInfo info = _renderer.getCameraInfo(_windowID);
		if (info == null) {
			// This window has not been opened yet (or is closed) force a redraw as everything will catch up
			// and the information has been saved to the view object
			_updateView.forceDirty();
			RenderManager.inst().queueRedraw();
			return;
		}

		info.trans = finalTrans;

		// HACK, manually set the near and far planes to keep the XY plane in view. This will be a bad thing when we go real 3D
		// TODO: not this
		info.nearDist = pi.radius * 0.1;
		info.farDist = pi.radius * 10;

		_renderer.setCameraInfoForWindow(_windowID, info);

		// Queue a redraw
		RenderManager.inst().queueRedraw();
	}

	public void setRotationAngles(double rotX, double rotZ) {
		PolarInfo pi = getPolarCoordsFromView();
		pi.rotX = rotX;
		pi.rotZ = rotZ;
		updateCamTrans(pi, true);
	}

	@Override
	public void setWindowID(int windowID) {
		_windowID = windowID;
	}

	@Override
	public void windowClosing() {
		if (!RenderManager.isGood()) { return; }

		RenderManager.inst().hideExistingPopups();
		RenderManager.inst().windowClosed(_windowID);
	}

	@Override
	public void mouseButtonDown(int windowID, int x, int y, int button, boolean isDown, int modifiers) {
		if (!RenderManager.isGood()) { return; }

		RenderManager.inst().handleMouseButton(windowID, x, y, button, isDown, modifiers);
	}

	@Override
	public void windowGainedFocus() {
		if (!RenderManager.isGood()) { return; }

		RenderManager.inst().setActiveWindow(_windowID);
	}

	/**
	 * Set the position information in the saved view to match this window
	 */
	private void updateViewPos(Vec3d viewPos, Vec3d viewCenter) {
		if (_updateView == null) {
			return;
		}

		_updateView.updateCenterAndPos(viewCenter, viewPos);

		FrameBox.valueUpdate();
	}

	@Override
	public void windowMoved(int x, int y, int width, int height)
	{
		// HACK!
		// Ignore the first 4 sets as these are spurious from the windowing system and we don't want to dirty
		// the simulation state. This should die when we have better input change detection
		if (_windowPosSetsToIgnore > 0) {
			_windowPosSetsToIgnore--;
			return;
		}

		// Filter out large negative values occuring from window minimize:
		if (x < -30000 || y < - 30000)
			return;

		_updateView.setWindowPos(x, y, width, height);
	}

	public View getView() {
		return _updateView;
	}

	private PolarInfo getPolarCoordsFromView() {

		PolarInfo pi = new PolarInfo();

		Vec3d camPos = _updateView.getGlobalPosition();
		pi.viewCenter = _updateView.getGlobalCenter();

		Vec3d viewDiff = new Vec3d();
		viewDiff.sub3(camPos, pi.viewCenter);

		pi.radius = viewDiff.mag3();

		pi.rotZ = Math.atan2(viewDiff.x, -viewDiff.y);

		double xyDist = Math.hypot(viewDiff.x, viewDiff.y);

		pi.rotX = Math.atan2(xyDist, viewDiff.z);

		// If we are near vertical (within about a quarter of a degree) don't rotate around Z (take X as up)
		if (Math.abs(pi.rotX) < 0.005) {
			pi.rotZ = 0;
		}
		return pi;
	}

	public void checkForUpdate() {
		if (!_viewTracker.checkAndClear()) {
			return;
		}

		PolarInfo pi = getPolarCoordsFromView();
		updateCamTrans(pi, false);

	}
}