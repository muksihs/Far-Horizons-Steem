package com.muksihs.farhorizons;

import com.muksihs.steembot.ui.AppWindow;

public class Main {

	public static void main(String[] args) throws InterruptedException {
//		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
//	    Configuration config = ctx.getConfiguration();
//	    LoggerConfig loggerCfg = config.getRootLogger();
//	    loggerCfg.setLevel(Level.WARN); //Using this level disables the logger.
//	    ctx.updateLoggers();
		
		new AppWindow("Far Horizons - STEEM");
		new Thread(new FarHorizonsApp(args)).start();
	}

}
