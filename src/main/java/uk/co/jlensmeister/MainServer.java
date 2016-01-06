package uk.co.jlensmeister;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class MainServer {

	private static Server AuthServer;
	private static Server mainServer;
	private static ConsoleListener commandlistener;
	private static java.sql.Connection sql;
	private static ArrayList<Connection> accepted = new ArrayList<Connection>();
	private static ArrayList<Connection> admins = new ArrayList<Connection>();
	private static HashMap<String, ArrayList<Connection>> rooms = new HashMap<String, ArrayList<Connection>>();
	private static HashMap<Connection, String> usernames = new HashMap<Connection, String>();
	
	public static void main(String[] args){
		
		startOutput();
		try {
			Class.forName("com.mysql.jdbc.Driver");
			sql = DriverManager.getConnection("jdbc:mysql://127.0.0.1/chat?user=root&password=&useUnicode=true&characterEncoding=UTF-8");
		} catch (ClassNotFoundException | SQLException e) {
			System.out.println(ConsoleFormatter.error("Could not connect to the MYSQL database!"));
			e.printStackTrace();
			System.exit(1);
		}
		startAuth();
		commandlistener = new ConsoleListener();
		startServer();
		
	}
	
	public static void startOutput(){
		
		System.out.println(ConsoleFormatter.info("Starting up Chat Program Server..."));
		System.out.println(ConsoleFormatter.info("Chat Program Server Version 1.0."));
		System.out.println(ConsoleFormatter.info("Works with Chat Program 1.0."));
		
	}
	
	public static void startAuth(){
		
		AuthServer = new Server();
		AuthServer.start();
		try {
			AuthServer.bind(6698);
		} catch (IOException e) {
			System.out.println(ConsoleFormatter.error("Could not bind to the port for the Auth Server!"));
			e.printStackTrace();
			System.exit(1);
		}
		AuthServer.addListener(new Listener(){
			public void received(Connection connection, Object object){
				if(object instanceof String){
					String incoming = (String) object;
					if(incoming.contains("/")){
						String[] request = incoming.split("/");
						if(request.length == 3){
							if(request[0].equals("L")){
								try {
									Statement statement = sql.createStatement();
									ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `Username` = '" + request[1].toLowerCase() + "'");
									if(results.next()){
										if(results.getString("Password").equals(request[2])){
											if(results.getBoolean("Banned")){
												connection.sendTCP("A/You are banned from the chat.");
											}else{
												if(results.getBoolean("Admin")){
													connection.sendTCP("S/Y");
												}else{
													connection.sendTCP("S/N");
												}
											}
										}else{
											connection.sendTCP("A/Password was incorrect.");
										}
									}else{
										connection.sendTCP("A/Username not found.");
									}
								} catch (SQLException e) {
									e.printStackTrace();
								}
								
							}else if(request[0].equals("R")){
								
								Statement statement;
								try {
									statement = sql.createStatement();
									ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `username` = '" + request[1].toLowerCase() + "'");
									if(results.next()){
										connection.sendTCP("A/Username is already taken.");
									}else{
										statement = sql.createStatement();
										System.out.println(request[0] + " " + request[1] + " "  + request[2]);
										statement.execute("INSERT INTO `Users` (`Username`, `Password`) VALUES ('" + request[1].toLowerCase() + "', '" + request[2] + "');");
										connection.sendTCP("S/N");
									}
								} catch (SQLException e) {
									e.printStackTrace();
									connection.sendTCP("I/SQL Query fail.");
								}
								
							}else{
								connection.sendTCP("I/Invalid Auth request sent.");
							}
						}else{
							connection.sendTCP("I/Invalid Auth request sent.");
						}
					}else{
						connection.sendTCP("I/Invalid Auth request sent.");
					}
				}
			}
		});
		
	}
	
	public static void startServer(){
		
		mainServer = new Server();
		mainServer.start();
		try {
			mainServer.bind(6697);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Kryo kryo = mainServer.getKryo();
		kryo.register(ChatMessage.class);
		kryo.register(Disconnected.class);
		kryo.register(Joined.class);
		
		mainServer.addListener(new Listener(){
			public void received(Connection connection, Object object){
				if(object instanceof String){
					String in = (String) object;
					if(in.contains("/")){
						String[] data = in.split("/");
						if(data[0].equals("F")){
							try{
								Statement statement = sql.createStatement();
								ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `username` = '" + data[1].toLowerCase() + "'");
								if(results.next()){
									if(results.getString("password").equals(data[2])){
										if(results.getBoolean("banned")){
											connection.close();
										}else{
											accepted.add(connection);
											if(results.getBoolean("admin")){
												admins.add(connection);
											}
											usernames.put(connection, data[1]);
											connection.sendTCP("R/" + results.getString("rooms"));
											String[] str = results.getString("rooms").split(",");
											for(String s: str){
												if(rooms.containsKey(s.toLowerCase())){
													rooms.get(s.toLowerCase()).add(connection);
												}else{
													rooms.put(s.toLowerCase(), new ArrayList<Connection>());
													rooms.get(s.toLowerCase()).add(connection);
												}
												for(Connection c : accepted){
													Joined joining = new Joined();
													joining.room = s.toLowerCase();
													joining.username = data[1];
													c.sendTCP(joining);
													if(rooms.get(s.toLowerCase()).contains(c)){
														Joined hasjoined = new Joined();
														hasjoined.room = s.toLowerCase();
														hasjoined.username = usernames.get(c);
														connection.sendTCP(hasjoined);
													}
												}
												
											}
										}
									}
								}
							}catch(SQLException e){
								e.printStackTrace();
							}
						}
					}
				}else if(object instanceof ChatMessage){
					ChatMessage message = (ChatMessage) object;
					if(rooms.containsKey(message.room.toLowerCase())){
						Statement insert;
						try {
							insert = sql.createStatement();
							insert.execute("INSERT INTO `logs` (`Room`, `User`, `Message`) VALUES ('" + message.room.toLowerCase() + "', '" + message.user.toLowerCase() + "', '" + message.message + "')");
						} catch (SQLException e) {
							e.printStackTrace();
						}
						for(Connection c : rooms.get(message.room.toLowerCase())){
							c.sendTCP(message);
						}
					}
				}else if(object instanceof Joined){
					Joined joining = (Joined) object;
					try {
						Statement statement = sql.createStatement();
						ResultSet result = statement.executeQuery("SELECT * FROM Users WHERE Username = '" + joining.username.toLowerCase() + "'");
						String out = "";
						if(result.next()){
						String in = result.getString("Rooms");
						out = in + "," + joining.room;
						}
						statement.execute("UPDATE Users SET Rooms='" + out + "' WHERE username = '" + joining.username.toLowerCase() + "'");
					} catch (SQLException e) {
						e.printStackTrace();
					}
					Joined global = new Joined();
					global.room = joining.room;
					global.username = joining.username;
					if(rooms.containsKey(global.room.toLowerCase())){
						rooms.get(global.room.toLowerCase()).add(connection);
						for(Connection c : rooms.get(global.room.toLowerCase())){
							c.sendTCP(global);
						}
						connection.sendTCP(global);
					}else{
						connection.sendTCP(global);
						rooms.put(global.room.toLowerCase(), new ArrayList<Connection>());
						rooms.get(global.room.toLowerCase()).add(connection);
					}
				}
			}
			public void disconnected(Connection connection){
				
				Disconnected leaving = new Disconnected();
				leaving.username = usernames.get(connection);
				usernames.remove(connection);
				for(Connection c : accepted){
					c.sendTCP(leaving);
				}
				
			}
		});
		
	}
	
}
