// Generated by the ${WOLipsContext.getPluginName()} at ${Date}
package your.app;

import com.webobjects.foundation.NSLog;

import er.extensions.ERXApplication;

public class Application extends ERXApplication {
	public static void main(String[] argv) {
		ERXApplication.main(argv, Application.class);
	}

	public Application() {
		NSLog.out.appendln("Welcome to " + name() + " !");
		/* ** put your initialization code in here ** */
	}
}