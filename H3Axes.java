// 
// Copyright 2000,2001,2002 The Regents of the University of California
// All Rights Reserved
// 
// Permission to use, copy, modify and distribute any part of this
// Walrus software package for educational, research and non-profit
// purposes, without fee, and without a written agreement is hereby
// granted, provided that the above copyright notice, this paragraph
// and the following paragraphs appear in all copies.
//   
// Those desiring to incorporate this into commercial products or use
// for commercial purposes should contact the Technology Transfer
// Office, University of California, San Diego, 9500 Gilman Drive, La
// Jolla, CA 92093-0910, Ph: (858) 534-5815, FAX: (858) 534-7345.
// 
// IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY
// PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL
// DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
//  
// THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE
// UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE,
// SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS. THE UNIVERSITY
// OF CALIFORNIA MAKES NO REPRESENTATIONS AND EXTENDS NO WARRANTIES
// OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT LIMITED
// TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
// PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE
// ANY PATENT, TRADEMARK OR OTHER RIGHTS.
//  
// The Walrus software is developed by the Walrus Team at the
// University of California, San Diego under the Cooperative Association
// for Internet Data Analysis (CAIDA) Program.  Support for this effort
// is provided by NSF grant ANI-9814421, DARPA NGI Contract N66001-98-2-8922,
// Sun Microsystems, and CAIDA members.
// 

import java.awt.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import com.sun.j3d.utils.geometry.Text2D;
import com.sun.j3d.utils.geometry.*;

public class H3Axes
{
    public H3Axes()
    {
	createAxes();
	createAxisLabels();
	createUnitSphere();

	createGeneralAppearances();
    }

    public BranchGroup makeBranchGraph()
    {
	BranchGroup retval = new BranchGroup();

	Appearance axisAppearance = cloneAppearance(m_axisAppearance);
	Appearance circleAppearance = cloneAppearance(m_circleAppearance);

	retval.addChild(new Shape3D(m_axes, axisAppearance));
	retval.addChild(new Shape3D(m_xyCircle, circleAppearance));
	retval.addChild(new Shape3D(m_yzCircle, circleAppearance));
	retval.addChild(new Shape3D(m_xzCircle, circleAppearance));

	retval.addChild(makeLabelBranchGraph(m_xLabel, m_xT3D));
	retval.addChild(makeLabelBranchGraph(m_yLabel, m_yT3D));
	retval.addChild(makeLabelBranchGraph(m_zLabel, m_zT3D));

	return retval;
    }

    // NOTE: This doesn't clone all of the attributes of an Appearance.
    private Appearance cloneAppearance(Appearance appearance)
    {
	Appearance retval = new Appearance();

	retval.setColoringAttributes
	    ((ColoringAttributes)cloneNodeComponent
	     (appearance.getColoringAttributes()));

	retval.setLineAttributes
	    ((LineAttributes)cloneNodeComponent
	     (appearance.getLineAttributes()));

	retval.setPointAttributes
	    ((PointAttributes)cloneNodeComponent
	     (appearance.getPointAttributes()));

	retval.setRenderingAttributes
	    ((RenderingAttributes)cloneNodeComponent
	     (appearance.getRenderingAttributes()));

	retval.setTransparencyAttributes
	    ((TransparencyAttributes)cloneNodeComponent
	     (appearance.getTransparencyAttributes()));

	return retval;
    }

    private NodeComponent cloneNodeComponent(NodeComponent component)
    {
	return (component == null ? null : component.cloneNodeComponent(true));
    }

    private TransformGroup makeLabelBranchGraph
	(Shape3D label, Transform3D transform)
    {
	Geometry geometry = label.getGeometry();
	Appearance appearance = cloneAppearance(label.getAppearance());
	Shape3D shape = new Shape3D(geometry, appearance);

	TransformGroup retval = new TransformGroup(transform);
	retval.addChild(shape);
	return retval;
    }

