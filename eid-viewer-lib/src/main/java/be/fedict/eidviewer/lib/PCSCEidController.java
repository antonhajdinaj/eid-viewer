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

package be.fedict.eidviewer.lib;

import be.fedict.eid.applet.service.Address;
import be.fedict.eid.applet.service.Identity;
import be.fedict.eidviewer.lib.file.EidFiles;
import be.fedict.trust.client.TrustServiceDomains;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Observable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

/**
 *
 * @author Frank Marien
 */
public class PCSCEidController extends Observable implements Runnable, Observer, EidData
{
    private static final Logger logger = Logger.getLogger(PCSCEidController.class.getName());
    private boolean running = false;
    private PCSCEid eid;
    private STATE state;
    private ACTIVITY activity;
    private ACTION runningAction;
    private Identity identity;
    private Address address;
    private byte[] photo;
    private boolean identityTrusted,addressTrusted,identityValidated,addressValidated;
    private List<X509Certificate> 		 ccaCertChain;
    private X509CertificateChainAndTrust rrnCertChain;
    private X509CertificateChainAndTrust authCertChain;
    private X509CertificateChainAndTrust signCertChain;
    private TrustServiceController trustServiceController;
    private Timer   yieldLockedTimer;
    private long    yieldConsideredLockedAt;
    private boolean autoValidatingTrust, yielding,loadedFromFile;
    private boolean hasExclusive;

    public PCSCEidController(PCSCEid eid)
    {
        this.eid = eid;
        setState(STATE.IDLE);
        setActivity(ACTIVITY.IDLE);
        this.runningAction = ACTION.NONE;
        this.autoValidatingTrust = false;
        yieldLockedTimer = new Timer("yieldLockedTimer", true);
        yieldConsideredLockedAt=Long.MAX_VALUE;
    }

