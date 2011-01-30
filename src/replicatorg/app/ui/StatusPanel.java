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
import replicatorg.machine.model.ToolModel;
import replicatorg.util.Point5d;

/**
 * Report various status bits of a printer, while in operation.
 **/
public class StatusPanel extends JPanel implements FocusListener {
    private ToolModel toolModel;
    private MachineController machine;

    public ToolModel getTool() { return toolModel; }
	
    protected double targetTemperature;
    protected double platformTargetTemperature;
	
    protected JLabel targetTempBox;
    protected JLabel currentTempBox;
	
    protected JLabel platformTargetTempBox;
    protected JLabel platformCurrentTempBox;

    protected JLabel pwmBox;
    protected JLabel rpmBox;

    protected JLabel positionBox;
    protected JLabel feedRateBox;

    JTextField logFileNameField;
    JCheckBox logFileBox;
    PrintWriter logFileWriter = null;
    boolean useCSV = false;
    
    final private static Color targetColor = Color.BLUE;
    final private static Color measuredColor = Color.RED;
    final private static Color platformTargetColor = Color.YELLOW;
    final private static Color platformMeasuredColor = Color.WHITE;
	
    long startMillis = System.currentTimeMillis();

    private TimeTableXYDataset measuredDataset = new TimeTableXYDataset();
    private TimeTableXYDataset targetDataset = new TimeTableXYDataset();
    private TimeTableXYDataset platformMeasuredDataset = 
	new TimeTableXYDataset();
    private TimeTableXYDataset platformTargetDataset = new TimeTableXYDataset();

    protected Driver driver;
	
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

