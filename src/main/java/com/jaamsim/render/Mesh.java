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
package com.jaamsim.render;

import java.util.Map;

import com.jaamsim.math.AABB;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.Ray;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec4d;

//import javax.media.opengl.*;

public class Mesh implements Renderable {

private MeshProto _proto;
private MeshProtoKey _key;
private Transform _trans;
private Vec4d _scale; // Allow for a non-uniform scale

private long _pickingID;
private VisibilityInfo _visInfo;

private AABB _bounds;

public Mesh(MeshProtoKey key, MeshProto proto, Transform trans, VisibilityInfo visInfo, long pickingID) {
	this(key, proto, trans, new Vec4d(0, 0, 0, 1.0d), visInfo, pickingID);
}

public Mesh(MeshProtoKey key, MeshProto proto, Transform trans, Vec4d scale, VisibilityInfo visInfo, long pickingID) {

	_trans = new Transform(trans);
	_proto = proto;
	_scale = new Vec4d(scale);
	_key = key;
	_visInfo = visInfo;

	Mat4d modelMat = RenderUtils.mergeTransAndScale(_trans, _scale);
	_bounds = _proto.getHull().getAABB(modelMat);

	_pickingID = pickingID;
}

public AABB getBoundsRef() {
	return _bounds;
}

@Override
public void render(Map<Integer, Integer> vaoMap, Renderer renderer, Camera cam, Ray pickRay) {
	Mat4d modelViewMat = new Mat4d();
	cam.getViewMat4d(modelViewMat);

	Mat4d modelMat = RenderUtils.mergeTransAndScale(_trans, _scale);

	modelViewMat.mult4(modelMat);

	Mat4d normalMat = RenderUtils.getInverseWithScale(_trans, _scale);
	normalMat.transpose4();

	_proto.render(vaoMap, renderer, modelMat, normalMat, cam);

	// Debug render of the convex hull
	if (renderer.debugDrawHulls()) {
		ConvexHullKey hullKey = new ConvexHullKey(_key);
		HullProto hp = renderer.getHullProto(hullKey);
		if (hp != null) {
			hp.render(vaoMap, renderer, modelViewMat, cam);
		}
	}
}

@Override
public long getPickingID() {
	return _pickingID;
}

@Override
public double getCollisionDist(Ray r)
{
	double boundsDist = _bounds.collisionDist(r);
	if (boundsDist < 0) {
		return boundsDist;
	}

	return _proto.getHull().collisionDistance(r, _trans, _scale);
}

@Override
public boolean hasTransparent() {
	return _proto.hasTransparent();
}

@Override
public void renderTransparent(Map<Integer, Integer> vaoMap, Renderer renderer, Camera cam, Ray pickRay) {

	Mat4d modelMat = RenderUtils.mergeTransAndScale(_trans, _scale);

	Mat4d normalMat = RenderUtils.getInverseWithScale(_trans, _scale);
	normalMat.transpose4();

	_proto.renderTransparent(vaoMap, renderer, modelMat, normalMat, cam);

}

@Override
public boolean renderForView(int viewID, double dist) {
	if (dist < _visInfo.minDist || dist > _visInfo.maxDist) {
		return false;
	}

	if (_visInfo.viewIDs == null || _visInfo.viewIDs.size() == 0) return true; //Default to always visible
	return _visInfo.viewIDs.contains(viewID);
}

}
