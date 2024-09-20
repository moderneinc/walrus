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


public class H3RenderQueue {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3RenderQueue(int size) {
        mData = new long[size];
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public synchronized boolean get(int index, Element element) {
        boolean retval = false;

        boolean tryAgain;
        do {
            tryAgain = false;
            if (index < mNumElements) {
                decode(mData[index], element);
                retval = true;
            } else {
                if (!mIsComplete) {
                    mIsWaitingForData = true;
                    waitIgnore();
                    tryAgain = true;
                }
            }
        }
        while (tryAgain);

        return retval;
    }

    public synchronized int getMaxNumElements() {
        return mData.length;
    }

    public synchronized int getCurrentNumElements() {
        return mNumElements;
    }

    public synchronized boolean isComplete() {
        return mIsComplete;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public synchronized void add(int n, long[] data) {
        System.arraycopy(data, 0, mData, mNumElements, n);
        mNumElements += n;
        notifyIfWaiting();
    }

    public synchronized void clear() {
        mNumElements = 0;
        mIsComplete = false;
        notifyIfWaiting();
    }

    public synchronized void end() {
        mIsComplete = true;
        notifyIfWaiting();
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void decode(long data, Element element) {
        // See comments for m_transformedData in H3Transformer.java
        // for details about the encoding of {data}.

        element.type = (int) (data >> 32);
        element.data = (int) (data & 0xFFFFFFFF);
    }

    private synchronized void notifyIfWaiting() {
        if (mIsWaitingForData) {
            mIsWaitingForData = false;
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

    private final long[] mData;
    private int mNumElements;
    private boolean mIsComplete;
    private boolean mIsWaitingForData;

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC CLASSES
    ////////////////////////////////////////////////////////////////////////

    public static class Element {
        public static final int TYPE_NODE = 0;
        public static final int TYPE_TREE_LINK = 1;
        public static final int TYPE_NONTREE_LINK = 2;

        int type;
        int data;
    }
}
