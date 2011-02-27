package replicatorg.app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.data.xy.DefaultXYDataset;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import replicatorg.app.Base;
import replicatorg.app.MachineController;
import replicatorg.drivers.Driver;
import replicatorg.drivers.RetryException;
import replicatorg.drivers.gen3.Sanguino3GDriver;
import replicatorg.machine.model.ToolModel;
import replicatorg.machine.model.BuildVolume;
import replicatorg.util.Point5d;

/**
 * Report various status bits of a printer, while in operation.
 **/
public class StatusPanel extends JPanel {
    private ToolModel toolModel;
    private MachineController machine;
    private StatusPanelWindow window;

    JLabel aBox;
    JLabel bBox;

    JLabel feedRateBox;

    JLabel pwmBox;

    JLabel rpmBox;

    JCheckBox tempEnable;
    JCheckBox xyzEnable;
    JCheckBox speedEnable;
    JCheckBox dataLogEnable;

    JComboBox updateBox;

    ValueAxis tempAxis;
    ValueAxis platformTempAxis;

    ValueAxis xAxis;
    ValueAxis yAxis;
    ValueAxis zAxis;

    PrintWriter logFileWriter = null;
    String fileName = null;
    boolean useCSV = false;
    
    final private static Color targetColor = Color.BLUE;
    final private static Color measuredColor = Color.RED;
    final private static Color platformTargetColor = Color.YELLOW;
    final private static Color platformMeasuredColor = Color.WHITE;

    final private static Color xyColor = Color.RED;
    final private static Color zColor = Color.BLUE;

    final private static String[] updateStrings = { ".1", ".5", "1", "2",
						    "5", "10", "30", "60" };

    long startMillis = System.currentTimeMillis();

    private TimeTableXYDataset measuredDataset = new TimeTableXYDataset();
    private TimeTableXYDataset targetDataset = new TimeTableXYDataset();
    private TimeTableXYDataset platformMeasuredDataset = 
	new TimeTableXYDataset();
    private TimeTableXYDataset platformTargetDataset = new TimeTableXYDataset();

    private DefaultXYDataset xyDataset = new DefaultXYDataset();
    private DefaultXYDataset zDataset = new DefaultXYDataset();

    protected Driver driver;
	
    private final Dimension labelMinimumSize = new Dimension(165, 25);
    private final Dimension textBoxSize = new Dimension(55, 25);

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

	tempAxis = plot.getRangeAxis();

	// set temperature range from 0 to 300
	// degrees C so you can see overshoots 
	tempAxis.setRange(0, 300);

        platformTempAxis = new NumberAxis();
	platformTempAxis.setLabelPaint(tempAxis.getLabelPaint());
	platformTempAxis.setLabelFont(tempAxis.getLabelFont());
	platformTempAxis.setRange(tempAxis.getRange());
	platformTempAxis.setStandardTickUnits(tempAxis.getStandardTickUnits());
	platformTempAxis.setTickLabelsVisible(false);
	platformTempAxis.setLabelAngle(Math.PI);

	// Tweak L&F of chart
	//((XYAreaRenderer)plot.getRenderer()).setOutline(true);
	XYStepRenderer renderer = new XYStepRenderer();
	plot.setDataset(1, targetDataset);

        plot.setRangeAxis(1, platformTempAxis);
        plot.mapDatasetToRangeAxis(1, 1);

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

