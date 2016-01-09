import ij.IJ;
import ij.ImageJ;
import ij.Prefs;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.io.Opener;
import ij.io.RoiEncoder;
import ij.measure.Calibration;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import imagescience.ImageScience;
import imagescience.image.Dimensions;
import imagescience.image.Image;
import imagescience.feature.Differentiator;
import imagescience.utility.FMath;
import imagescience.utility.Formatter;
import imagescience.utility.Progressor;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Checkbox;
import java.awt.CheckboxGroup;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Event;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.StringBuffer;
import java.lang.System;

// Launches NeuronJ and together with its auxiliary classes takes care of handling all interactions.
public final class NeuronJ_ implements PlugIn {
	
	// Minimum version numbers:
	private final static String MINIJVERSION = "1.50a";
	private final static String MINISVERSION = "3.0.0";
	private final static String MINJREVERSION = "1.6.0";
	
	// Performs checks and launches the application:
	public void run(final String arg) {
	
	// Check version numbers:
	if (System.getProperty("java.version").compareTo(MINJREVERSION) < 0) {
		NJ.error("This plugin requires Java version "+MINJREVERSION+" or higher");
		return;
	}
	
	if (IJ.getVersion().compareTo(MINIJVERSION) < 0) {
		NJ.error("This plugin requires ImageJ version "+MINIJVERSION+" or higher");
		return;
	}
	
	try { // This also works to check if ImageScience is installed
		if (ImageScience.version().compareTo(MINISVERSION) < 0)
		throw new IllegalStateException();
	} catch (Throwable e) {
		NJ.error("This plugin requires ImageScience version "+MINISVERSION+" or higher");
		return;
	}
	
	// NeuronJ does not work in batch mode:
	if (IJ.getInstance() == null) {
		NJ.error("This plugin does not work in batch mode");
		return;
	}
	
	// Currently it is not possible to have multiple instances of NeuronJ working in parallel:
	if (NJ.ntb != null) {
		NJ.notify(NJ.NAME+" is already running");
		return;
	}
	
	// Initialize program:
	NJ.init();
	}
	
}

// ***************************************************************************
final class NJ {
	
	// NeuronJ name and version number:
	static final String NAME = "NeuronJ";
	static final String VERSION = "1.4.3";
	
	// Initialization operations:
	static void init() {
		
		final long lStartTime = System.currentTimeMillis();
		
		// Activate uncaught exception catcher:
		catcher = new Catcher();
		try { Thread.currentThread().setUncaughtExceptionHandler(catcher); }
		catch (final Throwable e) { }
		
		// Before doing anything, load the parameter settings, which also determine whether or not to show log messages:
		loadParams();
		
		// Show version numbers:
		log("Running on "+System.getProperty("os.name")+" version "+System.getProperty("os.version"));
		log("Running on Java version "+System.getProperty("java.version"));
		log("Running on ImageJ version "+IJ.getVersion());
		log("Running on ImageScience version "+ImageScience.version());
		
		// Store relevant last settings:
		lasttool = Toolbar.getInstance().getToolId();
		lastdoublebuffering = Prefs.doubleBuffer;
		Prefs.doubleBuffer = true;
		
		// Install NeuronJ toolbar and handler:
		ntb = new TracingToolbar();
		nhd = new TracingHandler();
		
		NJ.copyright();
		log("Initialization completed in "+(System.currentTimeMillis()-lStartTime)+" ms");
	}
	
	// Uncaught exception catcher:
	static Catcher catcher = null;
	
	// Flag for hidden keys:
	static final boolean hkeys = true;
	
	// Handles for shared objects:
	static TracingToolbar ntb = null;
	static TracingHandler nhd = null;
	static MeasurementsDialog mdg = null;
	static AttributesDialog adg = null;
	static TextWindow grw = null;
	static TextWindow trw = null;
	static TextWindow vrw = null;
	
	// Range for cursor-tracing 'nearby' determination
	static final float NEARBYRANGE = 2;
	
	// Standard colors:
	static final Color ACTIVECOLOR = Color.red;
	static final Color HIGHLIGHTCOLOR = Color.white;
	
	// Regarding images:
	static ImagePlus imageplus = null;
	static boolean image = false;
	static boolean calibrate = true;
	static boolean interpolate = true;
	static String imagename = "";
	static String workdir = "";
	static String[] workimages = null;
	static int workimagenr = 0;
	
	static void image(final ImagePlus imp) {
		imageplus = imp;
		if (imageplus == null) {
			image = false;
			imagename = "";
		} else {
			image = true;
			final String title = imageplus.getTitle();
			final int dotIndex = title.lastIndexOf(".");
			if (dotIndex >= 0) imagename = title.substring(0,dotIndex);
			else imagename = title;
		}
	}
	
	// Method for showing no-image error message:
	static void noImage() {
		notify("Please load an image first using "+NAME);
		NJ.copyright();
	}
	
	// Colors supported by NeuronJ:
	static final Color[] colors = {
		Color.black, Color.blue, Color.cyan, Color.green, Color.magenta, Color.orange, Color.pink, Color.red, Color.yellow
	};
	
	static final String[] colornames = {
		"Black", "Blue", "Cyan", "Green", "Magenta", "Orange", "Pink", "Red", "Yellow"
	};
	
	static int colorIndex(final Color color) {
		final int nrcolors = colors.length;
		for (int i=0; i<nrcolors; ++i) if (color == colors[i]) return i;
		return -1;
	}
	
	// Tracing types, type colors, clusters:
	static String[] types = {
		"Default", "Axon", "Dendrite", "Primary", "Secondary", "Tertiary", "Type 06", "Type 07", "Type 08", "Type 09", "Type 10"
	};
	
	static Color[] typecolors = {
		Color.magenta, Color.red, Color.blue, Color.red, Color.blue, Color.yellow,
		Color.magenta, Color.magenta, Color.magenta, Color.magenta, Color.magenta
	};
	
	static String[] clusters = {
		"Default", "Cluster 01", "Cluster 02", "Cluster 03", "Cluster 04", "Cluster 05",
		"Cluster 06", "Cluster 07", "Cluster 08", "Cluster 09", "Cluster 10"
	};
	
	// Switch for enabling or disabling auto image window activation:
	static boolean activate = true;
	
	// Switch for enabling or disabling using the image name in result window titles:
	static boolean usename = false;
	
	// Switch for enabling or disabling (automatic) saving of tracings:
	static boolean autosave = false;
	static boolean save = false;
	
	// Switch for enabling or disabling log messaging:
	static boolean log = false;
	
	// To remember several last ImageJ settings:
	static boolean lastdoublebuffering = false;
	static int lasttool = 0;
	
	// Neurite appearance (bright = 0 and dark = 1):
	static int appear = 0;
	
	// Scale at which eigenvalues are computed:
	static float scale = 2.0f;
	
	// Cost component weight factor:
	static float gamma = 0.7f;
	
	// Half-window size for snapping cursor to locally lowest cost:
	static int snaprange = 4;
	
	// Window size for shortest-path searching. Must be less than about
	// 2900 in order to avoid cumulative costs exceeding the range
	// spanned by the integers.
	static int dijkrange = 2500;
	
	// For smoothing and subsampling tracing segments:
	static int halfsmoothrange = 5;
	static int subsamplefactor = 5;
	
