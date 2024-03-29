/**
 * A simple utility class to load twitter login credentials from a file.
 */

package de.tu_berlin.citlab.web;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {

	private static final Config INSTANCE = new Config();

	public static Config getInstance() {
		return INSTANCE;
	}

	private final Properties prop;

	private Config() {
		prop = new Properties();
		FileInputStream fis = null;

		try {
			fis = new FileInputStream("server.config");
			prop.load(fis);
		} catch (IOException e) {
			System.err.println("Could not load server configuration file");
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public String getProperty(String name) {
		return prop.getProperty(name);
	}

	public String getPath(String name) {
		return System.getProperty("user.home") + "/" + prop.getProperty(name);
	}
}
