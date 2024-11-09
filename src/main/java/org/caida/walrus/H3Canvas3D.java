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



import org.jogamp.java3d.Background;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.vecmath.Color3f;

import java.awt.*;
import java.util.Observable;
import java.util.Observer;

public class H3Canvas3D
        extends Canvas3D {
    public H3Canvas3D(GraphicsConfiguration config) {
        super(config);
        Background newBk = new Background(new Color3f(255, 255, 255));
//        newBk.setApplicationBounds(new BoundingSphere(new Point3d(0f,0f,0f), 100));
        getGraphicsContext3D().setBackground(newBk);
    }

    public void addPaintObserver(Observer o) {
        mObservable.addObserver(o);
    }

    public void removePaintObserver(Observer o) {
        mObservable.deleteObserver(o);
    }

    public void paint(Graphics g) {
        super.paint(g);
        mObservable.notifyPaintObservers();
    }

    //=======================================================================

    private final PaintObservable mObservable = new PaintObservable();

    private class PaintObservable extends Observable {
        public PaintObservable() {
            super();
        }

        public void notifyPaintObservers() {
            setChanged();
            notifyObservers();
        }
    }
}
