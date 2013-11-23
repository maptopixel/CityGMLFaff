import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import org.citygml4j.model.citygml.building.InteriorRoomProperty;
import org.citygml4j.model.citygml.building.InteriorWallSurface;
import org.citygml4j.model.citygml.building.OpeningProperty;
import org.citygml4j.model.citygml.building.Room;
import org.citygml4j.model.citygml.building.WallSurface;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.GMLClass;
import org.citygml4j.model.gml.feature.BoundingShape;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurface;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurfaceProperty;
import org.citygml4j.model.gml.geometry.complexes.CompositeSurface;
import org.citygml4j.model.gml.geometry.primitives.AbstractSurface;
import org.citygml4j.model.gml.geometry.primitives.Coord;
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
import org.nocrala.tools.gis.data.esri.shapefile.ShapeFileReader;
import org.nocrala.tools.gis.data.esri.shapefile.exception.InvalidShapeFileException;
import org.nocrala.tools.gis.data.esri.shapefile.header.ShapeFileHeader;
import org.nocrala.tools.gis.data.esri.shapefile.shape.AbstractShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.PointData;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.MultiPointZShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.PointShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.PolygonShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.PolygonZShape;


public class BuildingCreator {
	private CityGMLFactory citygml;
	private GMLFactory gml;
	private GMLGeometryFactory geom ;
	
	private String buildingFootprintFile = "../CityGMLFaff_test_data/dul_mm_bdy.shp";
	private String roomsSrcFile = "../CityGMLFaff_test_data/dul__1__annl__3_4531__rms0_178__2013-10-27-23h12m40s.shp";
	private String doorsSrcFile = "../CityGMLFaff_test_data/doorsExport.shp";

	public static void main(String[] args) throws Exception {
		
		System.out.println("----->>Getting command line arguments --");
		for(int i=0;i<args.length;i++) {
			System.out.println("Argument ["+(i+1)+"] is = "+args[i]);
		}

		new BuildingCreator().doMain();
	}

	public void doMain() throws Exception {
		SimpleDateFormat df = new SimpleDateFormat("[HH:mm:ss] "); 

		System.out.println(df.format(new Date()) + "setting up citygml4j context and JAXB builder");
		CityGMLContext ctx = new CityGMLContext();
		JAXBBuilder builder = ctx.createJAXBBuilder();

		System.out.println(df.format(new Date()) + "creating LOD4 building as citygml4j in-memory object tree");

		citygml = new CityGMLFactory();
		gml = new GMLFactory();	
		geom = new GMLGeometryFactory();

		GMLIdManager gmlIdManager = DefaultGMLIdManager.getInstance();

		CityModel cityModel = citygml.createCityModel();
		
		//create a building			
		FileInputStream is =new FileInputStream(buildingFootprintFile);

		Building building = citygml.createBuilding();
		buildExterior(is,building,gmlIdManager);		


		System.out.println("Getting rooms shapefile");
		
		//Get the rooms		
		FileInputStream is2 =new FileInputStream(roomsSrcFile);
		String outputFileName = "../CityGMLFaff_test_data/export/export_model.xml";

		//Now add room shapesto the building
		building = populateBuilding(is2, building,gmlIdManager);			


		//Add this building to the city model
		cityModel.setBoundedBy(building.calcBoundedBy(false));		
		cityModel.addCityObjectMember(citygml.createCityObjectMember(building));		

		//Write this object to xml file
		System.out.println(df.format(new Date()) + "writing citygml4j object tree");
		CityGMLOutputFactory out = builder.createCityGMLOutputFactory(CityGMLVersion.v1_0_0);
		CityGMLWriter writer = out.createCityGMLWriter(new File(outputFileName));

		writer.setPrefixes(CityGMLVersion.v1_0_0);
		writer.setSchemaLocations(CityGMLVersion.v1_0_0);
		writer.setIndentString("  ");
		writer.write(cityModel);
		writer.close();	

		System.out.println(df.format(new Date()) + "CityGML file written");
		System.out.println(df.format(new Date()) + "sample citygml4j application successfully finished");
	}



	//Takes a surface which should be a wall and inserts an opening into it
	public void createDoor(BoundarySurfaceProperty bsp) throws DimensionMismatchException {
		System.out.println("Creating a door");

		AbstractBoundarySurface b = bsp.getBoundarySurface();
		if (b instanceof InteriorWallSurface) {
			BoundingShape s = b.getBoundedBy();


			System.out.println("IS wall surf");			
			GMLGeometryFactory geom = new GMLGeometryFactory();			
			InteriorWallSurface ws = (InteriorWallSurface) b;		

			//Create the semantics
			Door door = citygml.createDoor();

			OpeningProperty openingProperty = citygml.createOpeningProperty();			
			openingProperty.setObject(door);		

			//Create some geometry for the door

			/*
			Polygon doorPoly= geom.createLinearPolygon(new double[] {0,2,0, 6,2,0, 6,2,6, 3,2,9, 0,2,6, 0,2,0}, 3);
			Polygon doorPoly2 = geom.createLinearPolygon(new double[] {6,3,0, 0,3,0, 0,3,6, 3,3,9, 6,3,6, 6,3,0}, 3);
			List l = new ArrayList();
			l.add(doorPoly);
			l.add(doorPoly2);			
			//door.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(doorPoly)));
			door.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(l)));
			 */			
			ws.addOpening(openingProperty);
		}				
	}



