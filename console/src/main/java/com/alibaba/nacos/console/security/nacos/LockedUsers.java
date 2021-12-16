package com.alibaba.nacos.console.security.nacos;

import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.StorageFactory;
import com.alibaba.nacos.core.storage.kv.KvStorage;


import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;



public final class LockedUsers {
	
	private static final String STORAGEPATH = "nacos_spam_login_prevention";
	
	private static final String SNAPSHOTFILENAME = "snapshot";
	
	private static final int TIMESTAMPINDEX = 0;
	
	private static final int USERNAMEINDEX = 1;
	
	private static final String SPLITREGEX = ",";
	
	private static final long CONVERSIONCONST = 1000;
	
	private static final String FORMATSTRING = "%l%s%s";
	
	
	private String dir;
	
    private static  int permittedtries = 3; //Number of allowed consecutive login tries
    
    private static int cooldowntime = 30;	//Time it takes (in seconds) to cooldown after consecutive failed login attempts
    
    private static Calendar cooldowncalendar = Calendar.getInstance();
    
    private Date currenttime;
    
    private long currentmillis;
	
    
    
	private static LockedUsers instance;	//A variable guaranteeing singleton status to this class
    
	private KvStorage kvStorage;			//The kvStorage instance used to maintain continuity in the registry of locked out users
	
	private Map<byte[],byte[]> storagemap;

    public LockedUsers() {
    	
    	try {
    		
            final String baseDir = System.getProperty("user.home");
            dir = baseDir + File.separator + STORAGEPATH;						//Setting the dir for future operations
           
            this.kvStorage = StorageFactory.createKvStorage(KvStorage.KvType.Memory, "", dir); //Switch to KvType.RocksDB once it's implementation gets fixed
            
            updateClean();
            
    	} catch (Exception e) {
    		
            e.printStackTrace();
        }
    	
    }

    
    
    public static LockedUsers getInstance() {
    	
        if (instance == null) {
        	
            instance = new LockedUsers();
            
        }
        return instance;
    }
    
    
    
    private void clearOld() throws KvStorageException {
    	
    	final Iterator<Map.Entry<byte[], byte[]>> tniterator = storagemap.entrySet().iterator();		// Creating an iterator from the storage that has been mapped
    	
    	while(tniterator.hasNext()) {												// Looping over the created iterator
    		
    		final Map.Entry<byte[], byte[]> entry = tniterator.next();
    		final byte[] rawvalue = entry.getValue();
    		final String strvalue = rawvalue.toString();								//Converting to string so data can be more logically accessible
    		
    		final String strtime = strvalue.split(SPLITREGEX)[TIMESTAMPINDEX];
    		
    		updateCurrentTime();
    		
    		long lockouttime;
    		
    		try{
    			
                lockouttime = (long) Integer.parseInt(strtime);					//Convert value to a long
                
            }
            catch (NumberFormatException ex){
            	
                ex.printStackTrace();
                lockouttime = currentmillis;
                
            }
    		
    		
    		if ( (currentmillis - lockouttime) > (((long) cooldowntime)* CONVERSIONCONST) ) {
    			
    			this.kvStorage.delete(entry.getKey());
    		}
    		
    	}	
    	
    	this.kvStorage.doSnapshot(this.dir+ File.separator+SNAPSHOTFILENAME);
    	
    }
    
    
    
    private void updateFromSnapshot() throws KvStorageException {
    	
    	this.kvStorage.snapshotLoad(this.dir+ File.separator+SNAPSHOTFILENAME);	// Load From Snapshot
    	
        final List<byte[]> keys = this.kvStorage.allKeys();								// Retrieve all keys from snapshot 
        
        storagemap = this.kvStorage.batchGet(keys);				// Retrieve values from snapshot
        								
    }
    
    
    
    private void updateClean() throws KvStorageException {	/* An encapsulating method to guarantee correct order of execution for updateFromSnapshot
    														and clearOld */
    	updateFromSnapshot();
    	
        clearOld();
        
    }
    
    private void updateCurrentTime() {						// Utilty function  to update both currenttime and currentmillis
    	
    	currenttime = cooldowncalendar.getTime();
    	
    	currentmillis = currenttime.getTime();
    	
    }
    
    
    public void registerAttempt(final String user) throws KvStorageException {					//Adds user to attempt list with current millis
    															//THIS METHOD NEEDS TO BE UPDATED USING NACOS PROTOCOL HASHING FOR EACH OF THE ENTRIES
    	
    	final Random random = new Random();
    	
    	final byte[] rawkey = null;
    	
    	random.nextBytes(rawkey);								// Gets random bytes for key
    	
    	updateCurrentTime();
    	
    	final String formattempt = String.format(FORMATSTRING,this.currentmillis,SPLITREGEX,user);	// Formats the attempt into the correct pattern
    	
    	final byte[] rawdata = formattempt.getBytes();
    	
    	this.kvStorage.put(rawkey,rawdata);
    	
    	this.kvStorage.doSnapshot(this.dir+ File.separator+SNAPSHOTFILENAME);
    	
    }
    
    public boolean verifyAllowed(final String user) throws KvStorageException {
    	
    	updateClean();
    	
    	int attemptcount = 0;
    	
    	final Iterator<Map.Entry<byte[], byte[]>> tniterator = storagemap.entrySet().iterator();		// Creating an iterator from the storage that has been mapped
    	
    	
    	while(tniterator.hasNext()) {												// Looping over the created iterator
    		
    		Map.Entry<byte[], byte[]> entry = tniterator.next();
    		byte[] rawvalue = entry.getValue();
    		String strvalue = rawvalue.toString();								//Converting to string so data can be more logically accessible
    		
    		String username = strvalue.split(SPLITREGEX)[USERNAMEINDEX];
    	
    		if (username.equals(user)) attemptcount +=1;
    	}
    	
    	return attemptcount<permittedtries;
    	
    }
    
}
