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


public class H3TransformQueue {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3TransformQueue(int numNodes) {
        mElements = new int[numNodes + 1];
        mRadii = new double[numNodes + 1];

        mRadii[0] = Double.MAX_VALUE; // sentinel for use in enqueue()
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public int dequeue() {
        if (mNumElements == 0) {
            throw new RuntimeException("Queue is empty.");
        }

        int retval = mElements[1];

        double lastRadius = mRadii[mNumElements];
        int lastElement = mElements[mNumElements];
        --mNumElements;

        int i = 1;
        boolean more = true;
        while (more && i * 2 <= mNumElements) {
            int child = i * 2;
            if (child < mNumElements) {
                if (mRadii[child + 1] > mRadii[child]) {
                    ++child;
                }
            }

            if (lastRadius < mRadii[child]) {
                mRadii[i] = mRadii[child];
                mElements[i] = mElements[child];
                i = child;
            } else {
                more = false;
            }
        }

        mRadii[i] = lastRadius;
        mElements[i] = lastElement;

        return retval;
    }

    public void enqueue(int node, double radius) {
        if (mNumElements == mElements.length - 1) {
            throw new RuntimeException("Queue is full.");
        }

        int i = ++mNumElements;
        while (mRadii[i / 2] < radius) {
            mRadii[i] = mRadii[i / 2];
            mElements[i] = mElements[i / 2];
            i /= 2;
        }

        mRadii[i] = radius;
        mElements[i] = node;
    }

    public void clear() {
        mNumElements = 0;
    }

    public boolean isEmpty() {
        return mNumElements == 0;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private int mNumElements;
    private final int[] mElements;
    private final double[] mRadii;

    ////////////////////////////////////////////////////////////////////////
    // TEST METHODS
    ////////////////////////////////////////////////////////////////////////

    public void dumpForTesting() {
        System.out.println();
        System.out.println(this + ":");
        System.out.println("\tnumElements: " + mNumElements);
        System.out.println("\telements.length: " + mElements.length);
        System.out.println("\tradii.length: " + mRadii.length);

        for (int i = 1; i <= mNumElements; i++) {
            System.out.println("\t" + i + ": ("
                    + mElements[i] + ", "
                    + mRadii[i] + ")");

            if (i * 2 <= mNumElements) {
                if (mRadii[i * 2] > mRadii[i]) {
                    System.out.println("ERROR: Heap order violated "
                            + "by left child.");
                } else if (i * 2 < mNumElements
                        && mRadii[i * 2 + 1] > mRadii[i]) {
                    System.out.println("ERROR: Heap order violated "
                            + "by right child.");
                }
            }
        }

        System.out.println();
    }

    public void dumpForTesting2() {
        System.out.println();
        System.out.println(this + ":");
        System.out.println("\tnumElements: " + mNumElements);
        System.out.println("\telements.length: " + mElements.length);
        System.out.println("\tradii.length: " + mRadii.length);

        if (mNumElements > 0) {
            dumpForTesting2Aux(1, 1);
        }

        System.out.println();
    }

    public void dumpForTesting2Aux(int n, int level) {
        if (n * 2 + 1 <= mNumElements) {
            dumpForTesting2Aux(n * 2 + 1, level + 1);
        }

        indentForTesting(level);
        System.out.println("[" + n + "] ("
                + mElements[n] + ", "
                + mRadii[n] + ")");

        if (mRadii[n] > mRadii[n / 2]) {
            System.out.println("ERROR: Heap order violated by " + n + ".");
        }

        if (n * 2 <= mNumElements) {
            dumpForTesting2Aux(n * 2, level + 1);
        }
    }

    public void indentForTesting(int n) {
        System.out.print('\t');
        for (int i = 0; i < n; i++) {
            System.out.print("  ");
        }
    }
}
