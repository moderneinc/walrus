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

import org.jogamp.vecmath.Matrix4d;
import org.jogamp.vecmath.Point3d;
import org.jogamp.vecmath.Point4d;

import java.util.Arrays;
import java.util.BitSet;

//
// This is the graph representation used and needed by the rendering part
// of Walrus.
// 
// Walrus traverses this graph while rendering the display.  Hence, the
// main requirement on the design of this class is fast read access.
// Also, nothing more need be stored in this class than the bare graph
// topology, coloring information, and coordinates; all other data (such
// as attributes associated with nodes and links) should be stored in a
// backing graph.  Because of these design goals, this class is minimal
// in implementation.
// 
// An additional design goal is the ability to handle large graphs.
// Because there will usually be a backing graph from which this class is
// populated, we want the overhead of construction to be as small as
// possible.  In particular, we want to eliminate the need for
// intermediate data structures during construction.  This goal is
// accomplished by requiring the user to populate links in a disciplined
// manner.  Specifically, links must be populated in batches, with all
// the links of each node populated in a consecutive sequence of
// operations.  That is, rather than allowing the user to add the links
// of all nodes in an arbitrary order, users must add all the links of
// some node A with a sequence of calls, and then all the links of some
// node B, and so on.  The ordering of the nodes themselves, however,
// doesn't matter.
// 
// This ordering requirement lets this class store a graph in a very
// compact way (as a set of parallel primitive arrays) without
// necessitating the use of an intermediate representation, which would
// raise the peak memory usage during graph construction---some graphs
// can take up more than 100 MBs of memory, so eliminating unnecessary
// data structures really is important.
// 
// So, the basic steps the user would take to create and populate an
// H3Graph is as follows, in pseudo-code:
// 
//        Create an H3Graph instance.
//        Iterate over the nodes in the user's data set [backing graph]:
//             If the node has outgoing links:
//                 Call startChildLinks().
//                 For each child link [tree link] (can be zero):
//                     Call addChildLink().
//                 Call startNontreeLinks().
//                 For each nontree link (can be zero):
//                     Call addNontreeLink().
//                 Call endNodeLinks().
// 
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// 
// This class refers to nodes and links using internally defined indices.
// Suppose you create an instance with the following:
// 
//     H3Graph graph = new H3Graph(numNodes, numLinks);
// 
// Then, as far as H3Graph is concerned, the nodes are identified with
// the indices 0, ..., numNodes-1, and links with 0, ..., numLinks-1.
// The actual correspondence between these indices and the nodes and
// links in the user's data set (backing graph) is entirely up to the
// user; all that matters to H3Graph is that the user always handles the
// correspondences consistently.  To help the user maintain the
// correspondences, H3Graph can associate an external ID number with each
// node or link.  This facility is provided through the methods
// {get,set}NodeID() and {get,set}LinkID().  Also, addChildLink() and
// addNontreeLink() take a parameter containing the external ID of the
// link being added.  In summary, to use H3Graph, users must have some
// way of mapping between their set of nodes and links and the IDs known
// by H3Graph.
// 
// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
// 
// Be sure to call setRootNode() at some point during construction so
// that the rendering part of Walrus (and any other users of this data)
// knows at which node to start its traversal.
// 

public class H3Graph {
    ////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    ////////////////////////////////////////////////////////////////////////

