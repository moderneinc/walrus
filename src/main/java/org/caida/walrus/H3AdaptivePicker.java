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

public class H3AdaptivePicker
        extends H3PickerCommon {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3AdaptivePicker(H3Graph graph, H3Canvas3D canvas,
                            H3ViewParameters parameters,
                            H3RenderQueue queue) {
        super(graph, canvas, parameters);

        mRenderQueue = queue;

        int numNodes = graph.getNumNodes();
        mNodesInEye = new int[numNodes];
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (H3Picker)
    ////////////////////////////////////////////////////////////////////////

    public void reset() {
        mNumExaminedElements = 0;
        mNumComputedPointsInEye = 0;
    }

    ////////////////////////////////////////////////////////////////////////
    // PROTECTED METHODS (abstract in H3PickerCommon)
    ////////////////////////////////////////////////////////////////////////

    protected void computePointsInEye() {
        int currentNumElements = mRenderQueue.getCurrentNumElements();
        if (mNumExaminedElements < currentNumElements) {
            if (DEBUG_PRINT) {
                System.out.println("computing points in eye ...");
                System.out.println("numExaminedElements = "
                        + mNumExaminedElements);
                System.out.println("currentNumElements = "
                        + currentNumElements);
                System.out.println("numComputedPointsInEye = "
                        + mNumComputedPointsInEye);
            }

            Transform3D transform = m_parameters.getObjectToEyeTransform();

            Point3d p = new Point3d();
            H3RenderQueue.Element element = new H3RenderQueue.Element();
            for (int i = mNumExaminedElements; i < currentNumElements; i++) {
                mRenderQueue.get(i, element);
                if (element.type == H3RenderQueue.Element.TYPE_NODE) {
                    mNodesInEye[mNumComputedPointsInEye] = element.data;

                    m_graph.getNodeCoordinates(element.data, p);
                    transform.transform(p);

                    m_pointsInEyeX[mNumComputedPointsInEye] = p.x;
                    m_pointsInEyeY[mNumComputedPointsInEye] = p.y;
                    m_pointsInEyeZ[mNumComputedPointsInEye] = p.z;

                    ++mNumComputedPointsInEye;
                }
            }

            mNumExaminedElements = currentNumElements;
        }
    }

    protected int getNumComputedPointsInEye() {
        return mNumComputedPointsInEye;
    }

    protected int getNodeInEye(int index) {
        return mNodesInEye[index];
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private final H3RenderQueue mRenderQueue;

    private int mNumExaminedElements;
    private int mNumComputedPointsInEye;

    private final int[] mNodesInEye;
}
