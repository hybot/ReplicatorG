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
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

public class StatusPanel extends JPanel implements FocusListener {
	private ToolModel toolModel;
	private MachineController machine;

	public ToolModel getTool() { return toolModel; }
	
        protected JLabel targetTempField;
	protected JLabel currentTempField;
	
	protected JLabel platformTargetTempField;
	protected JLabel platformCurrentTempField;

	protected double targetTemperature;
	protected double platformTargetTemperature;
	
	final private static Color targetColor = Color.BLUE;
	final private static Color measuredColor = Color.RED;
	final private static Color platformTargetColor = Color.YELLOW;
	final private static Color platformMeasuredColor = Color.WHITE;
	
	long startMillis = System.currentTimeMillis();

	private TimeTableXYDataset measuredDataset = new TimeTableXYDataset();
	private TimeTableXYDataset targetDataset = new TimeTableXYDataset();
	private TimeTableXYDataset platformMeasuredDataset = new TimeTableXYDataset();
	private TimeTableXYDataset platformTargetDataset = new TimeTableXYDataset();

	protected Pattern extrudeTimePattern;
	
	protected String[] extrudeTimeStrings = { /* "Continuous Move", */ "1s", "2s", "5s", "10s", "30s", "60s", "300s" };
	
	protected boolean continuousJogMode = false;
	protected long extrudeTime;
	private final String EXTRUDE_TIME_PREF_NAME = "extruderpanel.extrudetime";
	
	protected Driver driver;
	
	
	/**
	 * Make a label with an icon indicating its color on the graph.
	 * @param text The text of the label
	 * @param c The color of the matching line on the graph
	 * @return the generated label
	 */
	private JLabel makeKeyLabel(String text, Color c) {
		BufferedImage image = new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);
		Graphics g = image.getGraphics();
		g.setColor(c);
		g.fillRect(0,0,10,10);
		//image.getGraphics().fillRect(0,0,10,10);
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
		axis.setTickLabelsVisible(false); // We don't need to see the millisecond count
		axis = plot.getRangeAxis();
		axis.setRange(0,300); // set termperature range from 0 to 300 degrees C so you can see overshoots 
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

	private JLabel makeLabel(String text) {
		JLabel label = new JLabel();
		label.setText(text);
		label.setMinimumSize(labelMinimumSize);
		label.setMaximumSize(labelMinimumSize);
		label.setPreferredSize(labelMinimumSize);
		label.setHorizontalAlignment(JLabel.LEFT);
		return label;
	}

	
	private void setExtrudeTime(String mode) {
		if ("Continuous Jog".equals(mode)) {
			continuousJogMode = true;
			extrudeTime = 0;
		} else {
			// If we were in continuous jog mode, send a stop to be safe
			if (continuousJogMode) {
				this.driver.stop(false);			
			}
			continuousJogMode = false;
			Matcher jogMatcher = extrudeTimePattern.matcher(mode);
			if (jogMatcher.find())
				extrudeTime = Long.parseLong(jogMatcher.group(1));
		}
		if (mode != null && mode.length() > 0) {
			Base.preferences.put(EXTRUDE_TIME_PREF_NAME,mode);
		}		
	}
	
