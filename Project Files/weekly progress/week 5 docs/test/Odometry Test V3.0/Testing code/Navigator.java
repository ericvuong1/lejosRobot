import lejos.nxt.*;

public class Navigator extends Thread {
	public final NXTRegulatedMotor leftMotor = Motor.A;
	public final NXTRegulatedMotor rightMotor = Motor.C;
	private UltrasonicSensor frontUS, sideUS;
	// robot specifications
	private final double leftRadius = Odometer.wheelRadius;
	private final double rightRadius = Odometer.wheelRadius;
	private final double wheelDist = Odometer.wheelDistance;
	private final int motorStraight = 200;
	private final int motorLowLow = 100;
	private final int motorLow = 280;
	private final int motorHigh = 320;
	private final int motorHighHigh = 420;
	public static int sideData;
	public static int frontData;
	
	private final Odometer odometer;
	public Navigator(Odometer odometer, UltrasonicSensor frontUS, UltrasonicSensor sideUS) {
	        this.odometer = odometer;
	        this.frontUS = frontUS;
	        this.sideUS = sideUS;
	        
	        frontUS.off();
	        sideUS.off();
	    }
	 
	// navigator specifications
	private double[] path = new double[0];
	private boolean navigating = false;
	private final double THETA_THRESHOLD = 0.05;
	private static final double NAVIGATOR_TOLERANCE_DIST = 1.5;
	private final double reorientationThresh = 0.2;

	// avoid obstacle specifications
	private double dangerZone = 15;// threshold before avoiding an obstacle
	private double sideDangerZone = 15;// threshold before avoiding an obstacle
	private boolean avoid = false;
	private final int safeDist = 50;
	private final int wallFollowerClose = 10;
	private final int wallFollowerFar = 16;

	public void run() {
		// wait
//		leftMotor.setAcceleration(400);
//		rightMotor.setAcceleration(400);
		try {
			Thread.sleep(3000);
		} catch (InterruptedException ex) {
			// no error is expected here
		}
		// path order
		for (int i = 0; i < path.length; i += 2) {
			travelTo(path[i], path[i + 1]);
		}
	}

	public void travelTo(double x, double y) {
		navigating = true;

		// rotate to desired angle, and move to way-point
		leftMotor.setSpeed(motorStraight);
		rightMotor.setSpeed(motorStraight);
		driveTo(x, y);
		double differenceX = x - odometer.getX();
		double differenceY = y - odometer.getY();

		// main loop that checks for obstacle and destination arrival
		while (navigating) {
			setSpeed(motorStraight);
			// correct angle if deviated
			double currentTheta = odometer.getTheta();
			double theta = Math.atan2(differenceY, differenceX) * 180 / Math.PI;
			double AbsDTheta = Math.abs(theta - currentTheta);
			differenceX = x - odometer.getX();
			differenceY = y - odometer.getY();

			leftMotor.forward();
			rightMotor.forward();
			// conditions for the presence of an obstacle
			leftMotor.forward();
			rightMotor.forward();

			if (sideDist() < 10) 
			{
				leftMotor.setSpeed(motorHigh);
				rightMotor.setSpeed(motorLow);
			}
			else{
				leftMotor.setSpeed(motorStraight);
				rightMotor.setSpeed(motorStraight);
			}
			if (frontDist()< dangerZone && avoid == false) {
				driveDist(-6);
				// rotate until obstacle free in front
				turnTo(wrapAngle(currentTheta - Math.PI / 2));
				// avoid mode enabled
				avoid = true;
				// main loop for avoiding an obstacle
				while (avoid) {
					currentTheta = odometer.getTheta();
					leftMotor.forward();
					rightMotor.forward();
					// wallfollower -->bangbangcontroller
					if (sideDist() > wallFollowerFar) {
						leftMotor.setSpeed(motorLowLow+20);
						rightMotor.setSpeed(motorHighHigh);
					} 
					else if (sideDist() < wallFollowerClose) 
					{
						leftMotor.setSpeed(motorHighHigh);
						rightMotor.setSpeed(motorLowLow);
					} else {
						leftMotor.setSpeed(motorStraight);
						rightMotor.setSpeed(motorStraight);

					}

					if (frontDist()< dangerZone) {
						setSpeed(motorStraight);

						Sound.buzz();

						turnTo(wrapAngle(currentTheta - Math.PI / 2));
					}
					differenceX = x - odometer.getX();
					differenceY = y - odometer.getY();
					// relocate if orientation matches way-point
					if ((wrapAngle(Math.abs(odometer.getTheta())
							- wrapAngle(Math.atan2(differenceY, differenceX)))) < reorientationThresh) {
						setSpeed(0);
						Sound.beep();

						avoid = false;
					}
				}
				// re-orient itself and turn to target way-point once again
				driveTo(x, y);
			}
			// check if arrived at destination
			differenceX = x - odometer.getX();
			differenceY = y - odometer.getY();
			if (Math.sqrt(differenceX * differenceX + differenceY * differenceY) < NAVIGATOR_TOLERANCE_DIST) {
				setSpeed(0);
				Sound.beep();
				navigating = false;
			}
		}
	}

