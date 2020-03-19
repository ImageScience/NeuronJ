import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;

public class NeuronJ_Website implements PlugIn {
	
	public void run(String arg) {
		
		try { BrowserLauncher.openURL("http://www.imagescience.org/meijering/software/neuronj/"); }
		catch (Throwable e) { NJ.error("Could not open default internet browser"); }
	}
	
}