	// Line width for drawing tracings:
	static int linewidth = 1;
	static BasicStroke tracestroke = new BasicStroke(linewidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
	static final BasicStroke crossstroke = new BasicStroke();
	
	// Central method for writing log messages:
	static void log(final String message) {
		if (log) {
			checklog();
			logpan.append(message);
		}
	}
	
	static void checklog() {
		if (logwin == null || !logwin.isShowing()) {
			final String startupmsg = NAME+" version "+VERSION+"\nCopyright (C) Erik Meijering";
			logwin = new TextWindow(NAME+": Log",startupmsg,500,500);
			logpan = logwin.getTextPanel();
		}
	}
	
	static void closelog() {
		if (logwin != null) logwin.setVisible(false);
	}
	
	static TextWindow logwin = null;
	static TextPanel logpan = null;
	
	// Central method for error messages:
	static void error(final String message) {
		if (IJ.getInstance() == null) IJ.showMessage(NJ.NAME+": Error",message+".");
		else new ErrorDialog(NJ.NAME+": Error",message+".");
		IJ.showProgress(1.0);
		IJ.showStatus("");
	}
	
	// Central method for showing copyright notice:
	static final String COPYRIGHT = NAME+" "+VERSION+" (C) Erik Meijering";
	static void copyright() { IJ.showStatus(COPYRIGHT); }
	
	// Central method for notifications:
	static void notify(final String message) {
		if (IJ.getInstance() == null) IJ.showMessage(NJ.NAME+": Note",message+".");
		else new ErrorDialog(NJ.NAME+": Note",message+".");
		IJ.showProgress(1.0);
		IJ.showStatus("");
	}
	
	// Central method for out-of-memory notifications:
	static void outOfMemory() {
		log("Not enough memory for the computations");
		error("Not enough memory for the computations");
		IJ.showProgress(1.0);
		IJ.showStatus("");
	}
	
	// Method for saving the parameters:
	static void saveParams() {
		
		Prefs.set("nj.appear",appear);
		Prefs.set("nj.scale",scale);
		Prefs.set("nj.gamma",gamma);
		Prefs.set("nj.snaprange",snaprange);
		Prefs.set("nj.dijkrange",dijkrange);
		Prefs.set("nj.halfsmoothrange",halfsmoothrange);
		Prefs.set("nj.subsamplefactor",subsamplefactor);
		Prefs.set("nj.linewidth",linewidth);
		Prefs.set("nj.activate",activate);
		Prefs.set("nj.usename",usename);
		Prefs.set("nj.autosave",autosave);
		Prefs.set("nj.log",log);
	}
	
	// Method for loading the parameters:
	static void loadParams() {
		
		appear = (int)Prefs.get("nj.appear",appear);
		scale = (float)Prefs.get("nj.scale",scale);
		gamma = (float)Prefs.get("nj.gamma",gamma);
		snaprange = (int)Prefs.get("nj.snaprange",snaprange);
		dijkrange = (int)Prefs.get("nj.dijkrange",dijkrange);
		halfsmoothrange = (int)Prefs.get("nj.halfsmoothrange",halfsmoothrange);
		subsamplefactor = (int)Prefs.get("nj.subsamplefactor",subsamplefactor);
		linewidth = (int)Prefs.get("nj.linewidth",linewidth);
		tracestroke = new BasicStroke(linewidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
		activate = Prefs.get("nj.activate",activate);
		usename = Prefs.get("nj.usename",usename);
		autosave = Prefs.get("nj.autosave",autosave);
		log = Prefs.get("nj.log",log);
	}
	
	static void quit() {
		
		if (mdg != null) { mdg.close(); }
		if (adg != null) { adg.close(); }
		if (grw != null) { grw.setVisible(false); grw.dispose(); grw = null; }
		if (trw != null) { trw.setVisible(false); trw.dispose(); trw = null; }
		if (vrw != null) { vrw.setVisible(false); vrw.dispose(); vrw = null; }
		
		if (image) {
			nhd.closeTracings();
			// Close image but first restore listeners to avoid a call to ntb.windowClosed():
			ntb.restoreListeners();
			NJ.log("Closing current image...");
			imageplus.hide();
		}
		
		ntb.restoreToolbar();
		
		ntb = null;
		nhd = null;
		image(null);
		
		IJ.showStatus("");
		IJ.showProgress(1.0);
		IJ.setTool(lasttool);
		Prefs.doubleBuffer = lastdoublebuffering;
		log("Quitting "+NAME);
		closelog();
	}
	
}

// *************************************************************************************************
final class Catcher implements Thread.UncaughtExceptionHandler {
	
	public void uncaughtException(Thread t, Throwable e) {
		
		IJ.log("Unexpected exception in "+NJ.NAME+" "+NJ.VERSION);
		IJ.log("Please send a copy of this message");
		IJ.log("and a description of how to reproduce it");
		IJ.log("to Erik Meijering: meijering@imagescience.org");
		IJ.log("OS version: "+System.getProperty("os.name")+" "+System.getProperty("os.version"));
		IJ.log("Java version: "+System.getProperty("java.version"));
		IJ.log("ImageJ version: "+IJ.getVersion());
		IJ.log("ImageScience version: "+ImageScience.version());
		IJ.log(t.toString());
		final java.io.CharArrayWriter cw = new java.io.CharArrayWriter();
		final java.io.PrintWriter pw = new java.io.PrintWriter(cw);
		e.printStackTrace(pw);
		IJ.log(cw.toString());
	}
	
}

// ***************************************************************************
final class TracingToolbar extends Canvas implements MouseListener, MouseMotionListener, WindowListener {
	
	public static final int ERASE = 0;
	public static final int LOAD = 1;
	public static final int SAVE = 2;
	public static final int EXPORT = 3;
	public static final int SPACE1 = 4;
	public static final int ADD = 5;
	public static final int DELETE = 6;
	public static final int MOVE = 7;
	public static final int MEASURE = 8;
	public static final int ATTRIBS = 9;
	public static final int PARAMS = 10;
	public static final int SNAPSHOT = 11;
	public static final int MAGNIFY = 12;
	public static final int SCROLL = 13;
	public static final int SPACE2 = 14;
	public static final int HELP = 15;
	public static final int QUIT = 16;
	
	private static final int NUM_TOOLS = 17;
	private static final int LAST_TOOL = 16;
	private static final int SIZE = 22;
	private static final int OFFSET = 5;
	
	private int iPreviousTool = MAGNIFY;
	private int iCurrentTool = MAGNIFY;
	
	private static final Color gray = ImageJ.backgroundColor;
	private static final Color brighter = gray.brighter();
	private static final Color darker = gray.darker();
	private static final Color evenDarker = darker.darker();
	
	private final boolean[] down = new boolean[NUM_TOOLS];
	
	private Graphics g;
	private Toolbar previousToolbar;
	private ImagePlus imp;
	private ImageWindow imw;
	
	private int x;
	private int y;
	private int xOffset;
	private int yOffset;
	
	TracingToolbar() {
		
		// Set current ImageJ tool to magnifier so that all other images
		// will get magnified when clicked:
		IJ.setTool(Toolbar.MAGNIFIER);
		
		// Remove previous Toolbar and add present TracingToolbar:
		NJ.log("Removing current toolbar...");
		previousToolbar = Toolbar.getInstance();
		final Container container = previousToolbar.getParent();
		final Component[] component = container.getComponents();
		for (int i=0; i<component.length; ++i)
			if (component[i] == previousToolbar) {
				container.remove(previousToolbar);
				container.add(this, i);
				break;
			}
		
		// Reset tool buttons and set current tool:
		NJ.log("Installing "+NJ.NAME+" toolbar...");
		for (int i=0; i<NUM_TOOLS; ++i) down[i] = false;
		resetTool();
		
		// Other initializations:
		setForeground(gray);
		setBackground(gray);
		addMouseListener(this);
		addMouseMotionListener(this);
		container.validate();
	}
	
	int currentTool() { return iCurrentTool; }
	
	int previousTool() { return iPreviousTool; }
	
	public void mouseClicked(final MouseEvent e) {}
	
	public void mouseEntered(final MouseEvent e) {}
	
	public void mouseExited(final MouseEvent e) { NJ.copyright(); }
	
	public void mousePressed(final MouseEvent e) { try {
		
		// Determine which tool button was pressed:
		final int x = e.getX(); int iNewTool;
		for (iNewTool=0; iNewTool<NUM_TOOLS; ++iNewTool)
			if (x > iNewTool*SIZE && x < (iNewTool+1)*SIZE) break;
		setTool(iNewTool);
		
		// Carry out actions for selected tool:
		switch (iNewTool) {
			case ADD: {
				if (!NJ.image) {
					NJ.noImage();
					setPreviousTool();
				} else if (!NJ.nhd.computedCosts())
				NJ.nhd.computeCosts();
				break;
			}
			case DELETE:
			case MOVE: {
				if (!NJ.image) {
					NJ.noImage();
					setPreviousTool();
				}
				break;
			}
			case ATTRIBS: {
				if (NJ.adg == null)
				if (!NJ.image) { NJ.noImage(); setPreviousTool(); }
				else NJ.adg = new AttributesDialog();
				break;
			}
			case MEASURE: {
				if (NJ.mdg == null)
				if (!NJ.image) { NJ.noImage(); setPreviousTool(); }
				else NJ.mdg = new MeasurementsDialog();
				break;
			}
			case ERASE: {
				if (!NJ.image) NJ.noImage();
				else {
					final YesNoDialog ynd = new YesNoDialog("Erase","Do you really want to erase all tracings?");
					if (ynd.yesPressed()) {
						NJ.log("Erasing all tracings");
						NJ.nhd.eraseTracings();
						if (NJ.adg != null) NJ.adg.reset();
						IJ.showStatus("Erased all tracings");
					} else { NJ.copyright(); }
				}
				setPreviousTool();
				break;
			}
			case PARAMS: {
				final ParametersDialog pd = new ParametersDialog();
				if (NJ.image && NJ.nhd.computedCosts()) {
					if (pd.scaleChanged() || pd.appearChanged()) { NJ.nhd.computeCosts(); NJ.nhd.doDijkstra(); }
					else if (pd.gammaChanged()) { NJ.nhd.doDijkstra(); }
				}
				setPreviousTool();
				break;
			}
			case SNAPSHOT: {
				if (!NJ.image) NJ.noImage();
				else {
					final SnapshotDialog sdg = new SnapshotDialog();
					if (sdg.wasCanceled()) {
						NJ.copyright();
					} else {
						final ColorProcessor cp = NJ.nhd.makeSnapshot(sdg.drawImage(),sdg.drawTracings());
						if (cp != null) {
							final String title = NJ.usename ? (NJ.imagename+"-snapshot") : (NJ.NAME+": Snapshot");
							final ImagePlus ssimp = new ImagePlus(title,cp);
							ssimp.show(); ssimp.updateAndRepaintWindow();
							IJ.showStatus("Generated snapshot image");
						} else NJ.copyright();
					}
				}
				setPreviousTool();
				break;
			}
			case HELP: {
				try {
					NJ.log("Opening default browser showing online "+NJ.NAME+" manual");
					BrowserLauncher.openURL("http://www.imagescience.org/meijering/software/neuronj/manual/");
				} catch (Throwable throwable) {
					NJ.error("Could not open default internet browser");
				}
				setPreviousTool();
				break;
			}
			case QUIT: {
				final YesNoDialog ynd = new YesNoDialog("Quit","Do you really want to quit "+NJ.NAME+"?");
				if (ynd.yesPressed()) NJ.quit();
				else setPreviousTool();
				break;
			}
			case LOAD: {
				final FileDialog fdg = new FileDialog(IJ.getInstance(),NJ.NAME+": Load",FileDialog.LOAD);
				fdg.setFilenameFilter(new ImageDataFilter());
				fdg.setVisible(true);
				final String dir = fdg.getDirectory();
				final String file = fdg.getFile();
				fdg.dispose();
				if (dir != null && file != null) {
					final String ext = file.substring(file.lastIndexOf(".")+1);
					if (ext.equalsIgnoreCase("ndf")) {
						if (!NJ.image) NJ.noImage();
						else NJ.nhd.loadTracings(dir,file);
					} else {
						final boolean bLoaded = loadImage(dir,file);
						if (bLoaded) {
							final File images = new File(NJ.workdir);
							NJ.workimages = images.list(new ImageFilter());
							if (NJ.workimages != null && NJ.workimages.length > 0) {
								NJ.log("Found "+NJ.workimages.length+" images in "+NJ.workdir);
								for (int i=0; i<NJ.workimages.length; ++i)
									if (NJ.workimages[i].equals(file)) {
										NJ.workimagenr = i;
										NJ.log("Loaded image is number "+NJ.workimagenr+" on the list");
										break;
									}
							}
						}
					}
				} else NJ.copyright();
				setPreviousTool();
				break;
			}
			case SAVE: {
				if (!NJ.image) NJ.noImage();
				else {
					final FileDialog fdg = new FileDialog(IJ.getInstance(),NJ.NAME+": Save",FileDialog.SAVE);
					fdg.setFilenameFilter(new ImageDataFilter());
					fdg.setFile(NJ.imagename+".ndf");
					fdg.setVisible(true);
					final String dir = fdg.getDirectory();
					final String file = fdg.getFile();
					fdg.dispose();
					if (dir != null && file != null) NJ.nhd.saveTracings(dir,file);
					else NJ.copyright();
				}
				setPreviousTool();
				break;
			}
			case EXPORT: {
				if (!NJ.image) NJ.noImage();
				else {
					final ExportDialog edg = new ExportDialog();
					if (!edg.wasCanceled()) {
						final FileDialog fdg = new FileDialog(IJ.getInstance(),NJ.NAME+": Export",FileDialog.SAVE);
						fdg.setFilenameFilter(new ImageDataFilter());
						fdg.setFile(NJ.imagename+".txt");
						fdg.setVisible(true);
						final String dir = fdg.getDirectory();
						final String file = fdg.getFile();
						fdg.dispose();
						if (dir != null && file != null) NJ.nhd.exportTracings(dir,file,edg.lastChoice());
						else NJ.copyright();
					} else {
						NJ.copyright();
					}
				}
				setPreviousTool();
				break;
			}
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	boolean loadImage(final String dir, final String file) {
		
		final String directory = dir.endsWith(File.separator) ? dir : dir+File.separator;
		final ImagePlus newImp = (new Opener()).openImage(directory,file);
		boolean bAccept = false;
		
		if (newImp != null) {
			
			NJ.log("Checking image "+directory+file);
			final int type = newImp.getType();
			if (type != ImagePlus.GRAY8 && type != ImagePlus.COLOR_256)
			NJ.error("Only 8-bit images are supported");
			else if (newImp.getStackSize() != 1)
			NJ.error("Image stacks are not supported");
			else if (newImp.getWidth() < 3)
			NJ.error("Image too small in x-dimension");
			else if (newImp.getHeight() < 3)
			NJ.error("Image too small in y-dimension");
			else bAccept = true;
			
			if (bAccept) {
				NJ.log("Image accepted");
				if (NJ.image) {
					NJ.nhd.closeTracings();
					// Close image but first restore listeners to avoid
					// a call to windowClosed():
					restoreListeners();
					NJ.log("Closing current image...");
					imp.hide();
				}
				NJ.workdir = directory;
				NJ.image(newImp);
				imp = newImp; imp.show();
				imw = imp.getWindow();
				imw.addWindowListener(this);
				NJ.nhd.attach(imp);
				IJ.showStatus("Loaded image from "+directory+file);
				iPreviousTool = MAGNIFY;
				final String ndfile = NJ.imagename + ".ndf";
				final File ndf = new File(directory + ndfile);
				if (ndf.exists()) {
					NJ.log("Data file exists for loaded image");
					NJ.nhd.loadTracings(directory,ndfile);
				} else {
					NJ.log("Found no data file for loaded image");
					if (NJ.adg != null) NJ.adg.reset();
				}
				NJ.save = false;
			} else {
				NJ.log("Image not accepted");
				NJ.copyright();
			}
		} else {
			NJ.error("Unable to load image");
			NJ.copyright();
		}
		
		return bAccept;
	}
	
	void restoreListeners() { imw.removeWindowListener(this); }
	
	void restoreToolbar() {
		
		NJ.log("Restoring toolbar");
		final Container container = this.getParent();
		final Component component[] = container.getComponents();
		for (int i=0; i<component.length; ++i) {
			if (component[i] == this) {
				container.remove(this);
				container.add(previousToolbar, i);
				container.validate();
				break;
			}
		}
		previousToolbar.repaint();
	}
	
	public void mouseReleased(final MouseEvent e) {}
	
	public void mouseDragged(final MouseEvent e) {}
	
	public void mouseMoved(final MouseEvent e) { try {
		
		final int x = e.getX();
		for (int i=0; i<NUM_TOOLS; ++i)
			if (x > i*SIZE && x < (i+1)*SIZE) {
				showMessage(i);
				break;
			}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void paint(final Graphics g) {
		
		for (int i=0; i<NUM_TOOLS; ++i) drawButton(g,i);
	}
	
	private void setPreviousTool() {
		
		// To avoid having to reopen the attributes or measurements dialogs:
		if (iPreviousTool == ATTRIBS || iPreviousTool == MEASURE) setTool(MAGNIFY);
		else setTool(iPreviousTool);
	}
	
	void resetTool() { setTool(MAGNIFY); }
	
	void setTool(int tool) {
		
		// Check validity:
		if (tool < 0 ||
			tool == SPACE1 ||
			tool == SPACE2 ||
			tool > LAST_TOOL ||
			tool == iCurrentTool)
			return;
		
		// Reset current tool:
		down[iCurrentTool] = false;
		down[tool] = true;
		final Graphics g = this.getGraphics();
		drawButton(g,iCurrentTool);
		drawButton(g,tool);
		g.dispose();
		iPreviousTool = iCurrentTool;
		iCurrentTool = tool;
		if (iCurrentTool != ATTRIBS && NJ.adg != null) NJ.adg.close();
		if (iCurrentTool != MEASURE && NJ.mdg != null) NJ.mdg.close();
		
		// Adapt cursor to current tool:
		if (NJ.image) switch (iCurrentTool) {
			case ADD: NJ.nhd.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR)); break;
			case DELETE:
			case MOVE:
			case MEASURE: NJ.nhd.setCursor(new Cursor(Cursor.DEFAULT_CURSOR)); break;
			case MAGNIFY: NJ.nhd.setCursor(new Cursor(Cursor.MOVE_CURSOR)); break;
			case SCROLL: NJ.nhd.setCursor(new Cursor(Cursor.HAND_CURSOR)); break;
			default: NJ.nhd.setCursor(new Cursor(Cursor.DEFAULT_CURSOR)); break;
		}
	}
	
	private void drawButton(final Graphics g, final int tool) {
		
		if (tool==SPACE1 || tool==SPACE2) return;
		
		fill3DRect(g, tool*SIZE + 1, 1, SIZE, SIZE - 1, !down[tool]);
		int x = tool*SIZE + OFFSET;
		int y = OFFSET;
		this.g = g;
		
		switch (tool) {
			case ERASE:
				xOffset = x + 1;
				yOffset = y;
				g.setColor(Color.black);
				m(0,0); d(7,0); d(10,3); d(10,12); d(0,12); d(0,0);
				m(7,0); d(7,3); d(10,3);
				g.setColor(Color.white);
				m(8,2); d(8,2);
				for (int i=1; i<=3; ++i) { m(1,i); d(6,i); }
				for (int i=4; i<=11; ++i) { m(1,i); d(9,i); }
				break;
			case LOAD:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(7,1); d(8,0); d(10,0); d(13,3); d(13,1); m(13,3); d(11,3);
				m(0,4); d(1,3); d(3,3); d(4,4); d(9,4); d(9,7); d(5,7); d(0,12); d(0,4);
				m(9,7); d(13,7); d(8,12); d(0,12);
				g.setColor(Color.yellow.darker());
				m(1,4); d(3,4);
				m(1,5); d(8,5);
				m(1,6); d(8,6);
				m(1,7); d(4,7);
				m(1,8); d(3,8);
				m(1,9); d(2,9);
				m(1,10); d(1,10);
				g.setColor(evenDarker);
				m(5,8); d(11,8);
				m(4,9); d(10,9);
				m(3,10); d(9,10);
				m(2,11); d(8,11);
				break;
			case SAVE:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(0,0); d(13,0); d(13,12); d(1,12); d(0,11); d(0,0);
				m(2,0); d(2,5); d(3,6); d(10,6); d(11,5); d(11,0);
				m(11,2); d(13,2);
				m(3,12); d(3,8); d(11,8); d(11,12);
				m(3,9); d(8,9);
				m(3,10); d(8,10);
				m(3,11); d(8,11);
				g.setColor(Color.red.darker());
				m(1,1); d(1,11);
				m(2,6); d(2,11);
				m(2,7); d(11,7);
				m(11,6); d(11,7);
				m(12,3); d(12,11);
				break;
			case EXPORT:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(7,0); d(13,0); d(13,12); d(1,12); d(0,11); d(0,6); d(3,6);
				m(5,6); d(10,6); d(11,5); d(11,0);
				m(11,2); d(13,2);
				m(3,12); d(3,8); d(11,8); d(11,12);
				m(3,9); d(8,9);
				m(3,10); d(8,10);
				m(3,11); d(8,11);
				m(0,0); d(0,4); m(1,0); d(1,4); m(2,0); d(2,4); m(3,0); d(3,4);
				m(4,-1); d(4,5); m(5,0); d(5,4); m(6,1); d(6,3); d(7,2);
				g.setColor(Color.green.darker());
				m(1,7); d(1,11);
				m(2,7); d(2,11);
				m(2,7); d(11,7);
				m(11,6); d(11,7);
				m(12,3); d(12,11);
				break;
			case ADD:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.green.darker().darker().darker());
				m(0,4); d(2,4); d(2,3); d(4,3); d(4,2); d(6,2); d(6,1); d(8,1); d(8,0); d(9,0);
				m(3,12); d(3,10); d(4,10); d(4,9); d(5,9); d(5,8); d(6,8); d(6,7); d(7,7); d(7,6);
				d(8,6); d(8,5); d(9,5); d(9,4); d(11,4); d(11,3); d(13,3);
				g.setColor(Color.black);
				m(9,10); d(13,10); m(11,8); d(11,12);
				break;
			case DELETE:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.green.darker().darker().darker());
				m(0,4); d(2,4); d(2,3); d(4,3); d(4,2); d(6,2); d(6,1); d(8,1); d(8,0); d(9,0);
				m(3,12); d(3,10); d(4,10); d(4,9); d(5,9); d(5,8); d(6,8); d(6,7); d(7,7); d(7,6);
				d(8,6); d(8,5); d(9,5); d(9,4); d(11,4); d(11,3); d(13,3);
				g.setColor(Color.black);
				m(9,10); d(13,10);
				break;
			case MOVE:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.green.darker().darker().darker());
				m(13,0); d(10,0); d(10,1); d(8,1); d(8,2); d(4,2); d(3,3); d(7,3); d(7,4);
				d(3,4); d(3,5); d(7,5); d(6,6); d(2,6); d(2,7); d(1,7); d(1,9); d(0,9); d(0,12);
				g.setColor(Color.black);
				m(9,7); d(9,12); d(10,11); d(10,8); d(11,9); d(11,10); d(12,10);
				break;
			case MEASURE:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.green.darker().darker().darker());
				m(0,0); d(1,0); d(1,1); d(2,1); m(2,2); d(7,2); d(7,1); d(8,1); d(8,0); d(9,0);
				m(0,4); d(1,4); d(1,5); d(3,5); m(3,6); d(8,6); d(8,5); d(10,5); d(10,4); d(11,4); d(11,3);
				d(12,3); d(12,2); d(13,2); m(13,1);
				g.setColor(Color.black);
				m(0,10); d(2,8); d(2,12); d(0,10); d(13,10); d(11,8); d(11,12); d(13,10);
				break;
			case ATTRIBS:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.red);
				for (int i=0; i<=5; ++i) { m(0,i); d(5,i); }
				g.setColor(Color.yellow);
				for (int i=0; i<=5; ++i) { m(7,i); d(12,i); }
				g.setColor(Color.green);
				for (int i=7; i<=12; ++i) { m(0,i); d(5,i); }
				g.setColor(Color.blue);
				for (int i=7; i<=12; ++i) { m(7,i); d(12,i); }
				g.setColor(Color.black);
				m(0,0); d(5,0); d(5,5); d(0,5); d(0,0);
				m(7,0); d(12,0); d(12,5); d(7,5); d(7,0);
				m(0,7); d(5,7); d(5,12); d(0,12); d(0,7);
				m(7,7); d(12,7); d(12,12); d(7,12); d(7,7);
				break;
			case PARAMS:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(0,0); d(5,0); d(5,5); d(0,5); d(0,0);
				m(0,7); d(5,7); d(5,12); d(0,12); d(0,7);
				m(7,2); d(13,2); m(7,4); d(13,4);
				m(7,9); d(13,9); m(7,11); d(13,11);
				g.setColor(Color.white);
				m(1,1); d(4,1); m(1,2); d(4,2); m(1,3); d(4,3); m(1,4); d(4,4);
				m(1,8); d(4,8); m(1,9); d(4,9); m(1,10); d(4,10); m(1,11); d(4,11);
				break;
			case SNAPSHOT:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(1,1); d(12,1); d(13,2); d(13,11); d(12,12); d(1,12); d(0,11); d(0,2);
				m(7,0); d(12,0); d(11,-1); d(8,-1);
				m(5,4); d(8,4); d(8,5); d(9,5); d(9,8); d(8,8); d(8,9); d(5,9); d(5,8); d(4,8); d(4,5); d(5,5);
				g.setColor(Color.white);
				m(9,0); d(10,0);
				m(6,5); d(6,8); m(7,5); d(7,8); m(5,6); d(5,7); m(8,6); d(8,7);
				g.setColor(evenDarker);
				m(1,2); d(12,2); d(12,11); d(1,11); d(1,3); d(11,3); d(11,10); d(2,10); d(2,4);
				m(4,4); d(3,4); d(3,9); d(4,9); m(9,4); d(10,4); d(10,9); d(9,9);
				break;
			case MAGNIFY:
				xOffset = x;
				yOffset = y;
				g.setColor(Color.black);
				m(0,3); d(3,0); d(5,0); d(8,3); d(8,5); d(5,8); d(3,8); d(0,5); d(0,3);
				m(1,1); d(1,1); m(7,1); d(7,1); m(1,7); d(1,7);
				m(8,7); d(12,11); m(7,8); d(11,12); m(7,7); d(12,12);
				g.setColor(Color.white);
				m(3,1); d(5,1);
				m(2,2); d(6,2);
				m(1,3); d(7,3);
				m(1,4); d(7,4);
				m(1,5); d(7,5);
				m(2,6); d(6,6);
				m(3,7); d(5,7);
				g.setColor(Color.black);
				m(4,6); d(5,6); d(6,5); d(6,4);
				break;
			case SCROLL:
				xOffset = x-1;
				yOffset = y-1;
				g.setColor(Color.black);
				m(2,1); d(3,1); d(4,2); d(4,3); d(5,3); d(5,5); d(5,1); d(6,0); d(7,0); d(8,1); d(8,5);
				m(9,1); d(10,1); d(11,2); d(11,6); m(12,4); d(13,3); d(14,4); d(14,7); d(13,8); d(13,10); d(12,11); d(12,12); d(11,13);
				m(4,13); d(3,12); d(2,11); d(1,10); d(0,9); d(0,8); d(1,7); d(2,7); d(3,8); d(3,6); d(2,5); d(2,4); d(1,3); d(1,2);
				g.setColor(Color.white);
				m(2,2); d(3,2); d(3,3); d(2,3); d(3,4); d(4,4); d(4,5); d(3,5);
				m(6,1); d(6,5); d(7,5); d(7,1);
				m(9,2); d(9,5); d(10,5); d(10,2);
				m(12,6); d(12,5); d(13,4); d(13,6);
				m(4,6); d(10,6); m(4,7); d(13,7); m(4,8); d(12,8);
				m(2,8); d(1,8); d(1,9); d(12,9); d(12,10); d(2,10); d(3,11); d(11,11); d(11,12); d(4,12); d(5,13); d(10,13);
				break;
			case HELP:
				xOffset = x;
				yOffset = y-1;
				g.setColor(Color.black);
				m(2,3); d(5,0); d(8,0); d(11,3); d(11,5); d(7,9); d(7,10); d(6,11); d(7,12); d(6,13); d(5,13);
				d(4,12); d(5,11); d(4,10); d(4,9); d(8,5); d(8,4); d(7,3); d(6,3); d(4,5); d(3,5); d(2,4);
				g.setColor(Color.white);
				m(5,1); d(8,1); m(4,2); d(9,2); m(3,3); d(5,3); m(3,4); d(4,4);
				m(8,3); d(10,3); m(9,4); d(10,4); m(9,5); d(5,9); d(5,10); d(6,10); d(6,9); d(10,5);
				m(5,12); d(6,12);
				break;
			case QUIT:
				xOffset = x;
				yOffset = y-1;
				g.setColor(Color.black);
				m(0,0); d(8,0); d(8,9); d(5,9); d(5,5); d(0,0); d(0,8); d(5,13); d(5,9);
				m(11,5); d(11,5); m(10,6); d(12,6); m(9,7); d(13,7);
				m(10,8); d(12,8); m(10,9); d(12,9); m(10,10); d(12,10);
				m(9,11); d(11,11); m(8,12); d(10,12); m(7,13); d(8,13);
				g.setColor(Color.white);
				m(2,1); d(7,1); m(3,2); d(7,2); m(4,3); d(7,3); m(5,4); d(7,4);
				m(6,5); d(6,8); m(7,5); d(7,8);
				g.setColor(evenDarker);
				m(1,2); d(1,8); m(2,3); d(2,9); m(3,4); d(3,10); m(4,5); d(4,11);
				break;
		}
	}
	
	private void fill3DRect(
		final Graphics g,
		final int x,
		final int y,
		final int width,
		final int height,
		final boolean raised
	) {
		
		if (raised) g.setColor(gray);
		else g.setColor(darker);
		
		g.fillRect(x + 1, y + 1, width - 2, height - 2);
		g.setColor(raised ? brighter : evenDarker);
		g.drawLine(x, y, x, y + height - 1);
		g.drawLine(x + 1, y, x + width - 2, y);
		g.setColor((raised) ? (evenDarker) : (brighter));
		g.drawLine(x + 1, y + height - 1, x + width - 1, y + height - 1);
		g.drawLine(x + width - 1, y, x + width - 1, y + height - 2);
	}
	
