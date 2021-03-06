/*
 * eID Middleware Project.
 * Copyright (C) 2010-2013 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see
 * http://www.gnu.org/licenses/.
 */
package be.fedict.eidviewer.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import be.fedict.eid.applet.Messages.MESSAGE_ID;
import be.fedict.eid.applet.Status;
import be.fedict.eid.applet.View;
import be.fedict.eidviewer.gui.helper.ImageUtilities;
import be.fedict.eidviewer.gui.helper.LogHelper;
import be.fedict.eidviewer.gui.helper.ProxyUtils;
import be.fedict.eidviewer.gui.panels.AboutPanel;
import be.fedict.eidviewer.gui.panels.CardPanel;
import be.fedict.eidviewer.gui.panels.CertificatesPanel;
import be.fedict.eidviewer.gui.panels.IdentityPanel;
import be.fedict.eidviewer.gui.panels.LogPanel;
import be.fedict.eidviewer.gui.panels.PreferencesPanel;
import be.fedict.eidviewer.gui.printing.IDPrintout;
import be.fedict.eidviewer.lib.EidTransferHandler;
import be.fedict.eidviewer.lib.PCSCEid;
import be.fedict.eidviewer.lib.PCSCEidController;
import be.fedict.eidviewer.lib.TrustServiceController;
import be.fedict.eidviewer.lib.file.gui.EidFileFilter;
import be.fedict.eidviewer.lib.file.gui.EidFilePreviewAccessory;
import be.fedict.eidviewer.lib.file.gui.EidFileView;
import be.fedict.eidviewer.lib.file.helper.LibJ2PCSCGNULinuxFix;
import be.fedict.eidviewer.lib.file.helper.Version35LocalePrefs;

/**
 * 
 * @author Frank Marien
 */
