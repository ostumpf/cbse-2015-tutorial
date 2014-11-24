package cz.cuni.mff.d3s.roadtrain.components;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

import cz.cuni.mff.d3s.deeco.annotations.Component;
import cz.cuni.mff.d3s.deeco.annotations.In;
import cz.cuni.mff.d3s.deeco.annotations.InOut;
import cz.cuni.mff.d3s.deeco.annotations.Local;
import cz.cuni.mff.d3s.deeco.annotations.Out;
import cz.cuni.mff.d3s.deeco.annotations.PeriodicScheduling;
import cz.cuni.mff.d3s.deeco.annotations.Process;
import cz.cuni.mff.d3s.deeco.logging.Log;
import cz.cuni.mff.d3s.deeco.scheduler.CurrentTimeProvider;
import cz.cuni.mff.d3s.deeco.simulation.matsim.MATSimRouter;
import cz.cuni.mff.d3s.deeco.task.ParamHolder;
import cz.cuni.mff.d3s.demo.environment.Actuator;
import cz.cuni.mff.d3s.demo.environment.ActuatorProvider;
import cz.cuni.mff.d3s.demo.environment.ActuatorType;
import cz.cuni.mff.d3s.demo.environment.Sensor;
import cz.cuni.mff.d3s.demo.environment.SensorProvider;
import cz.cuni.mff.d3s.demo.environment.SensorType;
import cz.cuni.mff.d3s.roadtrain.demo.Settings;
import cz.cuni.mff.d3s.roadtrain.demo.environment.VehicleMonitor;
import cz.cuni.mff.d3s.roadtrain.utils.Navigator;
import cz.cuni.mff.d3s.roadtrain.utils.VehicleInfo;

@Component
public class Vehicle {
	/**
	 * Id of the vehicle component.
	 */
	public String id;

	/**
	 * Contains a list of link ids that lead to the destination. It is given to
	 * the MATSim to guide the vehicle which way it should go.
	 */
	@Local
	public List<Id> route;
	
	public Map<String, VehicleInfo> group = new HashMap<String, VehicleInfo>();

	/**
	 * Link where the vehicle is currently at.
	 */
	public Id currentLink;
	
	public String leaderCar;
	
	public int carNum;
	
	/**
	 * Destination city
	 */
	public String dstCity;
	
	/**
	 * Position of the current link.
	 */
	public Coord position;
	
	public Coord destination;

	@Local
	public Actuator<List<Id>> routeActuator;
	
	@Local
	public Sensor<Id> currentLinkSensor;

	@Local
	public MATSimRouter router;
	
	@Local
	public CurrentTimeProvider clock;

	public Vehicle(String id, String dstCity, Id currentLink,
			ActuatorProvider actuatorProvider, SensorProvider sensorProvider,
			MATSimRouter router, CurrentTimeProvider clock) {
		this.id = id;
		this.dstCity = dstCity;
		this.routeActuator = actuatorProvider.createActuator(ActuatorType.ROUTE);
		this.currentLinkSensor = sensorProvider.createSensor(SensorType.CURRENT_LINK);
		this.router = router;
		this.clock = clock;
	}

	/**
	 * Periodically prints out the values of sensors and important values of the
	 * knowledge.
	 */
	@Process
	@PeriodicScheduling(period = 5000, order = 10)
	public static void reportStatus(
			@In("id") String id,
			@In("currentLinkSensor") Sensor<Id> currentLinkSensor,
			@In("position") Coord position,
			@In("dstCity") String dstCity,
			@In("carNum") int carNum,
			@In("leaderCar") String leaderCar,
			@In("group") Map<String, VehicleInfo> group,
			@In("route") List<Id> route,
			@In("clock") CurrentTimeProvider clock) {

		Log.d("Entry [" + id + "]:reportStatus");

		System.out.format("%s [%s] pos: %s(%s, %s), group: %s, dist: %s, prevCar: %s, trainNum: %s, dst: %s(%s), route: %s\n",
				formatTime(clock.getCurrentMilliseconds()),
				id,
				currentLinkSensor.read(),
				position.getX(),
				position.getY(),
				groupToString(group),
				Navigator.getDesDist(dstCity, currentLinkSensor.read()),
				leaderCar,
				carNum,
				getDstLinkId(dstCity),
				dstCity,
				route);
		
		// Report information about vehicle
		VehicleMonitor.report(
				clock.getCurrentMilliseconds(),
				id,
				position,
				leaderCar,
				carNum,
				dstCity,
				Navigator.getPosition(dstCity).getCoord());
	}

