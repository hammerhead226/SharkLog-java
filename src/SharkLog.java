import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.opencsv.CSVWriter;

import edu.wpi.first.wpilibj.networktables.NetworkTable;
import edu.wpi.first.wpilibj.tables.IRemote;
import edu.wpi.first.wpilibj.tables.IRemoteConnectionListener;
import edu.wpi.first.wpilibj.tables.ITable;
import edu.wpi.first.wpilibj.tables.ITableListener;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class SharkLog implements ITableListener, IRemoteConnectionListener {

	public static void main(String[] args) throws ArgumentParserException, IOException, InterruptedException {
		new SharkLog().run(args);
	}

	private boolean firstConnect = true;
	private boolean dataReceived = false;
	private double downtime = getTimeSeconds();
	private boolean headers = false;

	// Command line vars
	private String ip;
	private String logDir;
	private int waitTime;
	private String tableName;
	private boolean oneRun;

	// Stylish printing methods vars
	private int numDots;
	private boolean s = true;

	// Logging vars
	private CSVWriter writer;
	private NetworkTable table;
	private String[] prevLine = {};
	private int n;
	private boolean firstLog = true;

	public void run(String[] args) throws ArgumentParserException, IOException, InterruptedException {
		// Handle command line args
		ArgumentParser parser = ArgumentParsers.newArgumentParser("SharkLog");
		parser.addArgument("-ip").help("The IP to initialize NetworkTables on").setDefault("roboRIO-226-FRC.local")
				.type(String.class);
		parser.addArgument("-ld", "--logdir").help("Directory that log files are stored in").setDefault("logs/")
				.type(String.class);
		parser.addArgument("-wt", "--waittime").help("Time to wait before timing out").setDefault(15)
				.type(Integer.class);
		parser.addArgument("-t", "--table").help("Table to log").setDefault("sharklog").type(String.class);
		parser.addArgument("-or", "--onerun").help("Program will exit instead of restarting after it times out")
				.action(Arguments.storeTrue()).setDefault(false);

		Namespace ns = parser.parseArgs(args);

		ip = ns.getString("ip");
		logDir = ns.getString("logdir");
		if (!logDir.substring(logDir.length() - 1).equals("/")) {
			logDir += "/";
		}
		waitTime = ns.getInt("waittime");
		tableName = ns.getString("table");
		oneRun = ns.getBoolean("onerun");

		System.out.println("Starting SharkLogger...");
		System.out.println("===========================================================");

		// Initialize NetworkTables
		NetworkTable.setClientMode();
		NetworkTable.setIPAddress(ip);
		NetworkTable.initialize();
		System.out.println("NetworkTables initilized on: " + ip);

		Path p = Paths.get(logDir);

		if (Files.notExists(p)) {
			try {
				Files.createDirectories(p);
				System.out.println("Created Directory: " + logDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		while (true) {
			dataReceived = false;
			downtime = getTimeSeconds();
			firstConnect = true;
			headers = false;
			numDots = 0;

			// Open new csv file & writer
			String logfileName = logDir + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
					+ ".csv";
			writer = new CSVWriter(new FileWriter(logfileName), ',', '"');
			System.out.println("Created File: " + logfileName);

			table = NetworkTable.getTable(tableName);
			System.out.println("Logging table: " + tableName);
			table.addConnectionListener(this, true);
			table.addTableListener(this, true);

			while (true) {
				// Only run on initial startup
				if (!table.isConnected() && firstConnect) {
					while (true) {
						if (table.isConnected()) {
							firstConnect = false;
							break;
						}
						printWaitingStylish();
						Thread.sleep(500);
					}
				}
				// Run when disconnected from table
				else if (!table.isConnected() && !firstConnect) {
					downtime = getTimeSeconds();
					while (true) {
						if (table.isConnected()) {
							break;
						}
						System.out.println("Waiting... " + (waitTime - getDowntime()));
						if (getDowntime() >= waitTime) {
							writer.close();
							if (oneRun) {
								System.out.println("EXITING");
								System.exit(0);
							} else {
								System.out.println("TIMED OUT");
								firstConnect = true;
								break;
							}
						}
						Thread.sleep(1000);
					}
					break;
				}
				// Run when connected to table
				else if (table.isConnected()) {
					System.out.println("Checking if data has been received...");
					Thread.sleep(1000);
					System.out.println(dataReceived);
					// If data is received, log
					if (dataReceived) {
						// Log until disconnected
						while (true) {
							// When disconnected, break and move up to waiting
							// loop
							if (!table.isConnected()) {
								break;
							}
							printLoggingStylish();
							Thread.sleep(500);
						}
					} else {
						System.out.println("Data not received");
					}
				}
			}

		}
	}

	public double getTimeSeconds() {
		return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
	}

	public double getDowntime() {
		return getTimeSeconds() - downtime;
	}

	public void printWaitingStylish() {
		if (s) {
			System.out.print("\rWaiting for initial data...");
		} else {
			System.out.print("\r                           ");
			System.out.print("\rWaiting for initial data..");
		}
		s = !s;
	}

	public void printLoggingStylish() {
		if (numDots == 0) {
			System.out.print("\r          ");
		}
		String output = "\rLogging";
		for (int i = numDots; i > 0; i--) {
			output += ".";
		}
		System.out.print(output);
		numDots++;
		if (numDots > 3) {
			numDots = 0;
		}
	}

	@Override
	public void valueChanged(ITable t, String key, Object value, boolean isNew) {
		dataReceived = true;
		downtime = System.currentTimeMillis();
		writeTable(table);
	}

	public void writeTable(ITable t) {
		String[] sortedKeys = t.getKeys().toArray(new String[t.getKeys().size()]);
		Arrays.sort(sortedKeys);
		if (firstLog) {
			n = sortedKeys.length;
			firstLog = false;
		}
		// If first time writing, create headers
		if (!headers) {
			writer.writeNext(sortedKeys);
			System.out.println("\nHeaders written.");
			headers = true;
		}
		
		// Only write the nth line if there are n variables being sent to the network table
		if (n-- == 1) {
			n = sortedKeys.length;
			// build a list of values - ALTERNATE METHOD
			/*
			 * ArrayList<String> values = new ArrayList<String>(0); for (String
			 * key : sortedKeys) {
			 * values.add(Double.toString(table.getNumber(key, -0))); } String[]
			 * line = values.toArray(new String[values.size()]);
			 */

			// Build a list of values
			String[] line = new String[sortedKeys.length];
			for (int i = 0; i < line.length; i++) {
				line[i] = table.getValue(sortedKeys[i], -6226226).toString();
			}
			writer.writeNext(line);
		}

		// if (!Arrays.equals(line, prevLine)) {
		// writer.writeNext(line);
		// }
		//
		// prevLine = line;
	}

	@Override
	public void connected(IRemote arg0) {
		System.out.println("\nCONNECTED TO ROBOT");
	}

	@Override
	public void disconnected(IRemote arg0) {
		System.out.println("\nLOST CONNECTION TO ROBOT");
		dataReceived = false;
	}
}