	public StatusPanel(MachineController machine, ToolModel t) {
		this.machine = machine;
		this.toolModel = t;
		
		int textBoxWidth = 75;
		Dimension panelSize = new Dimension(420, 30);
		driver = machine.getDriver();

		extrudeTimePattern = Pattern.compile("([.0-9]+)");
		
		// create our initial panel
		setLayout(new MigLayout());
		// create our motor options
		if (t.hasMotor()) {
			// Due to current implementation issues, we need to send the PWM
			// before the RPM for a stepper motor. Thus we display both controls in these
			// cases. This shouldn't be necessary for a Gen4 stepper extruder.
			if (t.getMotorStepperAxis() == null) {
				// our motor speed vars
				JLabel label = makeLabel("Motor Speed (PWM)");
				JLabel field = new JLabel();

				field.setMaximumSize(new Dimension(textBoxWidth, 25));
				field.setMinimumSize(new Dimension(textBoxWidth, 25));
				field.setPreferredSize(new Dimension(textBoxWidth, 25));
				field.setBorder(BorderFactory.createEtchedBorder());
				field.setText(Integer.toString(driver.getMotorSpeedPWM()));

				add(label);
				add(field,"wrap");
			}

			if (t.motorHasEncoder() || t.motorIsStepper()) {
				// our motor speed vars
				JLabel label = makeLabel("Motor Speed (RPM)");
				JLabel field = new JLabel();

				field.setMaximumSize(new Dimension(textBoxWidth, 25));
				field.setMinimumSize(new Dimension(textBoxWidth, 25));
				field.setPreferredSize(new Dimension(textBoxWidth, 25));
				field.setBorder(BorderFactory.createEtchedBorder());
				field.setText(Double.toString(driver.getMotorRPM()));

				add(label);
				add(field,"wrap");

				if (this.toolModel.getMotorStepperAxis() != null) {
					label = makeLabel("Extrude duration");
				
					JComboBox timeList = new JComboBox(extrudeTimeStrings);
					timeList.setEnabled(false);
					timeList.setSelectedItem(Base.preferences.get(EXTRUDE_TIME_PREF_NAME,"5s"));
					setExtrudeTime((String)timeList.getSelectedItem());
					add(label);
					add(timeList,"wrap");
				}
			}
		}

		// our temperature fields
		if (t.hasHeater()) {
			JLabel targetTempLabel = makeKeyLabel("Target Temperature (C)", targetColor);
			targetTempField = new JLabel();
			targetTempField.setMaximumSize(new Dimension(textBoxWidth, 25));
			targetTempField.setMinimumSize(new Dimension(textBoxWidth, 25));
			targetTempField.setPreferredSize(new Dimension(textBoxWidth, 25));
			targetTempField.setBorder(BorderFactory.createEtchedBorder());

			targetTemperature = driver.getTemperatureSetting();
			targetTempField.setText(Double.toString(targetTemperature));

			JLabel currentTempLabel = makeKeyLabel("Current Temperature (C)",measuredColor);
			currentTempField = new JLabel();
			currentTempField.setMaximumSize(new Dimension(textBoxWidth, 25));
			currentTempField.setMinimumSize(new Dimension(textBoxWidth, 25));
			currentTempField.setPreferredSize(new Dimension(textBoxWidth, 25));
			currentTempField.setBorder(BorderFactory.createEtchedBorder());

			add(targetTempLabel);
			add(targetTempField,"wrap");
			add(currentTempLabel);
			add(currentTempField,"wrap");
		}

		// our heated platform fields
		if (t.hasHeatedPlatform()) {
			JLabel targetTempLabel = makeKeyLabel("Platform Target Temp (C)",platformTargetColor);
			platformTargetTempField = new JLabel();
			platformTargetTempField.setMaximumSize(new Dimension(textBoxWidth, 25));
			platformTargetTempField.setMinimumSize(new Dimension(textBoxWidth, 25));
			platformTargetTempField.setPreferredSize(new Dimension(textBoxWidth, 25));
			platformTargetTempField.setBorder(BorderFactory.createEtchedBorder());

			double temperature = driver.getPlatformTemperatureSetting();
			platformTargetTemperature = temperature;
			platformTargetTempField.setText(Double.toString(temperature));

			JLabel currentTempLabel = makeKeyLabel("Platform Current Temp (C)", platformMeasuredColor);

			platformCurrentTempField = new JLabel();
			platformCurrentTempField.setMaximumSize(new Dimension(textBoxWidth, 25));
			platformCurrentTempField.setMinimumSize(new Dimension(textBoxWidth, 25));
			platformCurrentTempField.setPreferredSize(new Dimension(textBoxWidth, 25));
			platformCurrentTempField.setBorder(BorderFactory.createEtchedBorder());

			add(targetTempLabel);
			add(platformTargetTempField,"wrap");
			add(currentTempLabel);
			add(platformCurrentTempField,"wrap");
			
		}

		if (t.hasHeater() || t.hasHeatedPlatform()) {
			add(new JLabel("Temperature Chart"),"growx,spanx,wrap");
			add(makeChart(t),"growx,spanx,wrap");
		}

		// flood coolant controls
		if (t.hasFloodCoolant()) {
			JLabel floodCoolantLabel = makeLabel("Flood Coolant");

			JCheckBox floodCoolantCheck = new JCheckBox("enable");
			floodCoolantCheck.setEnabled(false);
			floodCoolantCheck.setName("flood-coolant");

			add(floodCoolantLabel);
			add(floodCoolantCheck,"wrap");
		}

		// mist coolant controls
		if (t.hasMistCoolant()) {
			JLabel mistCoolantLabel = makeLabel("Mist Coolant");

			JCheckBox mistCoolantCheck = new JCheckBox("enable");
			mistCoolantCheck.setEnabled(false);
			mistCoolantCheck.setName("mist-coolant");

			add(mistCoolantLabel);
			add(mistCoolantCheck,"wrap");
		}

		// cooling fan controls
		if (t.hasFan()) {
			String fanString = "Cooling Fan";
			String enableString = "enable";
			Element xml = findMappingNode(t.getXml(),"fan");
			if (xml != null) {
				fanString = xml.getAttribute("name");
				enableString = xml.getAttribute("actuated");
			}
			JLabel fanLabel = makeLabel(fanString);

			JCheckBox fanCheck = new JCheckBox(enableString);
			fanCheck.setEnabled(false);
			fanCheck.setName("fan-check");

			add(fanLabel);
			add(fanCheck,"wrap");
		}

		// cooling fan controls
		if (t.hasAutomatedPlatform()) {
			String abpString = "Build platform belt";
			String enableString = "enable";
			JLabel abpLabel = makeLabel(abpString);

			JCheckBox abpCheck = new JCheckBox(enableString);
			abpCheck.setEnabled(false);
			abpCheck.setName("abp-check");

			add(abpLabel);
			add(abpCheck,"wrap");
		}

		// valve controls
		if (t.hasValve()) {
			String valveString = "Valve";
			String enableString = "open";

			Element xml = findMappingNode(t.getXml(),"valve");
			if (xml != null) {
				valveString = xml.getAttribute("name");
				enableString = xml.getAttribute("actuated");
			}
			
			JLabel valveLabel = makeLabel(valveString);

			JCheckBox valveCheck = new JCheckBox(enableString);
			valveCheck.setEnabled(false);
			valveCheck.setName("valve-check");

			add(valveLabel);
			add(valveCheck,"wrap");
		}

		// valve controls
		if (t.hasCollet()) {
			JLabel colletLabel = makeLabel("Collet");

			JCheckBox colletCheck = new JCheckBox("open");
			colletCheck.setEnabled(false);
			colletCheck.setName("collet-check");

			add(colletLabel);
			add(colletCheck,"wrap");
		}
	}

