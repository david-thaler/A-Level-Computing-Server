package uk.co.jlensmeister;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ConsoleFormatter {

	public static String info(String message){
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
		Date dateNow = new Date();
		String currentTime = dateFormat.format(dateNow);
		String output = "[" + currentTime + "][INFO] " + message;
		return output;
		
	}
	
	public static String error(String message){
		
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");//dd/MM/yyyy
		Date dateNow = new Date();
		String currentTime = dateFormat.format(dateNow);
		String output = "[" + currentTime + "][ERROR] " + message;
		return output;
		
	}
	
}
