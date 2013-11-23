import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.JAXBBuilder;
import org.citygml4j.factory.CityGMLFactory;
import org.citygml4j.factory.GMLFactory;
import org.citygml4j.factory.geometry.DimensionMismatchException;
import org.citygml4j.factory.geometry.GMLGeometryFactory;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.building.AbstractBoundarySurface;
import org.citygml4j.model.citygml.building.BoundarySurfaceProperty;
import org.citygml4j.model.citygml.building.Building;
import org.citygml4j.model.citygml.building.Door;
import org.citygml4j.model.citygml.building.InteriorWallSurface;
import org.citygml4j.model.citygml.building.OpeningProperty;
import org.citygml4j.model.citygml.building.WallSurface;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.geometry.complexes.CompositeSurface;
import org.citygml4j.model.gml.geometry.primitives.DirectPositionList;
import org.citygml4j.model.gml.geometry.primitives.Exterior;
import org.citygml4j.model.gml.geometry.primitives.LinearRing;
import org.citygml4j.model.gml.geometry.primitives.Polygon;
import org.citygml4j.model.gml.geometry.primitives.Solid;
import org.citygml4j.model.gml.geometry.primitives.SurfaceProperty;
import org.citygml4j.model.module.citygml.CityGMLVersion;
import org.citygml4j.util.gmlid.DefaultGMLIdManager;
import org.citygml4j.util.gmlid.GMLIdManager;
import org.citygml4j.xml.io.CityGMLOutputFactory;
import org.citygml4j.xml.io.writer.CityGMLWriter;


public class BuildingWithDoor {
	private CityGMLFactory citygml;
	private GMLFactory gml;

	public static void main(String[] args) throws Exception {
		new BuildingWithDoor().doMain();
	}

	public void doMain() throws Exception {
		SimpleDateFormat df = new SimpleDateFormat("[HH:mm:ss] "); 

		System.out.println(df.format(new Date()) + "setting up citygml4j context and JAXB builder");
		CityGMLContext ctx = new CityGMLContext();
		JAXBBuilder builder = ctx.createJAXBBuilder();

		System.out.println(df.format(new Date()) + "creating LOD4 building as citygml4j in-memory object tree");
		GMLGeometryFactory geom = new GMLGeometryFactory();
		citygml = new CityGMLFactory();
		gml = new GMLFactory();

		GMLIdManager gmlIdManager = DefaultGMLIdManager.getInstance();

		Building building = citygml.createBuilding();

		Polygon ground = geom.createLinearPolygon(new double[] {0,0,0, 0,12,0, 6,12,0, 6,0,0, 0,0,0}, 3);
		
		
		Polygon wall_1 = geom.createLinearPolygon(new double[] {6,0,0, 6,12,0, 6,12,6, 6,0,6, 6,0,0}, 3);
		Polygon wall_2 = geom.createLinearPolygon(new double[] {0,0,0, 0,0,6, 0,12,6, 0,12,0, 0,0,0}, 3);
		Polygon wall_3 = geom.createLinearPolygon(new double[] {0,0,0, 6,0,0, 6,0,6, 3,0,9, 0,0,6, 0,0,0}, 3);
		Polygon wall_4 = geom.createLinearPolygon(new double[] {6,12,0, 0,12,0, 0,12,6, 3,12,9, 6,12,6, 6,12,0}, 3);
		Polygon roof_1 = geom.createLinearPolygon(new double[] {6,0,6, 6,12,6, 3,12,9, 3,0,9, 6,0,6}, 3);
		Polygon roof_2 = geom.createLinearPolygon(new double[] {0,0,6, 3,0,9, 3,12,9, 0,12,6, 0,0,6}, 3);

		ground.setId(gmlIdManager.generateGmlId());
		wall_1.setId(gmlIdManager.generateGmlId());
		wall_2.setId(gmlIdManager.generateGmlId());
		wall_3.setId(gmlIdManager.generateGmlId());
		wall_4.setId(gmlIdManager.generateGmlId());
		roof_1.setId(gmlIdManager.generateGmlId());
		roof_2.setId(gmlIdManager.generateGmlId());

		// lod2 solid - showlud be lod4
		List<SurfaceProperty> surfaceMember = new ArrayList<SurfaceProperty>();
		surfaceMember.add(gml.createSurfaceProperty('#' + ground.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + wall_1.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + wall_2.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + wall_3.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + wall_4.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + roof_1.getId()));
		surfaceMember.add(gml.createSurfaceProperty('#' + roof_2.getId()));

		CompositeSurface compositeSurface = gml.createCompositeSurface();
		compositeSurface.setSurfaceMember(surfaceMember);		
		Solid solid = gml.createSolid();
		solid.setExterior(gml.createSurfaceProperty(compositeSurface));

		building.setLod4Solid(gml.createSolidProperty(solid));

		
		// thematic boundary surfaces
		List<BoundarySurfaceProperty> boundedBy = new ArrayList<BoundarySurfaceProperty>();
		boundedBy.add(createBoundarySurface(CityGMLClass.GROUND_SURFACE, ground));
		boundedBy.add(createBoundarySurface(CityGMLClass.WALL_SURFACE, wall_1));
		boundedBy.add(createBoundarySurface(CityGMLClass.WALL_SURFACE, wall_2));		
		
		
		BoundarySurfaceProperty wall3BSurf = createBoundarySurface(CityGMLClass.WALL_SURFACE, wall_3);		
		createDoor(wall3BSurf);			
		boundedBy.add(wall3BSurf);
		
		
		/*
		WallSurface wallSurface3 = citygml.createWallSurface();		
		AbstractBoundarySurface boundarySurfaceWall3 = null;
		boundarySurfaceWall3 = citygml.createWallSurface();
		boundarySurfaceWall3.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(wall_3)));		
		BoundarySurfaceProperty bsp3 = citygml.createBoundarySurfaceProperty(boundarySurfaceWall3);
		boundedBy.add(bsp3);
		*/
		
		
		boundedBy.add(createBoundarySurface(CityGMLClass.WALL_SURFACE, wall_4));
		boundedBy.add(createBoundarySurface(CityGMLClass.ROOF_SURFACE, roof_1));
		boundedBy.add(createBoundarySurface(CityGMLClass.ROOF_SURFACE, roof_2));		
		building.setBoundedBySurface(boundedBy);
		
		
		

		CityModel cityModel = citygml.createCityModel();
		cityModel.setBoundedBy(building.calcBoundedBy(false));
		cityModel.addCityObjectMember(citygml.createCityObjectMember(building));

		System.out.println(df.format(new Date()) + "writing citygml4j object tree");
		CityGMLOutputFactory out = builder.createCityGMLOutputFactory(CityGMLVersion.v1_0_0);
		CityGMLWriter writer = out.createCityGMLWriter(new File("testLOD4_Building_v100_geoIndo.xml"));

		writer.setPrefixes(CityGMLVersion.v1_0_0);
		writer.setSchemaLocations(CityGMLVersion.v1_0_0);
		writer.setIndentString("  ");
		writer.write(cityModel);
		writer.close();	
		
		System.out.println(df.format(new Date()) + "CityGML file LOD4_Building_v100.xml written");
		System.out.println(df.format(new Date()) + "sample citygml4j application successfully finished");
	}

