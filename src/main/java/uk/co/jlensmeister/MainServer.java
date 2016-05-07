package uk.co.jlensmeister;

import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class MainServer {

	private static Server AuthServer; //instance of the Server api for the Authentication Server
	private static Server mainServer; //instance of the Server api for the Main Chat Server
	private static ConsoleListener commandlistener; //instance of the ConsoleListener class 
	private static java.sql.Connection sql; // sql connection variable
	private static ArrayList<Connection> accepted = new ArrayList<Connection>(); //ArrayList to store all accepted connections
	private static ArrayList<Connection> admins = new ArrayList<Connection>(); // ArrayList to store all administrators connections
	private static HashMap<String, ArrayList<Connection>> rooms = new HashMap<String, ArrayList<Connection>>(); // Map to store a string to an arraylist of connections that holds the connections for the chat rooms
	private static HashMap<Connection, String> usernames = new HashMap<Connection, String>(); //Map to store a Connection and the string of that user
	private static HashMap<String, Integer> userids = new HashMap<String, Integer>(); //Map String to an integer of the ID of the user to the username
	
	public static void main(String[] args){
		
		startOutput(); //Call startouput function
		//Connect to the mysql database
		try {
			Class.forName("com.mysql.jdbc.Driver");
			sql = DriverManager.getConnection("jdbc:mysql://127.0.0.1/chat?user=ChatServer&password=vapt49ua4ct09c94fe&useUnicode=true&characterEncoding=UTF-8");
		} catch (ClassNotFoundException | SQLException e) {
			System.out.println(ConsoleFormatter.error("Could not connect to the MYSQL database!"));
			e.printStackTrace();
			System.exit(1);
		}
		//get information about the database
		DatabaseMetaData meta = null;
		try {
			meta = sql.getMetaData();
			//check if the users table exists
			ResultSet result = meta.getTables(null, null, "Users", null);
			//if the table exists
			if(result.next()){
				//do nothing
			}else{
				//if table doesn't exist create it
				sql.createStatement().execute("CREATE TABLE `Users` (`ID` int(11) unsigned NOT NULL AUTO_INCREMENT,`Username` varchar(20) NOT NULL DEFAULT '',"
						+ "`Password` varchar(255) DEFAULT '',`Banned` tinyint(1) NOT NULL DEFAULT '0',`Admin` tinyint(1) NOT NULL DEFAULT '0',"
						+ "`rooms` varchar(255) NOT NULL DEFAULT 'Welcome',PRIMARY KEY (`ID`))");
			}
			//check if the logs table exists
			result = meta.getTables(null, null, "logs", null);
			//if table exists
			if(result.next()){
				//do nothing
			}else{
				//if table doesn't exist create it
				sql.createStatement().execute("CREATE TABLE `logs` (`id` int(11) unsigned NOT NULL AUTO_INCREMENT,`room` varchar(50) DEFAULT NULL,"
						+ "`UserID` int(11) DEFAULT NULL,`Message` varchar(255) DEFAULT NULL,PRIMARY KEY (`id`))");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		startAuth(); //Call startauth function
		commandlistener = new ConsoleListener(); //Initialise commandlistener variable
		startServer(); //Call startserver function
		
	}
	
	public static void startOutput(){
		//Output to the console a friendly user message.
		System.out.println(ConsoleFormatter.info("Starting up Chat Program Server..."));
		System.out.println(ConsoleFormatter.info("Chat Program Server Version 1.0."));
		System.out.println(ConsoleFormatter.info("Works with Chat Program 1.0."));
		
	}
	
	public static void startAuth(){
		//start authentication server on port 6698
		AuthServer = new Server();
		AuthServer.start();
		try {
			AuthServer.bind(6698);
		} catch (IOException e) {
			System.out.println(ConsoleFormatter.error("Could not bind to the port for the Auth Server!"));
			e.printStackTrace();
			System.exit(1);
		}
		//set up a listener for the authentication server
		AuthServer.addListener(new Listener(){
			//method of when something is received
			public void received(Connection connection, Object object){
				//if the received data is a string
				if(object instanceof String){
					String incoming = (String) object;
					if(incoming.contains("/")){
						String[] request = incoming.split("/");
						if(request.length == 3){
							//if it is a login request
							if(request[0].equals("L")){
								try {
									//create a mysql statement
									Statement statement = sql.createStatement();
									//select everything from the users table where the username is equal to what is provided
									ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `Username` = '" + request[1].toLowerCase() + "'");
									if(results.next()){
										//check if the password is correct to what's in the table
										if(results.getString("Password").equals(request[2])){
											//check if the user is banned
											if(results.getBoolean("Banned")){
												connection.sendTCP("A/You are banned from the chat.");
											}else{
												//check if the user is an admin
												if(results.getBoolean("Admin")){
													//reply to the client saying that they are logged in and is an admin
													connection.sendTCP("S/Y");
												}else{
													//reply to the client saying that they are logged in but not an admin
													connection.sendTCP("S/N");
												}
											}
										}else{
											//reply to the client saying the password was incorrect
											connection.sendTCP("A/Password was incorrect.");
										}
									}else{
										//reply to the client saying the username doesn't exist
										connection.sendTCP("A/Username not found.");
									}
								} catch (SQLException e) {
									e.printStackTrace();
								}
							//if it is a registration request
							}else if(request[0].equals("R")){
								//create mysql statement
								Statement statement;
								try {
									statement = sql.createStatement();
									//select everything from users the username is equal to the one provided
									ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `username` = '" + request[1].toLowerCase() + "'");
									if(results.next()){
										//if there is a match reply to the client saying the username is already taken
										connection.sendTCP("A/Username is already taken.");
									}else{
										statement = sql.createStatement();
										//insert a new record into the database for the new user with the given values
										statement.execute("INSERT INTO `Users` (`Username`, `Password`) VALUES ('" + request[1].toLowerCase() + "', '" + request[2] + "');");
										//reply to the client saying that they are now registered
										connection.sendTCP("S/N");
									}
								} catch (SQLException e) {
									e.printStackTrace();
									//tell the client that there was an SQL error
									connection.sendTCP("I/SQL Query fail.");
								}
								
							}else{
								//reply that there was an issue with the request sent to the server
								connection.sendTCP("I/Invalid Auth request sent.");
							}
						}else{
							//reply that there was an issue with the request sent to the server
							connection.sendTCP("I/Invalid Auth request sent.");
						}
					}else{
						//reply that there was an issue with the request sent to the server
						connection.sendTCP("I/Invalid Auth request sent.");
					}
				}
			}
		});
		
	}
	
	public static void startServer(){
		//start main chat server on port 6697
		mainServer = new Server();
		mainServer.start();
		try {
			mainServer.bind(6697);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//register and serialise the classes that get sent over TCP with the library used.
		Kryo kryo = mainServer.getKryo();
		kryo.register(ChatMessage.class);
		kryo.register(Disconnected.class);
		kryo.register(Joined.class);
		kryo.register(Left.class);
		//set up a listener for the main chat server
		mainServer.addListener(new Listener(){
			//method to be called when something is received
			public void received(Connection connection, Object object){
				//if the received data is a string
				if(object instanceof String){
					String in = (String) object;
					if(in.contains("/")){
						String[] data = in.split("/");
						//user login check
						if(data[0].equals("F")){
							try{
								//create sql statement
								Statement statement = sql.createStatement();
								//select everything from the users table where the username is equal to what it is provided
								ResultSet results = statement.executeQuery("SELECT * FROM `users` WHERE `username` = '" + data[1].toLowerCase() + "'");
								if(results.next()){
									//check the password against what is in the table
									if(results.getString("password").equals(data[2])){
										//check if the user is banned
										if(results.getBoolean("banned")){
											//close the conneciton if the user is banned
											connection.close();
										}else{
											//add the connection to the accepted arraylist
											accepted.add(connection);
											//check if the user is an admin
											if(results.getBoolean("admin")){
												//add the connection to the admins arraylist
												admins.add(connection);
											}
											//put the username to the connection in the map
											usernames.put(connection, data[1]);
											//put the userID to the username in the userids map
											userids.put(results.getString("Username"), results.getInt("ID"));
											//reply to the client with the list of rooms they were last in
											connection.sendTCP("R/" + results.getString("rooms"));
											//put the CSV into an array of rooms
											String[] str = results.getString("rooms").split(",");
											for(String s: str){
												//for each room either add the connection to an existing arraylist or create a new on for it
												if(rooms.containsKey(s.toLowerCase())){
													rooms.get(s.toLowerCase()).add(connection);
												}else{
													rooms.put(s.toLowerCase(), new ArrayList<Connection>());
													rooms.get(s.toLowerCase()).add(connection);
												}
												//alert all currently connected clients in those rooms that the new user has joined by sending a Joined class with details in
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
						//if an admin request
						}else if(data[0].equals("admin")){
							//if the request is coming from a confirmed admin
							if(admins.contains(connection)){
								//if the request is to set a user as an admin
								if(data[1].equals("A")){
									try {
										//create new sql statement
										Statement statement = sql.createStatement();
										//update the value of admin to true in the users table for the user
										statement.execute("UPDATE `Users` SET `Admin` = 1 WHERE `Username`='" + data[2].toLowerCase() +"';");
									} catch (SQLException e) {
										e.printStackTrace();
									}
								//if the request is to ban a user
								}else if(data[1].equals("B")){
									try {
										//create new sql statement
										Statement statement = sql.createStatement();
										//update value of banned to true in users table for the user
										statement.execute("UPDATE `Users` SET `Banned` = 1 WHERE `Username` = '" + data[2].toLowerCase() +"'");
									} catch (SQLException e) {
										e.printStackTrace();
									}
								//if the request is to unban a user
								}else if(data[1].equals("U")){
									try {
										//create new sql statement
										Statement statement = sql.createStatement();
										//update value of banned to false in users table for the user
										statement.execute("UPDATE `Users` SET `Banned` = 0 WHERE `Username` = '" + data[2].toLowerCase() +"'");
									} catch (SQLException e) {
										e.printStackTrace();
									}
								//if the request is for a message log
								}else if(data[1].equals("L")){
									//start the log reply string
									String reply = "L/";
									//create new sql statement
									Statement statement;
									try {
										statement = sql.createStatement();
										//select the message from the logs table and the username of the sender from the users table for the specific room
										ResultSet result = statement.executeQuery("SELECT `logs`.`message`, `users`.`Username` FROM `logs` INNER JOIN `users` ON `logs`.`UserID` = `users`.`ID` WHERE room = '" + data[2].toLowerCase() + "'");
										//loop through every record
										while(result.next()){
											//add the username followed by a : and a space then the message and a / at the end of the reply string
											reply = reply + result.getString("Username") + ": " + result.getString("message") + "/";
										}
									} catch (SQLException e) {
										e.printStackTrace();
									}
									//send the reply string to the requester
									connection.sendTCP(reply);
								}
							}
						}
					}
				//if the data received is a ChatMessage class
				}else if(object instanceof ChatMessage){
					//cast the object to ChatMessage
					ChatMessage message = (ChatMessage) object;
					//if the room exists to the main chat server
					if(rooms.containsKey(message.room.toLowerCase())){
						//create and execute a statement to insert the message into the logs table
						PreparedStatement insert;
						try {
							insert = sql.prepareStatement("INSERT INTO `logs` (`Room`, `UserID`, `Message`) VALUES ('" + message.room.toLowerCase() + "', '" + userids.get(message.user.toLowerCase()) + "', ?);");
							insert.setString(1, message.message);
							insert.execute();
						} catch (SQLException e) {
							e.printStackTrace();
						}
						//send the message to all connections in the room
						for(Connection c : rooms.get(message.room.toLowerCase())){
							c.sendTCP(message);
						}
					}
				//if the received data is a Joined class (if the user has joined a room)
				}else if(object instanceof Joined){
					//cast the object received to Joined
					Joined joining = (Joined) object;
					Joined global = new Joined();
					global.room = joining.room;
					global.username = joining.username;
					//check if the room exists already
					if(rooms.containsKey(global.room.toLowerCase())){
						//add the connection to the room
						rooms.get(global.room.toLowerCase()).add(connection);
						//send the joined alert to all connections in that room
						for(Connection c : rooms.get(global.room.toLowerCase())){
							c.sendTCP(global);
						}
						connection.sendTCP(global);
					}else{
						//create room and add the connection to the room
						connection.sendTCP(global);
						rooms.put(global.room.toLowerCase(), new ArrayList<Connection>());
						rooms.get(global.room.toLowerCase()).add(connection);
					}
				//if the data received is a Left class (user has left the room)
				}else if(object instanceof Left){
					//cast the object received to Left
					Left l = (Left) object;
					//remove the connection from the room
					rooms.get(l.room.toLowerCase()).remove(connection);
					//send the Left alert to all the connections in the room
					ArrayList<Connection> send = rooms.get(l.room.toLowerCase());
					for(Connection c : send){
						c.sendTCP(l);
					}
				}
			}
			//method to be called if a connection is disconnected
			public void disconnected(Connection connection){
				//create instance of Diconnected
				Disconnected leaving = new Disconnected();
				leaving.username = usernames.get(connection);
				//alert all connections that the user has disconnected
				for(Connection c : accepted){
					c.sendTCP(leaving);
				}
				//remove user from all rooms
				ArrayList<String> r = new ArrayList<String>();
				Iterator it = rooms.entrySet().iterator();
			    while (it.hasNext()) {
			        Map.Entry pair = (Map.Entry)it.next();
			        ArrayList<Connection> l = (ArrayList<Connection>) pair.getValue();
			        if(l.contains(connection)){
			        	r.add((String)pair.getKey());
			        	l.remove(connection);
			        }
			    }
			    String usersrooms = "";
			    int i = 1;
			    //find the rooms that the user is in and contruct a CSV string of them
			    for(String room : r){
			    	if(i < r.size()){
			    		usersrooms = usersrooms + room + ",";
			    	}else{
			    		usersrooms = usersrooms + room;
			    	}
			    	i += 1;
			    }
			    //create new sql statement
			    Statement statement;
				try {
					statement = sql.createStatement();
					//update user rooms where the username is equal to the user using the room CSV string
					statement.execute("UPDATE Users SET Rooms='" + usersrooms + "' WHERE username = '" + usernames.get(connection).toLowerCase() + "'");
				} catch (SQLException e) {
					e.printStackTrace();
				}
				//remove the connection from the usernames arraylist
				usernames.remove(connection);
			}
		});
		
	}
	
}