	private void createExtrudedBlock(PointData[] points, double extrudeBy, List<SurfaceProperty> surfaceMember,List<BoundarySurfaceProperty> boundedBy) throws DimensionMismatchException{
		GMLIdManager gmlIdManager = DefaultGMLIdManager.getInstance();

		//Loop through the vertices of the polygon
		for(int c=0; c < points.length -1 ; c++){
			PointData currentPoint = points[c];
			PointData nextPoint = points[c+1];

			Polygon linearPolygon = geom.createLinearPolygon(
					new double[]
					           {
							currentPoint.getX(), currentPoint.getY(),0,
							currentPoint.getX(), currentPoint.getY(),extrudeBy,
							nextPoint.getX(), nextPoint.getY(), extrudeBy,
							nextPoint.getX(), nextPoint.getY(), 0,
							currentPoint.getX(), currentPoint.getY(), 0
					           }
					, 3
			);	
			linearPolygon.setId(gmlIdManager.generateGmlId());
			double doorHeight = 1;

			surfaceMember.add(gml.createSurfaceProperty('#' + linearPolygon.getId()));

			boundedBy.add(createBoundarySurface(CityGMLClass.INTERIOR_WALL_SURFACE, linearPolygon));
			BoundarySurfaceProperty s  = boundedBy.get(0);


		}						

		BoundarySurfaceProperty bsp = boundedBy.get(0);
		System.out.println("bsp");
		System.out.println("bsp " + bsp.toString());
		System.out.println("bsp " + bsp.getType());
		System.out.println("bsp " + bsp.getBoundarySurface());
		System.out.println("bsp " + bsp.getBoundarySurface());

		InteriorWallSurface intWs = (InteriorWallSurface )bsp.getBoundarySurface();

		System.out.println("intWs " + intWs.toString());
		MultiSurfaceProperty lod4ms = intWs.getLod4MultiSurface();
		System.out.println("intWs getms " + lod4ms.getMultiSurface().getSurfaceMember().size());
		System.out.println("intWs citygmlclass " + lod4ms.getMultiSurface().getSurfaceMember().get(0).getGMLClass());
		SurfaceProperty ms = (SurfaceProperty) lod4ms.getMultiSurface().getSurfaceMember().get(0);


		System.out.println("intWs geom 2 " + ((Polygon) ms.getGeometry()).getExterior().getRing());

		/*
		LinearRing lr2 = (LinearRing) ((Polygon) ms).getExterior().getRing();
		//System.out.println("coords " + ((List<SurfaceProperty>) lr2.getCoordinates()).size());
		System.out.println("coords2  " + lr2.getCoord().size());

		//System.out.println("intWs geom 3" + aP.getExterior().getRing().;

		SurfaceProperty sp = surfaceMember.get(0);				
		System.out.println("Surface member count " + surfaceMember.size());
		System.out.println("sptype " + sp.getType());
		System.out.println("sp gml class " + sp.getGMLClass());
		System.out.println("sp geom gml class " + sp.getGeometry().getGMLClass());
		//System.out.println("sp geom gml class " + sp.getGeometry());
		Polygon p = (Polygon) sp.getGeometry();

		System.out.println("p ");

		System.out.println("after gml class ");
		System.out.println("after gml class " + p.getExterior());
		LinearRing lr = (LinearRing)p.getExterior().getRing();
		System.out.println("linear ring");
		System.out.println("polyExterior " + lr.getCoord().toString());
		System.out.println("polyInterio " +  lr.getCoord().size());
		 */

	}

	private Door createDoor() {
		Door door = citygml.createDoor();
		//setName(door.getName(), "door name");
		//setGlobalId(door, "666");
		MultiSurfaceProperty doorMSP = gml.createMultiSurfaceProperty();
		MultiSurface doorMs = gml.createMultiSurface();
		doorMSP.setMultiSurface(doorMs);
		door.setLod4MultiSurface(doorMSP);

		//setGeometry(doorMs, ifcRelatedBuildingElement);
		/*
		DoubleAttribute genericAttributeWidth = new DoubleAttributeImpl();
		genericAttributeWidth.setName("OverallWidth");
		genericAttributeWidth.setValue((double) ifcDoor.getOverallWidth());
		door.addGenericAttribute(genericAttributeWidth);
		DoubleAttribute genericAttributeHeight = new DoubleAttributeImpl();
		genericAttributeHeight.setValue((double) ifcDoor.getOverallHeight());
		genericAttributeHeight.setName("OverallHeight");
		door.addGenericAttribute(genericAttributeHeight);*/
		return door;
	}


