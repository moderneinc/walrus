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



import org.jogamp.java3d.*;
import org.jogamp.vecmath.*;

public class H3AdaptiveRenderLoop
        implements H3RenderLoop, Runnable {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3AdaptiveRenderLoop(H3Graph graph, H3Canvas3D canvas,
                                H3ViewParameters parameters,
                                H3Transformer transformer,
                                H3RenderQueue queue,
                                H3AdaptiveRenderer renderer) {
        mGraph = graph;
        mCanvas = canvas;
        mParameters = parameters;
        mTransformer = transformer;
        mRenderer = renderer;

        mPicker = new H3AdaptivePicker(graph, canvas, parameters, queue);
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public synchronized void synchronizeWithRendering() {
        startRequest();
        endRequest();
    }

    public synchronized void refreshDisplay() {
        startRequest();
        {
            if (mState == STATE_IDLE || mState == STATE_COMPLETE) {
                if (DEBUG_PRINT) {
                    System.out.print("[" + Thread.currentThread().getName()
                            + "]: ");
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
            if (mState == STATE_IDLE || mState == STATE_COMPLETE) {
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
            if (mState == STATE_IDLE || mState == STATE_COMPLETE) {
                mParameters.refresh(); // See comments for this elsewhere.
                mPicker.highlightNode(x, y);
            }
        }
        endRequest();
    }

    public synchronized void highlightNode(int node) {
        startRequest();
        {
            if (mState == STATE_IDLE || mState == STATE_COMPLETE) {
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
            mTransformer.pushPosition();
        }
        endRequest();
    }

    public synchronized void discardDisplayPosition() {
        startRequest();
        {
            if (!mRestoreDisplayRequested) {
                mParameters.discardObjectTransform();
                mTransformer.discardPosition();
            }
        }
        endRequest();
    }

    public synchronized void restoreDisplayPosition() {
        startRequest();
        {
            mRestoreDisplayRequested = true;
        }
        endRequest();
    }

    public synchronized H3DisplayPosition getDisplayPosition() {
        H3DisplayPosition retval;
        startRequest();
        {
            H3Transformer.Position position = mTransformer.getPosition();
            retval = new H3DisplayPosition(position.startingNode,
                    mParameters.getObjectTransform(),
                    position.transform);
        }
        endRequest();
        return retval;
    }

    public synchronized void setDisplayPosition(H3DisplayPosition position) {
        startRequest();
        {
            mDisplayPosition = position;
            mRestoreDisplayRequested = true;
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

    public synchronized void setMaxRotationDuration(long max) {
        startRequest();
        {
            mMaxRotationDuration = max;
        }
        endRequest();
    }

    public synchronized void setMaxTranslationDuration(long max) {
        startRequest();
        {
            mMaxTranslationDuration = max;
        }
        endRequest();
    }

    public synchronized void setMaxCompletionDuration(long max) {
        startRequest();
        {
            mMaxCompletionDuration = max;
        }
        endRequest();
    }

    public synchronized long getMaxRotationDuration() {
        return mMaxRotationDuration;
    }

    public synchronized long getMaxTranslationDuration() {
        return mMaxTranslationDuration;
    }

    public synchronized long getMaxCompletionDuration() {
        return mMaxCompletionDuration;
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (Runnable)
    ////////////////////////////////////////////////////////////////////////

    public void run() {
        while (true) {
            // This is necessary since Java3D isn't prompt in updating the
            // various view transformations after a window changes size.
            mParameters.refresh();

            if (DEBUG_PRINT) {
                System.out.print("[" + Thread.currentThread().getName()
                        + "]: ");
            }

            switch (mState) {
                case STATE_SHUTDOWN:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_SHUTDOWN");
                    }
                    beShutdownState();
                    System.out.println("H3AdaptiveRenderLoop exiting...");
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

                case STATE_COMPLETE_INIT:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_COMPLETE_INIT");
                    }
                    beCompleteInitState();
                    break;

                case STATE_COMPLETE:
                    if (DEBUG_PRINT) {
                        System.out.println("STATE_COMPLETE");
                    }
                    beCompleteState();
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
        mTransformer.shutdown();
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
                System.out.print("[" + Thread.currentThread().getName()
                        + "]: ");
                System.out.println("synchIdleState() waiting ...");
            }

            if (mRestoreDisplayRequested) {
                if (DEBUG_PRINT) {
                    System.out.print("[" + Thread.currentThread().getName()
                            + "]: ");
                    System.out.println("restoring display ...");
                }

                mRestoreDisplayRequested = false;
                mPicker.reset();
                mRenderer.reset();

                if (mDisplayPosition == null) {
                    mParameters.restoreObjectTransform();
                    mTransformer.popPosition();
                } else {
                    mParameters.setObjectTransform
                            (mDisplayPosition.getRotation());

                    H3Transformer.Position position =
                            new H3Transformer.Position();
                    position.startingNode = mDisplayPosition.getCenterNode();
                    position.transform.set(mDisplayPosition.getTranslation());
                    mTransformer.setPosition(position);

                    mDisplayPosition = null;
                }

                GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
                gc.clear();
                mParameters.drawAxes(gc);
                mParameters.putModelTransform(gc);
                mCanvas.swap();

                mState = STATE_COMPLETE_INIT;
            } else {
                mIsWaiting = true;
                mIsRequestTurn = true;

                if (mNumPendingRequests > 0) {
                    notifyAll();
                }

                waitIgnore();
            }
        }
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beRotateState() {
        mRenderer.reset();
        mRenderer.setMaxDuration(mMaxRotationDuration);

        Matrix4d rot = new Matrix4d();
        while (mRotationRequest.getRotation(rot)) {
            rotate(rot);
        }

        mState = STATE_COMPLETE_INIT;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beTranslateState() {
        mRenderer.reset();
        mRenderer.setMaxDuration(mMaxTranslationDuration);

        Point4d source = new Point4d();
        Point4d destination = new Point4d();

        mGraph.getNodeCoordinates(mTranslationNode, source);

        Point4d initialSource = new Point4d(source);
        mTransformer.pushPosition();

        boolean more = true;
        while (more) {
            double sourceLength = H3Math.vectorLength(source);
            if (sourceLength > TRANSLATION_THRESHOLD) {
                double delta = Math.max(TRANSLATION_MIN_DELTA,
                        1.0 - sourceLength);
                double scale = sourceLength / (sourceLength - delta);

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

        mTransformer.popPosition();
        translate(initialSource, H3Transform.ORIGIN4);

        mState = STATE_COMPLETE_INIT;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beRefreshState() {
        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        mParameters.putModelTransform(gc);
        gc.setBufferOverride(true);
        gc.setFrontBufferRendering(true);
        gc.clear();

        mParameters.drawAxes(gc);
        mParameters.putModelTransform(gc);
        mRenderer.reset();

        mState = STATE_COMPLETE;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beCompleteInitState() {
        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        mParameters.putModelTransform(gc);
        gc.setBufferOverride(true);
        gc.setFrontBufferRendering(true);
        mState = STATE_COMPLETE;
    }

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void beCompleteState() {
        mRenderer.setMaxDuration(mMaxCompletionDuration);

        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        while (mState == STATE_COMPLETE) {
            if (synchCompleteState()) {
                mRenderer.refine(gc);
            }
        }
    }

    private synchronized boolean synchCompleteState() {
        if (mNumPendingRequests > 0) {
            if (DEBUG_PRINT) {
                System.out.print("[" + Thread.currentThread().getName()
                        + "]: ");
                System.out.println("synchCompleteState() waiting ...");
                System.out.println("(" + mNumPendingRequests +
                        " request(s) pending)");
            }

            mIsRequestTurn = true;
            notifyAll();

            mIsWaiting = true;
            waitIgnore();
            mIsWaiting = false;
        }

        if (DEBUG_PRINT) {
            System.out.print("[" + Thread.currentThread().getName() + "]: ");
            System.out.println("synchCompleteState() running ...");
        }

        if (mState == STATE_COMPLETE && mRenderer.isFinished()) {
            mState = STATE_IDLE;
        }

        if (mState != STATE_COMPLETE) {
            GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
            gc.setBufferOverride(true);
            gc.setFrontBufferRendering(false);
        }

        return mState == STATE_COMPLETE;
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
            mRenderer.render(gc);
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

        mTransformer.transform(translation);
        mTransformer.transformNode(mTranslationNode, source);

        if (DEBUG_PRINT) {
            source.project(source);
            System.out.println("source transformed = " + source);
        }

        GraphicsContext3D gc = mCanvas.getGraphicsContext3D();
        gc.clear();
        {
            mParameters.drawAxes(gc);
            mParameters.putModelTransform(gc);
            mRenderer.render(gc);
        }
        mCanvas.swap();

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("translate.end[" + stopTime + "]");
            System.out.println("translate.time[" + duration + "]");
        }
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
    private static final boolean DEBUG_PRINT_TRANSFORMED = false;

    private static final boolean ANTIALIASING = false;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final int STATE_SHUTDOWN = 0;
    private static final int STATE_IDLE = 1;
    private static final int STATE_ROTATE = 2;
    private static final int STATE_TRANSLATE = 3;
    private static final int STATE_REFRESH = 4;
    private static final int STATE_COMPLETE_INIT = 5;
    private static final int STATE_COMPLETE = 6;
    private static final String[] STATE_NAMES = {
            "STATE_SHUTDOWN", "STATE_IDLE", "STATE_ROTATE", "STATE_TRANSLATE",
            "STATE_REFRESH", "STATE_COMPLETE_INIT", "STATE_COMPLETE"
    };

    private int mState = STATE_IDLE;
    private int mNumPendingRequests;
    private boolean mIsRequestTurn;
    private boolean mIsWaiting;

    private boolean mIsShutdown;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final H3Graph mGraph;
    private final H3Canvas3D mCanvas;
    private final H3Transformer mTransformer;
    private final H3AdaptiveRenderer mRenderer;
    private final H3ViewParameters mParameters;
    private final H3AdaptivePicker mPicker;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private long mMaxRotationDuration = Long.MAX_VALUE;
    private long mMaxTranslationDuration = Long.MAX_VALUE;
    private long mMaxCompletionDuration = Long.MAX_VALUE;

    private H3RotationRequest mRotationRequest;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final double TRANSLATION_STEP_DISTANCE = 0.05;
    private static final double TRANSLATION_MIN_DELTA = 0.01;
    private static final double TRANSLATION_THRESHOLD =
            1.0 - TRANSLATION_STEP_DISTANCE;

    private int mTranslationNode;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private boolean mRestoreDisplayRequested;
    private H3DisplayPosition mDisplayPosition;
}
