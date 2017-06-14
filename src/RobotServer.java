import java.util.concurrent.TimeUnit;

import edu.wpi.first.wpilibj.networktables.NetworkTable;

public class RobotServer {
	private double downtime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

	public static void main(String[] args) {
		new RobotServer().run();
	}

	public double getDowntime() {
		return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - downtime;
	}

	public void run() {
		NetworkTable.setServerMode();
		NetworkTable.setIPAddress("localhost");
		NetworkTable table = NetworkTable.getTable("sharklog");

		int t = 0;

		while (true) {
			table.putNumber("time", t);
			table.putNumber("Apples", t + 10);
			table.putNumber("CrabbeApples", t + 400);
			table.putNumber("encoder", Math.random() * 10);
		    table.putNumber("centerX", Math.random());
		    table.putNumber("vision", Math.random() * 100);
		    table.putNumber("num1", Math.random() * 100);
		    table.putNumber("num2", Math.random());
		    table.putNumber("batman", Math.random() * 10);
		    table.putNumber("robin", Math.random() * 100);
		    table.putNumber("DT_FLTalon", Math.random() * 10);
		    table.putNumber("DT_FRTalon", Math.random());
		    System.out.println(t);
			t++;
			try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
