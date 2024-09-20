package org.caida.walrus;//
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



import org.jogamp.java3d.GraphicsContext3D;
import org.jogamp.vecmath.Matrix4d;
import org.jogamp.vecmath.Point2d;
import org.jogamp.vecmath.Point4d;

public class H3NonadaptiveRenderLoop
        implements H3RenderLoop, Runnable {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3NonadaptiveRenderLoop(H3Graph graph, H3Canvas3D canvas,
                                   H3ViewParameters parameters,
                                   H3RenderList renderList,
                                   boolean useNodeSizes) {
        USE_NODE_SIZES = useNodeSizes;

        mGraph = graph;
        mCanvas = canvas;
        mParameters = parameters;
        mRenderList = renderList;
        mNumNodes = graph.getNumNodes();

        mPicker = new H3NonadaptivePicker(graph, canvas, parameters);
        mTranslation.setIdentity();
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (H3RenderLoop)
    ////////////////////////////////////////////////////////////////////////

    public synchronized void synchronizeWithRendering() {
        startRequest();
        endRequest();
    }

    public synchronized void refreshDisplay() {
        startRequest();
        {
            if (mState == STATE_IDLE) {
                if (DEBUG_PRINT) {
                    System.out.println("refreshing display ...");
                }

                mParameters.refresh();
                mParameters.installDepthCueing();
                mState = STATE_REFRESH;
            }
        }
        endRequest();
    }

    public synchronized void resizeDisplay() {
        startRequest();
        {
            if (DEBUG_PRINT) {
                System.out.println("resizing display ("
                        + STATE_NAMES[mState] + ")");
            }
            mPicker.reset();
        }
        endRequest();
    }

    public synchronized void rotateDisplay(H3RotationRequest request) {
        startRequest();
        {
            if (DEBUG_PRINT) {
                System.out.println("rotating display ("
                        + STATE_NAMES[mState] + ")");
            }

            mPicker.reset();
            mRotationRequest = request;
            mState = STATE_ROTATE;
        }
        endRequest();
    }

    public synchronized int pickNode(int x, int y, Point2d center) {
        int retval = -1;

        startRequest();
        {
            if (mState == STATE_IDLE) {
                mParameters.refresh(); // See comments for this elsewhere.
                retval = mPicker.pickNode(x, y, center);
            }
        }
        endRequest();

        return retval;
    }

    public synchronized void highlightNode(int x, int y) {
        startRequest();
        {
            if (mState == STATE_IDLE) {
                mParameters.refresh(); // See comments for this elsewhere.
                mPicker.highlightNode(x, y);
            }
        }
        endRequest();
    }

    public synchronized void highlightNode(int node) {
        startRequest();
        {
            if (mState == STATE_IDLE) {
                mParameters.refresh(); // See comments for this elsewhere.
                mPicker.highlightNode(node);
            }
        }
        endRequest();
    }

    public synchronized void translate(int node) {
        startRequest();
        {
            if (DEBUG_PRINT) {
                System.out.println("translating to node " + node + " ...");
            }

            mPicker.reset();
            mTranslationNode = node;
            mState = STATE_TRANSLATE;
        }
        endRequest();
    }

    public synchronized void saveDisplayPosition() {
        startRequest();
        {
            mParameters.saveObjectTransform();
            mSavedTranslation.set(mTranslation);
        }
        endRequest();
    }

    public synchronized void discardDisplayPosition() {
        startRequest();
        {
            mParameters.discardObjectTransform();
        }
        endRequest();
    }

    public synchronized void restoreDisplayPosition() {
        startRequest();
        {
            mPicker.reset();

            mParameters.restoreObjectTransform();
            mTranslation.set(mSavedTranslation);
            mGraph.transformNodes(mTranslation);

            mState = STATE_REFRESH;
        }
        endRequest();
    }

    public synchronized H3DisplayPosition getDisplayPosition() {
        H3DisplayPosition retval = null;
        startRequest();
        {
            retval = new H3DisplayPosition(mTranslationNode,
                    mParameters.getObjectTransform(),
                    mTranslation);
        }
        endRequest();
        return retval;
    }

    public synchronized void setDisplayPosition(H3DisplayPosition position) {
        startRequest();
        {
            mPicker.reset();

            mParameters.setObjectTransform(position.getRotation());
            mTranslation.set(position.getTranslation());
            mGraph.transformNodes(mTranslation);

            mState = STATE_REFRESH;
        }
        endRequest();
    }

    public synchronized void shutdown() {
        startRequest();
        {
            mState = STATE_SHUTDOWN;
        }
        endRequest();
    }

    // This method should not be synchronized, since it must allow the thread
    // running H3AdaptiveRenderLoop to enter the monitor as needed in order
    // to run to completion.
    public void waitForShutdown() {
        while (!mIsShutdown) {
            waitForShutdownEvent();
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (Runnable)
    ////////////////////////////////////////////////////////////////////////

    public void run() {
        while (true) {
            // This is necessary since Java3D isn't prompt in updating the
            // various view transformations after a window changes size.
            mParameters.refresh();

            switch (mState) {
                case STATE_SHUTDOWN:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_SHUTDOWN");
                    }
                    beShutdownState();
                    System.out.println("H3NonadaptiveRenderLoop exiting...");
                    return;

                case STATE_IDLE:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_IDLE");
                    }
                    beIdleState();
                    break;

                case STATE_ROTATE:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_ROTATE");
                    }
                    beRotateState();
                    break;

                case STATE_TRANSLATE:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_TRANSLATE");
                    }
                    beTranslateState();
                    break;

                case STATE_REFRESH:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_REFRESH");
                    }
                    beRefreshState();
                    break;

                default:
                    throw new RuntimeException("Invalid state: " + mState);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS (states)
    ////////////////////////////////////////////////////////////////////////

    private void beShutdownState() {
        synchShutdownState();
    }

    private synchronized void synchShutdownState() {
        mIsShutdown = true;

        mIsWaiting = false;
        mIsRequestTurn = true;

        if (mNumPendingRequests > 0) {
            notifyAll();
        }
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beIdleState() {
        synchIdleState();
    }

    private synchronized void synchIdleState() {
        while (mState == STATE_IDLE) {
            if (DEBUG_PRINT) {
                System.out.println("synchIdleState() waiting ...");
            }

            mIsWaiting = true;
            mIsRequestTurn = true;

            if (mNumPendingRequests > 0) {
                notifyAll();
            }

            waitIgnore();
        }
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beRotateState() {
        Matrix4d rot = new Matrix4d();
        while (mRotationRequest.getRotation(rot)) {
            rotate(rot);
        }

        mState = STATE_IDLE;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beTranslateState() {
        Point4d source = new Point4d();
        Point4d destination = new Point4d();

        boolean more = true;
        while (more) {
            mGraph.getNodeCoordinates(mTranslationNode, source);
            double sourceLength = H3Math.vectorLength(source);

            if (sourceLength > TRANSLATION_THRESHOLD) {
                double scale = sourceLength / (2.0 * sourceLength - 1.0);

                destination.set(source);
                destination.w *= scale;
            } else if (sourceLength > TRANSLATION_STEP_DISTANCE) {
                double scale = sourceLength /
                        (sourceLength - TRANSLATION_STEP_DISTANCE);

                destination.set(source);
                destination.w *= scale;
            } else {
                destination.set(H3Transform.ORIGIN4);
                more = false;
            }

            if (DEBUG_PRINT) {
                source.project(source);
                destination.project(destination);

                System.out.println("source = " + source);
                System.out.println("destination = " + destination);
                System.out.println("sourceLength = " + sourceLength);
            }

            translate(source, destination);
        }

        if (DEBUG_PRINT) {
            mGraph.getNodeCoordinates(mTranslationNode, source);
            source.project(source);
            System.out.println("FINAL source = " + source);
        }

        mState = STATE_IDLE;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beRefreshState() {
        refresh();
        mState = STATE_IDLE;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS (rendering)
    ////////////////////////////////////////////////////////////////////////

    private void rotate(Matrix4d rot) {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("rotate.begin[" + startTime + "]");
        }

        mParameters.extendObjectTransform(rot);

        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        gc.clear();
        {
            mParameters.drawAxes(gc);
            mParameters.putModelTransform(gc);
            render(gc);
        }
        mCanvas.swap();

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("rotate.end[" + stopTime + "]");
            System.out.println("rotate.time[" + duration + "]");
        }
    }

    //======================================================================

    private void translate(Point4d source, Point4d destination) {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("translate.begin[" + startTime + "]");
        }

        Matrix4d translation =
                H3Transform.buildTranslation(source, destination);

        translation.mul(mTranslation);
        mTranslation.set(translation);

        mGraph.transformNodes(mTranslation);

        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        gc.clear();
        {
            mParameters.drawAxes(gc);
            mParameters.putModelTransform(gc);
            render(gc);
        }
        mCanvas.swap();

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("translate.end[" + stopTime + "]");
            System.out.println("translate.time[" + duration + "]");
        }
    }

    //======================================================================

    private void refresh() {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("refresh.begin[" + startTime + "]");
        }

        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        gc.clear();
        {
            mParameters.drawAxes(gc);
            mParameters.putModelTransform(gc);
            render(gc);
        }
        mCanvas.swap();

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("refresh.end[" + stopTime + "]");
            System.out.println("refresh.time[" + duration + "]");
        }
    }

    //======================================================================

    private void render(GraphicsContext3D gc) {
        mRenderList.beginFrame();
        {
            for (int i = 0; i < mNumNodes; i++) {
                if (USE_NODE_SIZES) {
                    computeNodeRadius(i);
                }

                mRenderList.addNode(i);

                int childIndex = mGraph.getNodeChildIndex(i);
                int nontreeIndex = mGraph.getNodeNontreeIndex(i);
                int endIndex = mGraph.getNodeLinksEndIndex(i);

                for (int j = childIndex; j < nontreeIndex; j++) {
                    mRenderList.addTreeLink(j);
                }

                for (int j = nontreeIndex; j < endIndex; j++) {
                    mRenderList.addNontreeLink(j);
                }
            }
        }
        mRenderList.endFrame();
        mRenderList.render(gc);
    }

    // The same radius calculation is done in
    // H3Transformer.transformNode(int node).
    // The two methods should be kept in sync to ensure a consistent display
    // when the user turns adaptive rendering on/off.
    private void computeNodeRadius(int node) {
        mGraph.getNodeCoordinates(node, mNodeCoordinates);

        double radius = H3Math.computeRadiusEuclidean(mNodeCoordinates);
        mGraph.setNodeRadius(node, radius);
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS (request synchronization)
    ////////////////////////////////////////////////////////////////////////

    private synchronized void waitForShutdownEvent() {
        if (!mIsShutdown) {
            // Block till the next rendezvous point.
            startRequest();
            endRequest();
        }
    }

    private synchronized void startRequest() {
        ++mNumPendingRequests;
        while (!mIsRequestTurn) {
            waitIgnore();
        }
        --mNumPendingRequests;
    }

    private synchronized void endRequest() {
        mIsRequestTurn = false;

        if (mNumPendingRequests > 0 || mIsWaiting) {
            notifyAll();
        }
    }

    private synchronized void waitIgnore() {
        try {
            wait();
        } catch (InterruptedException e) {
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = false;
    private static final boolean ANTIALIASING = false;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final int STATE_SHUTDOWN = 0;
    private static final int STATE_IDLE = 1;
    private static final int STATE_ROTATE = 2;
    private static final int STATE_TRANSLATE = 3;
    private static final int STATE_REFRESH = 4;
    private static final String[] STATE_NAMES = {
            "STATE_SHUTDOWN", "STATE_IDLE", "STATE_ROTATE",
            "STATE_TRANSLATE", "STATE_REFRESH"
    };

    // USE_NODE_SIZES is set in the constructor.
    //
    // Specifies whether we should render nodes at three sizes.
    // For the nonadaptive render loop, this only determines whether we
    // should calculate the radii of nodes before rendering the display,
    // as H3PointRenderList expects the radii in H3Graph to be up-to-date.
    private final boolean USE_NODE_SIZES;

    private int mState = STATE_IDLE;
    private int mNumPendingRequests;
    private boolean mIsRequestTurn;
    private boolean mIsWaiting;

    private boolean mIsShutdown;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final H3Graph mGraph;
    private final H3Canvas3D mCanvas;
    private final H3ViewParameters mParameters;
    private final H3RenderList mRenderList;
    private final H3NonadaptivePicker mPicker;
    private final int mNumNodes;
    private H3RotationRequest mRotationRequest;

    private final Point4d mNodeCoordinates = new Point4d(); // scratch variable

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final double TRANSLATION_STEP_DISTANCE = 0.05;
    private static final double TRANSLATION_THRESHOLD =
            1.0 - TRANSLATION_STEP_DISTANCE;

    private int mTranslationNode;
    private final Matrix4d mTranslation = new Matrix4d();

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final Matrix4d mSavedTranslation = new Matrix4d();
}
