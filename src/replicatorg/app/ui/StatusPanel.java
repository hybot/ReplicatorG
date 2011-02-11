package replicatorg.app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeTableXYDataset;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import replicatorg.app.Base;
import replicatorg.app.MachineController;
import replicatorg.drivers.Driver;
import replicatorg.drivers.RetryException;
import replicatorg.drivers.gen3.Sanguino3GDriver;
import replicatorg.machine.model.ToolModel;
import replicatorg.util.Point5d;

/**
 * Report various status bits of a printer, while in operation.
 **/
public class StatusPanel extends JPanel {
    private ToolModel toolModel;
    private MachineController machine;
    private StatusPanelWindow window;

    JLabel xyzBox;
    JCheckBox xyzEnable;

    JLabel abBox;
    JCheckBox abEnable;

    JLabel feedRateBox;
    JCheckBox feedRateEnable;

    JLabel pwmBox;
    JCheckBox pwmEnable;

    JLabel rpmBox;
    JCheckBox rpmEnable;

    JLabel targetTempBox;
    JCheckBox targetTempEnable;

    JLabel currentTempBox;
    JCheckBox currentTempEnable;
	
    JLabel platformTargetTempBox;
    JCheckBox platformTargetTempEnable;

    JLabel platformCurrentTempBox;
    JCheckBox platformCurrentTempEnable;

    JCheckBox logFileEnable;
    JComboBox updateBox;

    PrintWriter logFileWriter = null;
    String fileName = null;
    boolean useCSV = false;
    
    final private static Color targetColor = Color.BLUE;
    final private static Color measuredColor = Color.RED;
    final private static Color platformTargetColor = Color.YELLOW;
    final private static Color platformMeasuredColor = Color.WHITE;

    final private static String[] updateStrings = { ".5", "1", "2", "5", "10",
						    "30", "60", "120" };

    long startMillis = System.currentTimeMillis();

    private TimeTableXYDataset measuredDataset = new TimeTableXYDataset();
    private TimeTableXYDataset targetDataset = new TimeTableXYDataset();
    private TimeTableXYDataset platformMeasuredDataset = 
	new TimeTableXYDataset();
    private TimeTableXYDataset platformTargetDataset = new TimeTableXYDataset();

    protected Driver driver;
	
    private final Dimension labelMinimumSize = new Dimension(175, 25);
    private final Dimension textBoxSize = new Dimension(65, 25);

    /**
     * Make a label with an icon indicating its color on the graph.
     * @param text The text of the label
     * @param c The color of the matching line on the graph
     * @return the generated label
     */
    private JLabel makeKeyLabel(String text, Color c) {
	BufferedImage image = 
	    new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
	Graphics g = image.getGraphics();
	g.setColor(c);
	g.fillRect(0,0,10,10);
	Icon icon = new ImageIcon(image);
	return new JLabel(text,icon,SwingConstants.LEFT);
    }

    public ChartPanel makeChart(ToolModel t) {
	JFreeChart chart = ChartFactory.createXYLineChart(null, null, null, 
				measuredDataset, PlotOrientation.VERTICAL, 
				false, false, false);
	chart.setBorderVisible(false);
	chart.setBackgroundPaint(null);
	XYPlot plot = chart.getXYPlot();
	ValueAxis axis = plot.getDomainAxis();
	axis.setLowerMargin(0);
	axis.setFixedAutoRange(3L*60L*1000L); // auto range to three minutes
	TickUnits unitSource = new TickUnits();
	unitSource.add(new NumberTickUnit(60L*1000L)); // minutes
	unitSource.add(new NumberTickUnit(1L*1000L)); // seconds
	axis.setStandardTickUnits(unitSource);
	axis.setTickLabelsVisible(false); // We don't need to see millisecs
	axis = plot.getRangeAxis();

	// set temperature range from 0 to 300
	// degrees C so you can see overshoots 
	axis.setRange(0, 300);

	// Tweak L&F of chart
	//((XYAreaRenderer)plot.getRenderer()).setOutline(true);
	XYStepRenderer renderer = new XYStepRenderer();
	plot.setDataset(1, targetDataset);
	plot.setRenderer(1, renderer);
	plot.getRenderer(1).setSeriesPaint(0, targetColor);
	plot.getRenderer(0).setSeriesPaint(0, measuredColor);
	if (t.hasHeatedPlatform()) {
	    plot.setDataset(2, platformMeasuredDataset);
	    plot.setRenderer(2, new XYLineAndShapeRenderer(true,false)); 
	    plot.getRenderer(2).setSeriesPaint(0, platformMeasuredColor);
	    plot.setDataset(3, platformTargetDataset);
	    plot.setRenderer(3, new XYStepRenderer()); 
	    plot.getRenderer(3).setSeriesPaint(0, platformTargetColor);
	}
	plot.setDatasetRenderingOrder(DatasetRenderingOrder.REVERSE);
	ChartPanel chartPanel = new ChartPanel(chart);
	chartPanel.setPreferredSize(new Dimension(400,160));
	chartPanel.setOpaque(false);
	return chartPanel;
    }