    public void start()
    {
        logger.fine("starting..");
        Thread me = new Thread(this,"PCSCEidController");
        me.setDaemon(true);
        me.start();
        
        yieldLockedTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                if(state==STATE.EID_PRESENT && (System.currentTimeMillis()>yieldConsideredLockedAt))
                {
                   setState(STATE.EID_YIELDED);
                }
                else
                {
                    if(state==STATE.EID_YIELDED && (System.currentTimeMillis()<yieldConsideredLockedAt))
                    {
                        setState(STATE.EID_PRESENT);
                    }
                }
            }   
        },
        1000, 500);
    }

    public void stop()
    {
        logger.fine("stopping..");
        running = false;
        yieldLockedTimer.cancel();
        if (trustServiceController != null)
            trustServiceController.stop();
    }

    public PCSCEidController setTrustServiceController(TrustServiceController trustServiceController)
    {
        logger.fine("setting TrustServiceController");
        this.trustServiceController = trustServiceController;
        this.trustServiceController.addObserver(this);
        this.trustServiceController.start();
        return this;
    }

    public void setAutoValidateTrust(boolean autoValidateTrust)
    {
        if (trustServiceController != null)
            this.autoValidatingTrust = autoValidateTrust;
    }

    private void eid_changePin() throws Exception
    {
        logger.fine("eid_changePin");
        
        try
        {
            eid.changePin();
        }
        catch (RuntimeException rte)
        {
            logger.log(Level.SEVERE, "ChangePin Operation Failed", rte);
        }

        runningAction = ACTION.NONE;
    }

    private void eid_verifyPin() throws Exception
    {
        logger.fine("eid_verifyPin");

        try
        {
            eid.verifyPin(false);
        }
        catch (RuntimeException rte)
        {
            logger.log(Level.SEVERE, "VerifyPin Operation Failed", rte);
        }

        runningAction = ACTION.NONE;
    }

    private void trustController_validateTrust() throws Exception
    {
        logger.fine("trustController_validateTrust");
        
        if (trustServiceController == null)
            return;

        try
        {
            if(rrnCertChain!=null)
                trustServiceController.validateLater(rrnCertChain);

            if(authCertChain!=null)
                trustServiceController.validateLater(authCertChain);

            if(signCertChain!=null)
                trustServiceController.validateLater(signCertChain);
            
            setState();
        }
        catch (RuntimeException rte)
        {
            logger.log(Level.SEVERE, "Failed To Enqueue Trust Validations", rte);
        }

        runningAction = ACTION.NONE;
    }

    public void clear()
    {
        logger.fine("clear");
        eid.clear();
        identity = null;
        address = null;
        photo = null;
        authCertChain = null;
        signCertChain = null;
        rrnCertChain = null;
        if (trustServiceController != null)
            trustServiceController.clear();
        identityValidated=false;
        addressValidated=false;
        identityTrusted=false;
        addressTrusted=false;
        setState();
    }

    public void update(Observable o, Object o1)
    {
        setState();
    }

    public void loadFromFile(File file)
    {
        setState(STATE.FILE_LOADING);
        
        try
        {
            clear();
            EidFiles.loadFromFile(file, this);
            setLoadedFromFile(true);
            setState(STATE.FILE_LOADED);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Failed To Load EID File", ex);
            clear();
            setState(STATE.IDLE);
        }
    }

    public void saveToXMLFile(File selectedFile) throws IOException
    {
        EidFiles.saveToXMLFile(selectedFile, this);
    }
    
    public void saveToCSVFile(File selectedFile) throws IOException
    {
        EidFiles.saveToCSVFile(selectedFile, this);
    }
    
    public void saveToXMLFile(OutputStream output) throws IOException {
    	EidFiles.saveToXMLFile(output,  this);
    }


    public static enum STATE
    {
        IDLE("state_idle"),
        ERROR("state_error"),
        NO_READERS("state_noreaders"),
        NO_EID_PRESENT("state_noeidpresent"),
        EID_PRESENT("state_eidpresent"),
        FILE_LOADING("state_fileloading"),
        FILE_LOADED("state_fileloaded"),
        EID_YIELDED("state_eidyielded");
        private final String state;

        private STATE(String state)
        {
            this.state = state;
        }

        @Override
        public String toString()
        {
            return this.state;
        }
    };

    public static enum ACTIVITY
    {
        IDLE                ("activity_idle"),
        READING_IDENTITY    ("reading_identity"),
        READING_ADDRESS     ("reading_address"),
        READING_PHOTO       ("reading_photo"),
        READING_RRN_CHAIN   ("reading_rrn_chain"),
        READING_CCA_CHAIN   ("reading_cca_chain"),
        VALIDATING_IDENTITY ("validating_identity"),
        VALIDATING_ADDRESS  ("validating_address"),
        READING_AUTH_CHAIN  ("reading_auth_chain"),
        READING_SIGN_CHAIN  ("reading_sign_chain");
        
        private final String state;

        private ACTIVITY(String state)
        {
            this.state = state;
        }

        @Override
        public String toString()
        {
            return this.state;
        }

        public static int getActivityCount() {
	    return ACTIVITY.values().length - 1;
        }
    }

    public static enum ACTION
    {
        NONE("none"), CHANGE_PIN("change_pin"), VALIDATETRUST("validatetrust"), VERIFY_PIN("verify_pin");
        private final String order;

        private ACTION(String order)
        {
            this.order = order;
        }

        @Override
        public String toString()
        {
            return this.order;
        }
    }

    private void setState(STATE newState)
    {
        state = newState;
        setState();
    }

    private void setActivity(ACTIVITY newActivity)
    {
        activity = newActivity;
        setState();
    }

    private void setStateAndActivity(STATE newState, ACTIVITY newActivity)
    {
        state = newState;
        activity = newActivity;
        setState();
    }

    private void setState()
    {
        logger.log(Level.FINER,"state {0} activity {1} action {2}", new Object[] {getState()!=null?getState().toString():"null", getActivity()!=null?getActivity().toString():"null",runningAction!=null?runningAction.toString():"null"});
        setChanged();
        notifyObservers();
    }

    public void run()
    {
        running = true;
        while(running)
        {
            try
            {
            	logger.fine("starting reader sequence");
                if(!eid.hasCardReader())
                {
                    logger.fine("waiting for card readers..");
                    setState(STATE.NO_READERS);
                    eid.waitForCardReader();
                }

                logger.fine("starting card sequence");
                if(!eid.isEidPresent())
                {
                    logger.fine("waiting for eid card..");
                    setState(STATE.NO_EID_PRESENT);
                    eid.waitForEidPresent();
                }
                
                if(isLoadedFromFile())
                {
                    logger.fine("clearing file-loaded data");
                    clear();
                    setState(STATE.IDLE);
                }
                
                /////////////////////////////////////////////////////////////////////////////////////////////
                // EXCLUSIVE ACCESS STARTS
                // set hasExclusive ourselves, because the methods above have called beginExclusive for us..
                hasExclusive=true;
                /////////////////////////////////////////////////////////////////////////////////////////////

                logger.fine("reading identity from card..");
                setStateAndActivity(STATE.EID_PRESENT, ACTIVITY.READING_IDENTITY);
                setLoadedFromFile(false);
                try
                {
                	beginExclusive();
                	identity = eid.getIdentity();
                }
                finally
                {
                	endExclusive();
                }
                setState();

                logger.fine("reading address from card..");
                setActivity(ACTIVITY.READING_ADDRESS);
                try
                {
                	beginExclusive();
                	address = eid.getAddress();
                }
                finally
                {
                	endExclusive();
                }
                setState();
                
                logger.fine("reading photo from card..");
                setActivity(ACTIVITY.READING_PHOTO);
                try
                {
                	beginExclusive();
                	photo = eid.getPhotoJPEG();
                }
                finally
                {
                	endExclusive();
                }
                setState();
                
                logger.fine("reading rrn chain from card..");
                setActivity(ACTIVITY.READING_RRN_CHAIN);
                try
                {
                	beginExclusive();
	                rrnCertChain = new X509CertificateChainAndTrust(TrustServiceDomains.BELGIAN_EID_NATIONAL_REGISTRY_TRUST_DOMAIN,eid.getRRNCertificateChain());
	                if (trustServiceController != null && autoValidatingTrust)
	                {
	                    logger.fine("enqueueing RRN chain for validation (auto-validate is on)");
	                    trustServiceController.validateLater(rrnCertChain);
	                }
                }
                finally
                {
                	endExclusive();
                }
                setState();
                
                logger.fine("reading cca chain from card..");
                setActivity(ACTIVITY.READING_CCA_CHAIN);
                try
                {
                	beginExclusive();
	                ccaCertChain = eid.getCCACertificateChain();
                }
                finally
                {
                	endExclusive();
                }
                setState();

                logger.fine("validating identity");
                setActivity(ACTIVITY.VALIDATING_IDENTITY);
                try
                {
                	beginExclusive();
                	identityTrusted=eid.isIdentityTrusted();
                	identityValidated=true;
                }
                finally
                {
                	endExclusive();
                }
                setState();

                logger.fine("validating address");
                setActivity(ACTIVITY.VALIDATING_ADDRESS);
                try
                {
                	beginExclusive();
                	addressTrusted=eid.isAddressTrusted();
                	addressValidated=true;
                }
                finally
                {
                	endExclusive();
                }
                setState();

                logger.fine("reading authentication chain from card..");
                setActivity(ACTIVITY.READING_AUTH_CHAIN);
                try
                {
                	beginExclusive();
	                List<X509Certificate> aChain=eid.getAuthnCertificateChain();
	                if(aChain!=null)
	                {
	                    logger.fine("authentication chain found");
	                    authCertChain = new X509CertificateChainAndTrust(TrustServiceDomains.BELGIAN_EID_AUTH_TRUST_DOMAIN, aChain);
	                    if (trustServiceController != null && autoValidatingTrust)
	                    {
	                        logger.fine("enqueueing authentication chain for validation (auto-validate is on)");
	                        trustServiceController.validateLater(authCertChain);
	                    }
	                }
	                else
	                {
	                   logger.fine("no authentication chain found.");
	                }
                }
                finally
                {
                	endExclusive();
                }
                setState();

                logger.fine("reading signing chain from card..");
                setActivity(ACTIVITY.READING_SIGN_CHAIN);
                try
                {
                	beginExclusive();
	                List<X509Certificate> sChain=eid.getSignCertificateChain();
	                if(sChain!=null)
	                {
	                    logger.fine("signing chain found");
	                    signCertChain = new X509CertificateChainAndTrust(TrustServiceDomains.BELGIAN_EID_NON_REPUDIATION_TRUST_DOMAIN, sChain);
	                    if (trustServiceController != null && autoValidatingTrust)
	                    {
	                        logger.fine("enqueueing signing chain for validation (auto-validate is on)");
	                        trustServiceController.validateLater(signCertChain);
	                    }
	                }
	                else
	                {
	                   logger.fine("no signing chain found.");
	                }
                }
                finally
                {
                	endExclusive();
                }
                
                setActivity(ACTIVITY.IDLE);

                logger.fine("waiting for actions or card removal..");

                while (eid.isCardStillPresent())
                {
                    if (runningAction == ACTION.CHANGE_PIN)
                    {
                        logger.fine("requesting change_pin action");
                        
                        try
                        {
                        	beginExclusive();
                        	eid_changePin();
                        }
                        finally
                        {
                        	endExclusive();
                        }
                    }
                    else if(runningAction == ACTION.VERIFY_PIN)
                    {
                        logger.fine("requesting verify_pin action");
                        try
                        {
                        	beginExclusive();
                        	eid_verifyPin();
                        }
                        finally
                        {
                        	endExclusive();
                        }
                    }
                    else if (runningAction == ACTION.VALIDATETRUST)
                    {
                        logger.fine("requesting validate_trust action");
                        trustController_validateTrust();
                    }
                    else
                    {
                        try
                        {
                            Thread.sleep(1000);
                        }
                        catch(InterruptedException iex)
                        {
                            logger.log(Level.SEVERE, "Activity Loop was Interrupted",iex);
                        }
                    }
                }

                logger.fine("card was removed..");

                if(!isLoadedFromFile())
                {
                    logger.fine("clearing data of removed card");
                    clear();
                    setState(STATE.IDLE);
                }
            }
            catch (Exception ex)   // something failed. Clear out all data for security
            {
                clear();
                runningAction = ACTION.NONE;
                setState(STATE.ERROR);
                logger.log(Level.SEVERE, "Clearing Data for security reasons, due to unexpected problem.", ex);

                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException ex1)
                {
                    logger.log(Level.SEVERE, "Error Grace Time Loop Interruped", ex1);
                }

                setState(STATE.IDLE);
            }
            finally
            {
            	logger.fine("closing card access");
            	endExclusive();
            	eid.close();
            }
        }
    }

    public Address getAddress()
    {
        return address;
    }

    public Identity getIdentity()
    {
        return identity;
    }

    public byte[] getPhoto()
    {
        return photo;
    }

    public Image getPhotoImage() throws IOException
    {
        return ImageIO.read(new ByteArrayInputStream(getPhoto()));
    }

    public STATE getState()
    {
        return state;
    }

    public ACTIVITY getActivity()
    {
        return activity;
    }

    public boolean hasAddress()
    {
        return (address != null);
    }

    public boolean hasIdentity()
    {
        return (identity != null);
    }

    public boolean hasPhoto()
    {
        return (photo != null);
    }

    public boolean hasAuthCertChain()
    {
        return (authCertChain != null);
    }

    public X509CertificateChainAndTrust getAuthCertChain()
    {
        return authCertChain;
    }

    public boolean hasSignCertChain()
    {
        return (signCertChain != null);
    }

    public X509CertificateChainAndTrust getSignCertChain()
    {
        return signCertChain;
    }

    public boolean hasRRNCertChain()
    {
        return (rrnCertChain != null);
    }

    public X509CertificateChainAndTrust getRRNCertChain()
    {
        return rrnCertChain;
    }
    
    public List<X509Certificate> getCitizenCACertChain()
	{
    	return ccaCertChain;
	}

    public PCSCEidController changePin()
    {
        runningAction = ACTION.CHANGE_PIN;
        return this;
    }

    public PCSCEidController verifyPin()
    {
        runningAction = ACTION.VERIFY_PIN;
        return this;
    }

    public PCSCEidController validateTrust()
    {
        if(trustServiceController == null)
            return this;
        
        if(state==STATE.FILE_LOADED)
        {
            try
            {
                logger.fine("validate_trust for data from file..");
                trustController_validateTrust();
            }
            catch (Exception ex)
            {
                logger.log(Level.SEVERE, "Problem Validating Trust From Saved Identity", ex);
            }
        }
        else
        {
            logger.fine("validate_trust for data on inserted card..");
            runningAction = ACTION.VALIDATETRUST;
        }
        
        return this;
    }

    public boolean isYielding()
    {
        return yielding;
    }

    public void setYielding(boolean yielding)
    {
        this.yielding = yielding;

        if(yielding)
            yieldConsideredLockedAt=1000+System.currentTimeMillis();
        else
            yieldConsideredLockedAt=Long.MAX_VALUE;
    }

    public boolean isReadyForCommand()
    {
        return (state == STATE.EID_PRESENT || state == STATE.FILE_LOADED) && (activity == ACTIVITY.IDLE) && (runningAction == ACTION.NONE) && (!isValidatingTrust());
    }

    public boolean isValidatingTrust()
    {
        return trustServiceController != null ? trustServiceController.isValidating() : false;
    }

    public boolean isAutoValidatingTrust()
    {
        return autoValidatingTrust;
    }

    public boolean isLoadedFromFile()
    {
        return loadedFromFile;
    }

    public boolean isAddressTrusted()
    {
        return addressTrusted;
    }

    public boolean isIdentityTrusted()
    {
        return identityTrusted;
    }

    public boolean isAddressValidated()
    {
        return addressValidated;
    }

    public boolean isIdentityValidated()
    {
        return identityValidated;
    }
    
    public synchronized PCSCEidController setLoadedFromFile(boolean loadedFromFile)
    {       
        this.loadedFromFile=loadedFromFile;
        return this;
    }
    
    public synchronized PCSCEidController setAddress(Address address)
    {
        this.address = address;
        return this;
    }

    public synchronized PCSCEidController setAuthCertChain(X509CertificateChainAndTrust authCertChain)
    {
        this.authCertChain = authCertChain;
        if (trustServiceController != null && autoValidatingTrust)
            trustServiceController.validateLater(authCertChain);
        return this;
    }

    public synchronized PCSCEidController setSignCertChain(X509CertificateChainAndTrust signCertChain)
    {
        this.signCertChain = signCertChain;
        if (trustServiceController != null && autoValidatingTrust)
            trustServiceController.validateLater(signCertChain);
        return this;
    }

    public synchronized PCSCEidController setRRNCertChain(X509CertificateChainAndTrust rrnCertChain)
    {
        this.rrnCertChain = rrnCertChain;
        if (trustServiceController != null && autoValidatingTrust)
            trustServiceController.validateLater(rrnCertChain);
        return this;
    }

    public synchronized PCSCEidController setIdentity(Identity identity)
    {
        this.identity = identity;
        return this;
    }

    public synchronized PCSCEidController setPhoto(byte[] photo)
    {
        this.photo = photo;
        return this;
    }

    public void closeFile()
    {
        if(isLoadedFromFile())
        {
            setLoadedFromFile(false);
            clear();
            setState(STATE.IDLE);
        }
    }
    
    private void beginExclusive() throws Exception
    {
    	if(!hasExclusive)
    	{
    		logger.fine("attempting to grab exclusive access");
    		while(!hasExclusive)
    		{
	    		try
	    		{
	    			eid.beginExclusive();
	    			hasExclusive=true;
	    			setYielding(false);
	    			logger.fine("exclusive access obtained");
	    		}
			catch(Exception cax)
	    		{
	    			setYielding(true);
	    			logger.fine("exclusive access deferred");
	    			
	    			try 
	    			{
						Thread.sleep(1000);
					}
	    			catch (InterruptedException ex)
	    			{
	    				logger.fine("interrupted while waiting for exclusive access");
					}
	    		}
    		}
    	}
    }
    
    private void endExclusive()
    {
    	if(hasExclusive)
    	{
    		logger.fine("attempting to release exclusive access");
    	
    		try
    		{
    			eid.endExclusive();
    			hasExclusive=false;
    			logger.fine("exclusive access released");
    		}
    		catch(Exception cax)
    		{
    			logger.fine("failed to release exclusive access");
    		}
    	}
    }
    

    @Override
    public X509Certificate getAuthCert()
    {
            try
            {
                    return eid.getAuthCert();
            }
            catch (Exception e)
            {
                    return null;
            }
    }

    @Override
    public X509Certificate getSignCert()
    {
            try
            {
                    return eid.getSignCert();
            }
            catch (Exception e)
            {
                    return null;
            }
    }

    @Override
    public X509Certificate getRRNCert()
    {
            try
            {
                    return eid.getRRNCert();
            }
            catch (Exception e)
            {
                    return null;
            }
    }

    @Override
    public X509Certificate getCACert()
    {
            try
            {
                    return eid.getCitizenCACert();
            }
            catch (Exception e)
            {
                    return null;
            }
    }

    @Override
    public X509Certificate getRootCert()
    {
            try
            {
                    return eid.getRootCACert();
            }
            catch (Exception e)
            {
                    return null;
            }
    }

  
	
}