    public void draw(GraphicsContext3D gc, Transform3D transform)
    {
	gc.setModelTransform(transform);

	gc.setAppearance(m_axisAppearance);
	gc.draw(m_axes);

	gc.setAppearance(m_circleAppearance);
	gc.draw(m_xyCircle);
	gc.draw(m_yzCircle);
	gc.draw(m_xzCircle);

	drawAxisLabels(gc, transform);
    }

    public void setAxisAppearance(Appearance appearance)
    {
	m_axisAppearance = appearance;
    }

    public void setCircleAppearance(Appearance appearance)
    {
	m_circleAppearance = appearance;
    }

    public void setAxisColor(float r, float g, float b)
    {
	ColoringAttributes attributes =
	    new ColoringAttributes(r, g, b, ColoringAttributes.FASTEST);

	m_axisAppearance.setColoringAttributes(attributes);
    }

    public void setCircleColor(float r, float g, float b)
    {
	ColoringAttributes attributes =
	    new ColoringAttributes(r, g, b, ColoringAttributes.FASTEST);

	m_circleAppearance.setColoringAttributes(attributes);
    }

    // ===================================================================

    private void createAxes()
    {
	double radius = 1.0;

	LineArray axisGeometry = new LineArray(6, LineArray.COORDINATES);
	Point3d[] coordinates = new Point3d[6];
	coordinates[0] = new Point3d(-radius, 0.0, 0.0);
	coordinates[1] = new Point3d(radius, 0.0, 0.0);
	coordinates[2] = new Point3d(0.0, -radius, 0.0);
	coordinates[3] = new Point3d(0.0, radius, 0.0);
	coordinates[4] = new Point3d(0.0, 0.0, -radius);
	coordinates[5] = new Point3d(0.0, 0.0, radius);
	axisGeometry.setCoordinates(0, coordinates);

	m_axes = axisGeometry;
    }

    private void createAxisLabels()
    {
	{
	    m_xT3D = new Transform3D();
	    m_xT3D.setScale(0.01);
	    m_xT3D.setTranslation(new Vector3d(1.0, 0.0, 0.0));

	    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
	    Text3D xLabel = createText3D("X");
	    Appearance xAppearance =
		createLabelAppearance(new Color3f(0.5f, 0.1f, 0.1f));
	    m_xLabel = new Shape3D(xLabel, xAppearance);
	}

	{
	    m_yT3D = new Transform3D();
	    m_yT3D.setScale(0.01);
	    m_yT3D.setTranslation(new Vector3d(0.0, 1.0, 0.0));

	    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
	    Text3D yLabel = createText3D("Y");
	    Appearance yAppearance =
		createLabelAppearance(new Color3f(0.1f, 0.5f, 0.1f));
	    m_yLabel = new Shape3D(yLabel, yAppearance);
	}

	{
	    Matrix4d rot = new Matrix4d();
	    rot.rotX(-Math.PI / 2.0);
	    rot.rotY(Math.PI / 2.0);

	    Matrix4d m = new Matrix4d();
	    m.set(0.01, new Vector3d(0.0, 0.0, 1.0));  // (scale, translation)
	    m.mul(rot);

	    m_zT3D = new Transform3D(m);

	    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
	    Text3D zLabel = createText3D("Z");
	    zLabel.setAlignment(Text3D.ALIGN_LAST);

	    Appearance zAppearance =
		createLabelAppearance(new Color3f(0.1f, 0.1f, 0.5f));
	    m_zLabel = new Shape3D(zLabel, zAppearance);
	}
    }

    private void drawAxisLabels(GraphicsContext3D gc, Transform3D transform)
    {
	{
	    Transform3D xT3D = new Transform3D(transform);
	    xT3D.mul(m_xT3D);
	    gc.setModelTransform(xT3D);
	    gc.draw(m_xLabel);
	}

	{
	    Transform3D yT3D = new Transform3D(transform);
	    yT3D.mul(m_yT3D);
	    gc.setModelTransform(yT3D);
	    gc.draw(m_yLabel);
	}

	{
	    Transform3D zT3D = new Transform3D(transform);
	    zT3D.mul(m_zT3D);
	    gc.setModelTransform(zT3D);
	    gc.draw(m_zLabel);
	}

	gc.setModelTransform(transform);
    }

