package ch.sebihr.viesshab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

public class Viesshab {
	
	private Map<String, String> mapping= new HashMap<String, String>();
	private String vclient;
	private String vclientHost;
	private String vclientPort;
	private String openhabUrl;
	private boolean debug;
	private long interval;
	private String authToken;
	
	public static void main(String[] args) throws IOException, InterruptedException {
		Viesshab viesshab = new Viesshab();
		viesshab.readConfig();
		viesshab.run();
	}

	private void run() throws InterruptedException, IOException {
		while(true) {
			readmap();
			process();
			Thread.sleep(interval);
		}
	}

	private void process() throws IOException {
		String commands= buildCommand();
		String execString = vclient + " -h " + vclientHost + ":" + vclientPort + " -m -c " + commands;
		if (debug) {
			System.out.println("Executing: " + execString);
		}
		Process process = Runtime.getRuntime().exec(execString);
		BufferedReader input= new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		while ((line= input.readLine()) != null) {
			if (debug) {
				System.out.println("Processing line: " + line);
			}
			String[] result= line.split(" ");
			String itemPart= result[0].substring(0, result[0].indexOf(".value"));
			String item= mapping.get(itemPart);
			String restUrl = openhabUrl + "items/" + item + "/state";
			if (debug) {
				System.out.println("Rest call to " + restUrl + (authToken == null?" (without authentication)":" with basic authentication"));
			}
			URL url = new URL(restUrl);
			HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
			if (authToken != null) {
				httpCon.setRequestProperty("Authorization", "Basic " + authToken);
			}
			httpCon.setDoOutput(true);
			httpCon.setRequestMethod("PUT");
			OutputStreamWriter out = new OutputStreamWriter(
			    httpCon.getOutputStream());
			double value = Double.parseDouble(result[1]);
			out.write(format(value));
			out.close();
			if (debug) {
				System.out.println("Response code: " + httpCon.getResponseCode());
			}
		}
	}

	private String format(double value) {
		DecimalFormat format= new DecimalFormat("#.0");
		DecimalFormatSymbols symbols= new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		format.setDecimalFormatSymbols(symbols);
		return format.format(value);
	}

	private String buildCommand() {
		StringBuilder comm= new StringBuilder();
		for (String command: mapping.keySet()) {
			if (comm.length() != 0) {
				comm.append(",");
			}
			comm.append(command);
		}
		return comm.toString();
	}

	private void readmap() throws FileNotFoundException, IOException {
		mapping.clear();
		File mapFile= new File("/etc/viesshab/mapping.properties");
		if (System.getProperty("viesshab.mapping") != null) {
			mapFile= new File(System.getProperty("viesshab.mapping"));
		}
		Properties map= new Properties();
		if (debug) {
			System.out.println("Reading mapping from " + mapFile.getAbsolutePath());
		}
		map.load(new FileReader(mapFile));
		for (Entry<Object, Object> entry: map.entrySet()) {
			mapping.put(entry.getKey().toString(), entry.getValue().toString());
			if (debug) {
				System.out.println("Mapping " + entry.getKey() + " to " + entry.getValue());
			}
		}
	}
	
	private void readConfig() throws FileNotFoundException, IOException {
		File configFile= new File("/etc/viesshab/config.properties");
		if (System.getProperty("viesshab.config") != null) {
			configFile= new File(System.getProperty("viesshab.config"));
		}
		Properties config= new Properties();
		config.load(new FileReader(configFile));
		vclient= "/usr/local/bin/vclient";
		if (config.getProperty("vclient.binary") != null) {
			vclient= config.getProperty("vclient.binary");
		}
		vclientHost= "localhost";
		if (config.getProperty("vclient.host") != null) {
			vclientHost= config.getProperty("vclient.host");
		}
		vclientPort= "3002";
		if (config.getProperty("vclient.port") != null) {
			vclientPort= config.getProperty("vclient.port");
		}
		openhabUrl= "http://127.0.0.1:8080/rest/";
		if (config.getProperty("openhab.resturl") != null) {
			openhabUrl= config.getProperty("openhab.resturl");
		}
		if (!openhabUrl.endsWith("/")) {
			openhabUrl= openhabUrl + "/";
		}
		String intervalInSec= "180";
		if (config.getProperty("interval") != null) {
			intervalInSec= config.getProperty("interval"); 
		}
		interval= Long.parseLong(intervalInSec) * 1000;
		authToken= null;
		if (config.getProperty("openhab.auth.token") != null) {
			authToken= config.getProperty("openhab.auth.token");
		}
		debug= Boolean.parseBoolean(config.getProperty("debug"));
		if (debug) {
			System.out.println("Starting with properties: ");
			System.out.println("vclient binary: " + vclient);
			System.out.println("vclient host: " + vclientHost);
			System.out.println("vclient port: " + vclientPort);
			System.out.println("openhab rest url: " + openhabUrl);
			if (authToken == null) {
				System.out.println("openhab auth disabled");
			} else {
				System.out.println("openhab auth enabled");
			}
			System.out.println("Using interval " + interval + "ms");
		}
	}
}