	public Building populateBuilding(FileInputStream is2, Building building, GMLIdManager gmlIdManager ) throws IOException, InvalidShapeFileException, DimensionMismatchException{

		GMLGeometryFactory geom = new GMLGeometryFactory();
		//GMLIdManager gmlIdManager = DefaultGMLIdManager.getInstance();		

		ShapeFileReader r2 = new ShapeFileReader(is2);
		ShapeFileHeader h2 = r2.getHeader();
		System.out.println("The shape type of this files is " + h2.getShapeType());

		int total2 = 0;
		AbstractShape s2;
		while ((s2 = r2.next()) != null) {
			switch (s2.getShapeType()) {
			case POINT:
				PointShape aPoint = (PointShape) s2;
				// Do something with the point shape...
				break;
			case MULTIPOINT_Z:
				MultiPointZShape aMultiPointZ = (MultiPointZShape) s2;
				// Do something with the MultiPointZ shape...
				break;
			case POLYGON:		
				PolygonShape aPolygon = (PolygonShape) s2;
				System.out.println("Getting a room");

				//For each part of the polygon. Most polys will be single part
				for (int i = 0; i < aPolygon.getNumberOfParts(); i++) {

					//Get the vertices of this polygon and store in points array 
					PointData[] points = aPolygon.getPointsOfPart(i);
					System.out.println("- part " + i + " has " + points.length
							+ " points.");

					//This polygon will be a room so make a room object
					//Polygon ground = geom.createLinearPolygon(new double[] {0,0,0, 0,12,0, 6,12,0, 6,0,0, 0,0,0}, 3);				
					Room room1 = citygml.createRoom();				

					//Ceiling heigh extrusion
					double extrudeBy = 2;

					//surfaceMember list holds the geometric surfaces that makes up the room  
					List<SurfaceProperty> surfaceMember = new ArrayList<SurfaceProperty>();
					List<BoundarySurfaceProperty> boundedBy = new ArrayList<BoundarySurfaceProperty>();			


					/*
					for(int c=0; c < points.length -1 ; c++){
						PointData currentPoint = points[c];
						PointData nextPoint = points[c+1];
						Polygon linearPolygon = geom.createLinearPolygon(
								new double[]
								           {
										currentPoint.getX(), currentPoint.getY(),0,
										currentPoint.getX(), currentPoint.getY(),extrudeBy,
										nextPoint.getX(), nextPoint.getY(), extrudeBy,
										nextPoint.getX(), nextPoint.getY(), 0,
										currentPoint.getX(), currentPoint.getY(), 0
								           }
								, 3
						);

						linearPolygon.setId(gmlIdManager.generateGmlId());						
						surfaceMember.add(gml.createSurfaceProperty('#' + linearPolygon.getId()));						
						boundedBy.add(createBoundarySurface(CityGMLClass.INTERIOR_WALL_SURFACE, linearPolygon));
					}				

					CompositeSurface compositeSurface = gml.createCompositeSurface();
					compositeSurface.setSurfaceMember(surfaceMember);				
					Solid solid = gml.createSolid();
					solid.setExterior(gml.createSurfaceProperty(compositeSurface));			
					building.setLod1Solid(gml.createSolidProperty(solid));				

					System.out.println("Setting building exterior boundedBySurface");
					building.setBoundedBySurface(boundedBy);		

					InteriorRoomProperty createInteriorRoomProperty = citygml.createInteriorRoomProperty();
					createInteriorRoomProperty.setObject(room1);
					building.addInteriorRoom(createInteriorRoomProperty);			

					 */



					//Create the extruded block to represent the room using the surface lists
					createExtrudedBlock(points,extrudeBy,surfaceMember,boundedBy);				

					//If there is a door on this polygon edge add it
					BoundarySurfaceProperty wallSurface = boundedBy.get(2);

					createDoor(wallSurface );

					CompositeSurface compositeSurface = gml.createCompositeSurface();
					compositeSurface.setSurfaceMember(surfaceMember);			

					Solid solid = gml.createSolid();
					solid.setInterior(surfaceMember);
					//solid.setInterior(gml.createSurfaceProperty(compositeSurface));

					room1.setLod4Solid(gml.createSolidProperty(solid));		
					room1.setBoundedBySurface(boundedBy);
					//building.setBoundedBySurface(boundedBy);

					InteriorRoomProperty createInteriorRoomProperty = citygml.createInteriorRoomProperty();
					createInteriorRoomProperty.setObject(room1);
					building.addInteriorRoom(createInteriorRoomProperty);	


				}

				break;
			default:
				System.out.println("Read other type of shape.");
			}
			//total++;
		}
		is2.close();		
		return building;
	}