	public void setSpeed(int speed)
	{
		leftMotor.setSpeed(speed);
		rightMotor.setSpeed(speed);
		return;
	}
	// this method adjusts the robot's orientation
	// the robot will turn to its minimal angle
	// theta is in radians
	public void turnTo(double theta) {
		setSpeed(motorStraight);
		double currentTheta = odometer.getTheta();
		double dTheta = theta - currentTheta;
		// conditions for rotating with minimal angle
		if (dTheta < Math.PI * -1) {
			dTheta = 2 * Math.PI + dTheta;
			rotateCCW(dTheta);
		} else if (dTheta >= Math.PI) {
			dTheta = 2* Math.PI - dTheta;
			rotateCW(dTheta);
		} else if (dTheta > 0) {
			rotateCCW(dTheta);
		} else {
			rotateCW(dTheta);
		}
	}

	public void rotateCW(double theta) {
		leftMotor.rotate(
				convertAngle(leftRadius, wheelDist, Math.abs(theta) * 180
						/ Math.PI), true);
		rightMotor.rotate(
				-convertAngle(rightRadius, wheelDist, Math.abs(theta) * 180
						/ Math.PI), false);
	}

	public void rotateCCW(double theta) {
		leftMotor.rotate(
				-convertAngle(leftRadius, wheelDist, Math.abs(theta) * 180
						/ Math.PI), true);
		rightMotor.rotate(
				convertAngle(rightRadius, wheelDist, Math.abs(theta) * 180
						/ Math.PI), false);
	}

	// turns to way-point and drive forward
	public void driveTo(double x, double y) {
		setSpeed(motorStraight);
		double differenceX = x - odometer.getX();
		double differenceY = y - odometer.getY();
		turnTo(Math.atan2(differenceY, differenceX));
		leftMotor.forward();
		rightMotor.forward();
	}

	// drive a desired distance in cm
	public void driveDist(double distance) {
		leftMotor.rotate(convertDistance(leftRadius, distance), true);
		rightMotor.rotate(convertDistance(rightRadius, distance), false);
	}

	public boolean isNavigating() {
		return this.navigating;
	}

	// creates a path with an arbitrary size that will be determined at run-time
	// to set a path, write in this form "setPath(x1,y1,x2,y2,x3,y3,...)";
	public void setPath(double... path) {
		this.path = path;
	}

	// turn to desired angle
	public static int convertAngle(double radius, double width, double angle) {
		return (int) ((180.0 * Math.PI * width * angle / 360.0) / (Math.PI * radius));
	}

	// travel desired distance
	private static int convertDistance(double radius, double distance) {
		return (int) ((180.0 * distance) / (Math.PI * radius));
	}

	// wrap the angle to simplify reading and avoid unecessary turns
	private double wrapAngle(double rads) {
		return ((rads % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
	}

	// sleeps robot given in milliseconds
	public void sleep(int time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			// no error expected
		}
	}

	public void turn(double angle) { // turn to relative angle

		if (angle < -180) {
			angle += 360;
		} else if (angle > 180) {
			angle -= 360;
		}
		Sound.beep();

		// turn to angle
		leftMotor.setSpeed(200);
		rightMotor.setSpeed(200);
		leftMotor.rotate(-convertAngle(leftRadius, wheelDist, angle), true);
		rightMotor.rotate(convertAngle(rightRadius, wheelDist, angle), false);
		Sound.beep();
	}
	
	public int frontDist()
	{
		int distance;
		frontUS.ping();
		sleep(10);
		distance = frontUS.getDistance();
		frontData = distance;
		return distance;
	}
	public int sideDist()
	{
		int distance;
		
		sideUS.ping();
		sleep(10);
		distance = sideUS.getDistance();
		sideData = distance;
		return distance;
	}
}