	public void createDoor(BoundarySurfaceProperty bsp) throws DimensionMismatchException {
		System.out.println("c");
		AbstractBoundarySurface b = bsp.getBoundarySurface();
		if (b instanceof WallSurface) {
			System.out.println("IS wall surf");
			
			GMLGeometryFactory geom = new GMLGeometryFactory();
			
			WallSurface ws = (WallSurface) b;
			
			Door door = citygml.createDoor();
			
			OpeningProperty openingProperty = citygml.createOpeningProperty();			
			openingProperty.setObject(door);			
			
			Polygon doorPoly= geom.createLinearPolygon(new double[] {0,2,0, 6,2,0, 6,2,6, 3,2,9, 0,2,6, 0,2,0}, 3);
			Polygon doorPoly2 = geom.createLinearPolygon(new double[] {6,3,0, 0,3,0, 0,3,6, 3,3,9, 6,3,6, 6,3,0}, 3);
			List l = new ArrayList();
			l.add(doorPoly);
			l.add(doorPoly2);
			
			//door.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(doorPoly)));
			door.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(l)));
			
			ws.addOpening(openingProperty);
		}				
	}
	
	
	private BoundarySurfaceProperty createBoundarySurface(CityGMLClass type, Polygon geometry) {
		AbstractBoundarySurface boundarySurface = null;

		switch (type) {
		case WALL_SURFACE:
			boundarySurface = citygml.createWallSurface();
			break;
		case ROOF_SURFACE:
			boundarySurface = citygml.createRoofSurface();
			break;
		case GROUND_SURFACE:
			boundarySurface = citygml.createGroundSurface();
			break;
		}

		if (boundarySurface != null) {
			
			boundarySurface.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(geometry)));
			return citygml.createBoundarySurfaceProperty(boundarySurface);
		}

		return null;
	}
	
	
	
	private static SurfaceProperty createSurfaceProperty(GMLFactory gml, List<Double> points) {
		// create a gml:Polygon
		Polygon polygon = gml.createPolygon();

		// create an exterior boundary object
		Exterior exterior = gml.createExterior();
		polygon.setExterior(exterior);

		// create a gml:LinearRing
		LinearRing linearRing = gml.createLinearRing();
		exterior.setRing(linearRing);

		// the coordinates of the linear ring are given as posList
		DirectPositionList posList = gml.createDirectPositionList();
		posList.setValue(points);
		posList.setSrsDimension(3);
		linearRing.setPosList(posList);

		// put the polygon into an embracing surfaceProperty
		SurfaceProperty surfaceProperty = gml.createSurfaceProperty();

		surfaceProperty.setSurface(polygon);

		// return surfaceProperty
		return surfaceProperty;
	}

	
	
	
	

}