	public void buildExterior(FileInputStream is, Building building, GMLIdManager gmlIdManager ) throws InvalidShapeFileException, IOException, DimensionMismatchException{		
		GMLGeometryFactory geom = new GMLGeometryFactory();
		//GMLIdManager gmlIdManager = DefaultGMLIdManager.getInstance();		

		ShapeFileReader r = new ShapeFileReader(is);

		ShapeFileHeader h = r.getHeader();
		System.out.println("The shape type of this files is " + h.getShapeType());

		int total = 0;
		AbstractShape s;
		while ((s = r.next()) != null) {
			switch (s.getShapeType()) {
			case POINT:
				PointShape aPoint = (PointShape) s;
				// Do something with the point shape...
				break;
			case MULTIPOINT_Z:
				MultiPointZShape aMultiPointZ = (MultiPointZShape) s;
				// Do something with the MultiPointZ shape...
				break;
			case POLYGON:			

				PolygonShape aPolygon = (PolygonShape) s;


				System.out.println("I read a Polygon with "
						+ aPolygon.getNumberOfParts() + " parts and "
						+ aPolygon.getNumberOfPoints() + " points");
				for (int i = 0; i < aPolygon.getNumberOfParts(); i++) {
					PointData[] points = aPolygon.getPointsOfPart(i);
					System.out.println("- part " + i + " has " + points.length
							+ " points.");
					//Room room1 = citygml.createRoom();
					//Polygon ground = geom.createLinearPolygon(new double[] {0,0,0, 0,12,0, 6,12,0, 6,0,0, 0,0,0}, 3);							

					List<SurfaceProperty> surfaceMember = new ArrayList<SurfaceProperty>();
					List<BoundarySurfaceProperty> boundedBy = new ArrayList<BoundarySurfaceProperty>();					

					/*
					double extrudeBy = 0;
					for(int c=0; c < points.length -1 ; c++){
						PointData currentPoint = points[c];
						PointData nextPoint = points[c+1];

						Polygon groundPolygon = geom.createLinearPolygon(
								new double[]
								           {
										currentPoint.getX(), currentPoint.getY(),0,
										currentPoint.getX(), currentPoint.getY(),extrudeBy,
										nextPoint.getX(), nextPoint.getY(), extrudeBy,
										nextPoint.getX(), nextPoint.getY(), 0,
										currentPoint.getX(), currentPoint.getY(), 0
								           }
								, 3
						);
					}
					groundPolygon.setId(gmlIdManager.generateGmlId());						
					surfaceMember.add(gml.createSurfaceProperty('#' + groundPolygon.getId()));
					boundedBy.add(createBoundarySurface(CityGMLClass.GROUND_SURFACE, groundPolygon));

					 */


					double extrudeBy = 2;

					for(int c=0; c < points.length -1 ; c++){
						PointData currentPoint = points[c];
						PointData nextPoint = points[c+1];
						Polygon linearPolygon = geom.createLinearPolygon(
								new double[]
								           {
										currentPoint.getX(), currentPoint.getY(),0,
										currentPoint.getX(), currentPoint.getY(),extrudeBy,
										nextPoint.getX(), nextPoint.getY(), extrudeBy,
										nextPoint.getX(), nextPoint.getY(), 0,
										currentPoint.getX(), currentPoint.getY(), 0
								           }
								, 3
						);

						linearPolygon.setId(gmlIdManager.generateGmlId());						
						surfaceMember.add(gml.createSurfaceProperty('#' + linearPolygon.getId()));						
						boundedBy.add(createBoundarySurface(CityGMLClass.WALL_SURFACE, linearPolygon));
					}				

					CompositeSurface compositeSurface = gml.createCompositeSurface();
					compositeSurface.setSurfaceMember(surfaceMember);				
					Solid solid = gml.createSolid();
					solid.setExterior(gml.createSurfaceProperty(compositeSurface));			
					building.setLod1Solid(gml.createSolidProperty(solid));				

					System.out.println("Setting building exterior boundedBySurface");
					building.setBoundedBySurface(boundedBy);		
				}
				break;
			default:
				System.out.println("Read other type of shape.");
			}
			total++;
		}
		System.out.println("Total shapes read: " + total);

		is.close();
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
		case INTERIOR_WALL_SURFACE:
			boundarySurface = citygml.createInteriorWallSurface();
			break;
		}

		if (boundarySurface != null) {
			boundarySurface.setLod4MultiSurface(gml.createMultiSurfaceProperty(gml.createMultiSurface(geometry)));
			return citygml.createBoundarySurfaceProperty(boundarySurface);
		}

		return null;
	}

}