	private void m(final int x, final int y) {
		
		this.x = xOffset + x;
		this.y = yOffset + y;
	}
	
	private void d(int x, int y) {
		
		x += xOffset;
		y += yOffset;
		g.drawLine(this.x, this.y, x, y);
		this.x = x;
		this.y = y;
	}
	
	private void showMessage (final int tool) {
		
		switch (tool) {
			case ERASE: IJ.showStatus("Erase tracings"); break;
			case LOAD: IJ.showStatus("Load image/tracings"); break;
			case SAVE: IJ.showStatus("Save tracings"); break;
			case EXPORT: IJ.showStatus("Export tracings"); break;
			case ADD: IJ.showStatus("Add tracings"); break;
			case DELETE: IJ.showStatus("Delete tracings"); break;
			case MOVE: IJ.showStatus("Move vertices"); break;
			case MEASURE: IJ.showStatus("Measure tracings"); break;
			case ATTRIBS: IJ.showStatus("Label tracings"); break;
			case PARAMS: IJ.showStatus("Set parameters"); break;
			case SNAPSHOT: IJ.showStatus("Make snapshot"); break;
			case MAGNIFY: IJ.showStatus("Zoom in/out"); break;
			case SCROLL: IJ.showStatus("Scroll canvas"); break;
			case HELP: IJ.showStatus("Open online manual"); break;
			case QUIT: IJ.showStatus("Quit "+NJ.NAME); break;
			default: NJ.copyright(); break;
		}
	}
	
	public void windowActivated(WindowEvent e) { }
	
