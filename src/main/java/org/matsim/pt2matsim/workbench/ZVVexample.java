package org.matsim.pt2matsim.workbench;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreatorMagic;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidateCreatorWeighted;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersGtfsShapes;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersOsmAttributes;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersWeightedCandidates;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;
import org.matsim.pt2matsim.osm.lib.AllowedTagsFilter;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.run.shp.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.HashSet;
import java.util.Map;

import static org.matsim.pt2matsim.workbench.PTMapperShapesExample.createTestPTMConfig;

/**
 * Mapping example for public transit in the zurich area (agency: ZVV).
 *
 * Transit schedule is available on opentransportdata.swiss
 *
 * @author polettif
 */
public class ZVVexample {

	private static String base = "zvv/";
	private static String osmName = base + "osm/zurich.osm";
	private static String gtfsFolder = base + "gtfs/";
	private static String gtfsShapeFile = gtfsFolder + GtfsDefinitions.Files.SHAPES.fileName;
	private static String inputNetworkFile = base + "network/network_unmapped.xml.gz";
	private static String fullScheduleFileUM = base + "mts/schedule_unmapped_full.xml.gz";
	private static String inputScheduleFile = base + "mts/schedule_unmapped.xml.gz";

	private static String coordSys = "EPSG:2056";
	private static String outputNetwork1 = base + "output/standard_network.xml.gz";
	private static String outputSchedule1 = base + "output/standard_schedule.xml.gz";
	private static String outputNetwork2 = base + "output/shapes_network.xml.gz";
	private static String outputSchedule2 = base + "output/shapes_schedule.xml.gz";
	private static String outputNetwork3 = base + "output/osm_network.xml.gz";
	private static String outputSchedule3 = base + "output/osm_schedule.xml.gz";
	private static String outputNetwork4 = base + "output/weight_network.xml.gz";
	private static String outputSchedule4 = base + "output/weight_schedule.xml.gz";
	private static OsmData osmData;

	public static void main(String[] args) throws Exception {
//		convertOsm();
//		convertSchedule();
//		runMappingStandard();
		runMappingWeighted();
//		runMappingShapes();
//		runMappingOsm();
//		System.out.println("\n--- Q8585 ---");
//		System.out.format("Standard %.1f", q8585standard);
//		System.out.format("Gewicht  %.1f", q8585w);
	}

	private static void convertOsm() {
		// 1. 	convert OSM
		// 1.1. setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setKeepPaths(true);
		osmConfig.setOutputCoordinateSystem(coordSys);

		// 1.2 load osm file
		osmData = new OsmDataImpl(AllowedTagsFilter.getDefaultPTFilter());
		new OsmFileReader(osmData).readFile(osmName);

		// 1.3 initiate and run converter
		OsmMultimodalNetworkConverter osmConverter = new OsmMultimodalNetworkConverter(osmData);
		osmConverter.convert(osmConfig);

		// 1.4 write converted network
		NetworkTools.writeNetwork(osmConverter.getNetwork(), inputNetworkFile);
	}

	public static void convertSchedule() {
		// 2. create schedule
		// 2.1 Load gtfs feed
		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsFolder);

		// 2. convert gtfs to a unmapped schedule
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordSys);

		// 3. write the transit schedule
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), fullScheduleFileUM);

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(fullScheduleFileUM);

		for(TransitLine transitLine : new HashSet<>(schedule.getTransitLines().values())) {
			for(TransitRoute transitRoute : new HashSet<>(transitLine.getRoutes().values())) {
				if(!transitRoute.getTransportMode().equals("bus")) {
					transitLine.removeRoute(transitRoute);
				}
			}
		}
//		ExtractDebugSchedule.removeRand(schedule, 100);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		ScheduleTools.writeTransitSchedule(schedule, inputScheduleFile);
	}

	/**
	 * Runs a standard mapping
	 */
	public static double runMappingStandard() {
		// Load schedule and network
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		// create PTM config
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		// run PTMapepr
		PTMapper ptMapper = new PTMapper(config, schedule, network);
		ptMapper.run();

		//
		NetworkTools.writeNetwork(network, outputNetwork1);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule1);

		// analyse result
		return runAnalysis(outputSchedule1, outputNetwork1);
	}

	/**
	 * Runs a mapping with weighted links
	 */
	public static double runMappingWeighted() {
		System.out.println("=====================================");
		System.out.println("Run mapping: WEIGHTED LINK CANDIDATES");
		System.out.println("=====================================");

		// Load schedule and network
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		// create PTM config
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		int nLinks = 6;
		double distanceMultiplier = 1.1;
		double maxDistance = 70;

		// run PTMapepr
		PTMapper ptMapper = new PTMapper(config, schedule, network,
				new LinkCandidateCreatorMagic(schedule, network, nLinks, distanceMultiplier, maxDistance, config.getModeRoutingAssignment()),
				new ScheduleRoutersWeightedCandidates(config, schedule, network));
		ptMapper.run();

		//
		NetworkTools.writeNetwork(network, outputNetwork4);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule4);

		Schedule2ShapeFile.run(coordSys, base + "output/shp/", schedule, network);

		// analyse result
		return runAnalysis(outputSchedule4, outputNetwork4);
	}

	/**
	 * Maps a schedule with gtfs shape information to the network
	 */
	public static void runMappingShapes() {
		System.out.println("===================");
		System.out.println("Run mapping: SHAPES");
		System.out.println("===================");

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsShapeFile, coordSys);

		PTMapper ptMapper = new PTMapper(config, schedule, network, new ScheduleRoutersGtfsShapes(config, schedule, network, shapes, 50));
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork2);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule2);

		// analysis
		runAnalysis(outputSchedule2, outputNetwork2);
	}

	/**
	 * Analyses the mapping result
	 */
	private static double runAnalysis(String scheduleFile, String networkFile) {
		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(scheduleFile),
				NetworkTools.readNetwork(networkFile),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);
		analysis.run();
		System.out.format("Q8585:       %.1f\n", analysis.getQ8585());
		System.out.format("Length diff: %.1f %%\n", Math.sqrt(analysis.getAverageSquaredLengthRatio()) * 100);

		return analysis.getQ8585();
	}

	/**
	 * Maps a schedule using osm pt information of the network
	 */
	public static void runMappingOsm() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		PTMapper ptMapper = new PTMapper(config, schedule, network, new ScheduleRoutersOsmAttributes(config, schedule, network, 0.7));
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork3);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), outputSchedule3);

		runAnalysis(outputSchedule3, outputNetwork3);
	}


}

