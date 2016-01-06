package uk.co.jlensmeister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConsoleListener implements Runnable{
	
	public Thread thread;
	
	public ConsoleListener(){
		thread = new Thread(this);
		thread.start();
	}
	
	public void run(){
		while(true){
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String cmd = "";
			try {
				cmd = br.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(cmd.equalsIgnoreCase("stop")){
				System.out.println(ConsoleFormatter.info("SHUTTING DOWN SERVER BY COMMAND"));
				System.exit(0);
			}
			
			else{
				System.out.println(ConsoleFormatter.error("Command not found."));
			}
		}
	}
	
}
