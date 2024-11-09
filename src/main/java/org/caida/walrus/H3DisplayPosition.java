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

import org.jogamp.java3d.Transform3D;
import org.jogamp.vecmath.Matrix4d;

public class H3DisplayPosition {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3DisplayPosition
            (int centerNode, Transform3D rotation, Matrix4d translation) {
        mCenterNode = centerNode;
        mRotation = new Transform3D(rotation);
        mTranslation = new Matrix4d(translation);
    }

    ///////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ///////////////////////////////////////////////////////////////////////

    public int getCenterNode() {
        return mCenterNode;
    }

    public Transform3D getRotation() {
        return mRotation;
    }

    public Matrix4d getTranslation() {
        return mTranslation;
    }

    ///////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ///////////////////////////////////////////////////////////////////////

    private final int mCenterNode;
    private final Transform3D mRotation;
    private final Matrix4d mTranslation;
}
