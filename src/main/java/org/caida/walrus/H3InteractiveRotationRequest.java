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

public class H3InteractiveRotationRequest
        implements H3RotationRequest {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3InteractiveRotationRequest() {
        clear();
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public synchronized void start() {
        clear();
    }

    public synchronized void rotate(double h, double v) {
        mHorizontal += h;
        mVertical += v;

        if (mIsWaiting) {
            notifyAll();
        }
    }

    public synchronized void end() {
        mIsRotating = false;

        if (mIsWaiting) {
            notifyAll();
        } else {
            waitIgnore();
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public synchronized boolean getRotation(Matrix4d rot) {
        boolean retval = false;

        boolean tryAgain;
        do {
            tryAgain = false;
            if (mHorizontal != 0.0 || mVertical != 0.0) {
                mRot.rotX(mVertical);
                rot.rotY(mHorizontal);
                rot.mul(mRot);

                mHorizontal = 0.0;
                mVertical = 0.0;
                retval = true;
            } else {
                if (mIsRotating) {
                    mIsWaiting = true;
                    waitIgnore();
                    tryAgain = true;
                } else {
                    notifyAll(); // Wake up any thread waiting in end().
                }
            }
        }
        while (tryAgain);

        return retval;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void clear() {
        mIsWaiting = false;
        mIsRotating = true;
        mHorizontal = 0.0;
        mVertical = 0.0;
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

    private boolean mIsWaiting;
    private boolean mIsRotating;
    private double mHorizontal;
    private double mVertical;

    private final Matrix4d mRot = new Matrix4d();
}