public class BelgianEidViewer extends javax.swing.JFrame implements View,
	Observer, DynamicLocale {
    private static final long serialVersionUID = -4473336524368319021L;
    private static final Logger logger = Logger
	    .getLogger(BelgianEidViewer.class.getName());
    private ResourceBundle bundle;
    private static final String EXTENSION_PNG = ".png";
    private static final String ICONS = "/be/fedict/eidviewer/gui/resources/icons/";

    private JMenuBar menuBar;

    private JMenu fileMenu;
    private JMenuItem printMenuItem;
    private JMenuItem openMenuItem;
    private JMenu saveAsMenu;

    private JMenuItem saveAsXMLMenuItem;
    private JMenuItem saveAsCSVMenuItem;
    private JMenuItem closeMenuItem;
    private JMenuItem preferencesMenuItem;
    private JMenuItem fileMenuQuitItem;

    private JMenu helpMenu;
    private JMenuItem aboutMenuItem;
    private JMenuItem faqMenuItem;
    private JMenuItem testMenuItem;
    private JCheckBoxMenuItem showLogMenuItem;

    private JMenu languageMenu;
    private JMenuItem languageGermanMenuItem; // Deutsch
    private JMenuItem languageEnglishMenuItem; // English
    private JMenuItem languageFrenchMenuItem; // Francais
    private JMenuItem languageDutchMenuItem; // Nederlands

    private JButton printButton;
    private JPanel printPanel;
    private JLabel statusIcon;
    private JPanel statusPanel;
    private JPanel versionStatusContainer;
    private JLabel statusText;
    private JLabel versionText;
    private JTabbedPane tabPanel;
    private JProgressBar progressBar;
    private PCSCEid eid;
    private PCSCEidController eidController;
    private TrustServiceController trustServiceController;
    private EnumMap<PCSCEidController.STATE, ImageIcon> cardStatusIcons;
    private EnumMap<PCSCEidController.STATE, String> cardStatusTexts;
    private EnumMap<PCSCEidController.ACTIVITY, String> activityTexts;
    private IdentityPanel identityPanel;
    private CertificatesPanel certificatesPanel;
    private CardPanel cardPanel;
    private LogPanel logPanel;
    private PrintAction printAction;
    private OpenFileAction openAction;
    private SaveFileAsXMLAction saveAsXMLAction;
    private SaveFileAsCSVAction saveAsCSVAction;
    private CloseFileAction closeAction;
    private AboutAction aboutAction;
    private FAQAction faqAction;
    private TestAction testAction;
    private QuitAction quitAction;
    private PreferencesAction preferencesAction;
    private ShowHideLogAction showHideLogAction;
    private Desktop desktop;
    private VersionChecker versionChecker;
    private File defaultFile;

    public BelgianEidViewer() {
	initDesktop();
	initActions();
	initTexts();
	initComponents();
	initI18N();
	initPanels();
	initIcons();
	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	LibJ2PCSCGNULinuxFix.fixNativeLibrary();
    }

    private void initDesktop()
    {
	if(!java.awt.Desktop.isDesktopSupported()) {

            logger.severe("Desktop not supported. Browser-based operations not available.");
            return;
        }
	
	this.desktop = Desktop.getDesktop();
	
	if( !desktop.isSupported( Desktop.Action.BROWSE ) ) {
	    logger.severe("Desktop BROWSE action not supported. Browser-based operations not available.");
	    this.desktop=null;
	}
    }

    private void initActions() {
	printAction = new PrintAction("Print", new Integer(KeyEvent.VK_P));
	openAction = new OpenFileAction("Open", new Integer(KeyEvent.VK_O));
	closeAction = new CloseFileAction("Close", new Integer(KeyEvent.VK_W));
	saveAsXMLAction = new SaveFileAsXMLAction("Save As XML", new Integer(
		KeyEvent.VK_S));
	saveAsCSVAction = new SaveFileAsCSVAction("Save As CSV", new Integer(
		KeyEvent.VK_COMMA));
	preferencesAction = new PreferencesAction("Preferences");
	aboutAction = new AboutAction("About");
	quitAction = new QuitAction("Quit", new Integer(KeyEvent.VK_Q));
	faqAction = new FAQAction("FAQ");
	testAction = new TestAction("Test");
	showHideLogAction = new ShowHideLogAction("Show Log", new Integer(
		KeyEvent.VK_L));
    }

    private void start() {
	LogHelper.logJavaSpecs(logger);
	logger.fine("starting..");

	// get ProxyUtils class instantiated early because it needs to detect
	// system proxy and
	// it will get confused if we change proxy settings first
	ProxyUtils.getSystemProxy();

	eid = new PCSCEid(this, ViewerPrefs.getLocale());
	eidController = new PCSCEidController(eid);
	identityPanel.setTransferHandler(new EidTransferHandler(eidController));
	trustServiceController = new TrustServiceController(
		ViewerPrefs.getTrustServiceURL());
	trustServiceController.start();

	setTrustServiceProxy();

	eidController.setTrustServiceController(trustServiceController);
	eidController.setAutoValidateTrust(ViewerPrefs.getIsAutoValidating());

	identityPanel.setEidController(eidController);

	cardPanel.setEidController(eidController);

	certificatesPanel.setEidController(eidController);
	certificatesPanel.start();

	eidController.addObserver(identityPanel);
	eidController.addObserver(cardPanel);
	eidController.addObserver(certificatesPanel);
	eidController.addObserver(this);
	eidController.start();
	
	versionChecker = new VersionChecker(bundle.getString("curr_version"));
	versionChecker.addObserver(this);
	versionChecker.start();

	setVisible(true);
    }

    private void stop() {
	logger.fine("stopping..");
	eidController.stop();
	trustServiceController.stop();
	versionChecker.stop();
	this.dispose();
    }

    public void update(Observable o, Object o1) {
	updateVisibleState();

    }

    private void updateVisibleState() {
	java.awt.EventQueue.invokeLater(new Runnable() {

	    public void run() {
		printAction.setEnabled(eidController.hasIdentity()
			&& eidController.hasAddress()
			&& eidController.hasPhoto());
		boolean safeToSave = (eidController.getActivity() == PCSCEidController.ACTIVITY.IDLE)
			&& eidController.hasIdentity()
			&& eidController.hasAddress()
			&& eidController.hasPhoto();

		saveAsMenu.setEnabled(safeToSave);
		saveAsXMLAction.setEnabled(safeToSave);
		saveAsCSVAction.setEnabled(safeToSave);

		openAction.setEnabled(eidController.getState() != PCSCEidController.STATE.EID_PRESENT
			&& eidController.getState() != PCSCEidController.STATE.EID_YIELDED);
		closeAction.setEnabled(eidController.isLoadedFromFile()
			&& (eidController.hasAddress()
				|| eidController.hasPhoto()
				|| eidController.hasAuthCertChain() || eidController
				    .hasSignCertChain()));

		boolean safeToUpdateI18N = (eidController.getActivity() == PCSCEidController.ACTIVITY.IDLE);
		languageGermanMenuItem.setEnabled(safeToUpdateI18N);
		languageEnglishMenuItem.setEnabled(safeToUpdateI18N);
		languageFrenchMenuItem.setEnabled(safeToUpdateI18N);
		languageDutchMenuItem.setEnabled(safeToUpdateI18N);

		preferencesAction.setEnabled(!trustServiceController
			.isValidating());
		
		faqAction.setEnabled(BelgianEidViewer.this.desktop!=null);
		testAction.setEnabled(BelgianEidViewer.this.desktop!=null);

		statusIcon.setIcon(cardStatusIcons.get(eidController.getState()));

		switch (eidController.getState()) {
		case EID_PRESENT:
		    PCSCEidController.ACTIVITY act = eidController.getActivity();
		    statusText.setText(activityTexts.get(act));
		    if(act == PCSCEidController.ACTIVITY.IDLE) {
		        resetProgress(100);
		    } else {
		        progressBar.setMaximum(PCSCEidController.ACTIVITY.getActivityCount());
		        setProgress(act.ordinal());
		    }
		    break;

		case FILE_LOADED:
		default:
		    statusText.setText(cardStatusTexts.get(eidController
			    .getState()));
		}
	    if(versionChecker.hasNewVersion()) {
	    	versionText.setText(bundle.getString("new_version_msg") + " " + versionChecker.getLatestVersion());
	    } else {
	    	versionText.setText("");
	    }
		if(defaultFile != null && defaultFile.isFile()) {
			eidController.loadFromFile(defaultFile);
		}
		defaultFile = null;
	    }
	});
    }

    private void initComponents() {
	tabPanel = new JTabbedPane();
	statusPanel = new JPanel();
	statusIcon = new JLabel();
	versionStatusContainer = new JPanel();
	statusText = new JLabel();
	versionText = new JLabel();
	printPanel = new JPanel();
	printButton = new JButton();

	menuBar = new JMenuBar();

	fileMenu = new JMenu();
	openMenuItem = new JMenuItem();
	saveAsMenu = new JMenu();
	saveAsXMLMenuItem = new JMenuItem();
	saveAsCSVMenuItem = new JMenuItem();
	closeMenuItem = new JMenuItem();
	preferencesMenuItem = new JMenuItem();
	printMenuItem = new JMenuItem();
	fileMenuQuitItem = new JMenuItem();

	languageMenu = new JMenu();
	languageGermanMenuItem = new JMenuItem();
	languageEnglishMenuItem = new JMenuItem();
	languageFrenchMenuItem = new JMenuItem();
	languageDutchMenuItem = new JMenuItem();

	helpMenu = new JMenu();
	aboutMenuItem = new JMenuItem();
	faqMenuItem = new JMenuItem();
	testMenuItem = new JMenuItem();
	showLogMenuItem = new JCheckBoxMenuItem();

	setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	setBackground(new Color(255, 255, 255));
	setMinimumSize(new Dimension(800, 600));

	tabPanel.setPreferredSize(new Dimension(600, 512));
	getContentPane().add(tabPanel, BorderLayout.CENTER);

	statusPanel.setLayout(new BorderLayout());

	statusIcon.setHorizontalAlignment(SwingConstants.CENTER);
	statusIcon
		.setIcon(new ImageIcon(
			getClass()
				.getResource(
					"/be/fedict/eidviewer/gui/resources/icons/state_noeidpresent.png"))); // NOI18N
	statusIcon.setMaximumSize(new Dimension(72, 72));
	statusIcon.setMinimumSize(new Dimension(72, 72));
	statusIcon.setPreferredSize(new Dimension(90, 72));
	statusPanel.add(statusIcon, BorderLayout.EAST);

	progressBar = new JProgressBar();
	
	versionStatusContainer.setLayout(new BorderLayout());
	statusText.setHorizontalAlignment(SwingConstants.RIGHT);
	versionStatusContainer.add(statusText, BorderLayout.EAST);
	versionText.setHorizontalAlignment(SwingConstants.LEFT);
	versionStatusContainer.add(versionText, BorderLayout.WEST);
	statusPanel.add(versionStatusContainer, BorderLayout.CENTER);
	statusPanel.add(progressBar, BorderLayout.SOUTH);
	progressBar.setVisible(false);
	
	printPanel.setMinimumSize(new Dimension(72, 72));
	printPanel.setPreferredSize(new Dimension(72, 72));
	printPanel.setLayout(new GridBagLayout());

	printButton.setAction(printAction);
	printButton.setHideActionText(true);
	printButton.setMaximumSize(new Dimension(200, 50));
	printButton.setMinimumSize(new Dimension(50, 50));
	printButton.setPreferredSize(new Dimension(200, 50));
	printButton.setIcon(new ImageIcon(getClass().getResource(
		"/be/fedict/eidviewer/gui/resources/icons/print.png"))); // NOI18N
	printPanel.add(printButton, new GridBagConstraints());

	statusPanel.add(printPanel, BorderLayout.WEST);

	getContentPane().add(statusPanel, BorderLayout.SOUTH);

	openMenuItem.setAction(openAction);
	openMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
	fileMenu.add(openMenuItem);

	saveAsXMLMenuItem.setAction(saveAsXMLAction);
	saveAsCSVMenuItem.setAction(saveAsCSVAction);
	saveAsMenu.add(saveAsXMLMenuItem);
	saveAsMenu.add(saveAsCSVMenuItem);
	fileMenu.add(saveAsMenu);

	closeMenuItem.setAction(closeAction);
	closeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
	fileMenu.add(closeMenuItem);

	fileMenu.addSeparator();

	preferencesMenuItem.setAction(preferencesAction);
	fileMenu.add(preferencesMenuItem);

	fileMenu.addSeparator();

	printMenuItem.setAction(printAction);
	printMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
	fileMenu.add(printMenuItem);

	fileMenu.addSeparator();

	quitAction.setEnabled(true);
	fileMenuQuitItem.setAction(quitAction);
	fileMenuQuitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q,
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
	fileMenu.add(fileMenuQuitItem);

	menuBar.add(fileMenu);

	languageGermanMenuItem.setAction(new LanguageAction("Deutsch",
		new Integer(KeyEvent.VK_D), new Locale("de", "BE"), this));
	languageGermanMenuItem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_D, Toolkit.getDefaultToolkit()
			.getMenuShortcutKeyMask()));
	languageEnglishMenuItem.setAction(new LanguageAction("English",
		new Integer(KeyEvent.VK_E), new Locale("en", "US"), this));
	languageEnglishMenuItem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_E, Toolkit.getDefaultToolkit()
			.getMenuShortcutKeyMask()));
	languageFrenchMenuItem.setAction(new LanguageAction("Français",
		new Integer(KeyEvent.VK_F), new Locale("fr", "BE"), this));
	languageFrenchMenuItem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_F, Toolkit.getDefaultToolkit()
			.getMenuShortcutKeyMask()));
	languageDutchMenuItem.setAction(new LanguageAction("Nederlands",
		new Integer(KeyEvent.VK_N), new Locale("nl", "BE"), this));
	languageDutchMenuItem.setAccelerator(KeyStroke.getKeyStroke(
		KeyEvent.VK_N, Toolkit.getDefaultToolkit()
			.getMenuShortcutKeyMask()));
	languageMenu.add(languageGermanMenuItem);
	languageMenu.add(languageEnglishMenuItem);
	languageMenu.add(languageFrenchMenuItem);
	languageMenu.add(languageDutchMenuItem);
	menuBar.add(languageMenu);

	aboutMenuItem.setAction(aboutAction);
	faqMenuItem.setAction(faqAction);
	testMenuItem.setAction(testAction);
	
	helpMenu.add(aboutMenuItem);
	helpMenu.addSeparator();
	helpMenu.add(faqMenuItem);
	helpMenu.add(testMenuItem);
	helpMenu.addSeparator();
	
	showLogMenuItem.setAction(showHideLogAction);
	showLogMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,
		Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
	helpMenu.add(showLogMenuItem);
	menuBar.add(helpMenu);
	setJMenuBar(menuBar);

	ArrayList<Image> imageList = new ArrayList<Image>();
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_512.png").getImage());
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_256.png").getImage());
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_128.png").getImage());
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_64.png").getImage());
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_32.png").getImage());
	imageList.add(ImageUtilities.getIcon(this.getClass(),
		ICONS + "logo_16.png").getImage());
	setIconImages(imageList);

	setTitle("eID Viewer");

	pack();
    }

    private void initI18N() {
	Locale.setDefault(ViewerPrefs.getLocale());
	bundle = ResourceBundle
		.getBundle("be/fedict/eidviewer/gui/resources/BelgianEidViewer"); // NOI18N
	fileMenu.setText(bundle.getString("fileMenuTitle")); // NOI18N
	languageMenu.setText(bundle.getString("languageMenuTitle")); // NOI18N
	helpMenu.setText(bundle.getString("helpMenuTitle")); // NOI18N
	aboutMenuItem.setText(bundle.getString("about.Action.text")); // NOI18N
	quitAction.setName(bundle.getString("quit.Action.text")); // NOI18N
	openAction.setName(bundle.getString("openFile.Action.text")); // NOI18N
	saveAsMenu.setText(bundle.getString("saveAs")); // NOI18N
	saveAsXMLAction.setName(bundle.getString("saveXML.Action.text")); // NOI18N
	saveAsCSVAction.setName(bundle.getString("saveCSV.Action.text")); // NOI18N

	closeAction.setName(bundle.getString("closeFile.Action.text")); // NOI18N
	printAction.setName(bundle.getString("print.Action.text")); // NOI18N
	aboutAction.setName(bundle.getString("about.Action.text")); // NOI18N
	faqAction.setName(bundle.getString("faq.Action.text")); // NOI18N
	testAction.setName(bundle.getString("test.Action.text")); // NOI18N
	preferencesAction.setName(bundle.getString("prefs.Action.text")); // NOI18N
	showHideLogAction.setName(bundle.getString("showLogTab")); // NOI18N
	cardStatusTexts = new EnumMap<PCSCEidController.STATE, String>(
		PCSCEidController.STATE.class);
	cardStatusTexts
		.put(PCSCEidController.STATE.NO_READERS, bundle
			.getString(PCSCEidController.STATE.NO_READERS
				.toString()));
	cardStatusTexts.put(PCSCEidController.STATE.ERROR,
		bundle.getString(PCSCEidController.STATE.ERROR.toString()));
	cardStatusTexts.put(PCSCEidController.STATE.NO_EID_PRESENT, bundle
		.getString(PCSCEidController.STATE.NO_EID_PRESENT.toString()));
	cardStatusTexts.put(PCSCEidController.STATE.FILE_LOADING, bundle
		.getString(PCSCEidController.STATE.FILE_LOADING.toString()));
	cardStatusTexts.put(PCSCEidController.STATE.FILE_LOADED, bundle
		.getString(PCSCEidController.STATE.FILE_LOADED.toString()));
	cardStatusTexts.put(PCSCEidController.STATE.EID_YIELDED, bundle
		.getString(PCSCEidController.STATE.EID_YIELDED.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.IDLE,
		bundle.getString(PCSCEidController.ACTIVITY.IDLE.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.READING_IDENTITY, bundle
		.getString(PCSCEidController.ACTIVITY.READING_IDENTITY
			.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.READING_ADDRESS, bundle
		.getString(PCSCEidController.ACTIVITY.READING_ADDRESS
			.toString()));
	activityTexts
		.put(PCSCEidController.ACTIVITY.READING_PHOTO, bundle
			.getString(PCSCEidController.ACTIVITY.READING_PHOTO
				.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.READING_RRN_CHAIN, bundle
		.getString(PCSCEidController.ACTIVITY.READING_RRN_CHAIN
			.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.READING_AUTH_CHAIN, bundle
		.getString(PCSCEidController.ACTIVITY.READING_AUTH_CHAIN
			.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.READING_SIGN_CHAIN, bundle
		.getString(PCSCEidController.ACTIVITY.READING_SIGN_CHAIN
			.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.VALIDATING_IDENTITY,
		bundle.getString(PCSCEidController.ACTIVITY.VALIDATING_IDENTITY
			.toString()));
	activityTexts.put(PCSCEidController.ACTIVITY.VALIDATING_ADDRESS, bundle
		.getString(PCSCEidController.ACTIVITY.VALIDATING_ADDRESS
			.toString()));
    }

    private void initPanels() {
	identityPanel = new IdentityPanel();
	cardPanel = new CardPanel();
	certificatesPanel = new CertificatesPanel();
	tabPanel.add(identityPanel);
	tabPanel.add(cardPanel);
	tabPanel.add(certificatesPanel);
	initTabsI18N();

	if (ViewerPrefs.getShowLogTab()) {
	    showLogMenuItem.setSelected(true);
	    showLog(true);
	}
    }

    private void initTabsI18N() {
	identityPanel.setName(bundle.getString("IDENTITY"));
	cardPanel.setName(bundle.getString("CARD"));
	certificatesPanel.setName(bundle.getString("CERTIFICATES"));
	if (logPanel != null)
	    logPanel.setName(bundle.getString("LOG"));
	for (int i = 0; i < tabPanel.getComponentCount(); i++)
	    tabPanel.setTitleAt(i, tabPanel.getComponent(i).getName());
    }

    private void initIcons() {
	cardStatusIcons = new EnumMap<PCSCEidController.STATE, ImageIcon>(
		PCSCEidController.STATE.class);
	cardStatusIcons.put(
		PCSCEidController.STATE.NO_READERS,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.NO_READERS + EXTENSION_PNG));
	cardStatusIcons.put(
		PCSCEidController.STATE.ERROR,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.ERROR + EXTENSION_PNG));
	cardStatusIcons.put(
		PCSCEidController.STATE.NO_EID_PRESENT,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.NO_EID_PRESENT
			+ EXTENSION_PNG));
	cardStatusIcons.put(
		PCSCEidController.STATE.EID_PRESENT,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.EID_PRESENT + EXTENSION_PNG));
	cardStatusIcons
		.put(PCSCEidController.STATE.FILE_LOADING,
			ImageUtilities.getIcon(this.getClass(), ICONS
				+ PCSCEidController.STATE.FILE_LOADING
				+ EXTENSION_PNG));
	cardStatusIcons.put(
		PCSCEidController.STATE.FILE_LOADED,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.FILE_LOADED + EXTENSION_PNG));
	cardStatusIcons.put(
		PCSCEidController.STATE.EID_YIELDED,
		ImageUtilities.getIcon(this.getClass(), ICONS
			+ PCSCEidController.STATE.EID_YIELDED + EXTENSION_PNG));
    }

    private void initTexts() {
	cardStatusTexts = new EnumMap<PCSCEidController.STATE, String>(
		PCSCEidController.STATE.class);
	activityTexts = new EnumMap<PCSCEidController.ACTIVITY, String>(
		PCSCEidController.ACTIVITY.class);
    }

    public void setDynamicLocale(Locale locale) {
	ViewerPrefs.setLocale(locale);
	initI18N();
	initTabsI18N();
	cardPanel.setDynamicLocale(locale);
	certificatesPanel.setDynamicLocale(locale);
	identityPanel.setDynamicLocale(locale);
	eid.setLocale(locale);

	// to update the status strings
	updateVisibleState();

	// also set this in Version 3.x.y-style preferences, to make sure
	// other eID software picks up the language choice
	Version35LocalePrefs.writeUserLocaleChoice(locale);
    }

    private class AboutAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = 6061672451631370548L;

	public AboutAction(String text) {
	    super(text);
	}

	public void actionPerformed(ActionEvent ae) {
	    JOptionPane.showMessageDialog(null, new AboutPanel(),
		    bundle.getString("about.Action.text"),
		    JOptionPane.PLAIN_MESSAGE);
	}
    }

    private class PreferencesAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -7995910159254024855L;

	public PreferencesAction(String text) {
	    super(text);
	}

	public void actionPerformed(ActionEvent ae) {
	    PreferencesPanel preferencesPanel = new PreferencesPanel();
	    int answer = JOptionPane.showOptionDialog(null, preferencesPanel,
		    bundle.getString("prefs.Action.text"),
		    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
		    null, null, null);

	    if (answer == JOptionPane.OK_OPTION) {
		ViewerPrefs.setProxyType(preferencesPanel.getHTTPProxyType());
		ViewerPrefs.setSpecificProxyHost(preferencesPanel
			.getHttpProxyHost());
		ViewerPrefs.setSpecificProxyPort(preferencesPanel
			.getHttpProxyPort());
		setTrustServiceProxy();
	    }
	}
    }

    private class PrintAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = 7539156424813527996L;

	public PrintAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("print action chosen..");
	    PrinterJob job = PrinterJob.getPrinterJob();
	    job.setJobName(eidController.getIdentity().getNationalNumber());

	    IDPrintout printout = new IDPrintout();
	    printout.setIdentity(eidController.getIdentity());
	    printout.setAddress(eidController.getAddress());

	    try {
		printout.setPhoto(eidController.getPhotoImage());
	    } catch (IOException ex) {
		logger.log(Level.SEVERE, "Photo conversion from JPEG Failed",
			ex);
	    }

	    job.setPrintable(printout);

	    boolean ok = job.printDialog();

	    if (ok) {
		try {
		    logger.finest("starting print job..");
		    job.print();
		    logger.finest("print job completed.");
		} catch (PrinterException pex) {
		    logger.log(Level.SEVERE, "Print Job Failed", pex);
		}
	    }
	}
    }

    private class QuitAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = 1752106249237329401L;

	public QuitAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("quit action chosen..");
	    BelgianEidViewer.this.stop();
	}
    }

    private class ShowHideLogAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = 8800895193399501827L;

	public ShowHideLogAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    ViewerPrefs.setShowLogTab(showLogMenuItem.getState());
	    showLog(ViewerPrefs.getShowLogTab());
	    if (ViewerPrefs.getShowLogTab())
		LogHelper.logJavaSpecs(logger);
	}
    }

    private class OpenFileAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -4445607438857967656L;

	public OpenFileAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("Open action chosen..");
	    final JFileChooser fileChooser = new JFileChooser(
		    ViewerPrefs.getLastOpenLocation());

	    fileChooser
		    .setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
	    fileChooser.setAcceptAllFileFilterUsed(false);

	    fileChooser.addChoosableFileFilter(new EidFileFilter(true,true, bundle.getString("allEIDFiles")));
	    fileChooser.addChoosableFileFilter(new EidFileFilter(false,true,bundle.getString("csvEIDFiles")));
	    fileChooser.setFileView(new EidFileView(bundle));

	    EidFilePreviewAccessory preview = new EidFilePreviewAccessory(
		    bundle);
	    fileChooser.setAccessory(preview);
	    fileChooser.addPropertyChangeListener(preview);
	    fileChooser.revalidate();

	    if (fileChooser.showOpenDialog(BelgianEidViewer.this) == JFileChooser.APPROVE_OPTION) {
		File file = fileChooser.getSelectedFile();
		if (file.isFile()) {
		    eidController.loadFromFile(file);
		}

		ViewerPrefs.setLastOpenLocation(file.getParentFile());
	    }
	}
    }

    private class SaveFileAsXMLAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -4991039413918478765L;

	public SaveFileAsXMLAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("Save action chosen..");
	    final JFileChooser fileChooser = new JFileChooser(
		    ViewerPrefs.getLastSaveLocation());
	    fileChooser.setAcceptAllFileFilterUsed(false);
	    fileChooser.addChoosableFileFilter(new EidFileFilter(true,true,bundle.getString("allEIDFiles")));
	    fileChooser.setCurrentDirectory(ViewerPrefs.getLastSaveLocation());

	    File suggestedFile = null;

	    try {
		suggestedFile = (new File(fileChooser.getCurrentDirectory(),
			eidController.getIdentity().getNationalNumber()
				+ ".eid"));
		logger.log(Level.FINE, "Suggesting \"{0}\"",
			suggestedFile.getCanonicalPath());
		fileChooser.setSelectedFile(suggestedFile);
	    } catch (IOException ex) {
		// suggested file likely doesn't exist yet but that's OK here.
	    }

	    if (fileChooser.showSaveDialog(BelgianEidViewer.this) == JFileChooser.APPROVE_OPTION) {
		File targetFile;
		try {
		    targetFile = fileChooser.getSelectedFile();
		    if (!targetFile.getCanonicalPath().toLowerCase()
			    .endsWith(".eid")) {
			logger.fine("File would not have correct extension, appending \".eid\"");
			targetFile = new File(targetFile.getCanonicalPath()
				+ ".eid");
		    }
		} catch (IOException ex) {
		    logger.log(
			    Level.SEVERE,
			    "Problem getting Canonical Name For Filename Extension Correction",
			    ex);
		    return;
		}
		try {
		    eidController.saveToXMLFile(targetFile);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not save file", ex);
			JOptionPane.showMessageDialog(fileChooser, ex.getLocalizedMessage(), "error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		ViewerPrefs.setLastSaveLocation(fileChooser.getSelectedFile()
			.getParentFile());
	    }
	}
    }

    private class SaveFileAsCSVAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -4991039413918478765L;

	public SaveFileAsCSVAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("Save action chosen..");
	    final JFileChooser fileChooser = new JFileChooser();
	    fileChooser.setAcceptAllFileFilterUsed(false);
	    fileChooser.addChoosableFileFilter(new EidFileFilter(false, true,bundle.getString("csvEIDFiles")));
	    fileChooser.setCurrentDirectory(ViewerPrefs.getLastSaveLocation());

	    File suggestedFile = null;

	    try {
		suggestedFile = (new File(fileChooser.getCurrentDirectory(),
			eidController.getIdentity().getNationalNumber()
				+ ".csv"));
		logger.log(Level.FINE, "Suggesting \"{0}\"",
			suggestedFile.getCanonicalPath());
		fileChooser.setSelectedFile(suggestedFile);
	    } catch (IOException ex) {
		// suggested file likely doesn't exist yet but that's OK here.
	    }

	    if (fileChooser.showSaveDialog(BelgianEidViewer.this) == JFileChooser.APPROVE_OPTION) {
		File targetFile;
		try {
		    targetFile = fileChooser.getSelectedFile();
		    if (!targetFile.getCanonicalPath().toLowerCase()
			    .endsWith(".csv")) {
			logger.fine("File would not have correct extension, appending \".csv\"");
			targetFile = new File(targetFile.getCanonicalPath()
				+ ".csv");
		    }
		} catch (IOException ex) {
		    logger.log(
			    Level.SEVERE,
			    "Problem getting Canonical Name For Filename Extension Correction",
			    ex);
		    return;
		}
		try {
		    eidController.saveToCSVFile(targetFile);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not save file", ex);
			JOptionPane.showMessageDialog(fileChooser, ex.getLocalizedMessage(), "error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		ViewerPrefs.setLastSaveLocation(fileChooser.getSelectedFile()
			.getParentFile());
	    }
	}
    }

    private class CloseFileAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -5295694046895449376L;

	public CloseFileAction(String text, Integer mnemonic) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.fine("Close action chosen..");
	    eidController.closeFile();
	}
    }

    private class FAQAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -8609609883790744461L;

	public FAQAction(String text) {
	    super(text);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.log(Level.FINE, "FAQ action chosen");

	    try
	    {
		BelgianEidViewer.this.desktop.browse(ViewerPrefs.getFAQURI(Locale.getDefault()));
	    }
	    catch (IOException ioex) {
		logger.log(Level.SEVERE, "Failed to browse to FAQ",ioex);
	    }
	}
    }
    
    private class TestAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = 3506188458464209588L;

	public TestAction(String text) {
	    super(text);
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.log(Level.FINE, "TEST action chosen");
	    try
	    {
		BelgianEidViewer.this.desktop.browse(ViewerPrefs.getTestURI());
	    }
	    catch (IOException ioex) {
		logger.log(Level.SEVERE, "Failed to browse to TEST",ioex);
	    }
	}
    }

    private class LanguageAction extends DynamicLocaleAbstractAction {
	private static final long serialVersionUID = -8912334588329666310L;
	private Locale locale;
	private DynamicLocale target;

	public LanguageAction(String text, Integer mnemonic, Locale locale,
		DynamicLocale target) {
	    super(text);
	    putValue(MNEMONIC_KEY, mnemonic);
	    this.locale = locale;
	    this.target = target;
	}

	public void actionPerformed(ActionEvent ae) {
	    logger.log(Level.FINE, "Language action chosen : [{0}]",
		    locale.toString());
	    target.setDynamicLocale(locale);
	}
    }

    private void setTrustServiceProxy() {
	Proxy proxyToUse = ViewerPrefs.getProxy();
	if (proxyToUse != Proxy.NO_PROXY)
	    trustServiceController.setProxy(ProxyUtils.getHostName(proxyToUse),
		    ProxyUtils.getPort(proxyToUse));
	else
	    trustServiceController.setProxy(null, 0);
    }

    /*
     * ------------------------ Interaction meant for Applets
     * ----------------------------------------------------------------
     */
    public void addDetailMessage(String detailMessage) {
	logger.finest(detailMessage);
    }

    public void setStatusMessage(Status status, MESSAGE_ID messageId) {
	String message = eid.getMessageString(messageId);
	logger.info(message);
    }

    public boolean privacyQuestion(boolean includeAddress,
	    boolean includePhoto, String identityDataUsage) {
	// this app's only purpose being to read eID cards.. asking
	// "are you sure" is merely annoying to the user
	// and gives no extra security whatsoever
	// (the privacyQuestion was designed for Applets and Middleware, where
	// it makes a *lot* of sense)
	return true;
    }

    public Component getParentComponent() {
	return this;
    }

    public void resetProgress(int max) {
        progressBar.setVisible(false);
        progressBar.setMaximum(max);
        progressBar.setValue(0);
    }

    public void increaseProgress() {
        progressBar.setIndeterminate(false);
        progressBar.setVisible(true);
        progressBar.setValue(progressBar.getValue() + 1);
    }

    public void setProgress(final int progress) {
        progressBar.setIndeterminate(false);
        progressBar.setVisible(true);
        progressBar.setValue(progress);
    }

    public void setProgressIndeterminate() {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
    }

    /*
     * --------------------------------------------------------------------------
     * --------------
     */
    public static void main(String args[]) {
	try {
	    logger.finest("Setting System Look And Feel");
	    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	} catch (Exception e) {
	    logger.log(Level.WARNING, "Can't Set SystemLookAndFeel", e);
	}

	BelgianEidViewer viewer = new BelgianEidViewer();
	if(args.length > 0) {
		viewer.defaultFile = new File(args[0]);
	}
	viewer.start();
    }

    public void showLog(boolean show) {
	if (show) {
	    if (logPanel == null) {
		logPanel = new LogPanel();
		logPanel.start();
		tabPanel.add(logPanel, bundle.getString("LOG"));
	    }
	} else {
	    if (logPanel != null) {
		logPanel.stop();
		tabPanel.remove(logPanel);
		logPanel = null;
	    }
	}
    }

	@Override
	public void confirmAuthenticationSignature(String message) {
		int response = JOptionPane.showConfirmDialog(this.getParentComponent(),
				message, "eID Authentication Signature",
				JOptionPane.YES_NO_OPTION);
		if (response != JOptionPane.YES_OPTION) {
			throw new SecurityException("user cancelled");
		}
	}

	@Override
	public int confirmSigning(String description, String digestAlgo) {
		String signatureCreationLabel = eid.getMessageString(MESSAGE_ID.SIGNATURE_CREATION);
		String signQuestionLabel = eid.getMessageString(MESSAGE_ID.SIGN_QUESTION);
		String signatureAlgoLabel = eid.getMessageString(MESSAGE_ID.SIGNATURE_ALGO);
		int response = JOptionPane.showConfirmDialog(this.getParentComponent(),
				signQuestionLabel + " \"" + description + "\"?\n"
						+ signatureAlgoLabel + ": " + digestAlgo + " with RSA",
				signatureCreationLabel, JOptionPane.YES_NO_OPTION);
		return response;
	}
}
