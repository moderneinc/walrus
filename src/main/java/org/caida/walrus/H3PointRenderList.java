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
import org.jogamp.vecmath.Point3d;

public class H3PointRenderList
        implements H3RenderList {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3PointRenderList(H3Graph graph, boolean useNodeSizes,
                             boolean includeColors) {
        this(graph, useNodeSizes,
                true, includeColors,
                true, includeColors,
                true, includeColors);
    }

    public H3PointRenderList(H3Graph graph,
                             boolean useNodeSizes,
                             boolean includeNodes,
                             boolean includeNodeColor,
                             boolean includeTreeLinks,
                             boolean includeTreeLinkColor,
                             boolean includeNontreeLinks,
                             boolean includeNontreeLinkColor) {
        mGraph = graph;

        USE_NODE_SIZES = useNodeSizes;
        INCLUDE_NODES = includeNodes;
        INCLUDE_NODE_COLOR = includeNodeColor;
        INCLUDE_TREE_LINKS = includeTreeLinks;
        INCLUDE_TREE_LINK_COLOR = includeTreeLinkColor;
        INCLUDE_NONTREE_LINKS =
                includeNontreeLinks && graph.getNumNontreeLinks() > 0;
        INCLUDE_NONTREE_LINK_COLOR =
                includeNontreeLinks && includeNontreeLinkColor;

        // Node data. - - - - - - - - - - - - - - - - - - - - - - - - - -

        if (INCLUDE_NODES) {
            int nodeFormat = PointArray.COORDINATES | PointArray.BY_REFERENCE;
            if (INCLUDE_NODE_COLOR) {
                nodeFormat |= PointArray.COLOR_3;
            }

            int numNodes = graph.getNumNodes();

            mNearNodes = new PointArray(numNodes, nodeFormat);
            mNearNodeCoordinates = new double[numNodes * 3];
            mNearNodes.setCoordRefDouble(mNearNodeCoordinates);
            mNearNodes.setValidVertexCount(0);

            if (USE_NODE_SIZES) {
                mMiddleNodes = new PointArray(numNodes, nodeFormat);
                mMiddleNodeCoordinates = new double[numNodes * 3];
                mMiddleNodes.setCoordRefDouble(mMiddleNodeCoordinates);
                mMiddleNodes.setValidVertexCount(0);

                // --  --  --  --  --  --  --  --  --  --  --  --  --  --  --

                mFarNodes = new PointArray(numNodes, nodeFormat);
                mFarNodeCoordinates = new double[numNodes * 3];
                mFarNodes.setCoordRefDouble(mFarNodeCoordinates);
                mFarNodes.setValidVertexCount(0);
            }

            if (INCLUDE_NODE_COLOR) {
                mNearNodeColors = new byte[numNodes * 3];
                mNearNodes.setColorRefByte(mNearNodeColors);

                if (USE_NODE_SIZES) {
                    mMiddleNodeColors = new byte[numNodes * 3];
                    mMiddleNodes.setColorRefByte(mMiddleNodeColors);

                    mFarNodeColors = new byte[numNodes * 3];
                    mFarNodes.setColorRefByte(mFarNodeColors);
                }
            }
        }

        // Link data. - - - - - - - - - - - - - - - - - - - - - - - - - -

        if (INCLUDE_TREE_LINKS) {
            int lineFormat = LineArray.COORDINATES | LineArray.BY_REFERENCE;
            if (INCLUDE_TREE_LINK_COLOR) {
                lineFormat |= LineArray.COLOR_3;
            }

            int numLinks = graph.getNumTreeLinks();
            mTreeLinks = new LineArray(numLinks * 2, lineFormat);
            mTreeLinkCoordinates = new double[numLinks * 3 * 2];
            mTreeLinks.setCoordRefDouble(mTreeLinkCoordinates);
            mTreeLinks.setValidVertexCount(0);

            if (INCLUDE_TREE_LINK_COLOR) {
                mTreeLinkColors = new byte[numLinks * 3 * 2];
                mTreeLinks.setColorRefByte(mTreeLinkColors);
            }
        }

        if (INCLUDE_NONTREE_LINKS) {
            int lineFormat = LineArray.COORDINATES | LineArray.BY_REFERENCE;
            if (INCLUDE_NONTREE_LINK_COLOR) {
                lineFormat |= LineArray.COLOR_3;
            }

            int numLinks = graph.getNumNontreeLinks();
            mNontreeLinks = new LineArray(numLinks * 2, lineFormat);
            mNontreeLinkCoordinates = new double[numLinks * 3 * 2];
            mNontreeLinks.setCoordRefDouble(mNontreeLinkCoordinates);
            mNontreeLinks.setValidVertexCount(0);

            if (INCLUDE_NONTREE_LINK_COLOR) {
                mNontreeLinkColors = new byte[numLinks * 3 * 2];
                mNontreeLinks.setColorRefByte(mNontreeLinkColors);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // INTERFACE METHODS (H3RenderList)
    ////////////////////////////////////////////////////////////////////////

    public void beginFrame() {
        mNumNearNodesDisplayed = 0;
        mNumMiddleNodesDisplayed = 0;
        mNumFarNodesDisplayed = 0;
        mNumTreeVerticesDisplayed = 0;
        mNumNontreeVerticesDisplayed = 0;

        mNearNodeIndex = 0;
        mMiddleNodeIndex = 0;
        mFarNodeIndex = 0;
        mTreeLinkIndex = 0;
        mNontreeLinkIndex = 0;

        mNearNodeColorIndex = 0;
        mMiddleNodeColorIndex = 0;
        mFarNodeColorIndex = 0;
        mTreeLinkColorIndex = 0;
        mNontreeLinkColorIndex = 0;
    }

    public void endFrame() {
        if (mNumNearNodesDisplayed > 0) {
            mNearNodes.setValidVertexCount(mNumNearNodesDisplayed);
        }

        if (mNumMiddleNodesDisplayed > 0) {
            mMiddleNodes.setValidVertexCount(mNumMiddleNodesDisplayed);
        }

        if (mNumFarNodesDisplayed > 0) {
            mFarNodes.setValidVertexCount(mNumFarNodesDisplayed);
        }

        if (mNumTreeVerticesDisplayed > 0) {
            mTreeLinks.setValidVertexCount(mNumTreeVerticesDisplayed);
        }

        if (mNumNontreeVerticesDisplayed > 0) {
            mNontreeLinks.setValidVertexCount(mNumNontreeVerticesDisplayed);
        }
    }

    public void addNode(int node) {
        if (INCLUDE_NODES && mGraph.checkNodeVisible(node)) {
            double radius = 0.0;
            mGraph.getNodeCoordinates(node, mSource);

            if (USE_NODE_SIZES) {
                radius = mGraph.getNodeRadius(node);
                if (radius < FAR_NODES_THRESHOLD) {
                    ++mNumFarNodesDisplayed;
                    mFarNodeCoordinates[mFarNodeIndex++] = mSource.x;
                    mFarNodeCoordinates[mFarNodeIndex++] = mSource.y;
                    mFarNodeCoordinates[mFarNodeIndex++] = mSource.z;
                } else if (radius < MIDDLE_NODES_THRESHOLD) {
                    ++mNumMiddleNodesDisplayed;
                    mMiddleNodeCoordinates[mMiddleNodeIndex++] = mSource.x;
                    mMiddleNodeCoordinates[mMiddleNodeIndex++] = mSource.y;
                    mMiddleNodeCoordinates[mMiddleNodeIndex++] = mSource.z;
                } else {
                    ++mNumNearNodesDisplayed;
                    mNearNodeCoordinates[mNearNodeIndex++] = mSource.x;
                    mNearNodeCoordinates[mNearNodeIndex++] = mSource.y;
                    mNearNodeCoordinates[mNearNodeIndex++] = mSource.z;
                }
            } else {
                ++mNumNearNodesDisplayed;
                mNearNodeCoordinates[mNearNodeIndex++] = mSource.x;
                mNearNodeCoordinates[mNearNodeIndex++] = mSource.y;
                mNearNodeCoordinates[mNearNodeIndex++] = mSource.z;
            }

            if (INCLUDE_NODE_COLOR) {
                int color = mGraph.getNodeColor(node);
                byte r = (byte) ((color >> 16) & 0xff);
                byte g = (byte) ((color >> 8) & 0xff);
                byte b = (byte) (color & 0xff);

                if (USE_NODE_SIZES) {
                    if (radius < FAR_NODES_THRESHOLD) {
                        mFarNodeColors[mFarNodeColorIndex++] = r;
                        mFarNodeColors[mFarNodeColorIndex++] = g;
                        mFarNodeColors[mFarNodeColorIndex++] = b;
                    } else if (radius < MIDDLE_NODES_THRESHOLD) {
                        mMiddleNodeColors[mMiddleNodeColorIndex++] = r;
                        mMiddleNodeColors[mMiddleNodeColorIndex++] = g;
                        mMiddleNodeColors[mMiddleNodeColorIndex++] = b;
                    } else {
                        mNearNodeColors[mNearNodeColorIndex++] = r;
                        mNearNodeColors[mNearNodeColorIndex++] = g;
                        mNearNodeColors[mNearNodeColorIndex++] = b;
                    }
                } else {
                    mNearNodeColors[mNearNodeColorIndex++] = r;
                    mNearNodeColors[mNearNodeColorIndex++] = g;
                    mNearNodeColors[mNearNodeColorIndex++] = b;
                }
            }
        }
    }

    public void addTreeLink(int link) {
        if (INCLUDE_TREE_LINKS && mGraph.checkLinkVisible(link)) {
            int sourceNode = mGraph.getLinkSource(link);
            int targetNode = mGraph.getLinkDestination(link);

            if (SHOW_LINKS_OF_HIDDEN_NODES
                    || (mGraph.checkNodeVisible(sourceNode)
                    && mGraph.checkNodeVisible(targetNode))) {
                mNumTreeVerticesDisplayed += 2;

                mGraph.getNodeCoordinates(sourceNode, mSource);
                mTreeLinkCoordinates[mTreeLinkIndex++] = mSource.x;
                mTreeLinkCoordinates[mTreeLinkIndex++] = mSource.y;
                mTreeLinkCoordinates[mTreeLinkIndex++] = mSource.z;

                mGraph.getNodeCoordinates(targetNode, mTarget);
                mTreeLinkCoordinates[mTreeLinkIndex++] = mTarget.x;
                mTreeLinkCoordinates[mTreeLinkIndex++] = mTarget.y;
                mTreeLinkCoordinates[mTreeLinkIndex++] = mTarget.z;

                if (INCLUDE_TREE_LINK_COLOR) {
                    int color = mGraph.getLinkColor(link);
                    byte r = (byte) ((color >> 16) & 0xff);
                    byte g = (byte) ((color >> 8) & 0xff);
                    byte b = (byte) (color & 0xff);

                    mTreeLinkColors[mTreeLinkColorIndex++] = r;
                    mTreeLinkColors[mTreeLinkColorIndex++] = g;
                    mTreeLinkColors[mTreeLinkColorIndex++] = b;

                    mTreeLinkColors[mTreeLinkColorIndex++] = r;
                    mTreeLinkColors[mTreeLinkColorIndex++] = g;
                    mTreeLinkColors[mTreeLinkColorIndex++] = b;
                }
            }
        }
    }

    public void addNontreeLink(int link) {
        if (INCLUDE_NONTREE_LINKS && mGraph.checkLinkVisible(link)) {
            int sourceNode = mGraph.getLinkSource(link);
            int targetNode = mGraph.getLinkDestination(link);

            if (SHOW_LINKS_OF_HIDDEN_NODES
                    || (mGraph.checkNodeVisible(sourceNode)
                    && mGraph.checkNodeVisible(targetNode))) {
                mNumNontreeVerticesDisplayed += 2;

                mGraph.getNodeCoordinates(sourceNode, mSource);
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mSource.x;
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mSource.y;
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mSource.z;

                mGraph.getNodeCoordinates(targetNode, mTarget);
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mTarget.x;
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mTarget.y;
                mNontreeLinkCoordinates[mNontreeLinkIndex++] = mTarget.z;

                if (INCLUDE_NONTREE_LINK_COLOR) {
                    int color = mGraph.getLinkColor(link);
                    byte r = (byte) ((color >> 16) & 0xff);
                    byte g = (byte) ((color >> 8) & 0xff);
                    byte b = (byte) (color & 0xff);

                    mNontreeLinkColors[mNontreeLinkColorIndex++] = r;
                    mNontreeLinkColors[mNontreeLinkColorIndex++] = g;
                    mNontreeLinkColors[mNontreeLinkColorIndex++] = b;

                    mNontreeLinkColors[mNontreeLinkColorIndex++] = r;
                    mNontreeLinkColors[mNontreeLinkColorIndex++] = g;
                    mNontreeLinkColors[mNontreeLinkColorIndex++] = b;
                }
            }
        }
    }

    public void render(GraphicsContext3D gc) {
        long startTime = 0;
        if (DEBUG_PRINT) {
            startTime = System.currentTimeMillis();
            System.out.println("render.begin[" + startTime + "]");
        }

        if (mNumNearNodesDisplayed > 0) {
            drawGeometry(gc, mNearNodes, mNearNodeAppearance);
        }

        if (mNumMiddleNodesDisplayed > 0) {
            drawGeometry(gc, mMiddleNodes, mMiddleNodeAppearance);
        }

        if (mNumFarNodesDisplayed > 0) {
            drawGeometry(gc, mFarNodes, mFarNodeAppearance);
        }

        if (mNumTreeVerticesDisplayed > 0) {
            drawGeometry(gc, mTreeLinks, mTreeLinkAppearance);
        }

        if (mNumNontreeVerticesDisplayed > 0) {
            drawGeometry(gc, mNontreeLinks, mNontreeLinkAppearance);
        }

        if (DEBUG_PRINT) {
            long stopTime = System.currentTimeMillis();
            long duration = stopTime - startTime;
            System.out.println("render.end[" + stopTime + "]");
            System.out.println("render.time[" + duration + "]");
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    ////////////////////////////////////////////////////////////////////////

    public void setNearNodeAppearance(Appearance appearance) {
        mNearNodeAppearance = appearance;
    }

    public void setMiddleNodeAppearance(Appearance appearance) {
        mMiddleNodeAppearance = appearance;
    }

    public void setFarNodeAppearance(Appearance appearance) {
        mFarNodeAppearance = appearance;
    }

    public void setTreeLinkAppearance(Appearance appearance) {
        mTreeLinkAppearance = appearance;
    }

    public void setNontreeLinkAppearance(Appearance appearance) {
        mNontreeLinkAppearance = appearance;
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    private void drawGeometry(GraphicsContext3D gc, Geometry geometry,
                              Appearance appearance) {
        Appearance currentAppearance = null;

        if (appearance != null) {
            currentAppearance = gc.getAppearance();
            gc.setAppearance(appearance);
        }

        gc.draw(geometry);

        if (currentAppearance != null) {
            gc.setAppearance(currentAppearance);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private static final boolean DEBUG_PRINT = false;

    private static final boolean SHOW_LINKS_OF_HIDDEN_NODES = true;

    private static final double MIDDLE_NODES_THRESHOLD = 0.5;
    private static final double FAR_NODES_THRESHOLD = 0.2;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private final H3Graph mGraph;

    // These are set in the constructor.
    private final boolean USE_NODE_SIZES;
    private final boolean INCLUDE_NODES;
    private final boolean INCLUDE_NODE_COLOR;
    private final boolean INCLUDE_TREE_LINKS;
    private final boolean INCLUDE_TREE_LINK_COLOR;
    private final boolean INCLUDE_NONTREE_LINKS;
    private final boolean INCLUDE_NONTREE_LINK_COLOR;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private Appearance mNearNodeAppearance;
    private Appearance mMiddleNodeAppearance;
    private Appearance mFarNodeAppearance;
    private Appearance mTreeLinkAppearance;
    private Appearance mNontreeLinkAppearance;

    private final Point3d mSource = new Point3d();  // scratch variable
    private final Point3d mTarget = new Point3d();  // scratch variable

    private int mNumNearNodesDisplayed;
    private int mNumMiddleNodesDisplayed;
    private int mNumFarNodesDisplayed;
    private int mNumTreeVerticesDisplayed;
    private int mNumNontreeVerticesDisplayed;

    private int mNearNodeIndex;
    private int mMiddleNodeIndex;
    private int mFarNodeIndex;
    private int mTreeLinkIndex;
    private int mNontreeLinkIndex;

    private int mNearNodeColorIndex;
    private int mMiddleNodeColorIndex;
    private int mFarNodeColorIndex;
    private int mTreeLinkColorIndex;
    private int mNontreeLinkColorIndex;

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // This contains the colors of all nodes flattened into a single array.
    // The color of each node appears as consecutive r, g, and b values.
    private byte[] mNearNodeColors;

    // This contains the coordinates of all nodes flattened into a single
    // array.  The coordinates of each node appear as consecutive x, y, and
    // z values.
    private double[] mNearNodeCoordinates;

    private PointArray mNearNodes; // refs the above two arrays

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    // See comments above for the set m_nearNodes, m_nearNodeColors, etc.
    private byte[] mMiddleNodeColors;
    private double[] mMiddleNodeCoordinates;
    private PointArray mMiddleNodes;

    private byte[] mFarNodeColors;
    private double[] mFarNodeCoordinates;
    private PointArray mFarNodes;

    private byte[] mTreeLinkColors;
    private double[] mTreeLinkCoordinates;
    private LineArray mTreeLinks;

    private byte[] mNontreeLinkColors;
    private double[] mNontreeLinkCoordinates;
    private LineArray mNontreeLinks;
}