    private void createUnitSphere()
    {
	int numSegments = 72;
	int[] stripLengths = { numSegments + 1 };

	{
	    Point3f[] xyPoints = createXYCircleCoordinates(1.0f, numSegments);
	    LineStripArray xyLines = new LineStripArray(xyPoints.length,
						GeometryArray.COORDINATES,
						stripLengths);
	    xyLines.setCoordinates(0, xyPoints);
	    m_xyCircle = xyLines;
	}

	{
	    Point3f[] yzPoints = createXYCircleCoordinates(1.0f, numSegments);
	    for (int i = yzPoints.length - 1; i >= 0; i--)
	    {
		Point3f p = yzPoints[i];
		p.z = p.y;
		p.y = p.x;
		p.x = 0.0f;
	    }

	    LineStripArray yzLines = new LineStripArray(yzPoints.length,
						GeometryArray.COORDINATES,
						stripLengths);
	    yzLines.setCoordinates(0, yzPoints);
	    m_yzCircle = yzLines;
	}

	{
	    Point3f[] xzPoints = createXYCircleCoordinates(1.0f, numSegments);
	    for (int i = xzPoints.length - 1; i >= 0; i--)
	    {
		Point3f p = xzPoints[i];
		p.z = p.x;
		p.x = p.y;
		p.y = 0.0f;
	    }

	    LineStripArray xzLines = new LineStripArray(xzPoints.length,
						GeometryArray.COORDINATES,
						stripLengths);
	    xzLines.setCoordinates(0, xzPoints);
	    m_xzCircle = xzLines;
	}
    }

    private Point3f[] createXYCircleCoordinates(float radius, int numSegments)
    {
	Point3f[] retval = new Point3f[numSegments + 1];

	Matrix4d rot = new Matrix4d();
	for (int i = 0; i <= numSegments; i++)
	{
	    retval[i] = new Point3f(radius, 0.0f, 0.0f);

	    double angle = 2.0 * Math.PI * i / numSegments;
	    rot.rotZ(angle);
	    rot.transform(retval[i]);
	}

	return retval;
    }

    private Text3D createText3D(String text)
    {
	Font font = new Font("SansSerif", Font.PLAIN, 14);
	Font3D font3D = new Font3D(font, new FontExtrusion());
	return new Text3D(font3D, text);
    }

    private Appearance createLabelAppearance(Color3f color)
    {
	Appearance retval = new Appearance();

	ColoringAttributes coloring = new ColoringAttributes(color,
					     ColoringAttributes.FASTEST);
	retval.setColoringAttributes(coloring);
	
	return retval;
    }

    private void createGeneralAppearances()
    {
	{
	    LineAttributes lineAttributes = new LineAttributes();
	    lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

	    ColoringAttributes coloringAttributes =
		new ColoringAttributes(0.3f, 0.6f, 0.3f,
				       ColoringAttributes.FASTEST);
      
	    m_axisAppearance = new Appearance();
	    m_axisAppearance.setLineAttributes(lineAttributes);
	    m_axisAppearance.setColoringAttributes(coloringAttributes);
	}

	{
	    LineAttributes lineAttributes = new LineAttributes();
	    lineAttributes.setLineAntialiasingEnable(ANTIALIASING);

	    ColoringAttributes coloringAttributes =
		new ColoringAttributes(0.3f, 0.6f, 0.3f,
				       ColoringAttributes.FASTEST);

	    m_circleAppearance = new Appearance();
	    m_circleAppearance.setLineAttributes(lineAttributes);
	    m_circleAppearance.setColoringAttributes(coloringAttributes);
	}
    }

    // =====================================================================

    private static final boolean ANTIALIASING = false;

    private Geometry m_axes;
    private Geometry m_xyCircle;
    private Geometry m_yzCircle;
    private Geometry m_xzCircle;
    private Shape3D m_xLabel;
    private Shape3D m_yLabel;
    private Shape3D m_zLabel;
    private Transform3D m_xT3D;
    private Transform3D m_yT3D;
    private Transform3D m_zT3D;
    private Appearance m_axisAppearance;
    private Appearance m_circleAppearance;
}