	public void windowClosed(WindowEvent e) { try {
		
		NJ.log("Image window closed by user");
		NJ.nhd.closeTracings();
		NJ.image(null);
		NJ.nhd.resetTracings();
		if (NJ.adg != null) NJ.adg.reset();
		resetTool();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowClosing(WindowEvent e) { }
	
	public void windowDeactivated(WindowEvent e) { }
	
	public void windowDeiconified(WindowEvent e) { }
	
	public void windowIconified(WindowEvent e) { }
	
	public void windowOpened(WindowEvent e) { }
	
}

// ***************************************************************************
final class TracingHandler extends Roi implements KeyListener, MouseListener, MouseMotionListener {
	
	private ImagePlus imp;
	private ImageCanvas imc;
	private ImageWindow imw;
	ByteProcessor ipgray;
	
	private final Dijkstra dijkstra = new Dijkstra();
	private float[][][] costs;
	private byte[][] dirsimage;
	
	private final Point clckPoint = new Point();
	private final Point currPoint = new Point();
	private final Point mousPoint = new Point();
	private final Point snapPoint = new Point();
	private final Point scrlPoint = new Point();
	private final Point movePoint = new Point();
	private final Point zoomPoint = new Point();
	
	private Tracings tracings = new Tracings();
	private Tracing currTracing;
	private Segment currSegment = new Segment();
	private Segment ssmpSegment = new Segment();
	private Point currVertex;
	
	private boolean bTracingActive;
	private boolean bManualTracing;
	private boolean bSnapCursor;
	private boolean bSmoothSegment;
	private boolean bComputedCosts;
	private boolean bDijkstra;
	private boolean bOnCanvas = false;
	
	private int iXSize, iYSize;
	
	private long lastClckTime = System.currentTimeMillis();
	
	TracingHandler() { super(0,0,1,1); }
	
	void attach(final ImagePlus impNew) {
		
		// Copy handles:
		imp = impNew;
		imw = imp.getWindow();
		imc = imw.getCanvas();
		
		// Create a copy that is surely a gray-scale image (the pixels
		// are already of type byte, but may represent color indices,
		// not actual gray-values):
		NJ.log("Creating gray-scale copy of new image...");
		iXSize = imp.getWidth(); iYSize = imp.getHeight();
		final ByteProcessor ipIn = (ByteProcessor)imp.getProcessor();
		final IndexColorModel icm = (IndexColorModel)ipIn.getColorModel();
		final int iMapSize = icm.getMapSize();
		final byte[] r = new byte[iMapSize]; icm.getReds(r);
		final byte[] g = new byte[iMapSize]; icm.getGreens(g);
		final byte[] b = new byte[iMapSize]; icm.getBlues(b);
		try {
			ipgray = new ByteProcessor(iXSize,iYSize);
			final byte[] g8pxs = (byte[])ipgray.getPixels();
			final byte[] inpxs = (byte[])ipIn.getPixels();
			final int nrpxs = inpxs.length;
			for (int i=0; i<nrpxs; ++i) {
				final int index = inpxs[i]&0xFF;
				g8pxs[i] = (byte)FMath.round((r[index]&0xFF)*0.3 + (g[index]&0xFF)*0.6 + (b[index]&0xFF)*0.1);
			}
		} catch (OutOfMemoryError e) {
			NJ.outOfMemory();
			ipgray = null;
		}
		
		// Remove and add listeners from and to canvas:
		NJ.log("Detaching ImageJ listeners...");
		imw.removeKeyListener(IJ.getInstance());
		imc.removeKeyListener(IJ.getInstance());
		imc.removeMouseListener(imc);
		imc.removeMouseMotionListener(imc);
		NJ.log("Attaching "+NJ.NAME+" listeners...");
		imw.addKeyListener(this);
		imc.addKeyListener(this);
		imc.addMouseListener(this);
		imc.addMouseMotionListener(this);
		NJ.log("Done");
		
		// Reset variables:
		costs = null;
		dirsimage = null;
		tracings.reset();
		Tracing.resetID();
		currSegment.reset();
		ssmpSegment.reset();
		currPoint.setLocation(-100,-100);
		zoomPoint.setLocation(0,0);
		bTracingActive = false;
		bManualTracing = false;
		bSnapCursor = true;
		bSmoothSegment = true;
		bComputedCosts = false;
		bDijkstra = false;
		
		// Enable displaying tracings:
		ic = null; // Work-around to prevent cloning in imp.setRoi()
		imp.setRoi(this);
	}
	
	void computeCosts() {
		final Costs ci = new Costs();
		final long lStartTime = System.currentTimeMillis();
		try {
			if (ipgray != null) costs = ci.run(ipgray,(NJ.appear==0),NJ.scale);
			else throw new OutOfMemoryError();
			NJ.log("Finished in "+(System.currentTimeMillis()-lStartTime)+" ms");
			bComputedCosts = true;
		} catch (OutOfMemoryError e) {
			NJ.outOfMemory();
			NJ.ntb.resetTool();
		}
		NJ.copyright();
	}
	
	boolean computedCosts() { return bComputedCosts; }
	
	void doDijkstra() {
		if (bTracingActive) bDijkstra = true;
	}
	
	Tracings tracings() { return tracings; }
	
	void redraw() { imc.repaint(); }
	
	public void draw(final Graphics g) { try {
		
		// Set stroke:
		if (g instanceof Graphics2D) ((Graphics2D)g).setStroke(NJ.tracestroke);
		
		// Draw finished tracings:
		tracings.draw(g,imc);
		
		// Draw currently active tracing and segment:
		if (bTracingActive) {
			currTracing.draw(g,imc);
			currSegment.draw(g,imc,NJ.ACTIVECOLOR);
		}
		
		final double mag = imc.getMagnification();
		final int ihalfmag = (int)(mag/2.0);
		
		if (currVertex != null) {
			g.setColor(NJ.HIGHLIGHTCOLOR);
			final int csx = imc.screenX(currVertex.x) + ihalfmag;
			final int csy = imc.screenY(currVertex.y) + ihalfmag;
			final int width = 3*NJ.linewidth;
			g.fillOval(csx-width/2,csy-width/2,width,width);
		}
		
		// Draw currPoint cursor:
		if (NJ.ntb.currentTool() == TracingToolbar.ADD && bOnCanvas) {
			if (g instanceof Graphics2D) ((Graphics2D)g).setStroke(NJ.crossstroke);
			g.setColor(Color.red);
			final int csx = imc.screenX(currPoint.x) + ihalfmag;
			final int csy = imc.screenY(currPoint.y) + ihalfmag;
			g.drawLine(csx,csy-5,csx,csy+5);
			g.drawLine(csx-5,csy,csx+5,csy);
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	// Note that this method is called only if imp is not null.
	public ColorProcessor makeSnapshot(final boolean snapshotimage, final boolean snapshottracings) {
		
		ColorProcessor cp = null;
		
		if (snapshotimage || snapshottracings) try {
			NJ.log("Creating snapshot image");
			iXSize = imp.getWidth(); iYSize = imp.getHeight();
			cp = new ColorProcessor(iXSize,iYSize);
			cp.setLineWidth(NJ.linewidth);
			if (snapshotimage) {
				final ByteProcessor bp = (ByteProcessor)imp.getProcessor();
				final IndexColorModel icm = (IndexColorModel)bp.getColorModel();
				final int iMapSize = icm.getMapSize();
				final byte[] r = new byte[iMapSize]; icm.getReds(r);
				final byte[] g = new byte[iMapSize]; icm.getGreens(g);
				final byte[] b = new byte[iMapSize]; icm.getBlues(b);
				final byte[] bpxs = (byte[])bp.getPixels();
				final int[] cpxs = (int[])cp.getPixels();
				final int nrpxs = bpxs.length;
				for (int i=0; i<nrpxs; ++i) {
					final int index = bpxs[i]&0xFF;
					cpxs[i] = ((r[index]&0xFF)<<16) | ((g[index]&0xFF)<<8) | (b[index]&0xFF);
				}
			}
			if (snapshottracings) {
				// Draw finished tracings:
				final Point spnt = new Point();
				final Point epnt = new Point();
				final int nrt = tracings.nrtracings();
				for (int t=0; t<nrt; ++t) {
					final Tracing tracing = tracings.get(t);
					final int nrs = tracing.nrsegments();
					cp.setColor(NJ.typecolors[tracing.type()]);
					for (int s=0; s<nrs; ++s) {
						final Segment segment = tracing.get(s);
						final int nrp = segment.nrpoints();
						if (nrp > 1) for (int p=1; p<nrp; ++p) {
							segment.get(p-1,spnt);
							segment.get(p,epnt);
							cp.drawLine(spnt.x,spnt.y,epnt.x,epnt.y);
						}
					}
				}
				// Draw currently active tracing and segment:
				if (bTracingActive) {
					// Draw current tracing:
					final Tracing tracing = currTracing;
					final int nrs = tracing.nrsegments();
					cp.setColor(NJ.typecolors[tracing.type()]);
					for (int s=0; s<nrs; ++s) {
						final Segment segment = tracing.get(s);
						final int nrp = segment.nrpoints();
						if (nrp > 1) for (int p=1; p<nrp; ++p) {
							segment.get(p-1,spnt);
							segment.get(p,epnt);
							cp.drawLine(spnt.x,spnt.y,epnt.x,epnt.y);
						}
					}
					// Draw current segment:
					cp.setColor(NJ.ACTIVECOLOR);
					final Segment segment = currSegment;
					final int nrp = segment.nrpoints();
					if (nrp > 1) for (int p=1; p<nrp; ++p) {
						segment.get(p-1,spnt);
						segment.get(p,epnt);
						cp.drawLine(spnt.x,spnt.y,epnt.x,epnt.y);
					}
				}
			}
			
		} catch (OutOfMemoryError e) {
			NJ.outOfMemory();
		}
		
		return cp;
	}
	
	public void keyPressed(final KeyEvent e) { try {
		
		final int iKeyCode = e.getKeyCode();
		
		if (iKeyCode == KeyEvent.VK_C && costs != null && NJ.hkeys) {
			try {
				NJ.log("Showing tracing cost image");
				final ByteProcessor ip = new ByteProcessor(iXSize,iYSize);
				final byte[] pixels = (byte[])ip.getPixels();
				for (int y=0, i=0; y<iYSize; ++y)
					for (int x=0; x<iXSize; ++x, ++i)
						pixels[i] = (byte)costs[0][y][x];
				final String title = NJ.usename ? (NJ.imagename+"-costs") : (NJ.NAME+": Costs");
				final ImagePlus tmp = new ImagePlus(title,ip);
				tmp.show(); tmp.updateAndRepaintWindow();
			} catch (OutOfMemoryError error) {
				NJ.outOfMemory();
			}
		} else if (iKeyCode == KeyEvent.VK_D && dirsimage != null && NJ.hkeys) {
			try {
				NJ.log("Showing local directions image");
				final ByteProcessor ip = new ByteProcessor(iXSize,iYSize);
				final byte[] pixels = (byte[])ip.getPixels();
				for (int y=0, i=0; y<iYSize; ++y)
					for (int x=0; x<iXSize; ++x, ++i)
						pixels[i] = (byte)(31*(0xFF & dirsimage[y][x]));
				final String title = NJ.usename ? (NJ.imagename+"-directions") : (NJ.NAME+": Directions");
				final ImagePlus tmp = new ImagePlus(title,ip);
				tmp.show(); tmp.updateAndRepaintWindow();
			} catch (OutOfMemoryError error) {
				NJ.outOfMemory();
			}
		} else if (iKeyCode == KeyEvent.VK_V && costs != null && NJ.hkeys) {
			try {
				NJ.log("Showing local vectors image");
				final ByteProcessor ip = new ByteProcessor(iXSize,iYSize);
				final byte[] pixels = (byte[])ip.getPixels();
				for (int y=0, i=0; y<iYSize; ++y)
					for (int x=0; x<iXSize; ++x, ++i)
						pixels[i] = (byte)(255.0f - costs[0][y][x]);
				final String title = NJ.usename ? (NJ.imagename+"-vectors") : (NJ.NAME+": Vectors");
				final ImagePlus tmp = new ImagePlus(title,ip);
				tmp.show(); tmp.updateAndRepaintWindow();
				final VectorField vf = new VectorField(tmp,costs);
			} catch (OutOfMemoryError error) {
				NJ.outOfMemory();
			}
		} else if (iKeyCode == KeyEvent.VK_CONTROL && bSnapCursor) {
			NJ.log("Switching off local snapping");
			currPoint.x = mousPoint.x;
			currPoint.y = mousPoint.y;
			bSnapCursor = false;
			if (bTracingActive) updateCurrSegment();
			redraw();
		} else if (iKeyCode == KeyEvent.VK_SHIFT && !bManualTracing) {
			NJ.log("Switching to manual tracing mode");
			bManualTracing = true;
			if (bTracingActive) updateCurrSegment();
			redraw();
		} else if (iKeyCode == KeyEvent.VK_S && bSmoothSegment && NJ.hkeys) {
			NJ.log("Disabling segment smoothing");
			bSmoothSegment = false;
			if (bTracingActive) updateCurrSegment();
			redraw();
		} else if ((iKeyCode == KeyEvent.VK_TAB || iKeyCode == KeyEvent.VK_SPACE) && bTracingActive) {
			NJ.log("Finishing current tracing");
			finishCurrSegment();
			finishCurrTracing();
			redraw();
		} else if (iKeyCode == KeyEvent.VK_LEFT && NJ.workimages != null) {
			if (NJ.workimagenr <= 0)
			NJ.notify("The current image is the first image");
			else {
				--NJ.workimagenr;
				NJ.log("Request to load image "+NJ.workdir+NJ.workimages[NJ.workimagenr]);
				NJ.ntb.loadImage(NJ.workdir,NJ.workimages[NJ.workimagenr]);
				NJ.ntb.resetTool();
			}
		} else if (iKeyCode == KeyEvent.VK_RIGHT && NJ.workimages != null) {
			if (NJ.workimagenr >= NJ.workimages.length-1)
			NJ.notify("The current image is the last image");
			else {
				++NJ.workimagenr;
				NJ.log("Request to load image "+NJ.workdir+NJ.workimages[NJ.workimagenr]);
				NJ.ntb.loadImage(NJ.workdir,NJ.workimages[NJ.workimagenr]);
				NJ.ntb.resetTool();
			}
		} else if (iKeyCode == KeyEvent.VK_ADD || iKeyCode == KeyEvent.VK_EQUALS) {
			imc.zoomIn(zoomPoint.x,zoomPoint.y);
			showValue(imc.offScreenX(zoomPoint.x),imc.offScreenY(zoomPoint.y));
		} else if (iKeyCode == KeyEvent.VK_MINUS || iKeyCode == KeyEvent.VK_SUBTRACT) {
			imc.zoomOut(zoomPoint.x,zoomPoint.y);
			showValue(imc.offScreenX(zoomPoint.x),imc.offScreenY(zoomPoint.y));
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyReleased(final KeyEvent e) { try {
		
		final int iKeyCode = e.getKeyCode();
		
		if (iKeyCode == KeyEvent.VK_CONTROL) {
			NJ.log("Switching on local snapping");
			currPoint.x = snapPoint.x;
			currPoint.y = snapPoint.y;
			bSnapCursor = true;
			if (bTracingActive) updateCurrSegment();
			redraw();
		} else if (iKeyCode == KeyEvent.VK_SHIFT) {
			NJ.log("Back to automatic tracing mode");
			bManualTracing = false;
			if (bTracingActive) updateCurrSegment();
			redraw();
		} else if (iKeyCode == KeyEvent.VK_S) {
			NJ.log("Enabling segment smoothing");
			bSmoothSegment = true;
			if (bTracingActive) updateCurrSegment();
			redraw();
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyTyped(final KeyEvent e) {}
	
	public void mouseClicked(final MouseEvent e) {}
	
	public void mouseDragged(final MouseEvent e) { try {
		
		final int x = e.getX();
		final int y = e.getY();
		final int osx = imc.offScreenX(x);
		final int osy = imc.offScreenY(y);
		
		showValue(osx,osy);
		
		switch (NJ.ntb.currentTool()) {
			case TracingToolbar.MOVE: {
				if (currVertex != null) {
					final int dx = osx - movePoint.x;
					final int dy = osy - movePoint.y;
					if (dx != 0 || dy != 0) {
						NJ.save = true;
						currVertex.x += dx;
						currVertex.y += dy;
						movePoint.x += dx;
						movePoint.y += dy;
						redraw();
					}
				}
				break;
			}
			case TracingToolbar.SCROLL: {
				scroll(x,y);
				break;
			}
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void mouseEntered(final MouseEvent e) { try {
		
		if (NJ.activate) {
			imw.toFront();
			imc.requestFocusInWindow();
		}
		
		zoomPoint.x = e.getX();
		zoomPoint.y = e.getY();
		
		bOnCanvas = true;
		redraw();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void mouseExited(final MouseEvent e) { try {
		
		NJ.copyright();
		bOnCanvas = false;
		redraw();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	private Point mouseMovedPoint = new Point();
	
	public void mouseMoved(final MouseEvent e) { try {
		
		zoomPoint.x = e.getX();
		zoomPoint.y = e.getY();
		
		final int x = imc.offScreenX(e.getX());
		final int y = imc.offScreenY(e.getY());
		
		showValue(x,y);
		
		switch (NJ.ntb.currentTool()) {
			case TracingToolbar.ADD: {
				final int prevMouseX = mousPoint.x;
				final int prevMouseY = mousPoint.y;
				mousPoint.x = x;
				mousPoint.y = y;
				
				// Move away from the border (the Dijkstra algorithm does
				// not allow the starting point to be on the border):
				if (mousPoint.x == 0) ++mousPoint.x;
				else if (mousPoint.x == iXSize-1) --mousPoint.x;
				if (mousPoint.y == 0) ++mousPoint.y;
				else if (mousPoint.y == iYSize-1) --mousPoint.y;
				
				// If the mouse point is still on the same pixel, there is
				// no need to do anything (this prevents superfluous screen
				// refreshments at zoom levels > 100%):
				if (prevMouseX != mousPoint.x || prevMouseY != mousPoint.y) {
					
					snapPoint.x = currPoint.x = mousPoint.x;
					snapPoint.y = currPoint.y = mousPoint.y;
					
					// Update directions map if necessary:
					if (bDijkstra) {
						bDijkstra = false;
						NJ.log("Computing shortest paths to clicked point...");
						IJ.showStatus("Computing optimal paths");
						final long lStartTime = System.currentTimeMillis();
						dirsimage = dijkstra.run(costs,clckPoint);
						NJ.log("Finished in "+(System.currentTimeMillis()-lStartTime)+" ms");
						NJ.copyright();
					}
					
					// Compute locally lowest cost point for snapping:
					int startx = mousPoint.x - NJ.snaprange; if (startx < 1) startx = 1;
					int starty = mousPoint.y - NJ.snaprange; if (starty < 1) starty = 1;
					int stopx = mousPoint.x + NJ.snaprange; if (stopx > iXSize-2) stopx = iXSize-2;
					int stopy = mousPoint.y + NJ.snaprange; if (stopy > iYSize-2) stopy = iYSize-2;
					for (int sy=starty; sy<=stopy; ++sy)
						for (int sx=startx; sx<=stopx; ++sx)
							if (costs[0][sy][sx] < costs[0][snapPoint.y][snapPoint.x]) {
								snapPoint.x = sx;
								snapPoint.y = sy;
							}
					
					// Snap if requested:
					if (bSnapCursor) {
						currPoint.x = snapPoint.x;
						currPoint.y = snapPoint.y;
					}
					
					if (bTracingActive) updateCurrSegment();
					
					// Draw:
					redraw();
				}
				break;
			}
			case TracingToolbar.MOVE: {
				final Point prevVertex = currVertex;
				currVertex = null;
				mouseMovedPoint.x = x;
				mouseMovedPoint.y = y;
				double mindist2 = Double.MAX_VALUE;
				final double NBR2 = 4*NJ.NEARBYRANGE*NJ.NEARBYRANGE;
				final int nrt = tracings.nrtracings();
				for (int t=0; t<nrt; ++t) {
					final Tracing tracing = tracings.get(t);
					final int nrs = tracing.nrsegments();
					for (int s=0; s<nrs; ++s) {
						final Segment segment = tracing.get(s);
						final int nrp = segment.nrpoints();
						for (int p=0; p<nrp; ++p) {
							final Point mpnt = segment.get(p);
							final double dx = mpnt.x - mouseMovedPoint.x;
							final double dy = mpnt.y - mouseMovedPoint.y;
							final double dist2 = dx*dx + dy*dy;
							if (dist2 < NBR2 && dist2 < mindist2) {
								currVertex = mpnt;
								mindist2 = dist2;
							}
						}
					}
				}
				if (currVertex != prevVertex) redraw();
				break;
			}
			case TracingToolbar.DELETE:
			case TracingToolbar.ATTRIBS: {
				mouseMovedPoint.x = x;
				mouseMovedPoint.y = y;
				final int nrt = tracings.nrtracings();
				int tmin = 0; double mindist2 = Double.MAX_VALUE;
				for (int t=0; t<nrt; ++t) {
					final Tracing tracing = tracings.get(t);
					tracing.highlight(false);
					final double dist2 = tracing.distance2(mouseMovedPoint);
					if (dist2 < mindist2) { mindist2 = dist2; tmin = t; }
				}
				final double NBR2 = NJ.NEARBYRANGE*NJ.NEARBYRANGE;
				if (mindist2 <= NBR2) tracings.get(tmin).highlight(true);
				else tmin = -1;
				if (tracings.changed()) {
					if (NJ.adg != null) NJ.adg.select(tmin+1);
					redraw();
				}
				break;
			}
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void mousePressed(final MouseEvent e) { try {
		
		final int x = e.getX();
		final int y = e.getY();
		
		switch (NJ.ntb.currentTool()) {
			case TracingToolbar.SCROLL: {
				scrlPoint.x = imc.offScreenX(x);
				scrlPoint.y = imc.offScreenY(y);
				break;
			}
			case TracingToolbar.MAGNIFY: {
				final int flags = e.getModifiers();
				if ((flags & (Event.ALT_MASK | Event.META_MASK | Event.CTRL_MASK)) != 0) imc.zoomOut(x,y);
				else imc.zoomIn(x,y);
				showValue(imc.offScreenX(x),imc.offScreenY(y));
				break;
			}
			case TracingToolbar.ADD: {
				final long currClckTime = System.currentTimeMillis();
				final int prevClckX = clckPoint.x;
				final int prevClckY = clckPoint.y;
				clckPoint.x = currPoint.x;
				clckPoint.y = currPoint.y;
				NJ.log("Clicked point ("+clckPoint.x+","+clckPoint.y+")");
				
				if (!bTracingActive) {
					currTracing = new Tracing();
					bTracingActive = true;
					bDijkstra = true;
				} else {
					finishCurrSegment();
					if ((currClckTime - lastClckTime < 500) && (prevClckX == clckPoint.x && prevClckY == clckPoint.y))
						finishCurrTracing();
				}
				
				lastClckTime = currClckTime;
				break;
			}
			case TracingToolbar.DELETE: {
				final int nrtracings = tracings.nrtracings();
				for (int w=0; w<nrtracings; ++w) {
					final Tracing tracing = tracings.get(w);
					if (tracing.highlighted()) {
						final YesNoDialog ynd =
						new YesNoDialog("Delete","Do you really want to delete this tracing?");
						if (ynd.yesPressed()) {
							NJ.log("Deleting tracing N"+tracing.id());
							tracings.remove(w);
							IJ.showStatus("Deleted tracing");
							if (NJ.adg != null) NJ.adg.reset();
						} else {
							tracing.highlight(false);
							NJ.copyright();
							if (NJ.adg != null) NJ.adg.select(0);
						}
						break;
					}
				}
				break;
			}
			case TracingToolbar.MOVE: {
				movePoint.x = imc.offScreenX(x);
				movePoint.y = imc.offScreenY(y);
				break;
			}
			case TracingToolbar.ATTRIBS: {
				final int nrtracings = tracings.nrtracings();
				for (int i=0; i<nrtracings; ++i) {
					final Tracing tracing = tracings.get(i);
					if (tracing.highlighted()) {
						if (!tracing.selected()) {
							NJ.log("Selecting tracing N"+tracing.id());
							tracing.select(true);
							IJ.showStatus("Selected tracing");
						} else {
							NJ.log("Deselecting tracing N"+tracing.id());
							tracing.select(false);
							tracing.highlight(false);
							if (NJ.adg != null) NJ.adg.select(0);
							IJ.showStatus("Deselected tracing");
						}
						break;
					}
				}
				break;
			}
		}
		
		redraw();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	private void finishCurrTracing() {
		
		if (currTracing.nrsegments() == 0) {
			NJ.log("Dumping tracing of zero units length");
			IJ.showStatus("Dumped tracing");
		} else {
			NJ.log("Adding tracing of length "+IJ.d2s(currTracing.length(),3)+" "+NJ.imageplus.getCalibration().getUnit());
			tracings.add(currTracing);
			if (NJ.adg != null) NJ.adg.reset();
			IJ.showStatus("Added tracing");
		}
		
		bTracingActive = false;
		bDijkstra = false;
		dirsimage = null;
	}
	
	private void finishCurrSegment() {
		
		if (currSegment.nrpoints() < 2) {
			NJ.log("Dumping segment of zero units length");
			IJ.showStatus("Dumped segment");
		} else {
			NJ.log("Adding segment of length "+IJ.d2s(currSegment.length(),3)+" "+NJ.imageplus.getCalibration().getUnit());
			currTracing.add(currSegment.duplicate());
			IJ.showStatus("Added segment");
		}
		
		currSegment.reset();
		bDijkstra = true;
	}
	
	private void updateCurrSegment() {
		
		currSegment.reset();
		
		if (currPoint.x != clckPoint.x || currPoint.y != clckPoint.y) {
			
			// Extract current segment from direction map:
			currSegment.add(new Point(currPoint));
			if (bManualTracing || dirsimage == null) {
				currSegment.add(new Point(clckPoint));
				currSegment.reverse();
			} else {
				final Point pnt = new Point(currPoint);
				while (pnt.x != clckPoint.x || pnt.y != clckPoint.y) {
					switch (dirsimage[pnt.y][pnt.x]) {
						case 0: { pnt.x = clckPoint.x; pnt.y = clckPoint.y; break; }
						case 1: { --pnt.x; --pnt.y; break; }
						case 2: { --pnt.y; break; }
						case 3: { ++pnt.x; --pnt.y; break; }
						case 4: { --pnt.x; break; }
						case 5: { ++pnt.x; break; }
						case 6: { --pnt.x; ++pnt.y; break; }
						case 7: { ++pnt.y; break; }
						case 8: { ++pnt.x; ++pnt.y; break; }
					}
					currSegment.add(new Point(pnt));
				}
				currSegment.reverse();
				// Smooth and subsample current segment:
				if (bSmoothSegment) smoothsample();
			}
		}
	}
	
	private void smoothsample() {
		
		// Copy current segment with borders:
		final Point pnt = new Point();
		currSegment.get(0,pnt);
		ssmpSegment.reset();
		for (int i=0; i<NJ.halfsmoothrange; ++i) { ssmpSegment.add(new Point(pnt)); }
		final int clckPoint = currSegment.nrpoints() - 1;
		for (int i=0; i<=clckPoint; ++i) { currSegment.get(i,pnt); ssmpSegment.add(new Point(pnt)); }
		for (int i=0; i<NJ.halfsmoothrange; ++i) { ssmpSegment.add(new Point(pnt)); }
		
		// Smooth and subsample except first and last point:
		int smppos = NJ.halfsmoothrange;
		ssmpSegment.get(smppos,pnt);
		currSegment.reset();
		currSegment.add(new Point(pnt));
		
		final float kernval = 1.0f/(2*NJ.halfsmoothrange + 1);
		final int lastsmp = clckPoint + NJ.halfsmoothrange;
		smppos += NJ.subsamplefactor;
		while (smppos < lastsmp) {
			ssmpSegment.get(smppos,pnt);
			float xpos = kernval*pnt.x;
			float ypos = kernval*pnt.y;
			for (int i=1; i<=NJ.halfsmoothrange; ++i) {
				ssmpSegment.get(smppos+i,pnt);
				xpos += kernval*pnt.x;
				ypos += kernval*pnt.y;
				ssmpSegment.get(smppos-i,pnt);
				xpos += kernval*pnt.x;
				ypos += kernval*pnt.y;
			}
			pnt.x = FMath.round(xpos);
			pnt.y = FMath.round(ypos);
			currSegment.add(new Point(pnt));
			smppos += NJ.subsamplefactor;
		}
		
		ssmpSegment.get(lastsmp,pnt);
		currSegment.add(new Point(pnt));
	}
	
	public void mouseReleased(final MouseEvent e) {}
	
	private void showValue(final int xp, final int yp) {
		final Calibration cal = NJ.imageplus.getCalibration();
		ipgray.setCalibrationTable(cal.getCTable());
		IJ.showStatus(
			"x="+IJ.d2s(xp*cal.pixelWidth,2)+" ("+xp+"), "+
			"y="+IJ.d2s(yp*cal.pixelHeight,2)+" ("+yp+"), "+
			"value="+IJ.d2s(ipgray.getPixelValue(xp,yp),2)+" ("+ipgray.getPixel(xp,yp)+")"
		);
	}
	
	private void scroll(final int x, final int y) {
		
		final Rectangle vofRect = imc.getSrcRect();
		final double mag = imc.getMagnification();
		int newx = scrlPoint.x - (int)(x/mag);
		int newy = scrlPoint.y - (int)(y/mag);
		if (newx < 0) newx = 0;
		if (newy < 0) newy = 0;
		if ((newx + vofRect.width) > iXSize) newx = iXSize - vofRect.width;
		if ((newy + vofRect.height) > iYSize) newy = iYSize - vofRect.height;
		vofRect.x = newx;
		vofRect.y = newy;
		imp.draw();
		Thread.yield();
	}
	
	void eraseTracings() { tracings.reset(); redraw(); }
	
	void resetTracings() { tracings.reset(); }
	
	void setCursor(final Cursor c) { imc.setCursor(c); }
	
	void loadTracings(final String dir, final String file) {
		
		final String path = (dir.endsWith(File.separator) ? dir : dir+File.separator) + file;
		NJ.log("Loading tracings from "+path);
		
		try {
			final BufferedReader br = new BufferedReader(new FileReader(path));
			if (!br.readLine().startsWith("// "+NJ.NAME+" Data File")) throw new IOException();
			final String version = br.readLine();
			
			int brappear = NJ.appear;
			float brscale = NJ.scale;
			float brgamma = NJ.gamma;
			int brsnaprange = NJ.snaprange;
			int brdijkrange = NJ.dijkrange;
			int brhalfsmoothrange = NJ.halfsmoothrange;
			int brsubsamplefactor = NJ.subsamplefactor;
			int brlinewidth = NJ.linewidth;
			final String[] brtypes = new String[11];
			final Color[] brtypecolors = new Color[11];
			final String[] brclusters = new String[11];
			final Tracings brtracings = new Tracings();
			
			if (version.compareTo(NJ.VERSION) <= 0) {
				NJ.log("   Opened "+NJ.NAME+" version "+version+" data file");
				
				br.readLine(); // Parameters
				if (version.compareTo("1.4.0") >= 0) brappear = Integer.valueOf(br.readLine()).intValue();
				else brappear = 0; // Bright neurites by default for older file versions
				brscale = Float.valueOf(br.readLine()).floatValue();
				brgamma = Float.valueOf(br.readLine()).floatValue();
				brsnaprange = Integer.valueOf(br.readLine()).intValue();
				brdijkrange = Integer.valueOf(br.readLine()).intValue();
				brhalfsmoothrange = Integer.valueOf(br.readLine()).intValue();
				brsubsamplefactor = Integer.valueOf(br.readLine()).intValue();
				if (version.compareTo("1.1.0") >= 0) brlinewidth = Integer.valueOf(br.readLine()).intValue();
				if (version.compareTo("1.1.0") < 0) {
					br.readLine(); // Skip pixel x-size
					br.readLine(); // Skip pixel y-size
					br.readLine(); // Skip pixel units
					br.readLine(); // Skip auto-save option
					br.readLine(); // Skip log option
				}
				NJ.log("   Read parameters");
				
				br.readLine(); // Type names and colors
				for (int i=0; i<=10; ++i) {
					brtypes[i] = br.readLine();
					brtypecolors[i] = NJ.colors[Integer.valueOf(br.readLine()).intValue()]; }
					NJ.log("   Read type names and colors");
					
				br.readLine(); // Cluster names
				for (int i=0; i<=10; ++i) brclusters[i] = br.readLine();
				NJ.log("   Read cluster names");
				
				// Tracings
				String line = br.readLine();
				while (line.startsWith("// Tracing")) {
					final Tracing tracing = new Tracing();
					tracing.id(Integer.valueOf(br.readLine()).intValue());
					tracing.type(Integer.valueOf(br.readLine()).intValue());
					tracing.cluster(Integer.valueOf(br.readLine()).intValue());
					tracing.label(br.readLine());
					line = br.readLine();
					while (line.startsWith("// Segment")) {
						final Segment segment = new Segment();
						line = br.readLine();
						while (!line.startsWith("//")) {
							final Point pnt = new Point();
							pnt.x = Integer.valueOf(line).intValue();
							pnt.y = Integer.valueOf(br.readLine()).intValue();
							segment.add(pnt);
							line = br.readLine();
						}
						if (segment.length() > 0.0) tracing.add(segment);
					}
					if (tracing.length() > 0.0) brtracings.add(tracing);
				}
				NJ.log("   Read tracings");
				
			} else throw new IllegalStateException("Data file version "+version+" while running version "+NJ.VERSION);
			
			br.close();
			
			boolean bAppearChanged = false;
			if (NJ.appear != brappear) {
				bAppearChanged = true;
				NJ.appear = brappear;
			}
			boolean bScaleChanged = false;
			if (NJ.scale != brscale) {
				bScaleChanged = true;
				NJ.scale = brscale;
			}
			boolean bGammaChanged = false;
			if (NJ.gamma != brgamma) {
				bGammaChanged = true;
				NJ.gamma = brgamma;
			}
			NJ.snaprange = brsnaprange;
			NJ.dijkrange = brdijkrange;
			NJ.halfsmoothrange = brhalfsmoothrange;
			NJ.subsamplefactor = brsubsamplefactor;
			NJ.linewidth = brlinewidth;
			NJ.tracestroke = new BasicStroke(NJ.linewidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
			NJ.types = brtypes;
			NJ.typecolors = brtypecolors;
			NJ.clusters = brclusters;
			tracings = brtracings;
			NJ.log("   Effectuated read data");
			
			NJ.log("Done");
			NJ.save = false;
			
			IJ.showStatus("Loaded tracings from "+path);
			
			if (bComputedCosts) {
				if (bScaleChanged || bAppearChanged) { computeCosts(); doDijkstra(); }
				else if (bGammaChanged) { doDijkstra(); }
			}
			
		} catch (NumberFormatException e) {
			NJ.log("Error reading from file");
			NJ.error("Error reading from file");
			NJ.copyright();
			
		} catch (IllegalStateException e) {
			NJ.log(e.getMessage());
			NJ.error(e.getMessage());
			NJ.copyright();
			
		} catch (Throwable e) {
			NJ.log("Unable to read from file");
			NJ.error("Unable to read from file");
			NJ.copyright();
		}
		
		if (NJ.mdg != null) NJ.mdg.reset();
		if (NJ.adg != null) NJ.adg.reset();
		
		redraw();
	}
	
	void closeTracings() {
		
		String status = "Dumped image";
		if (NJ.save) {
			if (NJ.autosave) {
				NJ.log("Automatically saving tracings");
				saveTracings(NJ.workdir,NJ.imagename+".ndf");
				status += " but saved tracings";
			} else {
				NJ.log("Asking user to save tracings");
				final YesNoDialog ynd = new YesNoDialog("Save","Do you want to save the tracings?");
				if (ynd.yesPressed()) {
					final FileDialog fdg = new FileDialog(IJ.getInstance(),NJ.NAME+": Save",FileDialog.SAVE);
					fdg.setFilenameFilter(new ImageDataFilter());
					fdg.setFile(NJ.imagename+".ndf");
					fdg.setVisible(true);
					final String dir = fdg.getDirectory();
					final String file = fdg.getFile();
					fdg.dispose();
					if (dir != null && file != null) {
						saveTracings(dir,file);
						status += " but saved tracings";
					} else {
						NJ.log("Dumping tracings");
						status += " and tracings";
					}
				} else {
					NJ.log("Dumping tracings");
					status += " and tracings";
				}
			}
		} else NJ.log("No need to save current tracings");
		
		costs = null; // To free more memory
		IJ.showStatus(status);
	}
	
	void saveTracings(final String dir, final String file) {
		
		final String path = (dir.endsWith(File.separator) ? dir : dir+File.separator) + file;
		NJ.log("Saving tracings to "+path);
		
		try {
			final FileWriter fw = new FileWriter(path);
			fw.write("// "+NJ.NAME+" Data File - DO NOT CHANGE\n");
			fw.write(NJ.VERSION+"\n");
			NJ.log("   Opened "+NJ.NAME+" version "+NJ.VERSION+" data file");
			
			fw.write("// Parameters\n");
			fw.write(NJ.appear+"\n");
			fw.write(NJ.scale+"\n");
			fw.write(NJ.gamma+"\n");
			fw.write(NJ.snaprange+"\n");
			fw.write(NJ.dijkrange+"\n");
			fw.write(NJ.halfsmoothrange+"\n");
			fw.write(NJ.subsamplefactor+"\n");
			fw.write(NJ.linewidth+"\n");
			NJ.log("   Wrote parameters");
			
			fw.write("// Type names and colors\n");
			final int nrtypes = NJ.types.length;
			for (int i=0; i<nrtypes; ++i) fw.write(NJ.types[i]+"\n"+NJ.colorIndex(NJ.typecolors[i])+"\n");
			NJ.log("   Wrote type names and colors");
			
			fw.write("// Cluster names\n");
			final int nrclusters = NJ.clusters.length;
			for (int i=0; i<nrclusters; ++i) fw.write(NJ.clusters[i]+"\n");
			NJ.log("   Wrote cluster names");
			
			final int nrtracings = tracings.nrtracings();
			for (int n=0; n<nrtracings; ++n) {
				final Tracing tracing = tracings.get(n);
				fw.write("// Tracing N"+tracing.id()+"\n");
				fw.write(tracing.id()+"\n");
				fw.write(tracing.type()+"\n");
				fw.write(tracing.cluster()+"\n");
				fw.write(tracing.label()+"\n");
				final int nrsegments = tracing.nrsegments();
				for (int s=0; s<nrsegments; ++s) {
					fw.write("// Segment "+(s+1)+" of Tracing N"+tracing.id()+"\n");
					final Segment segment = tracing.get(s);
					final int nrpoints = segment.nrpoints();
					for (int p=0; p<nrpoints; ++p) {
						final Point pnt = segment.get(p);
						fw.write(pnt.x+"\n"+pnt.y+"\n");
					}
				}
			}
			NJ.log("   Wrote tracings");
			
			fw.write("// End of "+NJ.NAME+" Data File\n");
			fw.close();
			NJ.log("Done");
			IJ.showStatus("Saved tracings to "+path);
			NJ.save = false;
			
		} catch (IOException ioe) {
			NJ.log("Unable to write to file");
			NJ.error("Unable to write to file");
			NJ.copyright();
		}
	}
	
	void exportTracings(final String dir, final String file, final int type) {
		
		final String path = (dir.endsWith(File.separator) ? dir : dir+File.separator) + file;
		
		try {
			boolean separate = false;
			String delim = "\t";
			switch (type) {
				case 0: // Tab-del single
					separate = false;
					delim = "\t";
					break;
				case 1: // Tab-del separate
					separate = true;
					delim = "\t";
					break;
				case 2: // Comma-del single
					separate = false;
					delim = ",";
					break;
				case 3: // Comma-del separate
					separate = true;
					delim = ",";
					break;
				case 4: // Segmented line selections
					separate = true;
					delim = "";
					break;
			}
			if (separate) {
				final FileWriter fw = new FileWriter(path);
				String pathbase, pathext;
				final int lastdot = path.lastIndexOf('.');
				if (lastdot < 0) {
					pathbase = path.substring(0,path.length());
					pathext = "";
				} else {
					pathbase = path.substring(0,lastdot);
					pathext = path.substring(lastdot,path.length());
				}
				if (type == 4) pathext = ".roi";
				final int nrt = tracings.nrtracings();
				for (int t=0; t<nrt; ++t) {
					final Tracing tracing = tracings.get(t);
					final String tpath = pathbase+".N"+tracing.id()+pathext;
					NJ.log("Exporting tracing to "+tpath);
					fw.write("Tracing N"+tracing.id()+": "+tpath+"\n");
					if (type == 4) {
						final int nrs = tracing.nrsegments();
						// First determine number of points:
						int nrptotal = 0;
						for (int s=0, p0=0; s<nrs; ++s, p0=1)
							nrptotal += tracing.get(s).nrpoints() - p0;
						// Extract points into arrays:
						final int[] xcoords = new int[nrptotal];
						final int[] ycoords = new int[nrptotal];
						for (int s=0, p=0, p0=0; s<nrs; ++s, p0=1) {
							final Segment segment = tracing.get(s);
							final int nrp = segment.nrpoints();
							for (int pi=p0; pi<nrp; ++pi, ++p) {
								final Point pnt = segment.get(pi);
								xcoords[p] = pnt.x;
								ycoords[p] = pnt.y;
							}
						}
						// Convert arrays to ROI and save:
						final PolygonRoi roi = new PolygonRoi(xcoords,ycoords,nrptotal,Roi.POLYLINE);
						final RoiEncoder roienc = new RoiEncoder(tpath);
						roienc.write(roi);
					} else {
						final FileWriter tfw = new FileWriter(tpath);
						final int nrs = tracing.nrsegments();
						for (int s=0, p0=0; s<nrs; ++s, p0=1) {
							final Segment segment = tracing.get(s);
							final int nrp = segment.nrpoints();
							for (int p=p0; p<nrp; ++p) {
								final Point pnt = segment.get(p);
								tfw.write(pnt.x+delim+pnt.y+"\n");
							}
						}
						tfw.close();
					}
				}
				fw.close();
				NJ.log("Done");
				IJ.showStatus("Exported tracings to "+path);
			} else {
				NJ.log("Exporting tracings to "+path);
				final FileWriter fw = new FileWriter(path);
				final int nrt = tracings.nrtracings();
				for (int t=0; t<nrt; ++t) {
					final Tracing tracing = tracings.get(t);
					fw.write("Tracing N"+tracing.id()+":\n");
					final int nrs = tracing.nrsegments();
					for (int s=0, p0=0; s<nrs; ++s, p0=1) {
						final Segment segment = tracing.get(s);
						final int nrp = segment.nrpoints();
						for (int p=p0; p<nrp; ++p) {
							final Point pnt = segment.get(p);
							fw.write(pnt.x+delim+pnt.y+"\n");
						}
					}
				}
				fw.close();
				NJ.log("Done");
				IJ.showStatus("Exported tracings to "+path);
			}
		} catch (Throwable e) {
			NJ.log("Unable to write to file");
			NJ.error("Unable to write to file");
			NJ.copyright();
		}
	}
	
}

// ***************************************************************************
final class ImageFilter implements FilenameFilter {
	
	public boolean accept(File dir, String name) {
		
		final String ext = name.substring(name.lastIndexOf(".")+1);
		if (ext.equalsIgnoreCase("tif") ||
			ext.equalsIgnoreCase("tiff") ||
			ext.equalsIgnoreCase("gif") ||
			ext.equalsIgnoreCase("jpg")) return true;
		else return false;
	}
	
}

// ***************************************************************************
final class ImageDataFilter implements FilenameFilter {
	
	public boolean accept(File dir, String name) {
		
		final String ext = name.substring(name.lastIndexOf(".")+1);
		if (ext.equalsIgnoreCase("tif") ||
			ext.equalsIgnoreCase("tiff") ||
			ext.equalsIgnoreCase("gif") ||
			ext.equalsIgnoreCase("jpg") ||
			ext.equalsIgnoreCase("ndf") ||
			ext.equalsIgnoreCase("txt")) return true;
		else return false;
	}
	
}

// ***************************************************************************
final class ErrorDialog extends Dialog implements ActionListener, KeyListener, WindowListener {
	
	private Button button;
	private Label label;
	
	private final static Font font = new Font("Dialog",Font.PLAIN,12);
	
	ErrorDialog(String title, String message) {
		
		super(IJ.getInstance(),title,true);
		if (message == null) return;
		setLayout(new BorderLayout(0,0));
		
		label = new Label(message);
		label.setFont(font);
		Panel panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER,15,15));
		panel.add(label);
		add("North", panel);
		
		button = new Button("  OK  ");
		button.setFont(font);
		button.addActionListener(this);
		button.addKeyListener(this);
		panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER,0,0));
		panel.add(button);
		add("Center", panel);
		
		panel = new Panel();
		panel.setLayout(new FlowLayout(FlowLayout.CENTER,0,5));
		add("South", panel);
		
		pack();
		GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(ActionEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyPressed(KeyEvent e) { try {
		
		if (e.getKeyCode() == KeyEvent.VK_ENTER) close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyReleased(KeyEvent e) { }
	
	public void keyTyped(KeyEvent e) { }
	
	private void close() {
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class YesNoDialog extends Dialog implements ActionListener, KeyListener, WindowListener {
	
	private Button yesButton;
	private Button noButton;
	
	private boolean yesPressed = false;
	private boolean noPressed = false;
	
	// Builds the dialog for specifying the parameters for derivative computing.
	YesNoDialog(final String title, final String question) {
		
		super(IJ.getInstance(),NJ.NAME+": "+title,true);
		setLayout(new BorderLayout());
		
		// Add question:
		final Panel questPanel = new Panel();
		questPanel.setLayout(new FlowLayout(FlowLayout.CENTER,15,15));
		final Label questLabel = new Label(question);
		questLabel.setFont(new Font("Dialog",Font.PLAIN,12));
		questPanel.add(questLabel);
		add("North",questPanel);
		
		// Add No and Yes buttons:
		final Panel yesnoPanel = new Panel();
		yesnoPanel.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		yesButton = new Button("  Yes  ");
		yesButton.addActionListener(this);
		yesButton.addKeyListener(this);
		yesnoPanel.add(yesButton);
		noButton = new Button("   No   ");
		noButton.addActionListener(this);
		noButton.addKeyListener(this);
		yesnoPanel.add(noButton);
		add("Center",yesnoPanel);
		
		// Add spacing below buttons:
		final Panel spacePanel = new Panel();
		spacePanel.setLayout(new FlowLayout(FlowLayout.CENTER,0,5));
		add("South",spacePanel);
		
		// Pack and show:
		pack();
		GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == yesButton) yesPressed = true;
		else if (e.getSource() == noButton) noPressed = true;
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyTyped(KeyEvent e) {}
	
	public void keyPressed(KeyEvent e) { try {
		
		final int keycode = e.getKeyCode();
		if (keycode == KeyEvent.VK_Y) yesPressed = true;
		else if (keycode == KeyEvent.VK_N || keycode == KeyEvent.VK_ESCAPE) noPressed = true;
		else if (keycode == KeyEvent.VK_ENTER)
			if (yesButton.hasFocus()) yesPressed = true;
			else if (noButton.hasFocus()) noPressed = true;
		
		if (yesPressed || noPressed) close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyReleased(KeyEvent e) {}
	
	boolean yesPressed() { return yesPressed; }
	
	boolean noPressed() { return noPressed; }
	
	private void close() {
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class AttributesDialog extends Dialog implements ActionListener, FocusListener, ItemListener, WindowListener {
	
	private final Choice idChoice;
	private final Choice typeChoice;
	private final Choice clusterChoice;
	
	private final TextField labelField;
	
	private final Button namesButton;
	private final Button colorsButton;
	private final Button okayButton;
	private final Button closeButton;
	
	private final GridBagConstraints c = new GridBagConstraints();
	private final GridBagLayout grid = new GridBagLayout();
	
	private boolean bFirstChoice = true;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for setting the tracing attributes.
	AttributesDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Attributes",false);
		setLayout(grid);
		
		// Add ID, type, and cluster choices:
		idChoice = addChoice("Tracing ID:");
		idChoice.addItemListener(this);
		typeChoice = addChoice("Type:");
		clusterChoice = addChoice("Cluster:");
		
		// Add label text field:
		final Label labelLabel = makeLabel("Label:");
		c.gridwidth = 1;
		c.gridx = 0; c.gridy++;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(2,12,5,0);
		grid.setConstraints(labelLabel,c);
		add(labelLabel);
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2,0,5,15);
		labelField = new TextField("Default",15);
		grid.setConstraints(labelField,c);
		labelField.setEditable(true);
		labelField.addFocusListener(this);
		add(labelField);
		
		// Add buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		namesButton = new Button("Rename");
		namesButton.addActionListener(this);
		colorsButton = new Button("Recolor");
		colorsButton.addActionListener(this);
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		closeButton = new Button(" Close ");
		closeButton.addActionListener(this);
		buttons.add(namesButton);
		buttons.add(colorsButton);
		buttons.add(okayButton);
		buttons.add(closeButton);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(20,11,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		reset();
		select(0);
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	private Choice addChoice(final String label) {
		
		final Label newLabel = makeLabel(label);
		c.gridwidth = 1;
		c.gridx = 0; c.gridy++;
		c.anchor = GridBagConstraints.EAST;
		if (bFirstChoice) c.insets = new Insets(20,12,5,0);
		else c.insets = new Insets(0,12,5,0);
		grid.setConstraints(newLabel,c);
		add(newLabel);
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		if (bFirstChoice) c.insets = new Insets(20,0,5,13);
		else c.insets = new Insets(0,0,5,13);
		final Choice newChoice = new Choice();
		grid.setConstraints(newChoice,c);
		add(newChoice);
		
		bFirstChoice = false;
		return newChoice;
	}
	
	void reset() {
		
		idChoice.removeAll();
		idChoice.addItem("Unknown");
		final Tracings tracings = NJ.nhd.tracings();
		final int nrtracings = tracings.nrtracings();
		for (int i=0; i<nrtracings; ++i) idChoice.addItem("N"+tracings.get(i).id());
		
		typeChoice.removeAll();
		final int nrtypes = NJ.types.length;
		for (int i=0; i<nrtypes; ++i) typeChoice.addItem(NJ.types[i]);
		
		clusterChoice.removeAll();
		final int nrclusters = NJ.clusters.length;
		for (int i=0; i<nrclusters; ++i) clusterChoice.addItem(NJ.clusters[i]);
		
		select(0);
	}
	
	void select(final int index) {
		
		final int nritems = idChoice.getItemCount();
		
		if (index > 0 && index < nritems) {
			idChoice.select(index);
			final Tracing tracing = NJ.nhd.tracings().get(index-1);
			typeChoice.select(tracing.type());
			clusterChoice.select(tracing.cluster());
			labelField.setText(tracing.label());
		} else {
			idChoice.select(0);
			typeChoice.select(0);
			clusterChoice.select(0);
			labelField.setText("Default");
		}
	}
	
	private Label makeLabel(String label) {
		if (IJ.isMacintosh()) label += "  ";
		return new Label(label);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			final int type = typeChoice.getSelectedIndex();
			final int cluster = clusterChoice.getSelectedIndex();
			final String label = labelField.getText();
			final Tracings tracings = NJ.nhd.tracings();
			final int nrtracings = tracings.nrtracings();
			int iCount = 0;
			for (int i=0; i<nrtracings; ++i) {
				final Tracing tracing = tracings.get(i);
				if (tracing.selected() || tracing.highlighted()) {
					NJ.log("Labeling tracing N"+tracing.id());
					tracing.type(type);
					tracing.cluster(cluster);
					tracing.label(label);
					tracing.select(false);
					tracing.highlight(false);
					++iCount;
				}
			}
			if (iCount == 0) IJ.showStatus("Labeled no tracings");
			else if (iCount == 1) IJ.showStatus("Labeled tracing");
			else IJ.showStatus("Labeled tracings");
			select(0);
			if (tracings.changed()) NJ.nhd.redraw();
			
		} else if (e.getSource() == colorsButton) {
			final RecolorDialog cd = new RecolorDialog();
			
		} else if (e.getSource() == namesButton) {
			final RenameDialog nd = new RenameDialog();
			final int index = idChoice.getSelectedIndex();
			reset(); select(index);
			if (NJ.mdg != null) NJ.mdg.reset();
			
		} else {
			close();
			NJ.ntb.resetTool();
			NJ.copyright();
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void focusGained(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).selectAll();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void focusLost(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).select(0,0);
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void itemStateChanged(final ItemEvent e) { try {
		
		if (e.getSource() == idChoice) {
			final int index = idChoice.getSelectedIndex();
			select(index);
			final Tracings tracings = NJ.nhd.tracings();
			final int nrtracings = tracings.nrtracings();
			for (int i=0; i<nrtracings; ++i) {
				tracings.get(i).highlight(false);
				tracings.get(i).select(false);
			}
			if (index > 0) tracings.get(index-1).highlight(true);
			NJ.nhd.redraw();
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	void close() {
		final Tracings tracings = NJ.nhd.tracings();
		final int nrtracings = tracings.nrtracings();
		for (int i=0; i<nrtracings; ++i) {
			final Tracing tracing = tracings.get(i);
			tracing.select(false);
			tracing.highlight(false);
		}
		NJ.nhd.redraw();
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
		NJ.adg = null;
		NJ.copyright();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		NJ.ntb.resetTool();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class RenameDialog extends Dialog implements ActionListener, FocusListener, KeyListener, WindowListener {
	
	private final Choice namesChoice;
	private final TextField nameField;
	
	private final Button okayButton;
	private final Button cancelButton;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for renaming the types and clusters.
	RenameDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Rename",true);
		final GridBagConstraints c = new GridBagConstraints();
		final GridBagLayout grid = new GridBagLayout();
		setLayout(grid);
		
		// Add choice:
		namesChoice = new Choice();
		final int nrtypes = NJ.types.length;
		for (int i=1; i<nrtypes; ++i) namesChoice.addItem(NJ.types[i]);
		final int nrclusters = NJ.clusters.length;
		for (int i=1; i<nrclusters; ++i) namesChoice.addItem(NJ.clusters[i]);
		namesChoice.select(0);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(20,13,5,0);
		grid.setConstraints(namesChoice,c);
		add(namesChoice);
		
		// Add text field:
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(20,10,5,15);
		nameField = new TextField("",15);
		grid.setConstraints(nameField,c);
		nameField.setEditable(true);
		nameField.addFocusListener(this);
		add(nameField);
		
		// Add Okay and Cancel buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		cancelButton = new Button("Cancel");
		cancelButton.addActionListener(this);
		buttons.add(okayButton);
		buttons.add(cancelButton);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(15,10,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			final String name = nameField.getText();
			final int index = namesChoice.getSelectedIndex() + 1;
			if (index < NJ.types.length) NJ.types[index] = name;
			else NJ.clusters[index-NJ.types.length+1] = name;
			IJ.showStatus("Changed name");
			NJ.save = true;
		} else { NJ.copyright(); }
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void focusGained(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).selectAll();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void focusLost(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).select(0,0);
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }

	private void close() {
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
	}
	
	public void keyTyped(final KeyEvent e) {}
	
	public void keyPressed(final KeyEvent e) { try {
		
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			NJ.copyright();
			close();
		}
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyReleased(final KeyEvent e) {}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class RecolorDialog extends Dialog implements ActionListener, ItemListener, WindowListener {
	
	private final Choice typeChoice;
	private final Choice colorChoice;
	
	private final Button okayButton;
	private final Button cancelButton;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for setting the type colors.
	RecolorDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Recolor",true);
		final GridBagConstraints c = new GridBagConstraints();
		final GridBagLayout grid = new GridBagLayout();
		setLayout(grid);
		
		// Add choices:
		typeChoice = new Choice();
		final int nrtypes = NJ.types.length;
		for (int i=0; i<nrtypes; ++i) typeChoice.addItem(NJ.types[i]);
		typeChoice.select(0);
		typeChoice.addItemListener(this);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(20,15,5,0);
		grid.setConstraints(typeChoice,c);
		add(typeChoice);
		
		colorChoice = new Choice();
		final int nrcolors = NJ.colors.length;
		for (int i=0; i<nrcolors; ++i) colorChoice.addItem(NJ.colornames[i]);
		colorChoice.select(NJ.colorIndex(NJ.typecolors[0]));
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(20,10,5,13);
		grid.setConstraints(colorChoice,c);
		add(colorChoice);
		
		// Add Okay and Cancel buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		cancelButton = new Button("Cancel");
		cancelButton.addActionListener(this);
		buttons.add(okayButton);
		buttons.add(cancelButton);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(15,10,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			NJ.typecolors[typeChoice.getSelectedIndex()] = NJ.colors[colorChoice.getSelectedIndex()];
			IJ.showStatus("Changed color");	    
			NJ.nhd.redraw();
			NJ.save = true;
		} else { NJ.copyright(); }
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void itemStateChanged(final ItemEvent e) { try {
		
		colorChoice.select(NJ.colorIndex(NJ.typecolors[typeChoice.getSelectedIndex()]));
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	private void close() {
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class MeasurementsDialog extends Dialog implements ActionListener, WindowListener {
	
	private final Choice typeChoice;
	private final Choice clusterChoice;
	
	private final Checkbox groupCheckbox;
	private final Checkbox traceCheckbox;
	private final Checkbox vertiCheckbox;
	
	private final Checkbox calibCheckbox;
	private final Checkbox interCheckbox;
	private final Checkbox clearCheckbox;
	
	private final Choice decsChoice;

	private static boolean group = true;
	private static boolean trace = true;
	private static boolean verti = true;
	private static boolean calib = true;
	private static boolean inter = true;
	private static boolean clear = true;

	private static int decs = 3;
	
	private static String pgh = null;
	private static String pth = null;
	private static String pvh = null;
	
	private final Button runButton;
	private final Button closeButton;
	
	private final GridBagConstraints c = new GridBagConstraints();
	private final GridBagLayout grid = new GridBagLayout();
	
	private boolean bFirstChoice = true;
	private boolean bFirstReset = true;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for doing length measurements.
	MeasurementsDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Measurements",false);
		setLayout(grid);
		
		// Add choices:
		typeChoice = addChoice("Tracing type:");
		clusterChoice = addChoice("Cluster:");
		
		// Add check boxes:
		c.gridx = 0;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		
		c.gridy++; c.insets = new Insets(15,18,0,18);
		groupCheckbox = new Checkbox(" Display group measurements");
		grid.setConstraints(groupCheckbox,c);
		groupCheckbox.setState(group);
		add(groupCheckbox);
		
		c.gridy++; c.insets = new Insets(0,18,0,18);
		traceCheckbox = new Checkbox(" Display tracing measurements");
		grid.setConstraints(traceCheckbox,c);
		traceCheckbox.setState(trace);
		add(traceCheckbox);
		
		c.gridy++;
		vertiCheckbox = new Checkbox(" Display vertex measurements");
		grid.setConstraints(vertiCheckbox,c);
		vertiCheckbox.setState(verti);
		add(vertiCheckbox);
		
		c.gridy++; c.insets = new Insets(15,18,0,18);
		calibCheckbox = new Checkbox(" Calibrate measurements");
		grid.setConstraints(calibCheckbox,c);
		calibCheckbox.setState(calib);
		add(calibCheckbox);
		
		c.gridy++; c.insets = new Insets(0,18,0,18);
		interCheckbox = new Checkbox(" Interpolate value measurements");
		grid.setConstraints(interCheckbox,c);
		interCheckbox.setState(inter);
		add(interCheckbox);
		
		c.gridy++;
		clearCheckbox = new Checkbox(" Clear previous measurements");
		grid.setConstraints(clearCheckbox,c);
		clearCheckbox.setState(clear);
		add(clearCheckbox);
		
		// Add decimals choice:
		final Panel decsPanel = new Panel();
		decsPanel.setLayout(new FlowLayout(FlowLayout.CENTER,0,0));
		final Label decsLabel = makeLabel("Maximum decimal places:");
		decsPanel.add(decsLabel);
		decsChoice = new Choice();
		decsPanel.add(decsChoice);
		c.gridy++;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(15,10,5,15);
		grid.setConstraints(decsPanel,c);
		add(decsPanel);
		for (int i=0; i<=10; ++i) decsChoice.addItem(String.valueOf(i));
		decsChoice.select(decs);
		
		// Add Run and Close buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		runButton = new Button("  Run  ");
		runButton.addActionListener(this);
		closeButton = new Button("Close");
		closeButton.addActionListener(this);
		buttons.add(runButton);
		buttons.add(closeButton);
		c.gridy++; c.insets = new Insets(20,10,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		reset();
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	private Choice addChoice(final String label) {
		
		final Label newLabel = makeLabel(label);
		c.gridx = 0; c.gridy++;
		c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		if (bFirstChoice) c.insets = new Insets(20,13,5,0);
		else c.insets = new Insets(0,13,5,0);
		grid.setConstraints(newLabel,c);
		add(newLabel);
		final Choice newChoice = new Choice();
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		if (bFirstChoice) c.insets = new Insets(20,0,5,13);
		else c.insets = new Insets(0,0,5,13);
		grid.setConstraints(newChoice,c);
		add(newChoice);
		
		bFirstChoice = false;
		return newChoice;
	}
	
	void reset() {
		
		final int nrtypes = NJ.types.length;
		final int tindex = bFirstReset ? nrtypes : typeChoice.getSelectedIndex();
		typeChoice.removeAll();
		for (int i=0; i<nrtypes; ++i) typeChoice.addItem(NJ.types[i]);
		typeChoice.addItem("All");
		typeChoice.select(tindex);
		
		final int nrclusters = NJ.clusters.length;
		final int cindex = bFirstReset ? nrclusters : clusterChoice.getSelectedIndex();
		clusterChoice.removeAll();
		for (int i=0; i<nrclusters; ++i) clusterChoice.addItem(NJ.clusters[i]);
		clusterChoice.addItem("All");
		clusterChoice.select(cindex);
		
		bFirstReset = false;
	}
	
	private Label makeLabel(String label) {
		
		if (IJ.isMacintosh()) label += "  ";
		return new Label(label);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		group = groupCheckbox.getState();
		trace = traceCheckbox.getState();
		verti = vertiCheckbox.getState();
		calib = NJ.calibrate = calibCheckbox.getState();
		inter = NJ.interpolate = interCheckbox.getState();
		clear = clearCheckbox.getState();
		decs = decsChoice.getSelectedIndex();
		
		if (e.getSource() == runButton) {
			final Tracings tracings = NJ.nhd.tracings();
			final int nrtracings = tracings.nrtracings();
			final int type = typeChoice.getSelectedIndex();
			final int cluster = clusterChoice.getSelectedIndex();
			final ByteProcessor bp = NJ.nhd.ipgray;
			final String cstring = calib ? "calibrated " : "uncalibrated ";
			final Calibration cal = NJ.imageplus.getCalibration();
			String su = calib ? new String(cal.getUnit()) : "pixel";
			if (su.equals("pixel")) su = "pix";
			String vu = calib ? new String(cal.getValueUnit()) : "Gray Value";
			if (vu.equals("Gray Value")) vu = "a.u.";
			if (calib) bp.setCalibrationTable(cal.getCTable());
			else bp.setCalibrationTable(null);
			final Formatter fm = new Formatter();
			fm.decs(decs);
			
			if (group == true) {
				final Values lengths = new Values();
				final Values values = new Values();
				for (int n=0; n<nrtracings; ++n) {
					final Tracing tracing = tracings.get(n);
					if ((tracing.type() == type || type == NJ.types.length) && (tracing.cluster() == cluster || cluster == NJ.clusters.length)) {
						lengths.add(tracing.length());
						tracing.values(bp,values);
					}
				}
				final String gh = new String(
					"Image\t"+
					"Cluster\t"+
					"Type\t"+
					"Count\t"+
					"Sum Len ["+su+"]\t"+
					"Mean Len ["+su+"]\t"+
					"SD Len ["+su+"]\t"+
					"Min Len ["+su+"]\t"+
					"Max Len ["+su+"]\t"+
					"Mean Val ["+vu+"]\t"+
					"SD Val ["+vu+"]\t"+
					"Min Val ["+vu+"]\t"+
					"Max Val ["+vu+"]"
				);
				final StringBuffer measures = new StringBuffer();
				measures.append(NJ.imagename);
				measures.append("\t" + (cluster==NJ.clusters.length ? "All" : NJ.clusters[cluster]));
				measures.append("\t" + (type==NJ.types.length ? "All" : NJ.types[type]));
				if (lengths.count() > 0) {
					lengths.stats();
					measures.append("\t" + String.valueOf(lengths.count()));
					measures.append("\t" + fm.d2s(lengths.sum()));
					measures.append("\t" + fm.d2s(lengths.mean()));
					measures.append("\t" + fm.d2s(lengths.sd()));
					measures.append("\t" + fm.d2s(lengths.min()));
					measures.append("\t" + fm.d2s(lengths.max()));
					values.stats();
					measures.append("\t" + fm.d2s(values.mean()));
					measures.append("\t" + fm.d2s(values.sd()));
					measures.append("\t" + fm.d2s(values.min()));
					measures.append("\t" + fm.d2s(values.max()));
					measures.append("\n");
				} else {
					measures.append("\t0\n");
				}
				if (NJ.grw == null || !NJ.grw.isShowing()) {
					NJ.log("Writing "+cstring+"measurements to new group results window");
					final String title = NJ.usename ? (NJ.imagename+"-groups") : (NJ.NAME+": Groups");
					NJ.grw = new TextWindow(title,gh,measures.toString(),820,300);
				} else {
					NJ.log("Writing "+cstring+"measurements to group results window");
					final TextPanel tp = NJ.grw.getTextPanel();
					if (clear == true || !gh.equals(pgh)) tp.setColumnHeadings(gh);
					tp.append(measures.toString());
				}
				pgh = gh;
			}
			if (trace == true) {
				int iCount = 0;
				final String th = new String(
					"Image\t"+
					"Tracing\t"+
					"Cluster\t"+
					"Type\t"+
					"Label\t"+
					"Length ["+su+"]\t"+
					"Mean Val ["+vu+"]\t"+
					"SD Val ["+vu+"]\t"+
					"Min Val ["+vu+"]\t"+
					"Max Val ["+vu+"]"
				);
				final StringBuffer measures = new StringBuffer();
				final Values values = new Values();
				for (int n=0; n<nrtracings; ++n) {
					final Tracing tracing = tracings.get(n);
					if ((tracing.type() == type || type == NJ.types.length) && (tracing.cluster() == cluster || cluster == NJ.clusters.length)) {
						measures.append(NJ.imagename);
						measures.append("\tN" + tracing.id());
						measures.append("\t" + NJ.clusters[tracing.cluster()]);
						measures.append("\t" + NJ.types[tracing.type()]);
						measures.append("\t" + tracing.label());
						measures.append("\t" + fm.d2s(tracing.length()));
						values.reset();
						tracing.values(bp,values);
						values.stats();
						measures.append("\t" + fm.d2s(values.mean()));
						measures.append("\t" + fm.d2s(values.sd()));
						measures.append("\t" + fm.d2s(values.min()));
						measures.append("\t" + fm.d2s(values.max()));
						measures.append("\n");
						++iCount;
					}
				}
				if (iCount == 0) {
					measures.append(NJ.imagename);
					measures.append("\tNone\n");
				}
				if (NJ.trw == null || !NJ.trw.isShowing()) {
					NJ.log("Writing "+cstring+"measurements to new tracing results window");
					final String title = NJ.usename ? (NJ.imagename+"-tracings") : (NJ.NAME+": Tracings");
					NJ.trw = new TextWindow(title,th,measures.toString(),820,300);
					final Point loc = NJ.trw.getLocation();
					loc.x += 20; loc.y += 20;
					NJ.trw.setLocation(loc.x,loc.y);
				} else {
					NJ.log("Writing "+cstring+"measurements to tracing results window");
					final TextPanel tp = NJ.trw.getTextPanel();
					if (clear == true || !th.equals(pth)) tp.setColumnHeadings(th);
					tp.append(measures.toString());
				}
				pth = th;
			}
			if (verti == true) {
				int iCount = 0;
				final String vh = new String(
					"Image\t"+
					"Tracing\t"+
					"Segment\t"+
					"Vertex\t"+
					"X ["+su+"]\t"+
					"Y ["+su+"]\t"+
					"Val ["+vu+"]"
				);
				final StringBuffer measures = new StringBuffer();
				final double pw = NJ.calibrate ? NJ.imageplus.getCalibration().pixelWidth : 1;
				final double ph = NJ.calibrate ? NJ.imageplus.getCalibration().pixelHeight : 1;
				for (int n=0; n<nrtracings; ++n) {
					final Tracing tracing = tracings.get(n);
					if ((tracing.type() == type || type == NJ.types.length) && (tracing.cluster() == cluster || cluster == NJ.clusters.length)) {
						final int nrsegments = tracing.nrsegments();
						for (int s=0, p0=0; s<nrsegments; ++s, p0=1) {
							final Segment segment = tracing.get(s);
							final int nrpoints = segment.nrpoints();
							for (int p=p0, v=1; p<nrpoints; ++p, ++v) {
								final Point point = segment.get(p);
								measures.append(NJ.imagename);
								measures.append("\tN"+tracing.id());
								measures.append("\t"+(s+1));
								measures.append("\t"+v);
								measures.append("\t"+fm.d2s(point.x*pw));
								measures.append("\t"+fm.d2s(point.y*ph));
								measures.append("\t"+fm.d2s(bp.getPixelValue(point.x,point.y)));
								measures.append("\n");
								++iCount;
							}
						}
					}
				}
				if (iCount == 0) {
					measures.append(NJ.imagename);
					measures.append("\tNone\n");
				}
				if (NJ.vrw == null || !NJ.vrw.isShowing()) {
					NJ.log("Writing "+cstring+"measurements to new vertex results window");
					final String title = NJ.usename ? (NJ.imagename+"-vertices") : (NJ.NAME+": Vertices");
					NJ.vrw = new TextWindow(title,vh,measures.toString(),820,300);
					final Point loc = NJ.vrw.getLocation();
					loc.x += 40; loc.y += 40;
					NJ.vrw.setLocation(loc.x,loc.y);
				} else {
					NJ.log("Writing "+cstring+"measurements to vertex results window");
					final TextPanel tp = NJ.vrw.getTextPanel();
					if (clear == true || !vh.equals(pvh)) tp.setColumnHeadings(vh);
					tp.append(measures.toString());
				}
				pvh = vh;
			}
		} else if (e.getSource() == closeButton) {
			close();
			NJ.ntb.resetTool();
		}
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	void close() {
		
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
		NJ.mdg = null;
		NJ.copyright();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		NJ.ntb.resetTool();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class ParametersDialog extends Dialog implements ActionListener, FocusListener, WindowListener {
	
	private final TextField scaleField;
	private final TextField gammaField;
	
	private final Choice appearChoice;
	private final Choice snapChoice;
	private final Choice dijkChoice;
	private final Choice smoothChoice;
	private final Choice sampleChoice;
	private final Choice lineChoice;
	
	private final Checkbox activateCheckbox;
	private final Checkbox usenameCheckbox;
	private final Checkbox autosaveCheckbox;
	private final Checkbox logCheckbox;
	
	private final Button saveButton;
	private final Button okayButton;
	private final Button cancelButton;
	
	private final GridBagConstraints c = new GridBagConstraints();
	private final GridBagLayout grid = new GridBagLayout();
	
	private boolean bFirstParam = true;
	private boolean bAppearChanged = false;
	private boolean bScaleChanged = false;
	private boolean bGammaChanged = false;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for setting the parameters.
	ParametersDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Parameters",true);
		setLayout(grid);
		
		// Add parameters:
		appearChoice = addChoice("Neurite appearance:");
		appearChoice.addItem("Bright");
		appearChoice.addItem("Dark");
		appearChoice.select(NJ.appear);
		
		scaleField = addTextField("Hessian smoothing scale:",String.valueOf(NJ.scale));
		gammaField = addTextField("Cost weight factor:",String.valueOf(NJ.gamma));
		
		snapChoice = addChoice("Snap window size:");
		final int maxsnapsize = 19;
		for (int i=1; i<=maxsnapsize; i+=2) snapChoice.addItem(i+" x "+i);
		snapChoice.select(NJ.snaprange);
		
		dijkChoice = addChoice("Path-search window size:");
		final int maxdijksize = 2500;
		for (int i=100; i<=maxdijksize; i+=100) dijkChoice.addItem(i+" x "+i);
		dijkChoice.select(NJ.dijkrange/100 - 1);
		
		smoothChoice = addChoice("Tracing smoothing range:");
		final int maxsmoothrange = 10;
		for (int i=0; i<=maxsmoothrange; ++i) smoothChoice.addItem(String.valueOf(i));
		smoothChoice.select(NJ.halfsmoothrange);
		
		sampleChoice = addChoice("Tracing subsampling factor:");
		final int maxsubsample = 10;
		for (int i=1; i<=maxsubsample; ++i) sampleChoice.addItem(String.valueOf(i));
		sampleChoice.select(NJ.subsamplefactor-1);
		
		lineChoice = addChoice("Line width:");
		final int maxlinewidth = 10;
		for (int i=1; i<=maxlinewidth; ++i) lineChoice.addItem(String.valueOf(i));
		lineChoice.select(NJ.linewidth-1);
		
		c.insets = new Insets(22,18,0,18);
		c.gridx = 0; c.gridy++; c.gridwidth = 2;
		c.anchor = GridBagConstraints.WEST;
		activateCheckbox = new Checkbox(" Activate image window when mouse enters");
		grid.setConstraints(activateCheckbox,c);
		activateCheckbox.setState(NJ.activate);
		add(activateCheckbox);
		
		c.gridy++;
		c.insets = new Insets(0,18,0,18);
		usenameCheckbox = new Checkbox(" Use image name in result window titles");
		grid.setConstraints(usenameCheckbox,c);
		usenameCheckbox.setState(NJ.usename);
		add(usenameCheckbox);
		
		c.gridy++;
		autosaveCheckbox = new Checkbox(" Automatically save tracings");
		grid.setConstraints(autosaveCheckbox,c);
		autosaveCheckbox.setState(NJ.autosave);
		add(autosaveCheckbox);
		
		c.gridy++;
		logCheckbox = new Checkbox(" Show log messages");
		grid.setConstraints(logCheckbox,c);
		logCheckbox.setState(NJ.log);
		add(logCheckbox);
		
		// Add Save, Okay, and Cancel buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		saveButton = new Button("  Save  ");
		saveButton.addActionListener(this);
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		cancelButton = new Button("Cancel");
		cancelButton.addActionListener(this);
		buttons.add(saveButton);
		buttons.add(okayButton);
		buttons.add(cancelButton);
		c.gridx = 0; c.gridy++; c.gridwidth = 2;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(22,10,12,10);
		grid.setConstraints(buttons, c);
		add(buttons);
		
		// Pack and show:
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	private Label makeLabel(String label) {
		if (IJ.isMacintosh()) label += "  ";
		return new Label(label);
	}
	
	private float stringToFloat(final String s, final float defval) {
		
		try {
			final Float f = new Float(s);
			return f.floatValue();
		}
		catch(NumberFormatException e) {}
		
		return defval;
	}
	
	private Choice addChoice(final String label) {
		
		final Label newLabel = makeLabel(label);
		c.gridx = 0; c.gridy++; c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		if (bFirstParam) c.insets = new Insets(25,15,3,0);
		else c.insets = new Insets(0,15,3,0);
		grid.setConstraints(newLabel,c);
		add(newLabel);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		if (bFirstParam) c.insets = new Insets(25,0,3,13);
		else c.insets = new Insets(0,0,3,13);
		final Choice newChoice = new Choice();
		grid.setConstraints(newChoice,c);
		add(newChoice);
		
		bFirstParam = false;
		return newChoice;
	}
	
	private TextField addTextField(final String label, final String value) {
		
		c.gridx = 0; c.gridy++; c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		if (bFirstParam) c.insets = new Insets(25,15,3,0);
		else c.insets = new Insets(0,15,3,0);
		final Label newLabel = makeLabel(label);
		grid.setConstraints(newLabel,c);
		add(newLabel);
		
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		if (bFirstParam) c.insets = new Insets(25,0,3,15);
		else c.insets = new Insets(0,0,3,15);
		final TextField newTextField = new TextField(value, 6);
		grid.setConstraints(newTextField,c);
		newTextField.setEditable(true);
		newTextField.addFocusListener(this);
		add(newTextField);
		
		bFirstParam = false;
		return newTextField;
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			setParams(); IJ.showStatus("Set parameters");
		} else if (e.getSource() == saveButton) {
			setParams(); NJ.saveParams(); IJ.showStatus("Saved parameters");
		} else { NJ.copyright(); }
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	private void setParams() {
		
		final boolean log = logCheckbox.getState();
		if (log) NJ.log = true;
		
		NJ.log("Setting parameters...");
		
		int appear = appearChoice.getSelectedIndex();
		if (appear == NJ.appear) bAppearChanged = false;
		else bAppearChanged = true;
		NJ.appear = appear;
		if (NJ.appear == 0) NJ.log("   Neurite appearance = Bright");
		else NJ.log("   Neurite appearance = Dark");
		
		float scale = stringToFloat(scaleField.getText(),1.0f);
		if (scale < 1.0f) scale = 1.0f;
		if (scale == NJ.scale) bScaleChanged = false;
		else bScaleChanged = true;
		NJ.scale = scale;
		NJ.log("   Hessian smoothing scale = "+NJ.scale+" pixels");
		
		float gamma = stringToFloat(gammaField.getText(),0.5f);
		if (gamma < 0.0f) gamma = 0.0f;
		else if (gamma > 1.0f) gamma = 1.0f;
		if (gamma == NJ.gamma) bGammaChanged = false;
		else bGammaChanged = true;
		NJ.gamma = gamma;
		NJ.log("   Cost weight factor = "+NJ.gamma);
		
		NJ.snaprange = snapChoice.getSelectedIndex();
		final int snapwinsize = 2*NJ.snaprange + 1;
		NJ.log("   Snap window size = "+snapwinsize+" x "+snapwinsize+" pixels");
		
		NJ.dijkrange = 100*(dijkChoice.getSelectedIndex() + 1);
		NJ.log("   Path-search window size = "+NJ.dijkrange+" x "+NJ.dijkrange+" pixels");
		
		NJ.halfsmoothrange = smoothChoice.getSelectedIndex();
		NJ.log("   Tracing smoothing range = "+NJ.halfsmoothrange+" pixels on both sides");
		
		NJ.subsamplefactor = sampleChoice.getSelectedIndex() + 1;
		NJ.log("   Tracing subsampling factor = "+NJ.subsamplefactor);
		
		NJ.linewidth = lineChoice.getSelectedIndex() + 1;
		NJ.log("   Line width = "+NJ.linewidth+" pixels");
		NJ.tracestroke = new BasicStroke(NJ.linewidth,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
		
		NJ.activate = activateCheckbox.getState();
		if (NJ.activate) NJ.log("   Activating image window when mouse enters");
		else NJ.log("   Not activating image window when mouse enters");
		
		NJ.usename = usenameCheckbox.getState();
		if (NJ.usename) NJ.log("   Using image name in result window titles");
		else NJ.log("   Using default result window titles");
		
		NJ.autosave = autosaveCheckbox.getState();
		if (NJ.autosave) NJ.log("   Automatically saving tracings");
		else NJ.log("   Asking user to save tracings");
		
		if (log) NJ.log("   Showing log messages");
		else NJ.log("   Stop showing log messages");
		
		NJ.log("Done");
		
		NJ.log = log;
		if (!log) NJ.closelog();
		NJ.save = true;
		
		if (NJ.image) NJ.nhd.redraw();
	}
	
	boolean appearChanged() { return bAppearChanged; }
	
	boolean scaleChanged() { return bScaleChanged; }
	
	boolean gammaChanged() { return bGammaChanged; }
	
	public void focusGained(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).selectAll();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void focusLost(final FocusEvent e) { try {
		
		Component c = e.getComponent();
		if (c instanceof TextField) ((TextField)c).select(0,0);
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	private void close() {
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class SnapshotDialog extends Dialog implements ActionListener, WindowListener {
	
	private final Checkbox imageCheckbox;
	private final Checkbox tracingsCheckbox;
	
	private static boolean drawimage = true;
	private static boolean drawtracings = true;
	
	private final Button okayButton;
	private final Button cancelButton;
	
	private final GridBagConstraints c = new GridBagConstraints();
	private final GridBagLayout grid = new GridBagLayout();
	
	private boolean canceled = true;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for setting the snapshot parameters.
	SnapshotDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Snapshot",true);
		setLayout(grid);
		
		// Add image and tracings checkboxes:
		c.insets = new Insets(20,18,0,18);
		c.gridx = c.gridy = 0; c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		imageCheckbox = new Checkbox(" Draw image                ");
		grid.setConstraints(imageCheckbox, c);
		imageCheckbox.setState(drawimage);
		add(imageCheckbox);
		c.gridy++; c.insets = new Insets(0,18,0,18);
		tracingsCheckbox = new Checkbox(" Draw tracings");
		grid.setConstraints(tracingsCheckbox,c);
		tracingsCheckbox.setState(drawtracings);
		add(tracingsCheckbox);
		
		// Add Okay, and Cancel buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		cancelButton = new Button("Cancel");
		cancelButton.addActionListener(this);
		buttons.add(okayButton);
		buttons.add(cancelButton);
		c.gridx = 0; c.gridy++; c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(20,10,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			drawimage = imageCheckbox.getState();
			drawtracings = tracingsCheckbox.getState();
			canceled = false;
		}
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public boolean drawImage() { return drawimage; }
	
	public boolean drawTracings() { return drawtracings; }
	
	public boolean wasCanceled() { return canceled; }
	
	private void close() {
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class ExportDialog extends Dialog implements ActionListener, WindowListener {
	
	private final CheckboxGroup checkboxgroup = new CheckboxGroup();
	private final Checkbox[] checkboxes = new Checkbox[5];
	private static final boolean[] states = { true, false, false, false, false };
	
	private final Button okayButton;
	private final Button cancelButton;
	
	private final GridBagConstraints c = new GridBagConstraints();
	private final GridBagLayout grid = new GridBagLayout();
	
	private boolean canceled = true;
	
	private static int left = -1;
	private static int top = -1;
	
	// Builds the dialog for choosing the export type.
	ExportDialog() {
		
		super(IJ.getInstance(),NJ.NAME+": Export",true);
		setLayout(grid);
		
		// Add message:
		final Label message = new Label("Export tracing vertex coordinates with one vertex per line to:");
		c.gridx = c.gridy = 0; c.gridwidth = 1;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(20,18,0,18);
		grid.setConstraints(message,c);
		add(message);
		
		// Add image and tracings checkboxes:
		c.gridy++; c.insets = new Insets(10,18,0,18);
		checkboxes[0] = new Checkbox(" Tab-delimited text file: single file for all tracings",states[0],checkboxgroup);
		grid.setConstraints(checkboxes[0],c);
		add(checkboxes[0]);
		
		c.gridy++; c.insets = new Insets(0,18,0,18);
		checkboxes[1] = new Checkbox(" Tab-delimited text files: separate file for each tracing",states[1],checkboxgroup);
		grid.setConstraints(checkboxes[1],c);
		add(checkboxes[1]);
		
		c.gridy++;
		checkboxes[2] = new Checkbox(" Comma-delimited text file: single file for all tracings",states[2],checkboxgroup);
		grid.setConstraints(checkboxes[2],c);
		add(checkboxes[2]);
		
		c.gridy++;
		checkboxes[3] = new Checkbox(" Comma-delimited text files: separate file for each tracing",states[3],checkboxgroup);
		grid.setConstraints(checkboxes[3],c);
		add(checkboxes[3]);
		
		c.gridy++;
		checkboxes[4] = new Checkbox(" Segmented line selection files: separate file for each tracing",states[4],checkboxgroup);
		grid.setConstraints(checkboxes[4],c);
		add(checkboxes[4]);
		
		// Add Okay, and Cancel buttons:
		final Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER,5,0));
		okayButton = new Button("   OK   ");
		okayButton.addActionListener(this);
		cancelButton = new Button("Cancel");
		cancelButton.addActionListener(this);
		buttons.add(okayButton);
		buttons.add(cancelButton);
		c.gridx = 0; c.gridy++; c.gridwidth = 1;
		c.anchor = GridBagConstraints.EAST;
		c.insets = new Insets(20,10,12,10);
		grid.setConstraints(buttons,c);
		add(buttons);
		
		// Pack and show:
		pack();
		if (left >= 0 && top >= 0) setLocation(left,top);
		else GUI.center(this);
		addWindowListener(this);
		setVisible(true);
	}
	
	public void actionPerformed(final ActionEvent e) { try {
		
		if (e.getSource() == okayButton) {
			for (int i=0; i<checkboxes.length; ++i)
			states[i] = checkboxes[i].getState();
			canceled = false;
		}
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }

	public int lastChoice() {
		
		for (int i=0; i<states.length; ++i)
			if (states[i] == true) return i;
		
		return -1;
	}
	
	public boolean wasCanceled() { return canceled; }
	
	private void close() {
		left = getX();
		top = getY();
		setVisible(false);
		dispose();
	}
	
	public void windowActivated(final WindowEvent e) { }
	
	public void windowClosed(final WindowEvent e) { }
	
	public void windowClosing(final WindowEvent e) { try {
		
		close();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void windowDeactivated(final WindowEvent e) { }
	
	public void windowDeiconified(final WindowEvent e) { }
	
	public void windowIconified(final WindowEvent e) { }
	
	public void windowOpened(final WindowEvent e) { }
	
}

// ***************************************************************************
final class VectorField extends Roi implements KeyListener {
	
	private float[][][] vf;
	private static float maxveclen = 1;
	
	VectorField(final ImagePlus imp, final float[][][] vf) {
		
		super(0,0,imp.getWidth(),imp.getHeight());
		setImage(imp);
		this.vf = vf;
		imp.setRoi(this);
		ic.addKeyListener(this);
	}
	
	public void draw(final Graphics g) {
		
		final float mag = (float)ic.getMagnification();
		if (mag > 4) {
			final int dx = (int)(mag/2.0);
			final int dy = (int)(mag/2.0);
			g.setColor(Color.red);
			final Rectangle vof = ic.getSrcRect();
			final int xmax = vof.x + vof.width;
			final int ymax = vof.y + vof.height;
			for (int y=vof.y; y<ymax; ++y)
				for (int x=vof.x; x<xmax; ++x) {
					final float scale = (255.0f - vf[0][y][x])*maxveclen*mag/255.0f;
					final int hvx = (int)(vf[1][y][x]*scale)/2;
					final int hvy = (int)(vf[2][y][x]*scale)/2;
					g.drawLine(ic.screenX(x)-hvx+dx,ic.screenY(y)-hvy+dy,ic.screenX(x)+hvx+dx,ic.screenY(y)+hvy+dy);
				}
		}
	}
	
	public void keyPressed(KeyEvent e) { try {
		
		if (e.getKeyCode() == KeyEvent.VK_UP) maxveclen += 0.05f;
		else if (e.getKeyCode() == KeyEvent.VK_DOWN) maxveclen -= 0.05f;
		
		ic.repaint();
		
	} catch (Throwable x) { NJ.catcher.uncaughtException(Thread.currentThread(),x); } }
	
	public void keyReleased(KeyEvent e) {}
	
	public void keyTyped(KeyEvent e) {}
	
}

// ***************************************************************************
final class Tracings {
	
	private int iCapacity = 20;
	private final int iCapInc = 20;
	private int iSize = 0;
	private Tracing[] tarray = new Tracing[iCapacity];
	
	void add(final Tracing tracing) {
		if (iSize == iCapacity) inccap();
		tarray[iSize++] = tracing;
		NJ.save = true;
	}
	
	private void inccap() {
		iCapacity += iCapInc;
		final Tracing[] newarray = new Tracing[iCapacity];
		for (int i=0; i<iSize; ++i) newarray[i] = tarray[i];
		tarray = newarray;
	}
	
	Tracing get(final int index) { return tarray[index]; }
	
	void remove(final int index) {
		for (int i1=index, i2=index+1; i2<iSize; ++i1, ++i2)
			tarray[i1] = tarray[i2];
		--iSize;
		NJ.save = true;
	}
	
	void reset() { iSize = 0; NJ.save = true; }
	
	int nrtracings() { return iSize; }
	
	boolean changed() {
		for (int w=0; w<iSize; ++w)
			if (tarray[w].changed()) return true;
		return false;
	}
	
	void draw(final Graphics g, final ImageCanvas imc) {
		for (int w=0; w<iSize; ++w) tarray[w].draw(g,imc);
	}
	
}

// ***************************************************************************
final class Tracing {
	
	private int iCapacity = 20;
	private final int iCapInc = 20;
	private int iSize = 0;
	private Segment[] sarray = null;
	
	private boolean hili = false;
	private boolean select = false;
	private boolean changed = false;
	
	private int type = 0;
	private int cluster = 0;
	private String label = "Default";
	
	private static int lastID = 0;
	private int ID;
	
	Tracing() {
		ID = ++lastID;
		sarray = new Segment[iCapacity];
	}
	
	Tracing(final int capacity) {
		ID = ++lastID;
		iCapacity = capacity;
		sarray = new Segment[iCapacity];
	}
	
	void id(final int id) { ID = id; if (ID > lastID) lastID = ID; NJ.save = true; }
	
	int id() { return ID; }
	
	static void resetID() { lastID = 0; }
	
	void add(final Segment segment) {
		if (iSize == iCapacity) inccap();
		sarray[iSize++] = segment;
		if (iSize > 1) sarray[iSize-1].first(sarray[iSize-2].last());
		changed = true;
		NJ.save = true;
	}
	
	private void inccap() {
		iCapacity += iCapInc;
		final Segment[] newarray = new Segment[iCapacity];
		for (int i=0; i<iSize; ++i) newarray[i] = sarray[i];
		sarray = newarray;
	}
	
	Segment get(final int index) { return sarray[index]; }
	
	int nrsegments() { return iSize; }
	
	double length() {
		double length = 0.0;
		for (int s=0; s<iSize; ++s)
		length += sarray[s].length();
		return length;
	}
	
	double distance2(final Point point) {
		double mindist2 = Double.MAX_VALUE;
		for (int s=0; s<iSize; ++s) {
			final double dist2 = sarray[s].distance2(point);
			if (dist2 < mindist2) mindist2 = dist2;
		}
		return mindist2;
	}
	
	void values(final ByteProcessor bp, final Values values) {
		for (int s=0; s<iSize; ++s)
			sarray[s].values(bp,values);
		final Point last = sarray[iSize-1].last();
		values.add(bp.getInterpolatedValue(last.x,last.y));
	}
	
	boolean changed() {	return changed; }
	
	void select(final boolean select) {
		if (this.select != select) {
			this.select = select;
			changed = true;
		}
	}
	
	boolean selected() { return select; }
	
	void highlight(final boolean hili) {
		if (this.hili != hili) {
			this.hili = hili;
			changed = true;
		}
	}
	
	boolean highlighted() { return hili; }
	
	void type(final int type) {
		if (this.type != type) {
			this.type = type;
			changed = true;
			NJ.save = true;
		}
	}
	
	int type() { return type; }
	
	void cluster(final int cluster) {
		if (this.cluster != cluster) {
			this.cluster = cluster;
			NJ.save = true;
		}
	}
	
	int cluster() { return cluster; }
	
	void label(final String label) {
		if (!this.label.equals(label)) {
			this.label = label;
			NJ.save = true;
		}
	}
	
	String label() { return label; }
	
	void draw(final Graphics g, final ImageCanvas imc) {
		final Color drawcolor = (hili || select) ? NJ.HIGHLIGHTCOLOR : NJ.typecolors[type];
		for (int s=0; s<iSize; ++s) sarray[s].draw(g,imc,drawcolor);
		changed = false;
	}
	
}

// ***************************************************************************
final class Segment {
	
	private int iCapacity = 500;
	private final int iCapInc = 500;
	private int iSize = 0;
	private Point[] parray = null;
	
	Segment() {
		parray = new Point[iCapacity];
	}
	
	Segment(final int capacity) {
		iCapacity = capacity;
		parray = new Point[iCapacity];
	}
	
	void add(final Point point) {
		if (iSize == iCapacity) inccap();
		parray[iSize++] = point;
	}
	
	private void inccap() {
		iCapacity += iCapInc;
		final Point[] newparray = new Point[iCapacity];
		for (int i=0; i<iSize; ++i) newparray[i] = parray[i];
		parray = newparray;
	}
	
	Point first() { return parray[0]; }
	
	void first(final Point point) { parray[0] = point; }
	
	Point last() { return parray[iSize-1]; }
	
	void last(final Point point) { parray[iSize-1] = point; }
	
	Point get(final int index) { return parray[index]; }
	
	void get(final int index, final Point point) {
		point.x = parray[index].x;
		point.y = parray[index].y;
	}
	
	int nrpoints() { return iSize; }
	
	void reset() { iSize = 0; }
	
	Segment duplicate() {
		final Segment segment = new Segment(iCapacity);
		segment.iSize = iSize;
		for (int i=0; i<iSize; ++i)
			segment.parray[i] = new Point(parray[i].x,parray[i].y);
		return segment;
	}
	
	double length() {
		double length = 0.0;
		final double pw = NJ.calibrate ? NJ.imageplus.getCalibration().pixelWidth : 1;
		final double ph = NJ.calibrate ? NJ.imageplus.getCalibration().pixelHeight : 1;
		if (iSize > 1) for (int i=1; i<iSize; ++i) {
			final double dx = (parray[i].x - parray[i-1].x)*pw;
			final double dy = (parray[i].y - parray[i-1].y)*ph;
			length += Math.sqrt(dx*dx + dy*dy);
		}
		return length;
	}
	
	double distance2(final Point point) {
		double mindist2 = Double.MAX_VALUE;
		// Minimum distance to vertices:
		for (int i=0; i<iSize; ++i) {
			final double dx = point.x - parray[i].x;
			final double dy = point.y - parray[i].y;
			final double dist2 = dx*dx + dy*dy;
			if (dist2 < mindist2) mindist2 = dist2;
		}
		// Minimum distance to edges:
		for (int i=1, im1=0; i<iSize; ++i, ++im1) {
			final double v12x = parray[i].x - parray[im1].x;
			final double v12y = parray[i].y - parray[im1].y;
			final double v13x = point.x - parray[im1].x;
			final double v13y = point.y - parray[im1].y;
			final double inprod = v12x*v13x + v12y*v13y;
			if (inprod >= 0.0f) {
				final double v12len2 = v12x*v12x + v12y*v12y;
				if (inprod <= v12len2) {
					final double v13len2 = v13x*v13x + v13y*v13y;
					final double dist2 = v13len2 - inprod*inprod/v12len2;
					if (dist2 < mindist2) mindist2 = dist2;
				}
			}
		}
		return mindist2;
	}
	
	void values(final ByteProcessor bp, final Values values) {
		final int ssfactor = NJ.interpolate ? NJ.subsamplefactor : 1;
		for (int i=1, im1=0; i<iSize; ++i, ++im1) {
			final double dx = (parray[i].x - parray[im1].x)/ssfactor;
			final double dy = (parray[i].y - parray[im1].y)/ssfactor;
			for (int j=0; j<ssfactor; ++j) {
				final double x = parray[im1].x + j*dx;
				final double y = parray[im1].y + j*dy;
				values.add(bp.getInterpolatedValue(x,y));
			}
		}
	}
	
	void reverse() {
		final int iHalf = iSize/2;
		for (int b=0, e=iSize-1; b<iHalf; ++b, --e) {
			final Point tmp = parray[b]; parray[b] = parray[e]; parray[e] = tmp;
		}
	}
	
	void draw(final Graphics g, final ImageCanvas imc, final Color color) {
		final Rectangle vof = imc.getSrcRect();
		final double mag = imc.getMagnification();
		final int dx = (int)(mag/2.0);
		final int dy = (int)(mag/2.0);
		g.setColor(color);
		if (iSize > 1) for (int i=1; i<iSize; ++i) {
			g.drawLine(
				dx + (int)((parray[i].x - vof.x)*mag),
				dy + (int)((parray[i].y - vof.y)*mag),
				dx + (int)((parray[i-1].x - vof.x)*mag),
				dy + (int)((parray[i-1].y - vof.y)*mag)
			);
		}
	}
	
}

// ***************************************************************************
final class Values {
	
	private int capacity = 1000;
	private final int capinc = 1000;
	private int size = 0;
	private double[] varray = new double[capacity];
	private double sum, mean, sd, min, max;
	
	void add(final double value) {
		if (size == capacity) inccap();
		varray[size++] = value;
	}
	
	private void inccap() {
		capacity += capinc;
		final double[] newarray = new double[capacity];
		for (int i=0; i<size; ++i) newarray[i] = varray[i];
		varray = newarray;
	}
	
	void reset() { size = 0; }
	
	void stats() {
		
		if (size == 0) {
			sum = mean = sd = min = max = 0;
		} else {
			double val = 0;
			sum = min = max = varray[0];
			for (int i=1; i<size; ++i) {
				val = varray[i];
				sum += val;
				if (val < min) min = val;
				else if (val > max) max = val;
			}
			mean = sum/size;
			double sumdev2 = 0;
			for (int i=0; i<size; ++i) {
				val = varray[i] - mean;
				sumdev2 += val*val;
			}
			sd = Math.sqrt(sumdev2/(size-1));
		}
	}
	
	int count() { return size; }
	
	double sum() { return sum; }
	
	double mean() { return mean; }
	
	double sd() { return sd; }
	
	double min() { return min; }
	
	double max() { return max; }
	
}

// ***************************************************************************
final class Dijkstra {
	
	private final int INFINITE = 2147483647;
	private final int PROCESSED = 2147483647;
	private final int FREE = 2147483646;
	
	private int[] ccost = null;
	private int[] istat = null;
	private byte[][] dirs = null;
	
	// Computes the shortest path based on the given cost values and
	// vectors. The first index is the image index: element 0 contains
	// the cost image, element 1 the x-component of the vector field,
	// and element 2 the y-component of the vector field. The second and
	// third index correspond to, respectively, the y- and x-coordinate.
	//
	// The returned image contains for every pixel the direction to the
	// predecessing pixel along the shortest path. The first index is
	// the y-coordinate and the second the x-coordinate. Note that if in
	// a series of calls to this method the cost image keeps the same
	// dimensions, the returned handle will be the same for every
	// call. That is to say, the directions image is reallocated only
	// when the cost image changes dimensions. Otherwise it is reused in
	// order to gain speed. The direction values should be interpreted
	// as follows:
	//
	// 0 = go directly to starting point
	// 1 = go one up, one left
	// 2 = go one up
	// 3 = go one up, one right
	// 4 = go one left
	// 5 = go one right
	// 6 = go one down, one left
	// 7 = go one down
	// 8 = go one down, one right
	//
	byte[][] run(final float[][][] costvector, final Point startpoint) {
		
		// Initialize variables and handles:
		final float[][] costimage = costvector[0];
		final float[][] costfieldx = costvector[1];
		final float[][] costfieldy = costvector[2];
		
		final int iYSize = costimage.length;
		final int iXSize = costimage[0].length;
		final int iYSizem1 = iYSize - 1;
		final int iXSizem1 = iXSize - 1;
		
		final int iStartY = startpoint.y;
		final int iStartX = startpoint.x;
		if (iStartY <= 0 || iStartY >= iYSizem1 || iStartX <= 0 || iStartX >= iXSizem1)
			throw new IllegalArgumentException("Starting point on or outside border of image");
		final int vstart = iStartY*iXSize + iStartX;
		
		final int iNrPixels = iYSize*iXSize;
		if (dirs == null || dirs.length != iYSize || dirs[0].length != iXSize) {
			dirs = new byte[iYSize][iXSize];
			ccost = new int[iNrPixels];
			istat = new int[iNrPixels];
		}
		
		// Mask border pixels and pixels outside window:
		final int iXSizem2 = iXSize - 2;
		final int iYSizem2 = iYSize - 2;
		int iLX = 1; int iLY = 1;
		int iHX = iXSizem2; int iHY = iYSizem2;
		final int iHalfWinSize = NJ.dijkrange/2;
		if (NJ.dijkrange < iXSizem2) {
			iLX = iStartX - iHalfWinSize;
			iHX = iStartX + iHalfWinSize;
			if (iLX < 1) { iLX = 1; iHX = NJ.dijkrange; }
			if (iHX > iXSizem2) { iHX = iXSizem2; iLX = iXSizem1 - NJ.dijkrange; }
		}
		if (NJ.dijkrange < iYSizem2) {
			iLY = iStartY - iHalfWinSize;
			iHY = iStartY + iHalfWinSize;
			if (iLY < 1) { iLY = 1; iHY = NJ.dijkrange; }
			if (iHY > iYSizem2) { iHY = iYSizem2; iLY = iYSizem1 - NJ.dijkrange; }
		}
		for (int y=0, i=0; y<iLY; ++y)
			for (int x=0; x<iXSize; ++x, ++i)
				{ istat[i] = PROCESSED; dirs[y][x] = 0; }
		for (int y=iHY+1, i=(iHY+1)*iXSize; y<iYSize; ++y)
			for (int x=0; x<iXSize; ++x, ++i)
				{ istat[i] = PROCESSED; dirs[y][x] = 0; }
		for (int y=iLY, i=iLY*iXSize; y<=iHY; ++y, i+=iXSize) {
			for (int x=0, j=i; x<iLX; ++x, ++j)
				{ istat[j] = PROCESSED; dirs[y][x] = 0; }
			for (int x=iHX+1, j=i+iHX+1; x<iXSize; ++x, ++j)
				{ istat[j] = PROCESSED; dirs[y][x] = 0; }
		}
		
		// Initialize arrays within window:
		for (int y=iLY, i=iLY*iXSize; y<=iHY; ++y, i+=iXSize)
			for (int x=iLX, j=i+iLX; x<=iHX; ++x, ++j) {
				dirs[y][x] = 0;
				ccost[j] = INFINITE;
				istat[j] = FREE;
			}
		
		// Initialize queue:
		final QueueElement[] queue = new QueueElement[256];
		for (int i=0; i<256; ++i) queue[i] = new QueueElement();
		
		// Define relative positions of neighboring points:
		final int[] rpos = new int[9];
		rpos[8] = -iXSize - 1;
		rpos[7] = -iXSize;
		rpos[6] = -iXSize + 1;
		rpos[5] = -1;
		rpos[4] = 1;
		rpos[3] = iXSize - 1;
		rpos[2] = iXSize;
		rpos[1] = iXSize + 1;
		rpos[0] = 0;
		
		// The following lines implement the shortest path algorithm as
		// proposed by E. W. Dijkstra, A Note on Two Problems in
		// Connexion with Graphs, Numerische Mathematik, vol. 1, 1959,
		// pp. 269-271. Note, however, that this is a special
		// implementation for discrete costs based on a circular queue.
		
		// Initialization:
		ccost[vstart] = 0;
		int pindex = -1;
		int cindex = 0;
		queue[cindex].add(vstart);
		boolean bQueue = true;
		
		final float gamma = NJ.gamma;
		final float invgamma = 1 - gamma;
		
		// Path searching:
		while (bQueue) {
			
			final int vcurrent = queue[cindex].remove();
			istat[vcurrent] = PROCESSED;
			final int iCY = vcurrent/iXSize;
			final int iCX = vcurrent%iXSize;
			
			for (int i=1; i<9; ++i) {
				final int vneighbor = vcurrent + rpos[i];
				if (istat[vneighbor] != PROCESSED) {
					final int iNY = vneighbor/iXSize;
					final int iNX = vneighbor%iXSize;
					float fDY = iNY - iCY;
					float fDX = iNX - iCX;
					final float fLen = (float)Math.sqrt(fDY*fDY + fDX*fDX);
					fDY /= fLen; fDX /= fLen;
					final int iCurCCost = ccost[vneighbor];
					final int iNewCCost = ccost[vcurrent] +
					(int)(gamma*costimage[iNY][iNX] +
						invgamma*127*(float)(Math.sqrt(1 - Math.abs(costfieldy[iCY][iCX]*fDY + costfieldx[iCY][iCX]*fDX)) +
						Math.sqrt(1 - Math.abs(costfieldy[iNY][iNX]*fDY + costfieldx[iNY][iNX]*fDX))));
					if (iNewCCost < iCurCCost) {
						ccost[vneighbor] = iNewCCost;
						dirs[iNY][iNX] = (byte)i;
						if (istat[vneighbor] == FREE)
							istat[vneighbor] = queue[iNewCCost & 255].add(vneighbor);
						else {
							final int iVIndex = iCurCCost & 255;
							final int iEIndex = istat[vneighbor];
							queue[iVIndex].remove(iEIndex);
							istat[queue[iVIndex].get(iEIndex)] = iEIndex;
							istat[vneighbor] = queue[iNewCCost & 255].add(vneighbor);
						}
					}
				}
			}
			
			pindex = cindex;
			while (queue[cindex].size() == 0) {
				++cindex; cindex &= 255;
				if (cindex == pindex) { bQueue = false; break; }
			}
		}
		
		return dirs;
	}
	
}

// ***************************************************************************
final class QueueElement {
	
	private int iCapacity = 40;
	private final int iCapInc = 40;
	private int iLast = -1;
	private int[] iarray = new int[iCapacity];
	
	int add(final int element) {
		if (++iLast == iCapacity) inccap();
		iarray[iLast] = element;
		return iLast;
	}
	
	private void inccap() {
		iCapacity += iCapInc;
		final int[] newarray = new int[iCapacity];
		for (int i=0; i<iLast; ++i) newarray[i] = iarray[i];
		iarray = newarray;
	}
	
	int get(final int index) { return iarray[index]; }
	
	int remove() { return iarray[iLast--]; }
	
	int remove(final int index) {
		final int i = iarray[index];
		iarray[index] = iarray[iLast--];
		return i;
	}
	
	int size() { return (iLast + 1); }
	
}

// ***************************************************************************
final class Costs {
	
	// Returns a cost image and vector field computed from the
	// eigenvalues and eigenvectors of the Hessian of the input
	// image. The second and third index correspond, respectively, to
	// the y- and x-coordinate. The first index selects the image:
	// element 0 contains the cost image, element 1 the x-component of
	// the vector field, and element 2 the y-component of the vector
	// field. The gray-value at any point in the cost image is computed
	// from the eigenvalues of the Hessian matrix at that
	// point. Specifically, the method computes both (adjusted)
	// eigenvalues and selects the one with the largest magnitude. Since
	// in the NeuronJ application we are interested in bright structures
	// on a dark background, the method stores this absolute eigenvalue
	// only if the actual eigenvalue is negative. Otherwise it stores a
	// zero. The eventual largest-eigenvalue image is inverted and
	// scaled to gray-value range [0,255]. The vector at any point in
	// the vector field is simply the eigenvector corresponding to the
	// largest absolute eigenvalue at that point.
	public float[][][] run(final ByteProcessor image, final boolean bright, final float scale) {
		
		NJ.log("Cost image and vector field from Hessian at scale "+scale+" ...");
		final Progressor pgs = new Progressor();
		pgs.display(true); pgs.enforce(true);
		
		// Compute Hessian components:
		pgs.status("Computing derivatives...");
		final Image inImage = Image.wrap(new ImagePlus("",image));
		final Differentiator differ = new Differentiator();
		differ.progressor.parent(pgs);
		pgs.range(0.0,0.3); final Image Hxx = differ.run(inImage,scale,2,0,0);
		pgs.range(0.3,0.6); final Image Hxy = differ.run(inImage,scale,1,1,0);
		pgs.range(0.6,0.9); final Image Hyy = differ.run(inImage,scale,0,2,0);
		
		// Compute and select adjusted eigenvalues and eigenvectors:
		pgs.status("Computing eigenimages...");
		final Dimensions dims = inImage.dimensions();
		pgs.steps(dims.y); pgs.range(0.9,1.0); pgs.start();
		final float[] ahxx = (float[])Hxx.imageplus().getStack().getPixels(1);
		final float[] ahxy = (float[])Hxy.imageplus().getStack().getPixels(1);
		final float[] ahyy = (float[])Hyy.imageplus().getStack().getPixels(1);
		final float[][][] evv = new float[3][dims.y][dims.x];
		final float[][] value = evv[0];
		final float[][] vectx = evv[1];
		final float[][] vecty = evv[2];
		final float inv = bright ? 1 : -1;
		for (int y=0, i=0; y<dims.y; ++y) {
			for (int x=0; x<dims.x; ++x, ++i) {
				final float b1 = inv*(ahxx[i] + ahyy[i]);
				final float b2 = inv*(ahxx[i] - ahyy[i]);
				final float d = (float)Math.sqrt(4*ahxy[i]*ahxy[i] + b2*b2);
				final float L1 = (b1 + 2*d)/3.0f;
				final float L2 = (b1 - 2*d)/3.0f;
				final float absL1 = Math.abs(L1);
				final float absL2 = Math.abs(L2);
				if (absL1 > absL2) {
					if (L1 > 0) value[y][x] = 0;
					else value[y][x] = absL1;
					vectx[y][x] = b2 - d;
				} else {
					if (L2 > 0) value[y][x] = 0;
					else value[y][x] = absL2;
					vectx[y][x] = b2 + d;
				}
				vecty[y][x] = 2*inv*ahxy[i];
			}
			pgs.step();
		}
		pgs.stop();
		
		// Convert eigenvalues to costs and normalize eigenvectors:
		float minval = value[0][0];
		float maxval = minval;
		for (int y=0; y<dims.y; ++y)
			for (int x=0; x<dims.x; ++x)
				if (value[y][x] > maxval) maxval = value[y][x];
				else if (value[y][x] < minval) minval = value[y][x];
		final float roof = 255;
		final float offset = 0;
		final float factor = (roof - offset)/(maxval - minval);
		final byte[] pixels = (byte[])image.getPixels();
		for (int y=0, i=0; y<dims.y; ++y)
			for (int x=0; x<dims.x; ++x, ++i) {
				value[y][x] = roof - (value[y][x] - minval)*factor;
				final float vectlen = (float)Math.sqrt(vectx[y][x]*vectx[y][x] + vecty[y][x]*vecty[y][x]);
				if (vectlen > 0) { vectx[y][x] /= vectlen; vecty[y][x] /= vectlen; }
			}
		
		return evv;
	}
	
}

// ***************************************************************************
