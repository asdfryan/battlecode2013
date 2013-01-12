package base;

import java.util.ArrayList;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.Team;
import battlecode.common.Upgrade;

public class NavSystem {

	public static BaseRobot robot;
	public static RobotController rc;
	public static int[] directionOffsets;
	
	public static boolean followingWaypoint = false;
	public static MapLocation currentWaypoint;
	public static MapLocation waypointDestination;
	
	/**
	 * MUST CALL THIS METHOD BEFORE USING NavSystem
	 * @param myRC
	 */
	public static void init(BaseRobot myRobot) {
		robot = myRobot;
		rc = robot.rc;
		int robotID = rc.getRobot().getID();
		// Randomly assign soldiers priorities for trying to move left or right
		if (robotID % 4 == 0 || robotID % 4 == 1) {
			directionOffsets = new int[]{0,1,-1,2,-2};
		} else {
			directionOffsets = new int[]{0, -1,1,-2,2};
		}
	}
	
	/**
	 * Tells rc to go to a location while defusing mines along the way
	 * @param location
	 * @throws GameActionException
	 */
	public static void goToLocation(MapLocation location) throws GameActionException {
		Direction dir = rc.getLocation().directionTo(location);
		if (dir != Direction.OMNI) {
			goDirectionAndDefuse(dir);
		}
	}
	
	public static void goToLocationAvoidMines(MapLocation location) throws GameActionException {
		Direction dir = rc.getLocation().directionTo(location);
		if (dir != Direction.OMNI){
			goDirectionAvoidMines(dir);
		}
	}
	
	public static void goDirectionAndDefuse(Direction dir) throws GameActionException {
		Direction lookingAtCurrently = dir;
		lookAround: for (int d : directionOffsets) {
			lookingAtCurrently = Direction.values()[(dir.ordinal() + d + 8) % 8];
			if (rc.canMove(lookingAtCurrently)) {
				if (hasBadMine(rc.getLocation().add(lookingAtCurrently))) {
					rc.defuseMine(rc.getLocation().add(lookingAtCurrently));
				} else {
					rc.move(lookingAtCurrently);
				}
				break lookAround;
			}
		}
	}
	
	public static void goDirectionAvoidMines(Direction dir) throws GameActionException {
		Direction lookingAtCurrently = dir;
		boolean movedYet = false;
		lookAround: for (int d : directionOffsets) {
			lookingAtCurrently = Direction.values()[(dir.ordinal() + d + 8) % 8];
			if (rc.canMove(lookingAtCurrently)) {
				if (!hasBadMine(rc.getLocation().add(lookingAtCurrently))) {
					movedYet = true;
					rc.move(lookingAtCurrently);
				}
				break lookAround;
			}
			if (!movedYet) { // if the robot still hasn't moved
				if (rc.senseNearbyGameObjects(Robot.class, 2, rc.getTeam().opponent()).length == 0) {
					// if there are no nearby enemies
					rangedDefuseMine();
				}
			}
		}
	}
	
	public static void rangedDefuseMine() throws GameActionException {
		if (rc.hasUpgrade(Upgrade.DEFUSION)) {
			MapLocation[] mines = rc.senseMineLocations(rc.getLocation(), 14, rc.getTeam().opponent());
			if (mines.length > 0) {
				rc.defuseMine(mines[0]);
			}
		}
	}
	
	private static boolean hasBadMine(MapLocation location) {
		Team bombTeam = rc.senseMine(location);
		return !(bombTeam == null || bombTeam == rc.getTeam());
	}
	
	public static void followWaypoint() throws GameActionException {
		// If we're close to the current waypoint, find the next one
		if (rc.getLocation().distanceSquaredTo(currentWaypoint) <= 5) {
			if (currentWaypoint.distanceSquaredTo(waypointDestination) <= 5) {
				// We're done following waypoints!
				followingWaypoint = false;
				goToLocation(waypointDestination);
			} else {
				calculateSmartWaypoint(waypointDestination);
				goToLocation(currentWaypoint);
			}
		} else {
			// keep moving to the current waypoint
			goToLocation(currentWaypoint);
		}
	}
	
	public static void calculateSmartWaypoint(MapLocation endLocation) throws GameActionException {
		followingWaypoint = true; // we are now following waypoints to get to endLocation
		waypointDestination = endLocation;
		MapLocation currentLocation = rc.getLocation();
//		if (currentLocation.distanceSquaredTo(endLocation) <= TeamConstants.PATH_GO_ALL_IN_SQ_RADIUS) {
//			currentWaypoint = endLocation;
//			return;
//		}
		// Count how many mines are in each of the directions we could move
		int bestScore = Integer.MAX_VALUE;
		MapLocation bestLocation = null;
		System.out.println("start score");
		String s = "";
		Direction dirLookingAt = currentLocation.directionTo(endLocation);
		for (int i = -2; i <= 2; i++) {
			Direction dir = Direction.values()[(dirLookingAt.ordinal() + i + 8) % 8];
			MapLocation iterLocation = currentLocation.add(dir, TeamConstants.PATH_OFFSET_RADIUS);
			int currentScore = smartScore(iterLocation, TeamConstants.PATH_CHECK_RADIUS, endLocation);			
			
			int numMines = rc.senseNonAlliedMineLocations(iterLocation, TeamConstants.PATH_CHECK_RADIUS * TeamConstants.PATH_CHECK_RADIUS).length;
			s += "dir: " + dir.ordinal() + ", mines: " + numMines + ", score: " + currentScore + " # ";			
			
			if (currentScore < bestScore) {
				bestScore = currentScore;
				bestLocation = iterLocation;
			}
		}
		rc.setIndicatorString(0, s);
		System.out.println("end score");
		currentWaypoint = bestLocation;
	}
	
	/**
	 * Scoring function for calculating how favorable it is to move in a certain direction.
	 * @param location
	 * @param radius
	 */
	public static int smartScore(MapLocation location, int radius, MapLocation endLocation) {
		int numMines = rc.senseNonAlliedMineLocations(location, radius * radius).length;
		// maximum number of mines within this radius should be 3 * radius^2
		int distanceSquared = location.distanceSquaredTo(endLocation);
		int mineDelay;
		if (rc.hasUpgrade(Upgrade.DEFUSION)) {
			mineDelay = GameConstants.MINE_DEFUSE_DEFUSION_DELAY;
		} else {
			mineDelay = GameConstants.MINE_DEFUSE_DELAY;
		}
		return distanceSquared + (int)(0.83 * mineDelay * numMines);
	}
}