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



import org.jogamp.vecmath.*;
import java.util.ArrayList;
import java.util.List;

public class H3Transformer
        implements Runnable {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3Transformer(H3Graph graph, H3RenderQueue queue,
                         boolean transformNontreeLinks) {
        mVisited = new int[graph.getNumNodes()];
        mStartingNode = graph.getRootNode();
        mGraph = graph;
        mRenderQueue = queue;
        mTransformQueue = new H3TransformQueue(graph.getNumNodes());
        mTransformNontreeLinks = transformNontreeLinks;
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public synchronized void transform(Matrix4d transform) {
        startRequest();
        {
            if (DEBUG_PRINT) {
                System.out.println("Hyperbolic.transform()");
            }

            if (mGraph.getNumNodes() > 0) {
                ++mIteration;
                mRenderQueue.clear();
                mTransformQueue.clear();

                mTransformTemporary.mul(transform, mTransform);
                mTransform.set(mTransformTemporary);

                markNodeVisited(mStartingNode, mIteration);
                mStartingRadius = transformAndEnqueueNode(mStartingNode);
                mState = STATE_NODE;
            } else {
                mRenderQueue.clear();
                mRenderQueue.end();
                mState = STATE_IDLE;
            }
        }
        endRequest();
    }

    public synchronized void transformNode(int node, Point4d p) {
        mGraph.getNodeLayoutCoordinates(node, p);
        mTransform.transform(p);
    }

    // XXX: Why isn't this safeguarded with startRequest() .. endRequest()?
    public synchronized void pushPosition() {
        Position position = new Position();
        position.startingNode = mStartingNode;
        position.transform.set(mTransform);

        mSavedPositions.add(position);
    }

    public synchronized void popPosition() {
        startRequest();
        {
            if (mGraph.getNumNodes() > 0) {
                Position position = mSavedPositions.remove(mSavedPositions.size() - 1);
                reinstatePosition(position);
            } else {
                mRenderQueue.clear();
                mRenderQueue.end();
                mState = STATE_IDLE;
            }
        }
        endRequest();
    }

    public synchronized void discardPosition() {
        mSavedPositions.remove(mSavedPositions.size() - 1);
    }

    public synchronized Position getPosition() {
        Position retval = new Position();
        retval.startingNode = mStartingNode;
        retval.transform.set(mTransform);
        return retval;
    }

    public synchronized void setPosition(Position position) {
        startRequest();
        {
            if (mGraph.getNumNodes() > 0) {
                reinstatePosition(position);
            } else {
                mRenderQueue.clear();
                mRenderQueue.end();
                mState = STATE_IDLE;
            }
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

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (Runnable)
    ////////////////////////////////////////////////////////////////////////

    public void run() {
        transformIdentity();

        while (true) {
            rendezvousWithRequests();
            if (mState == STATE_SHUTDOWN) {
                System.out.println("H3Transformer exiting...");
                return;
            }

            mNumTransformed = 0;
            while (mState != STATE_IDLE
                    && mNumTransformed < NUM_PER_ITERATION) {
                switch (mState) {
                    case STATE_NODE:
                        if (DEBUG_PRINT) {
                            System.out.println("Hyperbolic.STATE_NODE");
                        }
                        beNodeState();
                        break;

                    case STATE_CHILD_LINK:
                        if (DEBUG_PRINT) {
                            System.out.println("Hyperbolic.STATE_CHILD_LINK");
                        }
                        beChildLinkState();
                        break;

                    case STATE_NONTREE_LINK:
                        if (DEBUG_PRINT) {
                            System.out.println("Hyperbolic.STATE_NONTREE_LINK");
                        }
                        beNontreeLinkState();
                        break;

                    case STATE_SHUTDOWN:
                        //FALLTHROUGH
                    case STATE_IDLE:
                        //FALLTHROUGH
                    default:
                        throw new RuntimeException("Invalid state: " + mState);
                }
            }

            if (mNumTransformed > 0) {
                mRenderQueue.add(mNumTransformed, mTransformedData);
            }

            if (mState == STATE_IDLE) {
                mRenderQueue.end();
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void transformIdentity() {
        System.out.println("Hyperbolic.transformIdentity()");

        if (mGraph.getNumNodes() > 0) {
            ++mIteration;
            mRenderQueue.clear();
            mTransformQueue.clear();
            mTransform.setIdentity();

            markNodeVisited(mStartingNode, mIteration);
            mStartingRadius = transformAndEnqueueNode(mStartingNode);
            mState = STATE_NODE;
        } else {
            mRenderQueue.clear();
            mRenderQueue.end();
            mState = STATE_IDLE;
        }
    }

    // NOTE: This assumes that m_graph.getNumNodes() > 0.
    private synchronized void reinstatePosition(Position position) {
        ++mIteration;
        mRenderQueue.clear();
        mTransformQueue.clear();

        mStartingNode = position.startingNode;
        mTransform.set(position.transform);

        markNodeVisited(mStartingNode, mIteration);
        mStartingRadius = transformAndEnqueueNode(mStartingNode);
        mState = STATE_NODE;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS (states)
    ////////////////////////////////////////////////////////////////////////

    private void beNodeState() {
        if (mTransformQueue.isEmpty()) {
            mState = STATE_IDLE;
        } else {
            mCurrentNode = mTransformQueue.dequeue();
            mTransformedData[mNumTransformed++] =
                    ((long) H3RenderQueue.Element.TYPE_NODE << 32) | mCurrentNode;

            checkCandidateForStarting(mCurrentNode);

            int parent = mGraph.getNodeParent(mCurrentNode);
            if (parent >= 0) {
                transformAndEnqueueNodeIfNotVisited(parent);
            }

            mCurrentChildIndex = mGraph.getNodeChildIndex(mCurrentNode);
            mCurrentNontreeIndex = mGraph.getNodeNontreeIndex(mCurrentNode);
            mCurrentLinksEndIndex =
                    mGraph.getNodeLinksEndIndex(mCurrentNode);

            mCurrentIndex = mCurrentChildIndex;

            if (mCurrentChildIndex < mCurrentNontreeIndex) {
                mState = STATE_CHILD_LINK;
            } else {
                // Stay in STATE_NODE unless there are non-tree links.
                if (mTransformNontreeLinks) {
                    if (mCurrentNontreeIndex < mCurrentLinksEndIndex) {
                        mState = STATE_NONTREE_LINK;
                    }
                }
            }
        }
    }

    private void beChildLinkState() {
        mTransformedData[mNumTransformed++] =
                ((long) H3RenderQueue.Element.TYPE_TREE_LINK << 32)
                        | mCurrentIndex;

        int child = mGraph.getLinkDestination(mCurrentIndex);
        transformAndEnqueueNodeIfNotVisited(child);

        if (++mCurrentIndex == mCurrentNontreeIndex) {
            mState = STATE_NODE;
            if (mTransformNontreeLinks) {
                if (mCurrentNontreeIndex < mCurrentLinksEndIndex) {
                    mState = STATE_NONTREE_LINK;
                }
            }
        }
        // else stay in STATE_CHILD_LINK
    }

    private void beNontreeLinkState() {
        mTransformedData[mNumTransformed++] =
                ((long) H3RenderQueue.Element.TYPE_NONTREE_LINK << 32)
                        | mCurrentIndex;

        int target = mGraph.getLinkDestination(mCurrentIndex);
        transformAndEnqueueNodeIfNotVisited(target);

        if (++mCurrentIndex == mCurrentLinksEndIndex) {
            mState = STATE_NODE;
        }
        // else stay in STATE_NONTREE_LINK
    }

    private void transformAndEnqueueNodeIfNotVisited(int node) {
        if (!markNodeVisited(node, mIteration)) {
            transformAndEnqueueNode(node);
        }
    }

    private double transformAndEnqueueNode(int node) {
        double radius = transformNode(node);
        mTransformQueue.enqueue(node, radius);
        return radius;
    }

    // The same radius calculation is done in
    // H3NonadaptiveRenderLoop.computeNodeRadius().
    // The two methods should be kept in sync to ensure a consistent display
    // when the user turns adaptive rendering on/off.
    private double transformNode(int node) {
        mGraph.getNodeLayoutCoordinates(node, mNodeCoordinates);
        mTransform.transform(mNodeCoordinates);

        double radius = H3Math.computeRadiusEuclidean(mNodeCoordinates);

        mGraph.setNodeRadius(node, radius);
        mGraph.setNodeCoordinates(node, mNodeCoordinates);
        return radius;
    }

    private void checkCandidateForStarting(int node) {
        double radius = mGraph.getNodeRadius(node);
        if (radius > mStartingRadius) {
            mStartingNode = node;
            mStartingRadius = radius;
        }
    }

    private boolean checkNodeVisited(int node, int iteration) {
        return mVisited[node] == iteration;
    }

    private boolean markNodeVisited(int node, int iteration) {
        boolean retval = mVisited[node] == iteration;
        if (!retval) {
            mVisited[node] = iteration;
        }
        return retval;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS (request synchronization)
    ////////////////////////////////////////////////////////////////////////

    private synchronized void startRequest() {
        ++mNumPendingRequests;
        while (!mIsRequestTurn) {
            waitIgnore();
        }
        --mNumPendingRequests;
    }

    private synchronized void endRequest() {
        mIsRequestTurn = false;
        notifyAll();
    }

    private synchronized void rendezvousWithRequests() {
        if (mState == STATE_IDLE || mNumPendingRequests > 0) {
            do {
                mIsRequestTurn = true;
                notifyAll();
                waitIgnore();
            }
            while (mState == STATE_IDLE);
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

    private static final int STATE_SHUTDOWN = 0;
    private static final int STATE_IDLE = 1;
    private static final int STATE_NODE = 2;
    private static final int STATE_CHILD_LINK = 3;
    private static final int STATE_NONTREE_LINK = 4;

    private int mState = STATE_IDLE;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private int mNumPendingRequests;
    private boolean mIsRequestTurn;

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private int mIteration;
    // Whether a node has been visited in "traversal iteration" t > 0.
    private final int[] mVisited;

    private int mStartingNode; // Will be set to the root node in constructor.
    private double mStartingRadius = 0.0;

    private final boolean mTransformNontreeLinks;

    private final H3Graph mGraph;
    private final H3RenderQueue mRenderQueue;
    private final H3TransformQueue mTransformQueue;

    private final Matrix4d mTransform = new Matrix4d();

    private final List<Position> mSavedPositions =
            new ArrayList<>(); // List<Position>

    private final Matrix4d mTransformTemporary = new Matrix4d(); // scratch
    private final Point4d mNodeCoordinates = new Point4d(); // scratch variable

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private static final int NUM_PER_ITERATION = 100;

    private int mNumTransformed;

    // A long in m_transformedData is a structure of two ints (a, b),
    // where ((a << 32) | b) equals the long, and has one of the following
    // forms and meanings:
    //
    //    (TYPE_NODE, n)           => a node {n}
    //    (TYPE_TREE_LINK, t)      => a tree link {t}
    //    (TYPE_NONTREE_LINK, nt)  => a non-tree link {nt}
    //
    // where TYPE_NODE, etc., are the constants defined in
    // H3RenderQueue.Element.
    //
    private final long[] mTransformedData = new long[NUM_PER_ITERATION];

    //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private int mCurrentNode;
    private int mCurrentIndex;
    private int mCurrentChildIndex;
    private int mCurrentNontreeIndex;
    private int mCurrentLinksEndIndex;

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC CLASSES
    ////////////////////////////////////////////////////////////////////////

    public static class Position {
        public int startingNode;
        public Matrix4d transform = new Matrix4d(); // Copy of original matrix.
    }
}