    private final Dimension labelMinimumSize = new Dimension(175, 25);
    private final Dimension textBoxSize = new Dimension(65, 25);

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
	label.setText(text);
	label.setMinimumSize(textBoxSize);
	label.setMaximumSize(textBoxSize);
	label.setPreferredSize(textBoxSize);
	label.setBorder(BorderFactory.createCompoundBorder(
			         BorderFactory.createEtchedBorder(),
				 BorderFactory.createEmptyBorder(0, 3, 0, 0)));
	label.setHorizontalAlignment(JLabel.LEFT);
	return label;
    }
	
    public StatusPanel(MachineController machine, ToolModel t) {
	this.machine = machine;
	this.toolModel = t;
		
	Dimension panelSize = new Dimension(420, 30);

	driver = machine.getDriver();

	// create our initial panel
	setLayout(new MigLayout());
	    
	{
	    JLabel label = makeLabel("Firmware");
	    JLabel firmwareLabel = new JLabel();
	    firmwareLabel.setText("v" + driver.getVersion());
	    add(label);
	    add(firmwareLabel, "wrap");
	}

	{
	    JLabel label = makeLabel("Driver");
	    JLabel driverLabel = new JLabel();
	    driverLabel.setText(driver.getDriverName());
	    add(label);
	    add(driverLabel, "wrap");
	}

	// create our motor options
	if (t.hasMotor()) {
	    // Due to current implementation issues, we need to send the PWM
	    // before the RPM for a stepper motor. Thus we display both 
	    // controls in these cases. This shouldn't be necessary for a
	    // Gen4 stepper extruder.
	    if (t.getMotorStepperAxis() == null) {
		// our motor speed vars
		JLabel label = makeLabel("Motor Speed (PWM)");
		pwmBox = makeBox(String.valueOf(driver.getMotorSpeedPWM()));
		
		add(label);
		add(pwmBox,"wrap");
	    }

	    if (t.motorHasEncoder() || t.motorIsStepper()) {
		// our motor speed vars
		JLabel label = makeLabel("Motor Speed (RPM)");
		rpmBox = makeBox(String.valueOf(driver.getMotorRPM()));

		add(label);
		add(rpmBox,"wrap");
	    }
	}

	{
	    JLabel label = new JLabel("Position");
	    positionBox = makeBox(getPositionText());

	    Dimension size = new Dimension(220, 25);
	    positionBox.setPreferredSize(size);
	    positionBox.setMinimumSize(size);
	    positionBox.setMaximumSize(size);

	    add(label);
	    add(positionBox, "wrap");
	}

	{
	    JLabel label = new JLabel("Feed Rate");
	    feedRateBox = makeBox(String.valueOf(driver.getCurrentFeedrate()));
	    add(label);
	    add(feedRateBox, "wrap");
	}

	// our tool head temperature fields
	if (t.hasHeater()) {
	    targetTemperature = driver.getTemperatureSetting();

	    JLabel targetTempLabel = makeKeyLabel("Target Temperature (C)",
						  targetColor);
	    targetTempBox = makeBox(Double.toString(targetTemperature));

	    JLabel currentTempLabel = makeKeyLabel("Current Temperature (C)",
						   measuredColor);
	    currentTempBox = makeBox("");

	    add(targetTempLabel);
	    add(targetTempBox,"wrap");
	    add(currentTempLabel);
	    add(currentTempBox,"wrap");
	}

	// our heated platform fields
	if (t.hasHeatedPlatform()) {
	    platformTargetTemperature = driver.getPlatformTemperatureSetting();

	    JLabel targetTempLabel = makeKeyLabel("Platform Target Temp (C)",
						  platformTargetColor);

	    platformTargetTempBox = 
		makeBox(Double.toString(platformTargetTemperature));

	    JLabel currentTempLabel = makeKeyLabel("Platform Current Temp (C)",
						   platformMeasuredColor);

	    platformCurrentTempBox = makeBox("");

	    add(targetTempLabel);
	    add(platformTargetTempBox,"wrap");
	    add(currentTempLabel);
	    add(platformCurrentTempBox,"wrap");
	}

	if (t.hasHeater() || t.hasHeatedPlatform()) {
	    add(new JLabel("Temperature Chart"),"growx,spanx,wrap");
	    add(makeChart(t),"growx,spanx,wrap");
	}

	{
	    JPanel panel = new JPanel();
	    panel.setBorder(BorderFactory.createTitledBorder("Data Log File"));
	    panel.setLayout(new MigLayout());

	    final JLabel invalidLabel = new JLabel("Invaild File");
	    invalidLabel.setForeground(Color.RED);
	    invalidLabel.setVisible(false);

	    Dimension fileNameSize = new Dimension(180, 25);

	    JLabel label = new JLabel("File Name");
	    logFileNameField = new JTextField();
	    logFileNameField.setMinimumSize(fileNameSize);
	    logFileNameField.setMaximumSize(fileNameSize);
	    logFileNameField.setPreferredSize(fileNameSize);

	    ButtonGroup group = new ButtonGroup();
	    final JRadioButton csvBox = new JRadioButton("CSV");
	    final JRadioButton taggedBox = new JRadioButton("Tagged");
	    group.add(csvBox);
	    group.add(taggedBox);
	    ActionListener radioListener = new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
			useCSV = csvBox.isSelected();
		    }		    
		};

	    csvBox.addActionListener(radioListener);
	    taggedBox.addActionListener(radioListener);
	    csvBox.setSelected(useCSV);
	    taggedBox.setSelected(!useCSV);

	    logFileBox = new JCheckBox("Enable");
	    logFileBox.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent event) {
			if(logFileBox.isSelected()) {
			    String fileName = logFileNameField.getText();
			    try {
				logFileWriter = 
				    new PrintWriter(
				        new FileOutputStream(fileName, true));
				invalidLabel.setVisible(false);
			    } catch (FileNotFoundException fnfe) {
				logFileWriter = null;
				Base.logger.log(Level.INFO, "Cannot open " +
						fileName);
				invalidLabel.setVisible(true);
			    }
			} else {
			    logFileWriter = null;
			}
		    }
		});

	    panel.add(label);
	    panel.add(logFileNameField, "span 2");
	    panel.add(invalidLabel, "wrap");
	    panel.add(taggedBox);
	    panel.add(csvBox);
	    panel.add(logFileBox);
	    add(panel, "spanx,wrap");
	}
    }

    public String getPositionText() {
	Point5d p = driver.getCurrentPosition();
	if(p == null) {
	    return "unknown";
	}
	return String.format("%.2f, %.2f, %.2f, %.2f, %.2f",
			     p.x(), p.y(), p.z(), p.a(), p.b());
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
	    if(logFileWriter == null) {
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

    public void updateStatus() {
	if (machine.getModel().currentTool() == toolModel) {

	    Second second = 
		new Second(new Date(System.currentTimeMillis() - startMillis));

	    LogElement root  = 
		new LogElement("time", (int)(System.currentTimeMillis()/1000));

	    if (toolModel.hasMotor()) {
		if (toolModel.getMotorStepperAxis() == null) {
		    int pwm = driver.getMotorSpeedPWM();
		    pwmBox.setText(String.valueOf(pwm));
		    root.add(new LogElement("pwm", pwm));
		}
		if (toolModel.motorHasEncoder() || toolModel.motorIsStepper()) {
		    double rpm = driver.getMotorRPM();
		    rpmBox.setText(String.valueOf(rpm));
		    root.add(new LogElement("rpm", rpm));
		}
	    }

	    String position = getPositionText();
	    positionBox.setText(position);
	    root.add(new LogElement("position", position));

	    String feedRate = Double.toString(driver.getCurrentFeedrate());
	    feedRateBox.setText(feedRate);
	    root.add(new LogElement("feedRate", feedRate));

	    if (toolModel.hasHeater()) {
		targetTemperature = driver.getTemperatureSetting();
		double temperature = machine.getDriver().getTemperature();

		updateTemperature(second, temperature);
		root.add(new LogElement("targetTemperature", targetTemperature));
		root.add(new LogElement("temperature", temperature));
	    }

	    if (toolModel.hasHeatedPlatform()) {
		platformTargetTemperature =
		    driver.getPlatformTemperatureSetting();
		double temperature =
		    machine.getDriver().getPlatformTemperature();

		updatePlatformTemperature(second, temperature);
		root.add(new LogElement("platformTargetTemperature",
					platformTargetTemperature));
		root.add(new LogElement("platformTemperature", temperature));
	    }

	    root.log();
	}
    }
	
    synchronized public void updateTemperature(Second second,
					       double temperature) {
	targetTempBox.setText(Double.toString(targetTemperature));
	currentTempBox.setText(Double.toString(temperature));
	// avoid spikes in the graph when it's not readable
	if(temperature > 0 || targetTemperature == 0) {
	    measuredDataset.add(second, temperature, "a");
	}
	targetDataset.add(second, targetTemperature, "a");
    }

    synchronized public void updatePlatformTemperature(Second second,
						       double temperature) {
	platformTargetTempBox.setText(
		      Double.toString(platformTargetTemperature));
	platformCurrentTempBox.setText(Double.toString(temperature));

	// avoid spikes in the graph when it's not readable
	if(temperature > 0 || platformTargetTemperature == 0) {
	    platformMeasuredDataset.add(second, temperature, "a");
	}
	platformTargetDataset.add(second, platformTargetTemperature, "a");
    }

    public void focusGained(FocusEvent e) {
    }

    public void focusLost(FocusEvent e) {
    }
}