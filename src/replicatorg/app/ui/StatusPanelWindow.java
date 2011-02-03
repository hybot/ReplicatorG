/*
 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc

 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 
 */

package replicatorg.app.ui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.miginfocom.swing.MigLayout;
import replicatorg.app.Base;
import replicatorg.app.MachineController;
import replicatorg.drivers.Driver;
import replicatorg.drivers.RetryException;
import replicatorg.machine.MachineListener;
import replicatorg.machine.MachineProgressEvent;
import replicatorg.machine.MachineState;
import replicatorg.machine.MachineStateChangeEvent;
import replicatorg.machine.MachineToolStatusEvent;
import replicatorg.machine.model.AxisId;
import replicatorg.machine.model.Endstops;
import replicatorg.machine.model.ToolModel;

/**
 * Manage a Status Panel.
 **/
public class StatusPanelWindow extends JFrame implements
		ChangeListener, WindowListener,
		MachineListener {
    // Autogenerated by serialver
    static final long serialVersionUID = -3494348039028986935L;

    private static StatusPanelWindow instance = null;

    protected JTabbedPane toolsPane;
    protected MachineController machine;
    protected Driver driver;
    protected UpdateThread updateThread;
    List<StatusPanel> statusPanels = new ArrayList<StatusPanel>();

    public static synchronized 
    StatusPanelWindow getStatusPanel(MachineController m) {
	if (instance == null) {
	    instance = new StatusPanelWindow(m);
	} else {
	    if (instance.machine != m) {
		instance.dispose();
		instance = new StatusPanelWindow(m);
	    }
	}
	return instance;
    }
	
    private StatusPanelWindow(MachineController m) {
	super("Status Panel");

	// save our machine!
	machine = m;
	driver = machine.getDriver();
	driver.invalidatePosition(); // Always force a query when we open the panel

	// Listen to it-- stop and close if we're in build mode.
	machine.addMachineStateListener(this);
		
	// default behavior
	setDefaultCloseOperation(DISPOSE_ON_CLOSE);

	// create all our GUI interfaces
	add(createToolsPanel());

	// add our listener hooks.
	addWindowListener(this);

	setResizable(false);

	// start our thread.
	updateThread = new UpdateThread(this);
	updateThread.start();
    }

    protected JComponent createToolsPanel() {
	toolsPane = new JTabbedPane();

	for (Enumeration<ToolModel> e =
		 machine.getModel().getTools().elements(); 
	     e.hasMoreElements();) {
	    ToolModel t = e.nextElement();
	    if (t == null) continue;
	    if (t.getType().equals("extruder")) {
		Base.logger.fine("Creating panel for " + t.getName());
		StatusPanel statusPanel = new StatusPanel(machine, t, this);
		toolsPane.addTab(t.getName(), statusPanel);
		statusPanels.add(statusPanel);
		if (machine.getModel().currentTool() == t) {
		    toolsPane.setSelectedComponent(statusPanel);
		}
	    } else {
		Base.logger.warning("Unsupported tool for control panel.");
	    }
	}

	toolsPane.addChangeListener(new ChangeListener() {
	    public void stateChanged(ChangeEvent ce) {
		final JTabbedPane tp = (JTabbedPane)ce.getSource();
		final StatusPanel ep = (StatusPanel)tp.getSelectedComponent();
		machine.getModel().selectTool(ep.getTool().getIndex());
	    }
	});
	return toolsPane;
    }
	
    public void updateStatus() {
	for (StatusPanel panel : statusPanels) {
	    panel.updateStatus();
	}
    }
	
    public void windowClosing(WindowEvent e) {
	updateThread.interrupt();
    }

    public void windowClosed(WindowEvent e) {
	synchronized(getClass()) {
	    machine.removeMachineStateListener(this);
	    if (instance == this) {
		instance = null;
	    }
	}
    }

    public void windowOpened(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }

    synchronized void setUpdateInterval(int updateInterval) {
	if(updateThread != null) {
	    updateThread.setUpdateInterval(updateInterval);
	}
    }

    class UpdateThread extends Thread {
	StatusPanelWindow window;
	// this should be the same as the default in the StatusPanel
	int updateInterval = 2000;

	public UpdateThread(StatusPanelWindow w) {
	    super("Status Panel Update Thread");
	    window = w;
	}

	void setUpdateInterval(int updateInterval) {
	    this.updateInterval = updateInterval;
	}

	public void run() {
	    // we'll break on interrupts
	    try {
		while (true) {
		    try {
			window.updateStatus();
		    } catch (AssertionError ae) {
			// probaby disconnected unexpectedly; close window.
			window.dispose();
			break;
		    }
		    Thread.sleep(updateInterval);
		}
	    } catch (InterruptedException e) {
		// do nothing
	    }
	}
    }

    public void machineProgress(MachineProgressEvent event) {
    }

    public void machineStateChanged(MachineStateChangeEvent evt) {
	MachineState state = evt.getState();
	if (!state.isConnected() || 
	    state.getState() == MachineState.State.RESET) {
	    if (updateThread != null) { updateThread.interrupt(); }
	    SwingUtilities.invokeLater(new Runnable() {
		public void run() {
		    dispose();
		}
	    });
	}
    }

    public void toolStatusChanged(MachineToolStatusEvent event) {
    }

    public void stateChanged(ChangeEvent e) {
    }
}
