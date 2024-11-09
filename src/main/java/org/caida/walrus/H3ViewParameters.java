//
// The Walrus Graph Visualization Tool.
// Copyright (C) 2000,2001,2002 The Regents of the University of California.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// ######END_HEADER######
//
package org.caida.walrus;


import org.jogamp.java3d.*;
import org.jogamp.java3d.utils.geometry.Text2D;

import org.jogamp.vecmath.*;
import java.awt.*;

public class H3ViewParameters {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3ViewParameters(H3Canvas3D canvas) {
        mCanvas = canvas;

        initializeBasicAppearances();
        initializeGraphAppearances();
        initializeGradedNodeAppearances();
        initializePickAppearances();

        mPixelToMeterScale = computePixelToMeterScale(canvas.getScreen3D());
        mPickRadius = PICK_RADIUS_PIXELS * mPixelToMeterScale;
        mPickEquivalenceRadius =
                PICK_EQUIVALENCE_RADIUS_PIXELS * mPixelToMeterScale;
        mNodeRadius = NODE_RADIUS_PIXELS * mPixelToMeterScale;

        mPickViewer = new H3PickViewer(mPickRadius);

        mDepthCueing = new LinearFog(0.0f, 0.0f, 0.0f);
        mDepthCueing.setFrontDistance(DEPTH_CUEING_ENABLED_FRONT);
        mDepthCueing.setBackDistance(DEPTH_CUEING_ENABLED_BACK);
        mDepthCueing.setInfluencingBounds
                (new BoundingSphere(new Point3d(0.0, 0.0, 0.0), 100.0));
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public void refresh() {
        mCanvas.getImagePlateToVworld(mImageToVworld);

        mPickViewer.setImageToVworldTransform(mImageToVworld);
        mNodeImage.setImageToVworldTransform(mImageToVworld);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // (x, y) of the lower-left corner of the label in image-plate coordinates
    //
    // For some unknown reason, successive labels appear *behind* previous
    // labels.  The parameter {zOffset} is meant to provide a hint about
    // the ordering of a set of labels put up during one 'labelling session'.
    // This parameter is used to calculate a small +z offset to apply to
    // the label.  The best way to generate it in the caller is to pass in
    // a counter, starting at zero, which increments by one as successive
    // labels are requested.  It should be reset to zero when some user
    // interaction causes all labels to be cleared.
    public void drawLabel
    (GraphicsContext3D gc, double x, double y, int zOffset, String s) {
        boolean frontBufferRenderingState = gc.getFrontBufferRendering();
        gc.setBufferOverride(true);
        gc.setFrontBufferRendering(true);

        Point3d eye = getEye();
        double labelZ = zOffset * LABEL_Z_OFFSET_SCALE
                + LABEL_Z_SCALE * eye.z + LABEL_Z_OFFSET;

        double xOffset = LABEL_X_OFFSET * mPixelToMeterScale;
        double yOffset = LABEL_Y_OFFSET * mPixelToMeterScale;
        Point3d p = new Point3d(x + xOffset, y + yOffset, 0.0);
        {
            // Project given (x, y) coordinates to the plane z=labelZ.

            // Convert from image-plate to eye coordinates.
            p.x -= eye.x;
            p.y -= eye.y;

            double inversePerspectiveScale = 1.0 - labelZ / eye.z;
            p.x *= inversePerspectiveScale;
            p.y *= inversePerspectiveScale;

            // Convert from eye to image-plate coordinates.
            p.x += eye.x;
            p.y += eye.y;
        }

        Transform3D scale = new Transform3D();
        scale.set(LABEL_SCALE);

        Vector3d t = new Vector3d(p.x, p.y, labelZ);
        Transform3D translation = new Transform3D();
        translation.set(t);
        translation.mul(scale);

        Transform3D transform = new Transform3D(mImageToVworld);
        transform.mul(translation);

        gc.setModelTransform(transform);

        // XXX: Courier may not be available on all systems.
        Text2D text = new Text2D(s, new Color3f(1.0f, 1.0f, 1.0f),
                "Courier", 24, Font.BOLD);

        gc.draw(text);
        gc.flush(true);

        // NOTE: Resetting the model transform here is very important.
        //       For some reason, not doing this causes the immediate
        //       following frame to render incorrectly (but subsequent
        //       frames will render correctly).  In some ways, this
        //       makes sense, because most rendering code assumes that
        //       GraphicsContext3D has been set to some reasonable
        //       transform.
        gc.setModelTransform(mObjectTransform);
        gc.setFrontBufferRendering(frontBufferRenderingState);
    }

    // (x, y) in image-plate coordinates
    public void drawPickViewer(GraphicsContext3D gc, double x, double y) {
        mPickViewer.draw(gc, x, y);
    }

    public void drawAxes(GraphicsContext3D gc) {
        if (mAxesEnabled) {
            mAxes.draw(gc, mObjectTransform);
        }
    }

    public void setAxesEnabled(boolean enable) {
        mAxesEnabled = enable;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // NOTE: Fog objects are installed on a GraphicsContext3D, but a
    //       GraphicsContext3D is not available from a Canvas3D
    //       until the Canvas3D has been displayed.  Thus the need for
    //       this method to install Fog later on in program execution.
    //       A good place to call this is in H3RenderLoop.refreshDisplay(),
    //       which gets called at program startup and doesn't get called
    //       too often afterwards.
    public void installDepthCueing() {
        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        gc.setFog(mDepthCueing);
    }

    public void setDepthCueingEnabled(boolean enable) {
        mDepthCueingEnabled = enable;
        if (enable) {
            updateDepthCueing();
        } else {
            // XXX: In versions of Java3D prior to 1.3.1,
            //      GraphicsContext3D.setFog(null) throws a
            //      NullPointerException contrary to its specification.
            //      Therefore, we disable fog by simply using a very distant
            //      front and back distances.
            mDepthCueing.setFrontDistance(DEPTH_CUEING_DISABLED_FRONT);
            mDepthCueing.setBackDistance(DEPTH_CUEING_DISABLED_BACK);
        }
    }

    public void resetMagnification() {
        mMagnification = 1.0;
        updateObjectTransformScaling();
        updateDepthCueing();
    }

    // NOTE: The multiplicative factors for increasing and decreasing the
    //       magnification level should be reciprocals of each other.
    //       For example, 1.25 = 1/0.8.

    public void increaseMagnification() {
        mMagnification *= 1.25;
        updateObjectTransformScaling();
        updateDepthCueing();
    }

    public void decreaseMagnification() {
        mMagnification *= 0.8;
        updateObjectTransformScaling();
        updateDepthCueing();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public void putModelTransform(GraphicsContext3D gc) {
        gc.setModelTransform(mObjectTransform);
    }

    // NOTE: The target coordinate system of the returned transform isn't
    //       the physical eye coordinate system.  It is actually just the
    //       image plate coordinate system with the origin translated to
    //       the center of the image plate (from the lower-left corner).
    //       This slightly altered coordinate system is useful for picking,
    //       but it may not be suitable for other uses.
    public Transform3D getObjectToEyeTransform() {
        mCanvas.getCenterEyeInImagePlate(mEye);
        mImageToEye.set(new Vector3d(-mEye.x, -mEye.y, 0.0));
        mCanvas.getVworldToImagePlate(mVworldToImage);

        Transform3D transform = new Transform3D(mImageToEye);
        transform.mul(mVworldToImage);
        transform.mul(mObjectTransform);
        return transform;
    }

    public Transform3D extendObjectTransform(Matrix4d t) {
        Transform3D transform = new Transform3D(t);
        transform.mul(mObjectTransform);
        mObjectTransform.set(transform);

        return mObjectTransform;
    }

    public Transform3D getObjectTransform() {
        return mObjectTransform;
    }

    public void setObjectTransform(Transform3D transform) {
        mObjectTransform.set(transform);
    }

    public void saveObjectTransform() {
        mSavedObjectTransform.set(mObjectTransform);
    }

    public void discardObjectTransform() {
        // Nothing needs to be done; simply not use m_savedObjectTransform.
    }

    public void restoreObjectTransform() {
        mObjectTransform.set(mSavedObjectTransform);
    }

    public void resetObjectTransform() {
        mMagnification = 1.0;
        mObjectTransform.setIdentity();
    }

    public Point3d getEye() {
        mCanvas.getCenterEyeInImagePlate(mEye);
        return mEye;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public H3PickViewer getPickViewer() {
        return mPickViewer;
    }

    public H3Circle getNodeImage() {
        return mNodeImage;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public TransparencyAttributes getTransparencyAttributes() {
        return mTransparencyAttributes;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These two are used by the nonadaptive renderer.

    public Appearance getPointAppearance() {
        return mPointAppearance;
    }

    public Appearance getLineAppearance() {
        return mLineAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These three are used by the adaptive renderer.

    public Appearance getNodeAppearance() {
        return mNodeAppearance;
    }

    public Appearance getTreeLinkAppearance() {
        return mTreeLinkAppearance;
    }

    public Appearance getNontreeLinkAppearance() {
        return mNontreeLinkAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    // These three are used by the adaptive renderer.

    public Appearance getNearNodeAppearance() {
        return mNearNodeAppearance;
    }

    public Appearance getMiddleNodeAppearance() {
        return mMiddleNodeAppearance;
    }

    public Appearance getFarNodeAppearance() {
        return mFarNodeAppearance;
    }

    public Appearance getPickAppearance() {
        return mPickAppearance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 

    public double getNodeRadius() {
        return mNodeRadius;
    }

    public double getPickRadius() {
        return mPickRadius;
    }

    public double getPickEquivalenceRadius() {
        return mPickEquivalenceRadius;
    }

    public double getPixelToMeterScale() {
        return mPixelToMeterScale;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void updateObjectTransformScaling() {
        Vector3d translation = new Vector3d(0.0, 0.0, 0.0);
        Matrix3d rotation = new Matrix3d();
        mObjectTransform.get(rotation);
        mObjectTransform.set(rotation, translation, mMagnification);
    }

    // The front and back fog distances are computed using empirically
    // derived formulas.  Other ways were tried but rejected for one
    // reason or another.  The task of computing these values is less
    // than straightforward because of the way Java3D seems to work.
    //
    // Zooming support is currently implemented by extending m_objectTransform
    // with a scaling component.  This is a quick and natural way of
    // implementing zooming.  However, because this extended transform
    // is installed as the model transform of a GraphicsContext3D,
    // this technique has the potential of altering the relationships
    // between coordinate systems.  There are at least the following
    // two coordinate systems involved: vworld and eye.  Objects are
    // specified in vworld coordinates.  Fog parameters are specified in
    // vworld coordinates but with the origin shifted to the location
    // of the eye in vworld coordinates (+z extends along the line of sight).
    // Ideally, the interpretation of fog parameters should be independent
    // of any scaling done by the model transform of GraphicsContext3D.
    // Unfortunately, this is not the case.  The exact interaction of
    // scaling with fog parameters is still unclear.  Factoring out the
    // scaling from the fog parameters doesn't work as expected for unknown
    // reasons.  The upshot is that we are resorting to an empirically
    // derived formula which sets the bounds to pleasing distances at
    // each magnification level.
    //
    // UPDATE: It seems Java3D has a bug/feature in which updating the
    //         depth-cueing distances often doesn't take effect until
    //         the *SECOND* display refresh after the change.  This
    //         bug may have caused me to think that things were more
    //         complicated than they actually are.
    private void updateDepthCueing() {
        if (mDepthCueingEnabled) {
            double front =
                    DEPTH_CUEING_ENABLED_FRONT * Math.pow(mMagnification, -1.2);
            double back =
                    DEPTH_CUEING_ENABLED_BACK * Math.pow(mMagnification, -0.7);

            if (DEBUG_PRINT) {
                System.out.println("magnification=" + mMagnification);
                System.out.println("front=" + front);
                System.out.println("back=" + back);
            }

            if (back <= front) {
                front = DEPTH_CUEING_DISABLED_FRONT;
                back = DEPTH_CUEING_DISABLED_BACK;
            }

            mDepthCueing.setFrontDistance(front);
            mDepthCueing.setBackDistance(back);
        }
    }

    private void refreshAll() {
        mCanvas.getCenterEyeInImagePlate(mEye);
        mImageToEye.set(new Vector3d(-mEye.x, -mEye.y, 0.0));
        mEyeToImage.set(new Vector3d(mEye.x, mEye.y, 0.0));
        mCanvas.getVworldToImagePlate(mVworldToImage);
        mCanvas.getImagePlateToVworld(mImageToVworld);
    }

    private double computePixelToMeterScale(Screen3D screen) {
        double wm = screen.getPhysicalScreenWidth();
        double hm = screen.getPhysicalScreenHeight();

        java.awt.Dimension d = screen.getSize();
        int wp = d.width;
        int hp = d.height;

        double xScale = wm / wp;
        double yScale = hm / hp;

        if (Math.abs(xScale - yScale) > 1.0e-10) {
            System.err.println(
                    "WARNING: computePixelToMeterScale(): "
                            + "xScale(" + xScale + ") != yScale(" + yScale + ").");
        }

        return xScale;
    }

    private void initializeBasicAppearances() {
        LineAttributes lineAttributes = new LineAttributes();
        lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

        PointAttributes pointAttributes = new PointAttributes();
        pointAttributes.setPointSize(GENERAL_POINT_SIZE);
        pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        ColoringAttributes pointColoringAttributes =
                new ColoringAttributes(0.0f, 0.0f, 1.0f,
                        ColoringAttributes.FASTEST);

        ColoringAttributes lineColoringAttributes =
                new ColoringAttributes(0.7f, 0.7f, 0.7f,
                        //new ColoringAttributes(0.11765f, 0.58824f, 0.1f,
                        ColoringAttributes.FASTEST);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mPointAppearance = new Appearance();
        mPointAppearance.setLineAttributes(lineAttributes);
        mPointAppearance.setPointAttributes(pointAttributes);
        mPointAppearance.setColoringAttributes(pointColoringAttributes);

        mLineAppearance = new Appearance();
        mLineAppearance.setLineAttributes(lineAttributes);
        mLineAppearance.setPointAttributes(pointAttributes);
        mLineAppearance.setColoringAttributes(lineColoringAttributes);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mTransparencyAttributes =
                //new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.95f);
                new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.8f);
    }

    private void initializeGraphAppearances() {
        LineAttributes lineAttributes = new LineAttributes();
        lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

        PointAttributes pointAttributes = new PointAttributes();
        pointAttributes.setPointSize(NODE_POINT_SIZE);
        pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        ColoringAttributes coloringAttributes =
                new ColoringAttributes(1.0f, 1.0f, 0.0f,
                        ColoringAttributes.FASTEST);

        ColoringAttributes treeColoringAttributes =
                new ColoringAttributes(0.11765f, 0.58824f, 0.1f,
                        ColoringAttributes.FASTEST);

        ColoringAttributes nontreeColoringAttributes =
                new ColoringAttributes(0.7f, 0.7f, 0.7f,
                        ColoringAttributes.FASTEST);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mNodeAppearance = new Appearance();
        mNodeAppearance.setLineAttributes(lineAttributes);
        mNodeAppearance.setPointAttributes(pointAttributes);
        mNodeAppearance.setColoringAttributes(coloringAttributes);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mTreeLinkAppearance = new Appearance();
        mTreeLinkAppearance.setLineAttributes(lineAttributes);
        mTreeLinkAppearance.setPointAttributes(pointAttributes);
        mTreeLinkAppearance.setColoringAttributes(treeColoringAttributes);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mNontreeLinkAppearance = new Appearance();
        mNontreeLinkAppearance.setLineAttributes(lineAttributes);
        mNontreeLinkAppearance.setPointAttributes(pointAttributes);
        mNontreeLinkAppearance
                .setColoringAttributes(nontreeColoringAttributes);
    }

    private void initializeGradedNodeAppearances() {
        LineAttributes lineAttributes = new LineAttributes();
        lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

        PointAttributes nearPointAttributes = new PointAttributes();
        nearPointAttributes.setPointSize(NODE_NEAR_POINT_SIZE);
        nearPointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        PointAttributes middlePointAttributes = new PointAttributes();
        middlePointAttributes.setPointSize(NODE_MIDDLE_POINT_SIZE);
        middlePointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        PointAttributes farPointAttributes = new PointAttributes();
        farPointAttributes.setPointSize(NODE_FAR_POINT_SIZE);
        farPointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        ColoringAttributes coloringAttributes =
                new ColoringAttributes(1.0f, 1.0f, 0.0f,
                        ColoringAttributes.FASTEST);

        // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

        mNearNodeAppearance = new Appearance();
        mNearNodeAppearance.setLineAttributes(lineAttributes);
        mNearNodeAppearance.setPointAttributes(nearPointAttributes);
        mNearNodeAppearance.setColoringAttributes(coloringAttributes);

        mMiddleNodeAppearance = new Appearance();
        mMiddleNodeAppearance.setLineAttributes(lineAttributes);
        mMiddleNodeAppearance.setPointAttributes(middlePointAttributes);
        mMiddleNodeAppearance.setColoringAttributes(coloringAttributes);

        mFarNodeAppearance = new Appearance();
        mFarNodeAppearance.setLineAttributes(lineAttributes);
        mFarNodeAppearance.setPointAttributes(farPointAttributes);
        mFarNodeAppearance.setColoringAttributes(coloringAttributes);
    }

    private void initializePickAppearances() {
        PointAttributes pointAttributes = new PointAttributes();
        pointAttributes.setPointSize(PICK_POINT_SIZE);
        pointAttributes.setPointAntialiasingEnable(ANTIALIASING);

        ColoringAttributes pointColoringAttributes =
                new ColoringAttributes(0.9f, 0.1f, 0.1f,
                        ColoringAttributes.FASTEST);

        mPickAppearance = new Appearance();
        mPickAppearance.setPointAttributes(pointAttributes);
        mPickAppearance.setColoringAttributes(pointColoringAttributes);
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = false;
    private static final boolean ANTIALIASING = false;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final H3Canvas3D mCanvas;
    private final H3Axes mAxes = new H3Axes();
    private final H3PickViewer mPickViewer;
    private final H3Circle mNodeImage = new H3Circle();
    private boolean mAxesEnabled = true;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final Transform3D mObjectTransform = new Transform3D();
    private final Transform3D mSavedObjectTransform = new Transform3D();

    private final Point3d mEye = new Point3d();
    private final Transform3D mImageToEye = new Transform3D();
    private final Transform3D mEyeToImage = new Transform3D();
    private final Transform3D mVworldToImage = new Transform3D();
    private final Transform3D mImageToVworld = new Transform3D();

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final double mPixelToMeterScale;

    private static final float GENERAL_POINT_SIZE = 4.0F;
    private static final float NODE_POINT_SIZE = 4.0F;
    private static final float NODE_NEAR_POINT_SIZE = 5.0F;
    private static final float NODE_MIDDLE_POINT_SIZE = 3.0F;
    private static final float NODE_FAR_POINT_SIZE = 1.0F;

    private static final int NODE_RADIUS_PIXELS = 10;
    private final double mNodeRadius;

    // PICK_EQUIVALENCE_RADIUS_PIXELS, which must be less than or equal
    // to PICK_RADIUS_PIXELS, is the radius within which nodes are
    // considered to overlap.  With this radius, we should pick the
    // overlapping node that is closest to the eye along the line of sight.
    private static final int PICK_RADIUS_PIXELS = 10;
    private static final int PICK_EQUIVALENCE_RADIUS_PIXELS = 2;
    private static final float PICK_POINT_SIZE = 10.0F;
    private final double mPickRadius;
    private final double mPickEquivalenceRadius;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // Some good combinations of front and back distances are
    // (1.0, 3.5), (1.25, 3.5), (1.0, 4.0), (1.25, 4.0), and (1.5, 4.0).

    private static final double DEPTH_CUEING_ENABLED_FRONT = 1.8;
    private static final double DEPTH_CUEING_ENABLED_BACK = 3.5;

    private static final double DEPTH_CUEING_DISABLED_FRONT = 100.0;
    private static final double DEPTH_CUEING_DISABLED_BACK = 105.0;

    private boolean mDepthCueingEnabled = true;
    private final LinearFog mDepthCueing;

    // The magnification level of the display.
    private double mMagnification = 1.0;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // LABEL_SCALE is an arbitrarily chosen value that leads to a pleasing
    // size for textual labels when displayed near the default front clipping
    // distance of 0.1 meters (in PHYSICAL_EYE coordinates).  If the front
    // clipping distance changes, then this scaling factor must also change.
    private static final double LABEL_SCALE = 0.03;
    private static final double LABEL_Z_SCALE = 0.5;
    private static final double LABEL_Z_OFFSET = 0.0; // meters
    private static final double LABEL_Z_OFFSET_SCALE = 0.0001; // meters
    private static final double LABEL_X_OFFSET = 10; // pixels
    private static final double LABEL_Y_OFFSET = 10; // pixels

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private TransparencyAttributes mTransparencyAttributes;

    private Appearance mPointAppearance;
    private Appearance mLineAppearance;

    private Appearance mNodeAppearance;
    private Appearance mTreeLinkAppearance;
    private Appearance mNontreeLinkAppearance;

    private Appearance mNearNodeAppearance;
    private Appearance mMiddleNodeAppearance;
    private Appearance mFarNodeAppearance;

    private Appearance mPickAppearance;
}
