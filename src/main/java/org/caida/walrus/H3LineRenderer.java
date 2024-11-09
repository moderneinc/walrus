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

public class H3LineRenderer
        implements H3AdaptiveRenderer {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3LineRenderer(H3Graph graph, H3RenderQueue queue,
                          H3RenderList list) {
        mGraph = graph;
        mRenderQueue = queue;
        mRenderList = list;
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (H3AdaptiveRenderer)
    ////////////////////////////////////////////////////////////////////////

    public void render(GraphicsContext3D gc) {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("render.begin[" + startTime + "]");
        }

        computeRenderFrame();
        mRenderList.render(gc);

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("render.end[" + stopTime + "]");
            System.out.println("render.time[" + duration + "]");
        }
    }

    public void refine(GraphicsContext3D gc) {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("refine.begin[" + startTime + "]");
        }

        computeRefineFrame();
        mRenderList.render(gc);
        gc.flush(true);

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("refine.end[" + stopTime + "]");
            System.out.println("refine.time[" + duration + "]");
        }
    }

    public void reset() {
        mNumDisplayedElements = 0;
    }

    public boolean isFinished() {
        return mNumDisplayedElements == mRenderQueue.getCurrentNumElements()
                && mRenderQueue.isComplete();
    }

    public void setMaxDuration(long max) {
        mMaxDuration = max;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void computeRenderFrame() {
        long start = System.currentTimeMillis();

        mRenderList.beginFrame();

        boolean more = computeDisplay(0, mNumDisplayedElements);
        while (more && System.currentTimeMillis() - start < mMaxDuration) {
            more = computeDisplay(mNumDisplayedElements, NUM_PER_ITERATION);
        }

        mRenderList.endFrame();
    }

    private void computeRefineFrame() {
        long start = System.currentTimeMillis();

        mRenderList.beginFrame();

        boolean more = true;
        while (more && System.currentTimeMillis() - start < mMaxDuration) {
            more = computeDisplay(mNumDisplayedElements, NUM_PER_ITERATION);
        }

        mRenderList.endFrame();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private boolean computeDisplay(int index, int count) {
        boolean retval = true;

        mNumDisplayedElements = index;

        H3RenderQueue.Element element = new H3RenderQueue.Element();

        boolean more = true;
        while (more && count-- > 0) {
            if (mRenderQueue.get(mNumDisplayedElements, element)) {
                ++mNumDisplayedElements;

                if (element.type == H3RenderQueue.Element.TYPE_NODE) {
                    mRenderList.addNode(element.data);
                } else if (element.type == H3RenderQueue.Element.TYPE_TREE_LINK) {
                    mRenderList.addTreeLink(element.data);
                } else //(type == H3RenderQueue.Element.TYPE_NONTREE_LINK)
                {
                    mRenderList.addNontreeLink(element.data);
                }
            } else {
                retval = false;
                more = false;
            }
        }

        return retval;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = false;
    private static final int NUM_PER_ITERATION = 25;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private long mMaxDuration = Long.MAX_VALUE;

    private final H3Graph mGraph;
    private final H3RenderQueue mRenderQueue;
    private final H3RenderList mRenderList;

    private int mNumDisplayedElements;
}