	/**
	 * Periodically updates knowledge based on sensor readings. These knowledge
	 * fields are updated: currentLink.
	 */
	@Process
	@PeriodicScheduling(period = 200, order = 1)
	public static void updateSensors(@In("id") String id,
			@Out("currentLink") ParamHolder<Id> currentLinkHolder,
			@Out("position") ParamHolder<Coord> position,
			@In("currentLinkSensor") Sensor<Id> currentLinkSensor,
			@In("router") MATSimRouter router) {

		Log.d("Entry [" + id + "]:updateCurrentLink");

		currentLinkHolder.value = currentLinkSensor.read();
		position.value = router.getLink(currentLinkHolder.value).getCoord();
	}
	
	@Process
	@PeriodicScheduling(period = 5000)
	public static void organizeRoadTrains(
			@In("id") String id,
			@In("group") Map<String, VehicleInfo> group,
			@In("dstCity") String dstCity,
			@In("currentLinkSensor") Sensor<Id> currentLinkSensor,
			@In("router") MATSimRouter router,
			@InOut("leaderCar") ParamHolder<String> leaderCar,
			@InOut("carNum") ParamHolder<Integer> carNum) {
		
		Id currentLink = currentLinkSensor.read();
		
		double myTargetDist = router.route(currentLink, getDstLinkId(dstCity)).size();
		
		// Do nothing when already at destination
		if(myTargetDist == 0)
			return;
		
		// Try to find a car to follow
		String nearestCarId = null;
		VehicleInfo nearestCarInfo = null;
		Double nearestDist = null;
		for(Entry<String, VehicleInfo> entry: group.entrySet()) {
			Id carLink = router.findNearestLink(entry.getValue().position).getId();
			double distUsingCar = Navigator.getDestDistUsingCar(dstCity, currentLink, carLink);
			double distToCar = Navigator.getCarToCarDist(currentLink, carLink);
			double carToDestDist = Navigator.getDesDist(dstCity, carLink);
			
			// Skip ourself
			if(entry.getKey().equals(id))
				continue;
						
			// Skip cars already at destination
			if(carToDestDist == 0)
				continue;
			
			// Route using car position is beneficial (length using the car is the same as without)
			if(myTargetDist == distUsingCar && (nearestDist == null || nearestDist > distToCar)) {
				nearestCarId = entry.getKey();
				nearestCarInfo = entry.getValue();
				nearestDist = distToCar;
			}
		}
		
		// Follow the car or lead new road train
		if(nearestCarId != null && nearestCarInfo.trainNum < Settings.TRAIN_LENGTH_LIMIT - 1) {
			// There is car that is in front of us on the path to destination and the road train is short enough -> follow it
			leaderCar.value = nearestCarId;
			carNum.value = nearestCarInfo.trainNum + 1;
		} else {
			// There is no car in front of us on the path to destination, or road train is too long -> lead the new train
			leaderCar.value = null;
			carNum.value = 0;
		}
	}
	
	/**
	 * Plans the route to the destination.
	 */
	@Process
	@PeriodicScheduling(period = 2000, order = 4)
	public static void planRouteAndDrive(
			@In("id") String id,
			@In("currentLink") Id currentLink,
			@In("leaderCar") String leaderCar,
			@In("group") Map<String, VehicleInfo> group,
			@In("dstCity") String dstCity,
			@InOut("route") ParamHolder<List<Id> > route,
			@In("routeActuator") Actuator<List<Id> > routeActuator,
			@In("router") MATSimRouter router) throws Exception {
		
		// No car in front of us -> drive directly to destination
		if(leaderCar == null) {
			route.value = router.route(currentLink, getDstLinkId(dstCity), route.value);
		}
		
		// Car in front of us -> follow it
		if(leaderCar != null) {
			Coord carPos = group.get(leaderCar).position;
			Link carLink = router.findNearestLink(carPos);
			route.value = router.route(currentLink, carLink.getId(), route.value);
		}
		
		// Already at the destination -> stop
		if(currentLink.equals(Navigator.getPosition(dstCity).getId())) {
			route.value = new LinkedList<Id>();
		}
		
		routeActuator.set(route.value);
	}
	
	private static Id getDstLinkId(String dstCity) {
		return Navigator.getPosition(dstCity).getId();
	}
	
	private static String groupToString(Map<String, VehicleInfo> group) {
		StringBuilder builder = new StringBuilder();
		
		boolean first = true;
		builder.append("[");
		for(Entry<String, VehicleInfo> entry: group.entrySet()) {
			if(!first)
				builder.append(", ");
			first = false;
			builder.append(entry.getKey());
		}
		builder.append("]");
		
		return builder.toString();
	}
	
	private static String formatTime(long ts) {
		int msec = (int) (ts % 1000);
		ts = ts / 1000;
		int sec = (int) (ts % 60);
		ts = ts / 60;
		int min = (int) (ts % 60);
		ts = ts / 60;
		int hour = (int) ts;
		
		return String.format("<%02d:%02d:%02d.%03d>", hour, min, sec, msec);
	}
}