    public H3Graph(int numNodes, int numLinks) {
        // PRECONDITION: numNodes >= 0
        // PRECONDITION: numLinks >= 0

        m_numNodes = numNodes;
        m_numLinks = numLinks;

        mNodes = new Nodes(numNodes);
        mLinks = new Links(numLinks);
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC ACCESSOR METHODS
    ////////////////////////////////////////////////////////////////////////

    public int getNumNodes() {
        return m_numNodes;
    }

    public int getNumTreeLinks() {
        return mNumTreeLinks;
    }

    public int getNumNontreeLinks() {
        return mNumNontreeLinks;
    }

    public int getTotalNumLinks() {
        return m_numLinks;
    }

    public int getRootNode() {
        return mRootNode;
    }

    public int getNodeID(int node) {
        return mNodes.id[node];
    }

    public double getNodeRadius(int node) {
        return mNodes.radius[node];
    }

    public void getNodeCoordinates(int node, Point3d point) {
        point.x = mNodes.x[node];
        point.y = mNodes.y[node];
        point.z = mNodes.z[node];
    }

    public void getNodeCoordinates(int node, Point4d point) {
        point.x = mNodes.x[node];
        point.y = mNodes.y[node];
        point.z = mNodes.z[node];
        point.w = 1.0;
    }

    public void getNodeLayoutCoordinates(int node, Point3d point) {
        double w = mNodes.layoutW[node];
        point.x = mNodes.layoutX[node] / w;
        point.y = mNodes.layoutY[node] / w;
        point.z = mNodes.layoutZ[node] / w;
    }

    public void getNodeLayoutCoordinates(int node, Point4d point) {
        point.x = mNodes.layoutX[node];
        point.y = mNodes.layoutY[node];
        point.z = mNodes.layoutZ[node];
        point.w = mNodes.layoutW[node];
    }

    public int getNodeParent(int node) {
        int i = mNodes.parent[node];
        return i == -1 ? -1 : mLinks.source[i];
    }

    public int getNodeParentLink(int node) {
        return mNodes.parent[node];
    }

    public int getNodeNumChildren(int node) {
        return getNodeLinksEndIndex(node) - getNodeChildIndex(node);
    }

    // The following methods--getNodeChildIndex(), getNodeNontreeIndex(),
    // and getNodeLinksEndIndex()--provide a way of iterating over all
    // the outgoing links of a node.
    //
    // You would iterate over the outgoing links in the following manner:
    //
    //     int start = graph.getNodeChildIndex(node);
    //     int end = graph.getNodeLinksEndIndex(node);
    //     int nontreeStart = graph.getNodeNontreeIndex(node);
    //
    //     for (int i = start; i < nontreeStart; i++)
    //     {
    //        /* i is the index of an outgoing child link */
    //        graph.setLinkColor(i, Color.black);
    //        /* ... */
    //     }
    //
    //     for (int i = nontreeStart; i < end; i++)
    //     {
    //        /* i is the index of an outgoing nontree link */
    //     }

    public int getNodeChildIndex(int node) {
        return mNodes.treeLinks[node];
    }

    public int getNodeNontreeIndex(int node) {
        return mNodes.nontreeLinks[node];
    }

    public int getNodeLinksEndIndex(int node) {
        return mNodes.linksEnd[node];
    }

    public int getNodeColor(int node) {
        return mNodes.color[node];
    }

    // Returns true iff all nodes are visible.
    public boolean checkNodesVisible() {
        return mNodes.isVisible.check();
    }

    public boolean checkNodeVisible(int node) {
        return mNodes.isVisible.check(node);
    }

    public boolean checkNodeDisplayable(int node) {
        return mNodes.isDisplayable.check(node);
    }

    public boolean checkNodeSelected(int node) {
        return mNodes.isSelected.check(node);
    }

    //======================================================================

    public int getLinkID(int link) {
        return mLinks.id[link];
    }

    public int getLinkSource(int link) {
        return mLinks.source[link];
    }

    public int getLinkDestination(int link) {
        return mLinks.destination[link];
    }

    public int getLinkColor(int link) {
        return mLinks.color[link];
    }

    public boolean checkTreeLink(int link) {
        return mLinks.isTreeLink.get(link);
    }

    // Returns true iff all links are visible.
    public boolean checkLinksVisible() {
        return mLinks.isVisible.check();
    }

    public boolean checkLinkVisible(int link) {
        return mLinks.isVisible.check(link);
    }

    public boolean checkLinkDisplayable(int link) {
        return mLinks.isDisplayable.check(link);
    }

    public boolean checkLinkSelected(int link) {
        return mLinks.isSelected.check(link);
    }

    ////////////////////////////////////////////////////////////////////////
    // PUBLIC MUTATOR METHODS
    ////////////////////////////////////////////////////////////////////////

    // Computes new display coordinates for each node by transforming the
    // layout coordinates of nodes with the supplied matrix.
    public void transformNodes(Matrix4d t) {
        Point4d p = new Point4d();
        for (int i = 0; i < m_numNodes; i++) {
            p.x = mNodes.layoutX[i];
            p.y = mNodes.layoutY[i];
            p.z = mNodes.layoutZ[i];
            p.w = mNodes.layoutW[i];

            t.transform(p);

            mNodes.x[i] = p.x / p.w;
            mNodes.y[i] = p.y / p.w;
            mNodes.z[i] = p.z / p.w;
        }
    }

    public void setRootNode(int node) {
        mNodes.parent[node] = -1;
        mRootNode = node;
    }

    public void setNodeID(int node, int id) {
        mNodes.id[node] = id;
    }

    public void setNodeRadius(int node, double radius) {
        mNodes.radius[node] = radius;
    }

    public void setNodeCoordinates(int node, double x, double y, double z) {
        mNodes.x[node] = x;
        mNodes.y[node] = y;
        mNodes.z[node] = z;
    }

    public void setNodeCoordinates(int node, Point3d p) {
        mNodes.x[node] = p.x;
        mNodes.y[node] = p.y;
        mNodes.z[node] = p.z;
    }

    public void setNodeCoordinates(int node, Point4d p) {
        mNodes.x[node] = p.x / p.w;
        mNodes.y[node] = p.y / p.w;
        mNodes.z[node] = p.z / p.w;
    }

    public void setNodeLayoutCoordinates(int node, double x, double y,
                                         double z, double w) {
        mNodes.layoutX[node] = x;
        mNodes.layoutY[node] = y;
        mNodes.layoutZ[node] = z;
        mNodes.layoutW[node] = w;
    }

    public void setNodeLayoutCoordinates(int node, Point3d p) {
        mNodes.layoutX[node] = p.x;
        mNodes.layoutY[node] = p.y;
        mNodes.layoutZ[node] = p.z;
        mNodes.layoutW[node] = 1.0;
    }

    public void setNodeLayoutCoordinates(int node, Point4d p) {
        mNodes.layoutX[node] = p.x;
        mNodes.layoutY[node] = p.y;
        mNodes.layoutZ[node] = p.z;
        mNodes.layoutW[node] = p.w;
    }

    // The following two methods, addChildLink() and addNodeNontreeLink(),
    // must be called in a disciplined manner.  The sequence of calls to
    // add the links of one node should never interleave with the sequence
    // of another node.  Additionally, for a particular node, all child links
    // must be added first, followed by all non-tree links.
    // 
    // The required calling sequence for a node is as follows:
    //
    //    startChildLinks()
    //    addChildLink() ... addChildLink()  [zero or more times]
    //    startNontreeLinks()
    //    addNontreeLink() ... addNontreeLink()  [zero or more times]
    //    endNodeLinks()
    //
    // There need not be a sequence of these calls for nodes without any links.

    public void startChildLinks(int node) {
        mNodes.treeLinks[node] = mLinks.nextIndex;
    }

    // linkID is the ID of the corresponding link in the backing libsea graph
    // (or whatever backing data store you're using).
    public void addChildLink(int node, int child, int linkID) {
        ++mNumTreeLinks;

        int link = mLinks.nextIndex++;
        mNodes.parent[child] = link;
        mLinks.id[link] = linkID;
        mLinks.source[link] = node;
        mLinks.destination[link] = child;
        mLinks.isTreeLink.set(link);
    }

    public void startNontreeLinks(int node) {
        mNodes.nontreeLinks[node] = mLinks.nextIndex;
    }

    // linkID is the ID of the corresponding link in the backing libsea graph
    // (or whatever backing data store you're using).
    public void addNontreeLink(int node, int target, int linkID) {
        ++mNumNontreeLinks;

        int link = mLinks.nextIndex++;
        mLinks.id[link] = linkID;
        mLinks.source[link] = node;
        mLinks.destination[link] = target;
    }

    public void endNodeLinks(int node) {
        mNodes.linksEnd[node] = mLinks.nextIndex;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void setNodeColor(int node, int color) {
        mNodes.color[node] = color;
    }

    public void setNodeColor(int node, byte r, byte g, byte b) {
        mNodes.color[node] = (r << 16) | (g << 8) | b;
    }

    public void setNodeDefaultColor(int color) {
        Arrays.fill(mNodes.color, color);
    }

    public void setNodeDefaultColor(byte r, byte g, byte b) {
        int color = (r << 16) | (g << 8) | b;
        setNodeDefaultColor(color);
    }

    public void setNodeVisibility(int node, boolean isVisible) {
        mNodes.isVisible.set(node, isVisible);
    }

    public void setNodeDisplayability(int node, boolean isDisplayable) {
        mNodes.isDisplayable.set(node, isDisplayable);
    }

    public void setNodeSelectivity(int node, boolean isSelected) {
        mNodes.isSelected.set(node, isSelected);
    }

    public void setNodeVisibility(boolean isVisible) {
        mNodes.isVisible.set(isVisible);
    }

    public void setNodeDisplayability(boolean isDisplayable) {
        mNodes.isDisplayable.set(isDisplayable);
    }

    public void setNodeSelectivity(boolean isSelected) {
        mNodes.isSelected.set(isSelected);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void setLinkID(int link, int id) {
        mLinks.id[link] = id;
    }

    public void setLinkColor(int link, int color) {
        mLinks.color[link] = color;
    }

    public void setLinkColor(int link, byte r, byte g, byte b) {
        mLinks.color[link] = (r << 16) | (g << 8) | b;
    }

    public void setLinkDefaultColor(int color) {
        Arrays.fill(mLinks.color, color);
    }

    public void setLinkDefaultColor(byte r, byte g, byte b) {
        int color = (r << 16) | (g << 8) | b;
        setLinkDefaultColor(color);
    }

    public void setLinkVisibility(int link, boolean isVisible) {
        mLinks.isVisible.set(link, isVisible);
    }

    public void setLinkDisplayability(int link, boolean isDisplayable) {
        mLinks.isDisplayable.set(link, isDisplayable);
    }

    public void setLinkSelectivity(int link, boolean isSelected) {
        mLinks.isSelected.set(link, isSelected);
    }

    public void setLinkVisibility(boolean isVisible) {
        mLinks.isVisible.set(isVisible);
    }

    public void setLinkVisibility(boolean treeLink, boolean isVisible) {
        mLinks.isVisible.set(treeLink, isVisible);
    }

    public void setLinkDisplayability(boolean isDisplayable) {
        mLinks.isDisplayable.set(isDisplayable);
    }

    public void setLinkDisplayability(boolean treeLink, boolean isDisplayable) {
        mLinks.isDisplayable.set(treeLink, isDisplayable);
    }

    public void setLinkSelectivity(boolean isSelected) {
        mLinks.isSelected.set(isSelected);
    }

    public void setLinkSelectivity(boolean treeLink, boolean isSelected) {
        mLinks.isVisible.set(treeLink, isSelected);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    public void pruneSubtreeVisibility(int node) {
        if (getNodeParent(node) != -1) {
            setLinkDisplayability(getNodeParentLink(node), false);
        }
        setNodeDisplayability(node, false);
        setSubtreeDisplayability(node, false);
        computeVisibility();
    }

    public void pruneVisibilityToNeighborhood(int node, int distance) {
        setNeighborhoodDisplayability(node, 0, distance);
        computeVisibility();
    }

    public void narrowVisibility(int node) {
        setNodeDisplayability(false);
        setLinkDisplayability(false);

        setNodeDisplayability(node, true);

        // Show nodes & links up to root node from input node.
        int currentNode = node;
        int parent = getNodeParent(currentNode);
        while (parent != -1) {
            int link = getNodeParentLink(currentNode);
            setLinkDisplayability(link, true);
            setNodeDisplayability(parent, true);

            int old = parent;
            currentNode = parent;
            parent = getNodeParent(parent);
        }

        setSubtreeDisplayability(node, true);
        computeVisibility();
    }

    public void widenSubtreeVisibility(int node) {
        setNodeDisplayability(node, true);
        setSubtreeDisplayability(node, true);
        computeVisibility();
    }

    // Widen visibility to the first ancestor of the given node that
    // has more than one child--that is, to the closest branching point
    // in the path back to the root node.
    //
    // Returns the node to which visibility was widened.
    public int widenVisibilityTowardRoot(int node) {
        int currentNode = node;
        int parent = getNodeParent(currentNode);
        boolean found = false;
        while (!found && parent != -1) {
            if (getNodeNumChildren(parent) > 1) {
                found = true;
            }

            currentNode = parent;
            parent = getNodeParent(parent);
        }

        setSubtreeDisplayability(currentNode, true);
        computeVisibility();

        return currentNode;
    }

    // Show all nodes and links, modified only by selectivity.
    public void widenVisibility() {
        setNodeDisplayability(true);
        setLinkDisplayability(true);
        computeVisibility();
    }

    public void computeVisibility() {
        mNodes.isVisible.conjunction
                (mNodes.isDisplayable, mNodes.isSelected);

        computeNontreeLinkDisplayability();

        mLinks.isVisible.conjunction
                (mLinks.isDisplayable, mLinks.isSelected);
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE METHODS
    ////////////////////////////////////////////////////////////////////////

    // Assumes m_nodes.isVisible has been computed from m_nodes.isDisplayable
    // and m_nodes.isSelected.  This adjusts m_links.isDisplayable but neither
    // m_links.isSelected nor m_links.isVisible.  Thus, m_links.isVisible
    // must be computed sometime after calling this method.
    private void computeNontreeLinkDisplayability() {
        int node = 0;
        int numNontreeLinksSeen = 0;
        while (numNontreeLinksSeen < mNumNontreeLinks) {
            int nontreeStart = getNodeNontreeIndex(node);
            int end = getNodeLinksEndIndex(node);

            if (nontreeStart != end) {
                numNontreeLinksSeen += end - nontreeStart;
                if (checkNodeVisible(node)) {
                    for (int i = nontreeStart; i < end; i++) {
                        int destination = getLinkDestination(i);
                        boolean isDisplayable = checkNodeVisible(destination);
                        setLinkDisplayability(i, isDisplayable);
                    }
                } else {
                    for (int i = nontreeStart; i < end; i++) {
                        setLinkDisplayability(i, false);
                    }
                }
            }

            ++node;
        }
    }

    // The input node itself is assumed to have been taken care of.
    private void setSubtreeDisplayability(int node, boolean isDisplayable) {
        int start = getNodeChildIndex(node);
        int end = getNodeLinksEndIndex(node);
        int nontreeStart = getNodeNontreeIndex(node);

        for (int i = start; i < nontreeStart; i++) {
            int child = getLinkDestination(i);
            setNodeDisplayability(child, isDisplayable);
            setLinkDisplayability(i, isDisplayable);
            setSubtreeDisplayability(child, isDisplayable);
        }
    }

    // The input node itself is assumed to have been taken care of.
    private void setNeighborhoodDisplayability
    (int node, int distance, int maxDistance) {
        boolean isDisplayable = distance < maxDistance;

        int start = getNodeChildIndex(node);
        int end = getNodeLinksEndIndex(node);
        int nontreeStart = getNodeNontreeIndex(node);

        for (int i = start; i < nontreeStart; i++) {
            int child = getLinkDestination(i);
            setNodeDisplayability(child, isDisplayable);
            setLinkDisplayability(i, isDisplayable);
            setNeighborhoodDisplayability(child, distance + 1, maxDistance);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE FIELDS
    ////////////////////////////////////////////////////////////////////////

    private final int m_numNodes;
    private final int m_numLinks;

    private int mNumTreeLinks;
    private int mNumNontreeLinks;

    private int mRootNode;
    private final Nodes mNodes;
    private final Links mLinks;

    ////////////////////////////////////////////////////////////////////////
    // PRIVATE CLASSES
    ////////////////////////////////////////////////////////////////////////

    private static class Nodes {
        public Nodes(int numNodes) {
            id = new int[numNodes];
            radius = new double[numNodes];
            x = new double[numNodes];
            y = new double[numNodes];
            z = new double[numNodes];
            layoutX = new double[numNodes];
            layoutY = new double[numNodes];
            layoutZ = new double[numNodes];
            layoutW = new double[numNodes];
            parent = new int[numNodes];

            // The automatic initialization of these arrays to zero is
            // important in giving consistent values for nodes without
            // child or non-tree links (e.g., when startChildLinks() et al.
            // are not called for them).
            treeLinks = new int[numNodes];
            nontreeLinks = new int[numNodes];
            linksEnd = new int[numNodes];

            color = new int[numNodes];
            isVisible = new NodeProperty(numNodes);
            isDisplayable = new NodeProperty(numNodes);
            isSelected = new NodeProperty(numNodes);
        }

        ////////////////////////////////////////////////////////////////////
        // ESSENTIAL NODE ATTRIBUTES
        ////////////////////////////////////////////////////////////////////

        // The ID of the node in the backing org.caida.libsea.Graph.
        // This mapping is necessary since the IDs in the backing graph
        // need not form a contiguous block starting at zero.
        public int[] id;

        // The radius of the nodes as determined solely by their position.
        // This is inversely proportional to the distance from the origin to
        // a node as determined with the hyperbolic metric.
        public double[] radius;

        // The coordinates of nodes after hyperbolic view transformation.
        public double[] x;
        public double[] y;
        public double[] z;

        // The coordinates of nodes in initial layout.
        public double[] layoutX;
        public double[] layoutY;
        public double[] layoutZ;
        public double[] layoutW;

        // The parent value of a node gives the index of the link that
        // connects the parent to that node.  Hence the source of the parent
        // link gives the parent node and the destination gives the child node.
        // The root node of the graph has a parent link value of -1.
        public int[] parent;

        // The following arrays give indices into the array of links where
        // the links of a node appear.  All the links of a node appear in
        // a continguous block, with all child links grouped at the beginning
        // and all non-tree links at the end.  For a node i, the values have
        // the following relationship:
        //
        //  0 <= treeLinks[i] <= nontreeLinks[i] <= linksEnd[i] <= m_numLinks
        //
        // Additionally,
        //
        //   nontreeLinks[i] - treeLinks[i] gives the number of child links,
        //   linksEnd[i] - nontreeLinks[i] gives the number of non-tree links,
        // and linksEnd[i] - treeLinks[i] gives the total number of links.
        //
        // If a node has no links, then all the values will equal some
        // arbitrary value.
        public int[] treeLinks;
        public int[] nontreeLinks;
        public int[] linksEnd;

        ////////////////////////////////////////////////////////////////////
        // INESSENTIAL NODE ATTRIBUTES
        ////////////////////////////////////////////////////////////////////

        // Color in packed RGB format (R, G, and B in the lower three octets).
        public int[] color;

        // Whether a node should be drawn.
        // This is usually the logical AND of isDisplayable and isSelected.
        public NodeProperty isVisible;

        // Whether a node is displayable (that is, potentially visible,
        // depending on isSelected).
        public NodeProperty isDisplayable;

        // Whether a node has been chosen by a selection attribute for display.
        public NodeProperty isSelected;
    }

    ////////////////////////////////////////////////////////////////////////

    public static class Links {
        public Links(int numLinks) {
            id = new int[numLinks];
            source = new int[numLinks];
            destination = new int[numLinks];
            isTreeLink = new BitSet(numLinks);
            color = new int[numLinks];
            isVisible = new LinkProperty(numLinks, isTreeLink);
            isDisplayable = new LinkProperty(numLinks, isTreeLink);
            isSelected = new LinkProperty(numLinks, isTreeLink);
        }

        public int nextIndex;

        ////////////////////////////////////////////////////////////////////
        // ESSENTIAL LINK ATTRIBUTES
        ////////////////////////////////////////////////////////////////////

        // The ID of the link in the backing org.caida.libsea.Graph.
        // This mapping is necessary since the IDs in the backing graph
        // need not form a contiguous block starting at zero.
        public int[] id;

        // The indices of nodes.
        public int[] source;
        public int[] destination;

        // Whether a link is a tree link or a nontree link.
        public BitSet isTreeLink;

        ////////////////////////////////////////////////////////////////////
        // INESSENTIAL LINK ATTRIBUTES
        ////////////////////////////////////////////////////////////////////

        // Color in packed RGB format (R, G, and B in the lower three octets).
        public int[] color;

        // Whether a link should be drawn.
        // This is usually the logical AND of isDisplayable and isSelected.
        public LinkProperty isVisible;

        // Whether a link is displayable (that is, potentially visible,
        // depending on isSelected).
        public LinkProperty isDisplayable;

        // Whether a link has been chosen by a selection attribute for display.
        public LinkProperty isSelected;
    }

    ////////////////////////////////////////////////////////////////////////

    // This implementation uses inverted logic because BitSet is created
    // with all its bits cleared.
    private static final class NodeProperty {
        public NodeProperty(int numNodes) {
            m_numNodes = numNodes;
        }

        public int count() {
            int retval = m_numNodes;
            if (m_property != null) {
                int length = m_property.length();
                for (int i = 0; i < length; i++) {
                    if (m_property.get(i)) {
                        --retval;
                    }
                }
            }
            return retval;
        }

        // Returns true iff all nodes are visible.
        public boolean check() {
            return m_property == null || m_property.length() == 0;
        }

        public boolean check(int node) {
            return m_property == null || !m_property.get(node);
        }

        public void set(int node, boolean value) {
            if (value) {
                if (m_property != null) {
                    m_property.clear(node);
                }
            } else {
                ensureAllocated();
                m_property.set(node);
            }
        }

        // Set value for all nodes.
        public void set(boolean value) {
            if (value) {
                m_property = null;
            } else {
                ensureAllocated();
                for (int i = 0; i < m_numNodes; i++) {
                    m_property.set(i);
                }
            }
        }

        // Sets this to a logical AND of np1 and np2.
        // Because this implementation uses inverted logic, the logical
        // AND is carried out with a logical OR of the negated parameters
        // (that is, ~R = ~P | ~Q implies R = P & Q).
        public void conjunction(NodeProperty p1, NodeProperty p2) {
            if (p1.m_property == null && p2.m_property == null) {
                m_property = null;
            } else if (p1.m_property == null && p2.m_property != null) {
                m_property = (BitSet) p2.m_property.clone();
            } else if (p1.m_property != null && p2.m_property == null) {
                m_property = (BitSet) p1.m_property.clone();
            } else {
                m_property = (BitSet) p1.m_property.clone();
                m_property.or(p2.m_property);
            }
        }

        private void ensureAllocated() {
            if (m_property == null) {
                m_property = new BitSet(m_numNodes);
            }
        }

        private final int m_numNodes;
        private BitSet m_property;
    }

    ////////////////////////////////////////////////////////////////////////

    // This implementation uses inverted logic because BitSet is created
    // with all its bits cleared.
    private static final class LinkProperty {
        public LinkProperty(int numLinks, BitSet isTreeLink) {
            m_numLinks = numLinks;
            m_isTreeLink = isTreeLink;
        }

        public int count() {
            int retval = m_numLinks;
            if (m_property != null) {
                int length = m_property.length();
                for (int i = 0; i < length; i++) {
                    if (m_property.get(i)) {
                        --retval;
                    }
                }
            }
            return retval;
        }

        // Returns true iff all links are visible.
        public boolean check() {
            return m_property == null || m_property.length() == 0;
        }

        public boolean check(int link) {
            return m_property == null || !m_property.get(link);
        }

        public void set(int link, boolean value) {
            if (value) {
                if (m_property != null) {
                    m_property.clear(link);
                }
            } else {
                ensureAllocated();
                m_property.set(link);
            }
        }

        // Set value for all links.
        public void set(boolean value) {
            if (value) {
                m_property = null;
            } else {
                ensureAllocated();
                for (int i = 0; i < m_numLinks; i++) {
                    m_property.set(i);
                }
            }
        }

        // Set value for all links of the specified type.
        public void set(boolean treeLink, boolean value) {
            if (value) {
                if (m_property != null) {
                    setBits(treeLink, false);
                    if (m_property.length() == 0) {
                        m_property = null;
                    }
                }
            } else {
                ensureAllocated();
                setBits(treeLink, true);
            }
        }

        // Assumes m_property != null.
        private void setBits(boolean treeLink, boolean value) {
            int length = m_property.length();
            for (int i = 0; i < length; i++) {
                if (m_isTreeLink.get(i) == treeLink) {
                    if (value) {
                        m_property.set(i);
                    } else {
                        m_property.clear(i);
                    }
                }
            }
        }

        // Sets this to a logical AND of np1 and np2.
        // Because this implementation uses inverted logic, the logical
        // AND is carried out with a logical OR of the negated parameters
        // (that is, ~R = ~P | ~Q implies R = P & Q).
        public void conjunction(LinkProperty p1, LinkProperty p2) {
            if (p1.m_property == null && p2.m_property == null) {
                m_property = null;
            } else if (p1.m_property == null && p2.m_property != null) {
                m_property = (BitSet) p2.m_property.clone();
            } else if (p1.m_property != null && p2.m_property == null) {
                m_property = (BitSet) p1.m_property.clone();
            } else {
                m_property = (BitSet) p1.m_property.clone();
                m_property.or(p2.m_property);
            }
        }

        private void ensureAllocated() {
            if (m_property == null) {
                m_property = new BitSet(m_numLinks);
            }
        }

        private final int m_numLinks;
        private final BitSet m_isTreeLink;
        private BitSet m_property;
    }

    ////////////////////////////////////////////////////////////////////////
    // TEST METHODS
    ////////////////////////////////////////////////////////////////////////

    // Place nodes with invalid layout coordinates at the origin.
    public void sanitizeLayoutCoordinates() {
        Point4d p = new Point4d();
        for (int i = 0; i < m_numNodes; i++) {
            getNodeLayoutCoordinates(i, p);
            if (!H3Math.isFinite(p)) {
                setNodeLayoutCoordinates(i, 0.0, 0.0, 0.0, 1.0);
            }
        }
    }

    // Returns the number of nodes with valid (not NaN or infinite) layout
    // coordinates.
    public int checkLayoutCoordinates() {
        int retval = 0;

        Point4d p = new Point4d();
        for (int i = 0; i < m_numNodes; i++) {
            getNodeLayoutCoordinates(i, p);
            if (H3Math.isFinite(p)) {
                ++retval;
            }
        }

        return retval;
    }

    ////////////////////////////////////////////////////////////////////////

    // Returns the number of nodes reachable in the spanning tree, or -1
    // if some node is visited more than once, indicating a cycle,
    // a parallel edge, or some other problem.  The set of tree links
    // is assumed to define a spanning tree.
    public int checkSpanningTree() {
        BitSet visited = new BitSet();
        return checkSpanningTree(visited, mRootNode);
    }

    private int checkSpanningTree(BitSet visited, int node) {
        if (visited.get(node)) {
            return -1;
        }
        visited.set(node);

        int start = getNodeChildIndex(node);
        int nontreeStart = getNodeNontreeIndex(node);

        int retval = 1;
        for (int i = start; i < nontreeStart; i++) {
            int child = getLinkDestination(i);
            int numReachable = checkSpanningTree(visited, child);
            if (numReachable == -1) {
                return -1;
            }
            retval += numReachable;
        }

        return retval;
    }

    public int checkTreeReachability() {
        return checkTreeReachability(mRootNode);
    }

    public int checkTreeReachability(int node) {
        BitSet visited = new BitSet();
        int numReachable = checkReachability(visited, node);
        if (numReachable == m_numNodes) {
            String msg =
                    "PASSED: All nodes reachable in tree from node " + node;
            System.out.println(msg);
        } else {
            String msg = "FAILED: Only " + numReachable
                    + " nodes of " + m_numNodes
                    + " reachable in tree from node " + node;
            System.out.println(msg);

            System.out.println("Unvisited nodes:");
            for (int i = 0; i < m_numNodes; i++) {
                if (!visited.get(i)) {
                    System.out.println("\t" + i);
                }
            }
        }

        return numReachable;
    }

    private int checkReachability(BitSet visited, int node) {
        int retval = 1;

        if (visited.get(node)) {
            String msg = "ERROR: Encountered node " + node
                    + " more than once in the spanning tree; there could be"
                    + " cycles or parallel links.";
            System.out.println(msg);
            return 0;
        }
        visited.set(node);

        int treeLinks = mNodes.treeLinks[node];
        int nontreeLinks = mNodes.nontreeLinks[node];

        while (treeLinks < nontreeLinks) {
            int child = mLinks.destination[treeLinks++];
            retval += checkReachability(visited, child);
        }

        return retval;
    }

    public void dumpForTesting() {
        System.out.println();
        System.out.println(this + ":");
        System.out.println("\tnumNodes: " + m_numNodes);
        System.out.println("\tnumLinks: " + m_numLinks);
        System.out.println("\tnumTreeLinks: " + mNumTreeLinks);
        System.out.println("\tnumNontreeLinks: " + mNumNontreeLinks);
        System.out.println("\tnextIndex: " + mLinks.nextIndex);

        System.out.println("\nParent:\n");
        for (int i = 0; i < mNodes.parent.length; i++) {
            if (i == m_numNodes) {
                System.out.println("- - - - - - - - - - - - - - - - - - - -");
            }
            System.out.println(i + " => " + mNodes.parent[i]);
        }

        System.out.println("\nLinksStart:\n");
        for (int i = 0; i < mNodes.treeLinks.length; i++) {
            if (i == m_numNodes) {
                System.out.println("- - - - - - - - - - - - - - - - - - - -");
            }
            System.out.println(i + " => " + mNodes.treeLinks[i]);
        }

        System.out.println("\nNontreeLinksStart:\n");
        for (int i = 0; i < mNodes.nontreeLinks.length; i++) {
            if (i == m_numNodes) {
                System.out.println("- - - - - - - - - - - - - - - - - - - -");
            }
            System.out.println(i + " => " + mNodes.nontreeLinks[i]);
        }

        System.out.println("\nLinksEnd:\n");
        for (int i = 0; i < mNodes.linksEnd.length; i++) {
            if (i == m_numNodes) {
                System.out.println("- - - - - - - - - - - - - - - - - - - -");
            }
            System.out.println(i + " => " + mNodes.linksEnd[i]);
        }
    }

    public void dumpForTesting2() {
        System.out.println();
        System.out.println(this + ":");
        System.out.println("\tnumNodes: " + m_numNodes);
        System.out.println("\tnumLinks: " + m_numLinks);
        System.out.println("\tnumTreeLinks: " + mNumTreeLinks);
        System.out.println("\tnumNontreeLinks: " + mNumNontreeLinks);
        System.out.println("\tnextIndex: " + mLinks.nextIndex);

        for (int i = 0; i < m_numNodes; i++) {
            System.out.println("Node " + i + ":");
            System.out.println("\tparent link: " + mNodes.parent[i]);
            System.out.println("\tparent node: "
                    + (mNodes.parent[i] >= 0
                    ? mLinks.source[mNodes.parent[i]] : -1));

            int treeLinks = mNodes.treeLinks[i];
            int nontreeLinks = mNodes.nontreeLinks[i];
            int linksEnd = mNodes.linksEnd[i];

            System.out.println("\ttreeLinks: " + treeLinks);
            System.out.println("\tnontreeLinks: " + nontreeLinks);
            System.out.println("\tlinksEnd: " + linksEnd);

            for (int j = treeLinks; j < linksEnd; j++) {
                if (j == nontreeLinks) {
                    System.out.println("\t\t- - - - - - - - - - - - - - - -");
                }

                System.out.println("\t\t" + j + ": "
                        + mLinks.source[j] + " => "
                        + mLinks.destination[j]);
            }
        }
    }
}