    public ChartPanel makeXYZChart() {
	JFreeChart chart = ChartFactory.createScatterPlot(null, "x", "y", 
				xyDataset, PlotOrientation.VERTICAL, 
				false, false, false);
	chart.setBorderVisible(false);
	chart.setBackgroundPaint(null);
	XYPlot plot = chart.getXYPlot();

	TickUnits unitSource = new TickUnits();
	unitSource.add(new NumberTickUnit(20L)); // 20 mm


	//get our platform ranges
	BuildVolume buildVolume = machine.getModel().getBuildVolume();

	int maxX = (int)(buildVolume.getX()/2.0 + .5);
	int maxY = (int)(buildVolume.getY()/2.0 + .5);

	xAxis = plot.getDomainAxis();
	xAxis.setStandardTickUnits(unitSource);
	xAxis.setRange(-maxX, maxX);
	xAxis.setMinorTickMarksVisible(false);
	
	yAxis = plot.getRangeAxis();
	yAxis.setStandardTickUnits(unitSource);
	yAxis.setRange(-maxY, maxY);
	yAxis.setMinorTickMarksVisible(false);

	XYDotRenderer renderer = new XYDotRenderer();
	renderer.setDotHeight(5);
	renderer.setDotWidth(7);
	renderer.setBasePaint(xyColor);
	renderer.setSeriesPaint(0, xyColor);
	plot.setRenderer(0, renderer);
   
	// add a right side axis for Z
        zAxis = new NumberAxis();
	// make it look like the y label
	zAxis.setLabelPaint(yAxis.getLabelPaint());
	zAxis.setLabelFont(yAxis.getLabelFont());
	zAxis.setLabelAngle(Math.PI);

	zAxis.setStandardTickUnits(unitSource);
	zAxis.setRange(0, buildVolume.getZ());
	zAxis.setMinorTickMarksVisible(false);

        plot.setRangeAxis(1, zAxis);
	plot.setDataset(1, zDataset);
        plot.mapDatasetToRangeAxis(1, 1);

	// we use this as a line drawer, so that we only need
	// to add a single point to the z dataset.
	renderer = new XYDotRenderer();
	renderer.setDotHeight(2);
	renderer.setDotWidth(20);
	renderer.setBasePaint(zColor);
	renderer.setSeriesPaint(0, zColor);
	plot.setRenderer(1, renderer);

	ChartPanel chartPanel = new ChartPanel(chart);
	chartPanel.setPreferredSize(new Dimension(210, 180));
	chartPanel.setMaximumSize(new Dimension(210, 180));
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
	setLayout(new MigLayout("insets 2"));
	    
	JPanel infoPanel = new JPanel();
	infoPanel.setLayout(new MigLayout("insets 1"));
	{
	    JLabel label = makeLabel("Driver");
	    JLabel driverLabel = new JLabel();
	    driverLabel.setText(driver.getDriverName());
	    infoPanel.add(label);
	    infoPanel.add(driverLabel, "wrap");
	}

	{
	    JLabel label = makeLabel("Firmware");
	    JLabel firmwareLabel = new JLabel();
	    String version = "Motherboard v" + driver.getVersion();
	    if(driver instanceof Sanguino3GDriver) {
		version += " / Extruder v" +
		    ((Sanguino3GDriver)driver).getToolVersion();
	    }
	    firmwareLabel.setText(version);
	    infoPanel.add(label);
	    infoPanel.add(firmwareLabel, "wrap");
	}

	add(infoPanel, "growx, span 2, wrap");

	// temperature
	JPanel tempPanel = new JPanel();
	tempPanel.setBorder(BorderFactory.createTitledBorder("Temperature"));
	tempPanel.setLayout(new MigLayout("insets 1"));

	tempEnable = makeCheckBox("Enable", "status.temp", true);
	tempEnable.setToolTipText("Toolhead and platform temperatures, " +
				  "current and target");
	tempPanel.add(tempEnable, "wrap");

	if (t.hasHeater() || t.hasHeatedPlatform()) {
	    tempPanel.add(makeChart(t));
	} else {
	    tempEnable.setEnabled(false);
	}

	add(tempPanel, "growx, growy");
	
	// position
	final JPanel posPanel = new JPanel();
	posPanel.setBorder(BorderFactory.createTitledBorder("Position"));
	posPanel.setLayout(new MigLayout("insets 1"));

	xyzEnable = makeCheckBox("Enable", "status.xyz", true);
	xyzEnable.setToolTipText("5D position");
	posPanel.add(xyzEnable, "span 3, wrap");

	aBox = makeBox(null);
	bBox = makeBox(null);

	posPanel.add(makeXYZChart(), "spany 4, growx");
	posPanel.add(new JLabel(" "), "span 2, wrap"); // glue
	
	posPanel.add(new JLabel("A"));
	posPanel.add(aBox, "wrap");

	posPanel.add(new JLabel(" "), "span 2, wrap"); // glue
	posPanel.add(new JLabel("B"));
	posPanel.add(bBox, "wrap");

	add(posPanel, "growx, growy, wrap");

	// speed
	final JPanel speedPanel = new JPanel();
	speedPanel.setBorder(BorderFactory.createTitledBorder("Speed"));
	speedPanel.setLayout(new MigLayout("insets 1"));

	speedEnable = makeCheckBox("Enable", "status.speed", false);
	speedEnable.setToolTipText("Extrusion motor speeds and feed rates");
	speedPanel.add(speedEnable, "wrap");

	// create our motor options
	if (t.hasMotor()) {
	    // Due to current implementation issues, we need to send the PWM
	    // before the RPM for a stepper motor. Thus we display both 
	    // controls in these cases. This shouldn't be necessary for a
	    // Gen4 stepper extruder.
	    if (t.getMotorStepperAxis() == null) {
		pwmBox = makeBox(null);
		
		speedPanel.add(makeLabel("Motor Speed (PWM)"));
		speedPanel.add(pwmBox, "wrap");
	    }

	    if (t.motorHasEncoder() || t.motorIsStepper()) {
		rpmBox = makeBox(null);

		speedPanel.add(makeLabel("Motor Speed (RPM)"));
		speedPanel.add(rpmBox, "wrap");
	    }
	}

	feedRateBox = makeBox(null);

	speedPanel.add(makeLabel("Feed Rate (mm/min)"));
	speedPanel.add(feedRateBox, "wrap");

	add(speedPanel, "growx, growy");

	// data logging
	final JPanel dataPanel = new JPanel();
	dataPanel.setBorder(BorderFactory.createTitledBorder("Data Log"));
	dataPanel.setLayout(new MigLayout("insets 1"));

	dataLogEnable = new JCheckBox("Enable", false);
	dataLogEnable.setToolTipText("log data to selected file; unlike " +
				     "other checkboxes checking this is " +
				     "not sticky");
	dataPanel.add(dataLogEnable, "wrap");

	fileName = Base.preferences.get("status.log_file", fileName);

	final JLabel fileLabel = makeLabel(getName(fileName));
	fileLabel.setToolTipText("base file name of the log file");

	final JFileChooser chooser = new JFileChooser(fileName);

	JButton saveButton = new JButton("Select File ...");
	saveButton.setToolTipText("data will be appended to the selected file");
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
	taggedBox.setToolTipText("log data in an XML style tag format");

	final JRadioButton csvBox = new JRadioButton("CSV");
	csvBox.setSelected(useCSV);
	csvBox.setToolTipText("log data in a spreadsheet compatible " +
			      "Comma Separated Value format");

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

	dataLogEnable.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent event) {
		if(fileName == null) {
		    dataLogEnable.setSelected(false);
		    return;
		}

		if(!dataLogEnable.isSelected()) {
		    logFileWriter = null;
		    return;
		}

		try {
		    logFileWriter =
			new PrintWriter(new FileOutputStream(fileName, true));
		} catch (FileNotFoundException fnfe) {
		    logFileWriter = null;
		    Base.logger.warning("Cannot open " + fileName);
		} finally {
		    if(logFileWriter == null) {
			dataLogEnable.setSelected(false);
			fileLabel.setText("Cannot write " +
					  ((fileName == null) ?
					   "File" : getName(fileName)));
			fileLabel.setForeground(Color.RED);
		    }
		}
	    }
	});

	dataPanel.add(saveButton, "growx, span 2");

	dataPanel.add(fileLabel, "wrap");
	dataPanel.add(taggedBox);
	dataPanel.add(csvBox);
	add(dataPanel, "growx");

	JLabel updateLabel = new JLabel("Update Interval (sec)");
	updateBox = new JComboBox(updateStrings);
	updateBox.setToolTipText("How often fields and charts are updated");
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

	// use 1 second as the default.
	// if you change this, change the intial delay in
	// StatusPanelWindow
	updateBox.setSelectedItem("1");
	infoPanel.add(updateLabel);
	infoPanel.add(updateBox, "wrap");
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

    public String getPositionText(Point5d position) {
	if(position == null) {
	    return "unknown";
	}
	return String.format("%.2f, %.2f, %.2f, %.2f, %.2f", 
			     position.x(), position.y(), position.z(),
			     position.a(), position.b());
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
	    if(logFileWriter == null || !dataLogEnable.isSelected()) {
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

	    if(xyzEnable.isSelected()) {
		position = driver.getCurrentPosition();
	    }

	    if(xyzEnable.isSelected()) {
		//todo: permit multiple series for data overlays
		final String series = "1";

		xyDataset.addSeries(series,  new double[][] { {position.x()},
							      {position.y()}});
		zDataset.addSeries(series,  new double[][]
		    { {xAxis.getUpperBound()}, {position.z()} });

		xAxis.setLabel(String.format("X = %.1f", position.x())); 
		yAxis.setLabel(String.format("Y = %.1f", position.y())); 
		zAxis.setLabel(String.format("Z = %.1f", position.z())); 

		aBox.setText(String.valueOf(position.a()));
		bBox.setText(String.valueOf(position.b()));
		root.add(new LogElement("position", getPositionText(position)));
	    }

	    if (toolModel.hasMotor()) {
		if (toolModel.getMotorStepperAxis() == null) {
		    if(speedEnable.isSelected()) {
			int pwm = driver.getMotorSpeedPWM();
			pwmBox.setText(String.valueOf(pwm));
			root.add(new LogElement("pwm", pwm));
		    }
		}
		if (toolModel.motorHasEncoder() || toolModel.motorIsStepper()) {
		    if(speedEnable.isSelected()) {
			double rpm = driver.getMotorRPM();
			rpmBox.setText(String.valueOf(rpm));
			root.add(new LogElement("rpm", rpm));
		    }
		}
	    }

	    if(speedEnable.isSelected()) {
		String feedRate = Double.toString(driver.getCurrentFeedrate());
		feedRateBox.setText(feedRate);
		root.add(new LogElement("feedRate", feedRate));
	    }

	    if (toolModel.hasHeater() && tempEnable.isSelected()) {
		double target = driver.getTemperatureSetting();
		targetDataset.add(second, target, "a");
		root.add(new LogElement("targetTemperature", target));

		double temperature = driver.getTemperature();

		// avoid spikes in the graph when it's not readable
		if(temperature > 0) {
		    measuredDataset.add(second, temperature, "a");
		}
		root.add(new LogElement("temperature", temperature));

		tempAxis.setLabel(String.format("temp = %.0f/%.0f", 
						temperature, target));
	    }

	    if (toolModel.hasHeatedPlatform() && tempEnable.isSelected()) {
		double target = driver.getPlatformTemperatureSetting();
		platformTargetDataset.add(second, target, "a");
		root.add(new LogElement("platformTargetTemperature",
					target));

		double temperature = driver.getPlatformTemperature();

		// avoid spikes in the graph when it's not readable
		if(temperature > 0) {
		    platformMeasuredDataset.add(second, temperature, "a");
		}
		root.add(new LogElement("platformTemperature",
					temperature));

		platformTempAxis.setLabel(String.format("plat temp = %.0f/%.0f", 
							temperature, target));
	    }

	    root.log();
	}
    }
}