	private Element findMappingNode(Node xml,String portName) {
		// scan the remapping nodes.
		NodeList children = xml.getChildNodes();
		for (int j=0; j<children.getLength(); j++) {
			Node child = children.item(j);
			if (child.getNodeName().equals("remap")) {
				Element e = (Element)child;
				if (e.getAttribute("port").equals(portName)) {
					return e;
				}
			}
		}
		return null;
	}

	public void updateStatus() {
		
		Second second = new Second(new Date(System.currentTimeMillis() - startMillis));
		int toolStatus = machine.getDriver().getToolStatus();
		
		if (machine.getModel().currentTool() == toolModel && toolModel.hasHeater()) {
			targetTemperature = driver.getTemperatureSetting();
			double temperature = machine.getDriver().getTemperature();
			updateTemperature(second, temperature);
		}
		if (machine.getModel().currentTool() == toolModel && toolModel.hasHeatedPlatform()) {
			platformTargetTemperature = driver.getPlatformTemperatureSetting();
			double temperature = machine.getDriver().getPlatformTemperature();
			updatePlatformTemperature(second, temperature);
		}
	}
	
	synchronized public void updateTemperature(Second second, double temperature)
	{
	        targetTempField.setText(Double.toString(targetTemperature));
		currentTempField.setText(Double.toString(temperature));
		measuredDataset.add(second, temperature,"a");
		targetDataset.add(second, targetTemperature,"a");
	}

	synchronized public void updatePlatformTemperature(Second second, double temperature)
	{
		platformTargetTempField.setText(Double.toString(platformTargetTemperature));
		platformCurrentTempField.setText(Double.toString(temperature));
		platformMeasuredDataset.add(second, temperature,"a");
		platformTargetDataset.add(second, platformTargetTemperature,"a");
	}

	public void focusGained(FocusEvent e) {
	}

	public void focusLost(FocusEvent e) {
	}
}
