package de.tu_berlin.citlab.logging;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

public class ConnectionFactory {

	private static ConnectionFactory instance = null;

	public static ConnectionFactory getInstance() {
		if (instance == null) {
			instance = new ConnectionFactory();
		}
		return instance;
	}

	// overridden by config file
	private String serverName = "";
	private String serverPort = "";
	private String databaseName = "";
	private String tableName = "";
	private String user = "";
	private String pass = "";

	private BoneCP connectionPool = null;

	private ConnectionFactory() {
		if (databaseDriverLoadable()) {
			try {
				connectionPool = new BoneCP(getConfig());
				// connectionPool.shutdown();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean databaseDriverLoadable() {
		boolean result = true;
		try {
			// load the database driver (make sure this is in your classpath!)
			Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception e) {
			e.printStackTrace();
			result = false;
		}
		return result;
	}

	private BoneCPConfig getConfig() {
		BoneCPConfig config = new BoneCPConfig();
		if (loadProperties()) {
			config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",
					serverName, serverPort, databaseName));
			config.setUsername(user);
			config.setPassword(pass);
			config.setMinConnectionsPerPartition(5);
			config.setMaxConnectionsPerPartition(10);
			config.setPartitionCount(1);
		}
		return config;
	}

	private boolean loadProperties() {
		boolean result = true;
		try {
			serverName = System.getProperty("servername");
			serverPort = System.getProperty("serverPort");
			databaseName = System.getProperty("databaseName");
			tableName = System.getProperty("tableName");
			user = System.getProperty("user");
			pass = System.getProperty("pass");
			if(serverName == null || serverPort == null || databaseName == null || tableName == null || user == null || pass == null) {
				result = false;
			}
		}
		catch(Exception ex) {
			result = false;
		}
		return result;
	}

	public Connection getConnection() {
		Connection result = null;
		try {
			result = connectionPool.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static Connection getConnectionStatic() {
		return getInstance().getConnection();
	}

	public static void main(String[] args) {
		getConnectionStatic();
	}

}