    private JLabel makeLabel(String text) {
	JLabel label = new JLabel();
	label.setText(text);
	label.setMinimumSize(labelMinimumSize);
	label.setMaximumSize(labelMinimumSize);
	label.setPreferredSize(labelMinimumSize);
	label.setHorizontalAlignment(JLabel.LEFT);
	return label;
    }

    private JLabel makeBox(String text) {
	JLabel label = new JLabel();
	label.setText(text == null ? "" : text);
	label.setMinimumSize(textBoxSize);
	label.setMaximumSize(textBoxSize);
	label.setPreferredSize(textBoxSize);
	label.setBorder(BorderFactory.createCompoundBorder(
			         BorderFactory.createEtchedBorder(),
				 BorderFactory.createEmptyBorder(0, 3, 0, 0)));
	label.setHorizontalAlignment(JLabel.LEFT);
	return label;
    }
	
    public StatusPanel(MachineController machine, ToolModel t, 
		       final StatusPanelWindow window) {
	this.machine = machine;
	this.toolModel = t;
	this.window = window;
		
	Dimension panelSize = new Dimension(420, 30);

	driver = machine.getDriver();

	// create our initial panel
	setLayout(new MigLayout());
	    
	{
	    JLabel label = makeLabel("Driver");
	    JLabel driverLabel = new JLabel();
	    driverLabel.setText(driver.getDriverName());
	    add(label);
	    add(driverLabel, "wrap");
	}

	{
	    JLabel label = makeLabel("Firmware");
	    JLabel firmwareLabel = new JLabel();
	    String version = "Motherboard v" + driver.getVersion();
	    if(driver instanceof Sanguino3GDriver) {
		version += "/ Toolhead v" +
		    ((Sanguino3GDriver)driver).getToolVersion();
	    }
	    firmwareLabel.setText(version);
	    add(label);
	    add(firmwareLabel, "wrap");
	}

	add(new JLabel(""), "span 2");
	add(new JLabel("Enable"), "wrap");

	{
	    xyzBox = makeBox(getPositionXyz(null));
	    xyzEnable = makeCheckBox("", "status.xyz", false);

	    Dimension size = new Dimension(170, 25);
	    xyzBox.setPreferredSize(size);
	    xyzBox.setMinimumSize(size);
	    xyzBox.setMaximumSize(size);

	    add(makeLabel("X, Y, Z"));
	    add(xyzBox);
	    add(xyzEnable, "wrap");
	}

	{
	    abBox = makeBox(getPositionAb(null));
	    abEnable = makeCheckBox("", "status.ab", false);

	    Dimension size = new Dimension(120, 25);
	    abBox.setPreferredSize(size);
	    abBox.setMinimumSize(size);
	    abBox.setMaximumSize(size);

	    add(makeLabel("A, B"));
	    add(abBox);
	    add(abEnable, "wrap");
	}

	// create our motor options
	if (t.hasMotor()) {
	    // Due to current implementation issues, we need to send the PWM
	    // before the RPM for a stepper motor. Thus we display both 
	    // controls in these cases. This shouldn't be necessary for a
	    // Gen4 stepper extruder.
	    if (t.getMotorStepperAxis() == null) {
		// our motor speed vars
		pwmBox = makeBox(null);
		pwmEnable = makeCheckBox("", "status.pwm", false);
		
		add(makeLabel("Motor Speed (PWM)"));
		add(pwmBox);
		add(pwmEnable, "wrap");
	    }

	    if (t.motorHasEncoder() || t.motorIsStepper()) {
		// our motor speed vars
		rpmBox = makeBox(null);
		rpmEnable = makeCheckBox("", "status.rpm", false);

		add(makeLabel("Motor Speed (RPM)"));
		add(rpmBox);
		add(rpmEnable, "wrap");
	    }
	}

	{
	    feedRateBox = makeBox(null);
	    feedRateEnable = makeCheckBox("", "status.feedrate", false);

	    add(makeLabel("Feed Rate"));
	    add(feedRateBox);
	    add(feedRateEnable, "wrap");
	}

	// our tool head temperature fields
	if (t.hasHeater()) {
	    targetTempBox = makeBox(null);
	    targetTempEnable = makeCheckBox("", "status.target_temp", true);

	    add(makeKeyLabel("Target Temp (\u00b0C)", targetColor));
	    add(targetTempBox);
	    add(targetTempEnable, "wrap");

	    currentTempBox = makeBox("");
	    currentTempEnable = makeCheckBox("", "status.current_temp", true);

	    add(makeKeyLabel("Current Temp (\u00b0C)", measuredColor));
	    add(currentTempBox);
	    add(currentTempEnable, "wrap");
	}

	// our heated platform fields
	if (t.hasHeatedPlatform()) {
	    platformTargetTempBox = makeBox(null);
	    platformTargetTempEnable = 
		makeCheckBox("", "status.platform.target_temp", true);

	    add(makeKeyLabel("Platform Target Temp (\u00b0C)",
			     platformTargetColor));
	    add(platformTargetTempBox);
	    add(platformTargetTempEnable, "wrap");

	    platformCurrentTempBox = makeBox("");
	    platformCurrentTempEnable = 
		makeCheckBox("", "status.platform.current_temp", true);

	    add(makeKeyLabel("Platform Current Temp (\u00b0C)",
			     platformMeasuredColor));
	    add(platformCurrentTempBox);
	    add(platformCurrentTempEnable, "wrap");
	}

	if (t.hasHeater() || t.hasHeatedPlatform()) {
	    add(makeLabel("Temperature Chart"), "growx,spanx,wrap");
	    add(makeChart(t), "growx,span 2,wrap");
	}

	{
	    final JPanel panel = new JPanel();
	    panel.setBorder(BorderFactory.createTitledBorder("Data Log"));
	    panel.setLayout(new MigLayout());

	    fileName = Base.preferences.get("status.log_file", fileName);

	    final JLabel fileLabel = makeLabel(getName(fileName));
	    final JFileChooser chooser = new JFileChooser(fileName);

	    JButton saveButton = new JButton("Select File ...");
	    saveButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    int returnVal = chooser.showSaveDialog(window);
		    if(returnVal == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if(file == null || 
			   (file.exists() && !file.canWrite())) {
			    fileLabel.setText("Cannot write " +
					      ((file == null) ? 
					       "File" : file.getName()));
			    fileLabel.setForeground(Color.RED);
			    logFileWriter = null;
			    fileName = null;
			    return;
			}

			fileName = file.getAbsolutePath();
			Base.preferences.put("status.log_file", fileName);

			fileLabel.setText(file.getName());
			fileLabel.setForeground(Color.BLACK);
		    }
		}
	    });

	    final JRadioButton taggedBox = new JRadioButton("Tagged");
	    taggedBox.setSelected(!useCSV);

	    final JRadioButton csvBox = new JRadioButton("CSV");
	    csvBox.setSelected(useCSV);

	    ButtonGroup group = new ButtonGroup();
	    group.add(taggedBox);
	    group.add(csvBox);

	    ActionListener radioListener = new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    useCSV = csvBox.isSelected();
		}		    
	    };

	    taggedBox.addActionListener(radioListener);
	    csvBox.addActionListener(radioListener);

	    // this is the one enable checkbox that doesn't save state.
	    // -- the user has to start logging each time it's desired.
	    logFileEnable = new JCheckBox("", false);
	    logFileEnable.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    if(fileName == null) {
			logFileEnable.setSelected(false);
			return;
		    }

		    if(!logFileEnable.isSelected()) {
			logFileWriter = null;
			return;
		    }

		    try {
			logFileWriter = new PrintWriter(
					  new FileOutputStream(fileName, true));
		    } catch (FileNotFoundException fnfe) {
		        logFileWriter = null;
			Base.logger.warning("Cannot open " + fileName);
		    } finally {
			if(logFileWriter == null) {
			    logFileEnable.setSelected(false);
			    fileLabel.setText("Cannot write " +
					      ((fileName == null) ?
					       "File" : getName(fileName)));
			    fileLabel.setForeground(Color.RED);
			}
		    }
		}
	    });

	    panel.add(saveButton, "growx, span 2");

	    panel.add(fileLabel, "wrap");
	    panel.add(taggedBox);
	    panel.add(csvBox);
	    add(panel, "span 2");
	    add(logFileEnable, "wrap");
	}

	{
	    JLabel label = new JLabel("Update Interval (sec)");
	    updateBox = new JComboBox(updateStrings);
	    updateBox.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent event) {
		    String interval = (String) updateBox.getSelectedItem();
		    try {
			int newInterval = 
			    (int)(Double.parseDouble(interval) * 1000);
			if(newInterval > 50) {
			    window.setUpdateInterval(newInterval);
			} else {
			    Base.logger.warning("Update interval too small:" +
						newInterval);
			}
		    } catch (NumberFormatException nfe) {
			Base.logger.warning("Can't set update interval = " +
					    interval);
		    }
		}
		    
	    });

	    // use 2 seconds as the default.
	    // if you change this, change the intial delay in
	    // StatusPanelWindow
	    updateBox.setSelectedItem("2");
	    add(label);
	    add(updateBox, "wrap");
	}

    }

    JCheckBox makeCheckBox(String text, final String pref, boolean defaultVal) {
	JCheckBox cb = new JCheckBox(text);
	cb.setSelected(Base.preferences.getBoolean(pref, defaultVal));
	cb.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    JCheckBox box = (JCheckBox)e.getSource();
		    Base.preferences.putBoolean(pref, box.isSelected());
		}
	});
	return cb;
    }

    ToolModel getTool() {
	return toolModel;
    }

    // get just the trailing file name comoponent of a path
    String getName(String filePath) {
	if(filePath == null) {
	    return null;
	}
	int i = filePath.lastIndexOf(System.getProperty("file.separator"));
	if(i >= 0) {
	    return filePath.substring(i+1);
	}
	return filePath;
    }

    public String getPositionXyz(Point5d position) {
	if(position == null) {
	    return "unknown";
	}
	return String.format("%.2f, %.2f, %.2f", 
			     position.x(), position.y(), position.z());
    }
	    
    public String getPositionAb(Point5d position) {
	if(position == null) {
	    return "unknown";
	}
	return String.format("%.2f, %.2f", position.a(), position.b());
    }
	    
    class LogElement {
	String tag;
	String value;

	List<LogElement> children;

	LogElement(String tag, String value) {
	    this.tag = tag;
	    this.value = value;
	}

	LogElement(String tag, int value) {
	    this(tag, String.valueOf(value));
	}

	LogElement(String tag, long value) {
	    this(tag, String.valueOf(value));
	}

	LogElement(String tag, double value) {
	    this(tag, String.valueOf(value));
	}
	
	void add(LogElement child) {
	    if(children == null) {
		children = new ArrayList<LogElement>();
	    }
	    children.add(child);
	}

	void log() {
	    log("");
	}

	// this assumes the value will not contain < or >
	void log(String indent) {
	    if(logFileWriter == null || !logFileEnable.isSelected()) {
		return;
	    }

	    if(useCSV) {
		StringBuilder buf = new StringBuilder();
		buf.append(value);
		if(children != null) {
		    for(LogElement child : children) {
			buf.append(",").append(child.value);
		    }
		}
		buf.append("\n");
		logFileWriter.append(buf.toString());
	    } else {
		if(children != null) {
		    logFileWriter.append(String.format("%s<%s>\n%s%s\n",
						       indent,
						       tag,
						       indent + "  ",
						       value));

		    for(LogElement child : children) {
			child.log(indent + "  ");
		    }

		    logFileWriter.append(String.format("%s</%s>\n",
						       indent, tag));
		} else {
		    //compact form for childless nodes
		    logFileWriter.append(String.format("%s<%s>%s</%s>\n",
						       indent,
						       tag,
						       value,
						       tag));
		}
	    }
	    logFileWriter.flush();
	}
    }

    synchronized public void updateStatus() {
	if (machine.getModel().currentTool() == toolModel) {

	    Second second = 
		new Second(new Date(System.currentTimeMillis() - startMillis));

	    LogElement root  = 
		new LogElement("time", System.currentTimeMillis());

	    // chid logElements should be added here in the same 
	    // order as their UI elements are displayed

	    Point5d position = null;

	    if(xyzEnable.isSelected() || abEnable.isSelected()) {
		position = driver.getCurrentPosition();
	    }

	    if(xyzEnable.isSelected()) {
		String xyz = getPositionXyz(position);
		xyzBox.setText(xyz);
		root.add(new LogElement("xyz", xyz));
	    }

	    if(abEnable.isSelected()) {
		String ab = getPositionAb(position);
		abBox.setText(ab);
		root.add(new LogElement("ab", ab));
	    }

	    if (toolModel.hasMotor()) {
		if (toolModel.getMotorStepperAxis() == null) {
		    if(pwmEnable.isSelected()) {
			int pwm = driver.getMotorSpeedPWM();
			pwmBox.setText(String.valueOf(pwm));
			root.add(new LogElement("pwm", pwm));
		    }
		}
		if (toolModel.motorHasEncoder() || toolModel.motorIsStepper()) {
		    if(rpmEnable.isSelected()) {
			double rpm = driver.getMotorRPM();
			rpmBox.setText(String.valueOf(rpm));
			root.add(new LogElement("rpm", rpm));
		    }
		}
	    }

	    if(feedRateEnable.isSelected()) {
		String feedRate = Double.toString(driver.getCurrentFeedrate());
		feedRateBox.setText(feedRate);
		root.add(new LogElement("feedRate", feedRate));
	    }

	    if (toolModel.hasHeater()) {
		if(targetTempEnable.isSelected()) {
		    double target = driver.getTemperatureSetting();
		    targetTempBox.setText(Double.toString(target));
		    targetDataset.add(second, target, "a");
		    root.add(new LogElement("targetTemperature", target));
		}
		if(currentTempEnable.isSelected()) {
		    double temperature = driver.getTemperature();
		    currentTempBox.setText(Double.toString(temperature));

		    // avoid spikes in the graph when it's not readable
		    if(temperature > 0) {
			measuredDataset.add(second, temperature, "a");
		    }
		    root.add(new LogElement("temperature", temperature));
		}
	    }

	    if (toolModel.hasHeatedPlatform()) {
		if(platformTargetTempEnable.isSelected()) {
		    double target = driver.getPlatformTemperatureSetting();
		    platformTargetTempBox.setText(Double.toString(target));
		    platformTargetDataset.add(second, target, "a");
		    root.add(new LogElement("platformTargetTemperature",
					    target));
		}
		if(platformCurrentTempEnable.isSelected()) {
		    double temperature = driver.getPlatformTemperature();
		    platformCurrentTempBox.setText(Double.toString(temperature));

		    // avoid spikes in the graph when it's not readable
		    if(temperature > 0) {
			platformMeasuredDataset.add(second, temperature, "a");
		    }
		    root.add(new LogElement("platformTemperature",
					    temperature));
		}
	    }

	    root.log();
	}
    }
}
