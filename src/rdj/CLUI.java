/*
 * Copyright © 2017 Ron de Jong (ronuitzaandam@gmail.com).
 *
 * This is free software; you can redistribute it 
 * under the terms of the Creative Commons License
 * Creative Commons License: (CC BY-NC-ND 4.0) as published by
 * https://creativecommons.org/licenses/by-nc-nd/4.0/ ; either
 * version 4.0 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * Creative Commons Attribution-NonCommercial-NoDerivatives 4.0
 * International Public License for more details.
 *
 * You should have received a copy of the Creative Commons 
 * Public License License along with this software;
 */

package rdj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static rdj.GUIFX.getHexString;

/* commandline test routine

clear; echo -n -e \\x05 > 1; echo -n -e \\x03 > 2; java -jar FinalCrypt.jar
clear; echo -n -e \\x05 > 1; echo -n -e \\x03 > 2; java -cp FinalCrypt.jar rdj/CLUI --encrypt --print -k 2 -t 1
clear; echo -n ZYXVWUTSRQPONMLKJIHGFEDCBA098765 > a; echo -n abcdefghijklstuvwxyz > b; java -cp FinalCrypt.jar rdj/CLUI --print -k b -t a

*/

public class CLUI implements UI
{
    private FinalCrypt finalCrypt;
    private Version version;
    private UI ui;
    private final Configuration configuration;
    private boolean symlink = false;
    private boolean verbose = false;
    
    private boolean encrypt = false;
    private boolean decrypt = false;
    private boolean createkeydev = false;
    private boolean createkeyfile = false;
    private boolean clonekeydev = false;
    private boolean key_checksum = false;
    private boolean printgpt = false;
    private boolean deletegpt = false;
    
    private FCPathList encryptableList;
    private FCPathList decryptableList;
    private FCPathList createKeyList;
    private FCPathList cloneKeyList;
    
    private boolean encryptablesFound = false;
    private boolean decryptablesFound = false;
    private boolean createKeyDeviceFound = false;
    private boolean cloneKeyDeviceFound = false;
    private FCPathList printGPTTargetList;
    private boolean printGPTDeviceFound;
    private boolean deleteGPTDeviceFound;
    private FCPathList deleteGPTTargetList;
    private  FCPathList targetFCPathList;
    private boolean keySourceChecksumReadEnded = false;
    private int bufferSize;
    private Long totalTranfered;
    private Long filesizeInBytes = 100L * (1024L * 1024L);  // Create OTP Key File Size
    private Path keyPath;
//    private boolean disabledMAC = false;
    private boolean encryptModeNeeded;
    private TimeoutThread timeoutThread;
    private ReaderThread readerThread;
    
    private String pwd =		"";
    private boolean pwdPromptNeeded =	false;
    private boolean pwdIsSet =		false;
    
    public CLUI(String[] args)
    {	
        this.ui = this;
        boolean tfset = false;
	boolean tfsetneeded = false;
	boolean kfset = false;
	boolean kfsetneeded = true;
        boolean validInvocation = true;
        boolean negatePattern = false;

        ArrayList<Path> targetPathList = new ArrayList<>();
        ArrayList<Path> extendedTargetPathList = new ArrayList<>();
        Path batchFilePath = null;
//        Path targetFilePath = null;
	
//        Path keyFilePath = null;
        FCPath keyFCPath = null;
	
        Path outputFilePath = null;
        configuration = new Configuration(this);
        version = new Version(this);
        version.checkCurrentlyInstalledVersion(this);

        String pattern = "glob:*";
        
        // Load the FinalCrypt Objext
        finalCrypt = new FinalCrypt(this);
        finalCrypt.start();
        finalCrypt.setBufferSize(finalCrypt.getBufferSizeDefault());
        
////      SwingWorker version of FinalCrype
//        finalCrypt.execute();

        // Validate Parameters
	
	log(getRuntimeEnvironment(), false, false, true, false ,false);
	
	if (args.length == 0 ) { log("\r\nWarning: No parameters entered!\r\n", false, true, true, false, false); usagePrompt(true); } 
	
        for (int paramCnt=0; paramCnt < args.length; paramCnt++)
        {
//          Options
            if      (( args[paramCnt].equals("-h")) || ( args[paramCnt].equals("--help") ))                         { usage(false); }
	    else if (  args[paramCnt].equals("--examples"))							    { examples(); }
            else if (  args[paramCnt].equals("--disable-MAC"))							    { finalCrypt.disabledMAC = true; FCPath.KEY_SIZE_MIN = 1; encryptModeNeeded = true; }
            else if (  args[paramCnt].equals("--encrypt"))							    { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { encrypt = true; kfsetneeded = true; tfsetneeded = true; } }
            else if (  args[paramCnt].equals("--decrypt"))							    { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { decrypt = true; kfsetneeded = true; tfsetneeded = true; } }
            else if (( args[paramCnt].equals("-p")) || ( args[paramCnt].equals("--password") ))                     { pwd = args[paramCnt+1]; pwdIsSet = true; paramCnt++; }
            else if (( args[paramCnt].equals("-pp")) || ( args[paramCnt].equals("--password-prompt") ))             { pwdPromptNeeded = true; }

	    else if (  args[paramCnt].equals("--create-keydev"))						    { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { createkeydev = true; kfsetneeded = true; tfsetneeded = true; } }
            else if (  args[paramCnt].equals("--create-keyfile"))						    { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { createkeyfile = true; kfsetneeded = false; tfsetneeded = false; } }
            else if (  args[paramCnt].equals("--clone-keydev"))							    { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { clonekeydev = true; kfsetneeded = true; tfsetneeded = true; } }
            else if (( args[paramCnt].equals("--key-chksum") ))							    { key_checksum = true; kfsetneeded = true; }
            else if (( args[paramCnt].equals("--no-key-size") ))						    { FCPath.KEY_SIZE_MIN = 1; }
            else if (  args[paramCnt].equals("--print-gpt"))                                                        { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { printgpt = true; kfsetneeded = false; tfsetneeded = true; } }
            else if (  args[paramCnt].equals("--delete-gpt"))                                                       { if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)) { deletegpt = true; kfsetneeded = false; tfsetneeded = true; } }
            else if (( args[paramCnt].equals("--print") ))							    { finalCrypt.setPrint(true); }
            else if (( args[paramCnt].equals("-v")) || ( args[paramCnt].equals("--verbose") ))                      { finalCrypt.setVerbose(true); verbose = true; }
            else if (( args[paramCnt].equals("-l")) || ( args[paramCnt].equals("--symlink") ))			    { finalCrypt.setSymlink(true); symlink = true; }
//            else if (  args[paramCnt].equals("--txt"))                                                              { finalCrypt.setTXT(true); }
//            else if (  args[paramCnt].equals("--bin"))                                                              { finalCrypt.setBin(true); }
//            else if (  args[paramCnt].equals("--dec"))                                                              { finalCrypt.setDec(true); }
//            else if (  args[paramCnt].equals("--hex"))                                                              { finalCrypt.setHex(true); }
//            else if (  args[paramCnt].equals("--chr"))                                                              { finalCrypt.setChr(true); }
            else if (  args[paramCnt].equals("--version"))                                                          { log(version.getProductName() + " " + version.getCurrentlyInstalledOverallVersionString() + "\r\n", false, true, true, false, false); System.exit(0); }
            else if (  args[paramCnt].equals("--license"))                                                          { log(version.getProductName() + " " + Version.getLicense() + "\r\n", false, true, true, false, false); System.exit(0); }
            else if (  args[paramCnt].equals("--check-update"))                                                           { version.checkLatestOnlineVersion(this); 	    String[] lines = version.getUpdateStatus().split("\r\n"); for (String line: lines) { log(line + "\r\n", false, true, true, false, false); } System.exit(0); }
            else if (( args[paramCnt].equals("-s")) && (!args[paramCnt+1].isEmpty()) )				    { if ( validateIntegerString(args[paramCnt + 1]) ) { finalCrypt.setBufferSize(Integer.valueOf( args[paramCnt + 1] ) * 1024 ); paramCnt++; } else { log("\r\nWarning: Invalid Option Value [-b size]" + "\r\n", false, true, true, false, false); usagePrompt(true); }}
            else if (( args[paramCnt].equals("-S")) && (!args[paramCnt+1].isEmpty()) )				    { if ( validateIntegerString(args[paramCnt + 1]) ) { filesizeInBytes = Long.valueOf( args[paramCnt + 1] ); paramCnt++; } else { log("\r\nWarning: Invalid Option Value [-S size]" + "\r\n", false, true, true, false, false); usagePrompt(true); }}

//	    Mode parameters
	    else if (
			    (!encrypt)
			&&  (!decrypt)
			&&  (!createkeydev)
			&&  (!clonekeydev)
			&&  (!printgpt)
			&&  (!deletegpt)
			&&  (!createkeyfile)
		    )												    { log("\r\nWarning: No <--Mode> parameter specified" + "\r\n",			    false, true, true, false, false); usagePrompt(true); }

//          Filtering Options
            else if ( args[paramCnt].equals("--dry"))                                                               { finalCrypt.setDry(true); }
            else if ( ( args[paramCnt].equals("-w")) && (!args[paramCnt+1].isEmpty()) )				    { negatePattern = false; pattern = "glob:" + args[paramCnt+1]; paramCnt++; }
            else if ( ( args[paramCnt].equals("-W")) && (!args[paramCnt+1].isEmpty()) )				    { negatePattern = true; pattern = "glob:" + args[paramCnt+1]; paramCnt++; }
            else if ( ( args[paramCnt].equals("-r")) && (!args[paramCnt+1].isEmpty()) )				    { pattern = "regex:" + args[paramCnt+1]; paramCnt++; }

//          File Parameters
            else if ( ( args[paramCnt].equals("-k")) )								    { if (paramCnt+1 < args.length) { keyFCPath = Validate.getFCPath(ui, "", Paths.get(args[paramCnt+1]), true, Paths.get(args[paramCnt+1]), finalCrypt.disabledMAC, true); kfset = true; paramCnt++; } else { log("\r\nWarning: Missing key parameter <-k \"keyfile\">" + "\r\n", false, true, true, false, false); usagePrompt(true); } }
            else if ( ( args[paramCnt].equals("-K")) && (!args[paramCnt+1].isEmpty()) )				    { keyPath = Paths.get(args[paramCnt+1]); paramCnt++; } // Create OTP Key File
            else if ( ( args[paramCnt].equals("-t")) )								    { if (paramCnt+1 < args.length) { targetPathList.add(Paths.get(args[paramCnt+1])); tfset = true; paramCnt++; } else { log("\r\nWarning: Missing target parameter <[-t \"file/dir\"]>" + "\r\n", false, true, true, false, false); usagePrompt(true); } }
            else if ( ( args[paramCnt].equals("-b")) && (!args[paramCnt+1].isEmpty()) )				    { tfset = addBatchTargetFiles(args[paramCnt+1], targetPathList); paramCnt++; }
            else { log("\r\nWarning: Invalid Parameter: " + args[paramCnt] + "\r\n", false, true, true, true, false); usagePrompt(true); }
        }

        if (( encryptModeNeeded )   && ( decrypt ))								    { log("\r\nWarning: MAC Mode Disabled! Use --encrypt if you know what you are doing!!!\r\n",  false, true, true, false, false); usagePrompt(true); }
        if (( encryptModeNeeded )   && ( ! encrypt ))								    { log("\r\nWarning: Missing valid parameter <--encrypt>" + "\r\n",			    false, true, true, false, false); usagePrompt(true); }
        if (( kfsetneeded )	    && ( ! kfset ))								    { log("\r\nWarning: Missing valid parameter <-k \"keyfile\">" + "\r\n",			    false, true, true, false, false); usagePrompt(true); }
        if (( tfsetneeded )	    && ( ! tfset ))								    { log("\r\nWarning: Missing valid parameter <-t \"file/dir\"> or <-b \"batchfile\">" + "\r\n",false, true, true, false, false); usagePrompt(true); }
//	if ((!encrypt)&&(!decrypt)&&(!createkeydev)&&(!clonekeydev)&&(!printgpt)&&(!deletegpt)&&(!createkeydev))    { log("\r\nWarning: No <--Mode> parameter specified" + "\r\n",			    false, true, true, false, false); usagePrompt(true); }
                
//////////////////////////////////////////////////// VALIDATE SELECTION /////////////////////////////////////////////////

	// Key Validation
	if ( (kfsetneeded) )
	{
	    if ( ! keyFCPath.isValidKey)
	    {
		String exist ="";
		String size ="";
		String dir ="";
		String sym ="";
		String all = "";

		if (keyFCPath.exist == false)
		{
		    exist += " [key does not exist] "; 		
		}
		else
		{
		    if (keyFCPath.type == FCPath.DIRECTORY) { dir += " [is dir] "; } 
		    if (keyFCPath.type == FCPath.SYMLINK) { sym += " [is symlink] "; } // finalCrypt.disabledMAC = true

		    if (! finalCrypt.disabledMAC)
		    {
			if (( keyFCPath.size < FCPath.KEY_SIZE_MIN )) { size += " [size < " + FCPath.KEY_SIZE_MIN + "] try: \"--no-key-size\" option "; }
			if (( keyFCPath.size < FCPath.MAC_SIZE ) ) { size += " [size < " + FCPath.MAC_SIZE + "] try: \"--disable-MAC\" option if you know what you are doing !!! "; }
		    }
		    else { if (( keyFCPath.size < FCPath.KEY_SIZE_MIN )) { size += " [size < " + FCPath.KEY_SIZE_MIN + "] try: \"--no-key-size\" option "; } }
		}

		all = exist + dir + sym + size ;

		log("\r\nWarning: Key parameter: -k \"" + keyFCPath.path + "\" Invalid:" + all + "\r\n\r\n", false, true, true, false, false);
		log(Validate.getFCPathStatus(keyFCPath), false, true, false, false, false); usagePrompt(true);
	    }
	}
	else
	{

	}
			
	// Target Validation
	
	if (tfsetneeded)
	{
	    for(Path targetPath : targetPathList)
	    {		
		if (Files.exists(targetPath))
		{
//    				  isValidDir(UI ui, Path targetDirPath, boolean symlink, boolean report)
		    if ( Validate.isValidDir( this,         targetPath,         symlink,        verbose))
		    {
			if (verbose) { log("Info: Target parameter: " + targetPath + " is a valid dir\r\n", false, true, true, false, false); }
		    }
//				       isValidFile(UI ui, String caller, Path targetSourcePath,  isKey, boolean device, long minSize, boolean symlink, boolean writable, boolean report)
		    else if ( Validate.isValidFile(this, "CLUI.CLUI() ",            targetPath,	false,          false,	         1L,         symlink,             true,        verbose))
		    {
			if (verbose) { log("Info: Target parameter: " + targetPath + " is a valid file\r\n", false, true, true, false, false); }
		    }
		}
		else
		{ 
			log("Warning: Target parameter: -t \"" + targetPath + "\" does not exists\r\n", false, true, true, false, false); usagePrompt(true);
		}            
	    }
	}

//	Command line input for an optional Password

	if ( pwdPromptNeeded )
	{
	    ConsoleEraser consoleEraser = new ConsoleEraser();
	    System.out.print("Password: ");
	    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	    consoleEraser.start();
	    try { pwd = in.readLine(); pwdIsSet = true; }
	    catch (IOException err) { log("Error: Can't read password! " + err.getMessage() + "\r\n", false, true, true, true, false); usagePrompt(true); System.exit(1); }
 
	    consoleEraser.halt();
	}

	if ( pwdIsSet ) { FinalCrypt.setPwd(pwd); }

//	====================================================================================================================
//	 Start writing OTP key file
//	====================================================================================================================



	if (createkeyfile)
	{
	    Long factor = 0L;
	    bufferSize = 1048576;
	    totalTranfered = 0L;

	    
	    if ( Files.exists(keyPath, LinkOption.NOFOLLOW_LINKS) ) { log("Warning: file: \"" + keyPath.toAbsolutePath().toString() + "\" exists! Aborted!\r\n\r\n", false, true, false, false, false); System.exit(1); }
	    else						    { log("Creating OTP Key File" + " (" + Validate.getHumanSize(filesizeInBytes, 1) + ")...", false, true, false, false, false); }

	    if ( filesizeInBytes < bufferSize) { bufferSize = filesizeInBytes.intValue(); }

	    boolean inputEnded = false;
	    long writeKeyFileChannelPosition = 0L;
	    long writeKeyFileChannelTransfered = 0L;
	    totalTranfered = 0L;
	    Long remainder = 0L;

//	    Write the keyfile to 1st partition

	    byte[]      randomBytes1 =	    new byte[bufferSize];
	    byte[]      randomBytes2 =	    new byte[bufferSize];
	    byte[]      randomBytes3 =	    new byte[bufferSize];
	    ByteBuffer  randomBuffer1 =	    ByteBuffer.allocate(bufferSize); randomBuffer1.clear();
	    ByteBuffer  randomBuffer2 =	    ByteBuffer.allocate(bufferSize); randomBuffer2.clear();
	    ByteBuffer  randomBuffer3 =	    ByteBuffer.allocate(bufferSize); randomBuffer3.clear();


	    SecureRandom random = new SecureRandom();

	    write1loop: while ( (totalTranfered < filesizeInBytes) && (! inputEnded ))
	    {
		remainder = (filesizeInBytes - totalTranfered);

		if	    ( remainder >= bufferSize )				
		{
		    randomBytes1 =	    new byte[bufferSize];
		    randomBytes2 =	    new byte[bufferSize];
		    randomBytes3 =	    new byte[bufferSize];
		    randomBuffer1 =	    ByteBuffer.allocate(bufferSize); randomBuffer1.clear();
		    randomBuffer2 =	    ByteBuffer.allocate(bufferSize); randomBuffer2.clear();
		    randomBuffer3 =	    ByteBuffer.allocate(bufferSize); randomBuffer3.clear();
		}
		else if (( remainder > 0 ) && ( remainder < bufferSize ))
		{
		    randomBytes1 =	    new byte[remainder.intValue()];
		    randomBytes2 =	    new byte[remainder.intValue()];
		    randomBytes3 =	    new byte[remainder.intValue()];
		    randomBuffer1 =	    ByteBuffer.allocate(remainder.intValue()); randomBuffer1.clear();
		    randomBuffer2 =	    ByteBuffer.allocate(remainder.intValue()); randomBuffer2.clear();
		    randomBuffer3 =	    ByteBuffer.allocate(remainder.intValue()); randomBuffer3.clear();
		}
		else							{ inputEnded = true; }
//              Randomize raw key or write raw key straight to partition
		random.nextBytes(randomBytes1); randomBuffer1.put(randomBytes1); randomBuffer1.flip();
		random.nextBytes(randomBytes2); randomBuffer2.put(randomBytes2); randomBuffer2.flip();

		randomBuffer3 = FinalCrypt.encryptBuffer(randomBuffer1, randomBuffer2, false); // Encrypt

//              Write Device
		try (final SeekableByteChannel writeKeyFileChannel = Files.newByteChannel(keyPath, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.SYNC)))
		{
		    writeKeyFileChannel.position(writeKeyFileChannelPosition);
		    writeKeyFileChannelTransfered = writeKeyFileChannel.write(randomBuffer3); randomBuffer3.rewind();
		    totalTranfered += writeKeyFileChannelTransfered; 
//		    log("tot: " + filesizeInBytes + " trans: " + totalTranfered + " remain: " + remainder + " p: " + (double)totalTranfered / filesizeInBytes + "\r\n", false, true, false, false, false);
		    
		    writeKeyFileChannelPosition += writeKeyFileChannelTransfered;

		    writeKeyFileChannel.close();
		} catch (IOException ex) { log("\r\nError: " + ex.getMessage() + "\r\n", false, true, true, true, false); inputEnded = true; break; }
		randomBuffer1.clear(); randomBuffer2.clear(); randomBuffer3.clear();
	    }
	    writeKeyFileChannelPosition = 0;                
	    writeKeyFileChannelTransfered = 0;                
	    inputEnded = false;


	    log("finished\r\n", false, true, false, false, false);
	    System.exit(0);
	}

//	====================================================================================================================
//	Finieshed writing key file
//	====================================================================================================================
	




//////////////////////////////////////////////////// KEY CHECKSUM =====================================================

	if (key_checksum)
	{
	    log("\r\nKey CheckSum: (" + FinalCrypt.HASH_ALGORITHM_NAME + "): \"" + keyFCPath.path.toAbsolutePath().toString() + "\"...\r\n", false, true, false, false, false); 
	    long    readKeySourceChannelPosition =  0; 
	    long    readKeySourceChannelTransfered =  0; 
	    int readKeySourceBufferSize = (1 * 1024 * 1024);
	    ByteBuffer keySourceBuffer = ByteBuffer.allocate(readKeySourceBufferSize); keySourceBuffer.clear();
	    MessageDigest messageDigest = null; try { messageDigest = MessageDigest.getInstance(FinalCrypt.HASH_ALGORITHM_NAME); } catch (NoSuchAlgorithmException ex) { log("Error: NoSuchAlgorithmException: MessageDigest.getInstance(\"SHA-256\")\r\n", false, true, true, true, false);}
	    int x = 0;
	    while ( ! keySourceChecksumReadEnded )
	    {
		try (final SeekableByteChannel readKeySourceChannel = Files.newByteChannel(keyFCPath.path, EnumSet.of(StandardOpenOption.READ,StandardOpenOption.SYNC)))
		{
		    readKeySourceChannel.position(readKeySourceChannelPosition);
		    readKeySourceChannelTransfered = readKeySourceChannel.read(keySourceBuffer); keySourceBuffer.flip(); readKeySourceChannelPosition += readKeySourceChannelTransfered;
		    readKeySourceChannel.close();

		    messageDigest.update(keySourceBuffer);
		    if ( readKeySourceChannelTransfered < 0 ) { keySourceChecksumReadEnded = true; }
		} catch (IOException ex) { keySourceChecksumReadEnded = true; log("Error: readKeySourceChannel = Files.newByteChannel(..) " + ex.getMessage() + "\r\n", false, true, false, true, false); }
		x++;
		keySourceBuffer.clear();
	    }
	    byte[] hashBytes = messageDigest.digest();
	    String hashString = getHexString(hashBytes,2);
	    log("Message Digest:         " + hashString + "\r\n\r\n", false, true, false, false, false); 
	}
	
	
//////////////////////////////////////////////////// BUILD SELECTION /////////////////////////////////////////////////
        
	targetFCPathList = new FCPathList();
//	if (!cfsetneeded) { keyFCPath = (FCPath) targetPathList.get(0); }
	if (!kfsetneeded) 
	{
//    					  getFCPath(UI ui, String caller,	      Path path, boolean isKey,          Path keyPath, boolean disabledMAC,    boolean report)
		     keyFCPath = Validate.getFCPath(ui,            "", targetPathList.get(0),         false, targetPathList.get(0), finalCrypt.disabledMAC,          true);
	}
//	   buildTargetSelection(UI ui, ArrayList<Path> userSelectedItemsPathList, Path keyPath, ArrayList<FCPath> targetFCPathList, boolean symlink, String pattern, boolean negatePattern,    boolean disabledMAC, boolean status)
	Validate.buildSelection(this,			          targetPathList,    keyFCPath,		          targetFCPathList,	    symlink,	    pattern,	     negatePattern, finalCrypt.disabledMAC,         false);
	
/////////////////////////////////////////////// SET BUILD MODES ////////////////////////////////////////////////////

	if ((keyFCPath != null) && (keyFCPath.isValidKey))
	{
//	    log(targetFCPathList.getStats());
	    // Encryptables
	    if (targetFCPathList.encryptableFiles > 0)
	    {
		encryptableList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.isEncryptable); // log("Encryptable List:\r\n" + encryptableList.getStats());
		encryptablesFound = true;
	    }

	    // Encryptables
	    if (targetFCPathList.decryptableFiles > 0)
	    {
		decryptableList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.isDecryptable); // log("Decryptable List:\r\n" + decryptableList.getStats());
		decryptablesFound = true;
	    }

	    // Create Key Device
	    if (keyFCPath.type == FCPath.FILE)
	    {
		if (targetFCPathList.validDevices > 0)
		{
		    createKeyList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.type == FCPath.DEVICE); // log("Create Key List:\r\n" + createKeyList.getStats());
		    createKeyDeviceFound = true;
		} else { createKeyDeviceFound = false; }
	    }		
	    else if (keyFCPath.type == FCPath.DEVICE)
	    {
		// Clone Key Device
		if ((targetFCPathList.validDevices > 0) && (targetFCPathList.matchingKey == 0))
		{
		    final FCPath keyFCPath2 = keyFCPath; // for Lambda expression
		    cloneKeyList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.type == FCPath.DEVICE && fcPath.path.compareTo(keyFCPath2.path) != 0); // log("Clone Key List:\r\n" + cloneKeyList.getStats());
		    cloneKeyDeviceFound = true;
		} else { cloneKeyDeviceFound = false; }
	    } else { cloneKeyDeviceFound = false; }
	} else { createKeyDeviceFound = false; }

	if ((printgpt) && ((targetFCPathList.validDevices > 0) || (targetFCPathList.validDevicesProtected > 0)))
	{
	    printGPTTargetList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.type == FCPath.DEVICE || fcPath.type == FCPath.DEVICE_PROTECTED); // log("Create Key List:\r\n" + createKeyList.getStats());
	    printGPTDeviceFound = true;
	} else { printGPTDeviceFound = false; }
	
	if ((deletegpt) && (targetFCPathList.validDevices > 0))
	{
	    deleteGPTTargetList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.type == FCPath.DEVICE); // log("Create Key List:\r\n" + createKeyList.getStats());
	    if ( deleteGPTTargetList.size() > 0 ) { deleteGPTDeviceFound = true; }
	    else { deleteGPTDeviceFound = false; }
	}
	else if ((deletegpt) && (targetFCPathList.validDevicesProtected > 0))
	{
	    deleteGPTTargetList = filter(targetFCPathList,(FCPath fcPath) -> fcPath.type == FCPath.DEVICE_PROTECTED); // log("Create Key List:\r\n" + createKeyList.getStats());
	    FCPath fcPath = (FCPath) deleteGPTTargetList.get(0); log("WARNING: Device: " + fcPath.path + " is protected!!!\r\n", false, true, true, false, false); deleteGPTDeviceFound = false; 
	}
	else { deleteGPTDeviceFound = false; }

	
/////////////////////////////////////////////// FINAL VALIDATION & EXECUTE MODES ////////////////////////////////////////////////////

//	log("Warning: Default Message Authentication Code Mode Disabled! NOT compattible to MAC Mode Encrypted files!!!\r\n", true, true, true, false, false);
//	log("Info:    Default Message Authentication Code Mode Enabled\r\n", true, true, true, false, false);

	DeviceManager deviceManager;
	if ((encrypt))
	{
	    if (finalCrypt.disabledMAC)	{ log("\"Warning: MAC Mode Disabled! (files will be encrypted without Message Authentication Code Header)\r\n", true, true, true, false, false); }
	    
	    if ((encryptablesFound))
	    {
		Runtime.getRuntime().addShutdownHook(new Thread()
		{
		    @Override public void run()
		    {
			
			if (finalCrypt.processRunning)
			{
			    finalCrypt.setStopPending(true);
			    try{ Thread.sleep(2000); } catch (InterruptedException ex) {}
			    log("\r\nEncryption User Interrupted...\r\n", false, true, true, false, false);
			}
		    }
		});
		processStarted(); 
		finalCrypt.encryptSelection(targetFCPathList, encryptableList, keyFCPath, true, pwd, false);
//		catch (InterruptedException ex){ log("Encryption Interrupted (CLUI): " + ex.getMessage() +" \r\n", false, true, true, false, false); }
	    }
	    else			{ log("No encryptable targets found:\r\n", false, true, true, false, false); log(targetFCPathList.getStats(), false, true, false, false, false); }
	}
	else if ((decrypt))
	{
	    if (finalCrypt.disabledMAC)
	    {
		log("Warning: MAC Mode Disabled! Use --encrypt if you know what you are doing!!!\r\n", true, true, true, false, false);
	    }
	    else
	    {
		if (decryptablesFound)
		{
		    Runtime.getRuntime().addShutdownHook(new Thread()
		    {
			@Override public void run()
			{
			    if (finalCrypt.processRunning)
			    {
				finalCrypt.setStopPending(true);
				try{ Thread.sleep(2000); } catch (InterruptedException ex) {}
				log("\r\nDecryption User Interrupted...\r\n", false, true, true, false, false);
			    }
			}
		    });
		    processStarted();
		    finalCrypt.encryptSelection(targetFCPathList, decryptableList, keyFCPath, false, pwd, false);
//		    catch (InterruptedException ex) { log("Decryption Interrupted (CLUI): " + ex.getMessage() +" \r\n", false, true, true, false, false); }
		}
		else			
		{
		    log("No decryptable targets found\r\n\r\n", false, true, true, false, false);
		    if ( targetFCPathList.encryptedFiles > 0 ) { log("Wrong key / password?\r\n\r\n", false, true, false, false, false); }
		    log(targetFCPathList.getStats(), false, true, true, false, false);
		}
	    }
	}
	else if (createkeydev)
	{
	    if (createKeyDeviceFound)	{ processStarted(); deviceManager = new DeviceManager(ui); deviceManager.start(); deviceManager.createKeyDevice(keyFCPath, (FCPath) createKeyList.get(0)); processFinished(new FCPathList(), false); }
	    else			{ log("No valid target device found:\r\n", false, true, true, false, false); log(targetFCPathList.getStats(), false, true, false, false, false); }
	}
	else if ((clonekeydev) && (cloneKeyDeviceFound))
	{
	    if (cloneKeyDeviceFound)	{ processStarted(); deviceManager = new DeviceManager(ui); deviceManager.start(); deviceManager.cloneKeyDevice(keyFCPath, (FCPath) cloneKeyList.get(0));  processFinished(new FCPathList(), false); }
	    else			{ log("No valid target device found:\r\n", false, true, true, false, false); log(targetFCPathList.getStats(), false, true, false, false, false); }
	}
	else if ((printgpt) && (printGPTDeviceFound))
	{
	    if (printGPTDeviceFound)	{ deviceManager = new DeviceManager(ui); deviceManager.start(); deviceManager.printGPT( (FCPath) printGPTTargetList.get(0)); }
	    else			{ log("No valid target device found:\r\n", false, true, true, false, false); log(targetFCPathList.getStats(), false, true, false, false, false); }
	}
	else if ((deletegpt) && (deleteGPTDeviceFound))
	{
	    if (deleteGPTDeviceFound)	{ deviceManager = new DeviceManager(ui); deviceManager.start(); deviceManager.deleteGPT( (FCPath) deleteGPTTargetList.get(0)); }
	    else			{ log("No valid target device found:\r\n", false, true, true, false, false); log(targetFCPathList.getStats(), false, true, false, false, false); }
	}
    } // End of default constructor
    
    
    
//  =======================================================================================================================================================================


    private boolean addBatchTargetFiles(String batchFilePathString, ArrayList<Path> targetFilesPathList)
    {
        boolean ifset = false;
        Path batchFilePath;
        Path targetFilePath;
//		      isValidFile(UI ui, String caller,                       Path targetSourcePath, isKey	boolean device, long minSize, boolean symlink, boolean writable, boolean report)
        if ( Validate.isValidFile(this,  "CLUI.addBatchTargetFiles", Paths.get(batchFilePathString), false,              false,	          1L,         symlink,             true,           true) )
        {
            log("Adding items from batchfile: " + batchFilePathString + "\r\n", false, true, true, false, false);
            batchFilePath = Paths.get(batchFilePathString);
            try
            {
                for (String targetFilePathString:Files.readAllLines(batchFilePath))
                {
//                  Entry may not be a directory (gets filtered and must be a valid file)
//				  isValidFile(UI ui, String caller,                        Path targetSourcePath, boolean isKey, boolean device, long minSize, boolean symlink, boolean writable, boolean report)
                    if ( Validate.isValidFile(   ui, "CLUI.addBatchTargetFiles", Paths.get(targetFilePathString),          false,	     false,	      0L,         symlink,             true,           true) )
                    {
                        targetFilePath = Paths.get(targetFilePathString); targetFilesPathList.add(targetFilePath); ifset = true;
//                        println("Adding: " + targetFilePathString);
                    }
                    else { /* println("Invalid file: " + targetFilePathString);*/ } // Reporting in isValidFile is already set to true, so if invalid then user is informed
                }
            }
            catch (IOException ex) { log("Files.readAllLines(" + batchFilePath + ");" + ex.getMessage(), false, true, true, true, false); }
            if ( ! ifset ) { log("Warning: batchfile: " + batchFilePathString + " doesn't contain any valid items!\r\n", false, true, true, false, false); }
        }
        else
        {
            log("Warning: batchfile: " + batchFilePathString + " is not a valid file!\r\n", false, true, true, false, false);
        }
        return ifset;
    }

    public static FCPathList filter(ArrayList<FCPath> fcPathList, Predicate<FCPath> fcPath)
    {
	FCPathList result = new FCPathList();
	for (FCPath fcPathItem : fcPathList) { if (fcPath.test(fcPathItem)) { result.add(fcPathItem); } }
	return result;
    }
    
    public static Predicate<FCPath> isHidden() { return (FCPath fcPath) -> fcPath.isHidden; }
    
    public List<FCPath> filter(Predicate<FCPath> criteria, ArrayList<FCPath> list)
    {
	return list.stream().filter(criteria).collect(Collectors.<FCPath>toList());
    }
    
    private boolean validateIntegerString(String text) { try { Integer.parseInt(text); return true;} catch (NumberFormatException e) { return false; } }

    private void usagePrompt(boolean error)
    {
        timeoutThread = new TimeoutThread(this); timeoutThread.start();
        readerThread = new ReaderThread(this); readerThread.start();
	while (timeoutThread.isAlive()) { try { Thread.sleep(100); } catch (InterruptedException ex) { } }
	log("\r\n\r\n", false, true, false, false, false);
	System.exit(1);
    }
    
    protected void usage(boolean error)
    {
//	if ( autoExitTaskTimer != null ) { autoExitTaskTimer.cancel(); autoExitTaskTimer.purge(); }
	
        String fileSeparator = java.nio.file.FileSystems.getDefault().getSeparator();
        log("\r\n", false, true, false, false, false);
        log("Usage:	    java -cp FinalCrypt.jar rdj/CLUI   <Mode>  [options] <Parameters>\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("Examples:\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --examples   Print commandline examples.\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj.CLUI --create-keyfile -K mykeyfile -S 268435456 # (256 MiB) echo $((1024**2*256))\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k key_file -t target_file\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -k key_file -t target_file\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k key_file -t target_dir\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k key_file -t target_file -t target_dir\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("Mode:\r\n", false, true, false, false, false);
        log("            <--encrypt>           -k \"key_file\"   -t \"target\"	    Encrypt Targets.\r\n", false, true, false, false, false);
        log("            <--decrypt>           -k \"key_file\"   -t \"target\"	    Decrypt Targets.\r\n", false, true, false, false, false);
        log("            <--create-keydev>     -k \"key_file\"   -t \"target\"	    Create Key Device (only unix).\r\n", false, true, false, false, false);
        log("            <--create-keyfile>    -K \"key_file\"   -S \"Size (bytes)\"	    Create OTP Key File.\r\n", false, true, false, false, false);
        log("            <--clone-keydev>      -k \"source_device\" -t \"target_device\"     Clone Key Device (only unix).\r\n", false, true, false, false, false);
        log("            [--print-gpt]         -t \"target_device\"			    Print GUID Partition Table.\r\n", false, true, false, false, false);
        log("            [--delete-gpt]        -t \"target_device\"			    Delete GUID Partition Table (DATA LOSS!).\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
	log("Options:\r\n", false, true, false, false, false);
        log("            [-h] [--help]	  Shows this help page.\r\n", false, true, false, false, false);
        log("            [--password]          -p \'password\'			    Additional password (non-interactive).\r\n", false, true, false, false, false);
        log("            [--password-prompt]   -pp				    Additional password (interactive prompt).\r\n", false, true, false, false, false);
        log("            [--key-chksum]        -k \"key_file\"			    Calculate key checksum.\r\n", false, true, false, false, false);
        log("            [--no-key-size]       Allow key-size less than the default minimum of 1024 bytes.\r\n", false, true, false, false, false);
        log("            [-d] [--debug]        Enables debugging mode.\r\n", false, true, false, false, false);
        log("            [-v] [--verbose]      Enables verbose mode.\r\n", false, true, false, false, false);
        log("            [--print]		  Print all bytes binary, hexdec & char (slows encryption severely).\r\n", false, true, false, false, false);
        log("            [-l] [--symlink]      Include symlinks (can cause double encryption! Not recommended!).\r\n", false, true, false, false, false);
        log("            [--disable-MAC]       Disable Message Authentication Code - (files will be encrypted without Message Authentication Code header).\r\n", false, true, false, false, false);
        log("            [--version]           Print " + version.getProductName() + " version.\r\n", false, true, false, false, false);
        log("            [--license]           Print " + version.getProductName() + " license.\r\n", false, true, false, false, false);
        log("            [--check-update]      Check for online updates.\r\n", false, true, false, false, false);
//        log("            [--txt]               Print text calculations.\r\n", false, true, false, false, false);
//        log("            [--bin]               Print binary calculations.\r\n", false, true, false, false, false);
//        log("            [--dec]               Print decimal calculations.\r\n", false, true, false, false, false);
//        log("            [--hex]               Print hexadecimal calculations.\r\n", false, true, false, false, false);
//        log("            [--chr]               Print character calculations.\r\n", false, true, false, false, false);
//        log("                                  Warning: The above Print options slows encryption severely.\r\n", false, true, false, false, false);
        log("            [-s size]             Changes default I/O buffer size (size = KiB) (default 1024 KiB).\r\n", false, true, false, false, false);
        log("            [-S size]             OTP Key File Size (size = bytes). See --create-keyfile \r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("Filtering Options:\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            [--dry]               Dry run without encrypting files for safe testing purposes.\r\n", false, true, false, false, false);
        log("            [-w \'wildcard\']       File wildcard INCLUDE filter. Uses: \"Globbing Patterns Syntax\".\r\n", false, true, false, false, false);
        log("            [-W \'wildcard\']       File wildcard EXCLUDE filter. Uses: \"Globbing Patterns Syntax\".\r\n", false, true, false, false, false);
        log("            [-r \'regex\']          File regular expression filter. Advanced filename filter!\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("Parameters:\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            <-k \"keyfile\">        The file that encrypts your file(s). Keep keyfile SECRET!\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            <-t / -b>             The target items you want to encrypt. Individual (-t) or by batch (-b).\r\n", false, true, false, false, false);
        log("            <[-t \"file/dir\"]>     Target file or dir you want to encrypt (encrypts dirs recursively).\r\n", false, true, false, false, false);
        log("            <[-b \"batchfile\"]>    Batchfile with targetfiles you want to encrypt (only files).\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log(Version.getProductName() + " " + version.checkCurrentlyInstalledVersion(this) + " - Author: " + Version.getAuthor() + " - Copyright: " + Version.getCopyright() + "\r\n\r\n", false, true, false, false, false);
        System.exit(error ? 1 : 0);
    }

    private void examples()
    {
        log("\r\n", false, true, false, false, false);
        log("Examples:   java -cp FinalCrypt.jar rdj/CLUI <Mode> [options] <Parameters>\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt myfile with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k mykeyfile -t myfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -k mykeyfile -t myfile\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt myfile and all content in mydir with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k mykeyfile -t myfile -t mydir\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -k mykeyfile -t myfile -t mydir\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt files in batchfile with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k mykeyfile -b mybatchfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -k mykeyfile -b mybatchfile\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt all files with *.bit extension in mydir with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -w '*.bit'-k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -w '*.bit'-k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt all files without *.bit extension in mydir with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -W '*.bit' -k mykeyfile -t mydir \r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -W '*.bit' -k mykeyfile -t mydir \r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt all files with *.bit extension in mydir with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -r '^.*\\.bit$' -k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -r '^.*\\.bit$' -k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt all files excluding .bit extension in mydir with mykeyfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -r '(?!.*\\.bit$)^.*$' -k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -r '(?!.*\\.bit$)^.*$' -k mykeyfile -t mydir\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
	log("Create OTP Key file:\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj.CLUI --create-keyfile -K mykeyfile -S 268435456 # (256 MiB) echo $((1024**2*256))\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
	log("Key Device Examples (Linux):\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Create Key Device with 2 key partitions (e.g. on USB Mem Stick)\r\n", false, true, false, false, false);
        log("            # Beware: keyfile gets randomized before writing to Device\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --create-keydev -k mykeyfile -t /dev/sdb\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Print GUID Partition Table\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --print-gpt -t /dev/sdb\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Delete GUID Partition Table\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --delete-gpt -t /dev/sdb\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Clone Key Device (-k sourcekeydevice -t destinationkeydevice)\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --clone-keydev -k /dev/sdb -t /dev/sdc\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log("            # Encrypt / Decrypt myfile with raw key partition\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --encrypt -k /dev/sdb1 -t myfile\r\n", false, true, false, false, false);
        log("            java -cp FinalCrypt.jar rdj/CLUI --decrypt -k /dev/sdb1 -t myfile\r\n", false, true, false, false, false);
        log("\r\n", false, true, false, false, false);
        log(Version.getProductName() + " " + version.checkCurrentlyInstalledVersion(this) + " - Author: " + Version.getAuthor() + " - Copyright: " + Version.getCopyright() + "\r\n\r\n", false, true, false, false, false);
        System.exit(0);
    }

    @Override public void processGraph(int value) {  }

    @Override
    public void processStarted() 
    {
    }

    @Override public void processProgress(int filesProgress, int fileProgress, long bytesTotalParam, long bytesProcessedParam, double bytesPerMiliSecondParam)
    {
//        log("filesProgress: " + filesProgress + " fileProgress: " + fileProgress);
    }
    
    @Override public void processFinished(FCPathList openFCPathList, boolean open)
    {
    }

    @Override
    public void fileProgress()
    {
    }

//    @Override public void buildProgress(FCPathList targetFCPathList) {  }

    @Override
    public void buildReady(FCPathList fcPathListParam, boolean validBuild)
    {
	targetFCPathList = fcPathListParam;
    }
    
    private String getRuntimeEnvironment()
    {
	String env = "";
	
	String symbols = "";
	symbols += "Symbols:            ";
	symbols += FinalCrypt.UTF8_ENCRYPT_DESC + ": " + FinalCrypt.UTF8_ENCRYPT_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_DECRYPT_DESC + ": " + FinalCrypt.UTF8_DECRYPT_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_XOR_NOMAC_DESC + ": " + FinalCrypt.UTF8_XOR_NOMAC_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_CLONE_DESC + ": " + FinalCrypt.UTF8_CLONE_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_DELETE_DESC + ": " + FinalCrypt.UTF8_DELETE_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_PAUSE_DESC + ": " + FinalCrypt.UTF8_PAUSE_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_STOP_DESC + ": " + FinalCrypt.UTF8_STOP_SYMBOL + " ";
	symbols += FinalCrypt.UTF8_FINISHED_DESC + ": " + FinalCrypt.UTF8_FINISHED_SYMBOL + " ";
	
	env +=    "Welcome to:         " + Version.getProductName() + " " + version.getCurrentlyInstalledOverallVersionString() + " (CLUI)\r\n";
	env += "\r\n";
	env +=    "Interface:          rdj/CLUI\r\n";
	env +=    "Email:              " + Version.getAuthorEmail() + "\r\n";
	env +=    "Copyright:          " + Version.getCopyright() + " " + Version.getAuthor() + "\r\n";
	env +=    "Logfiles:           " + configuration.getLogDirPath().toString() + "\r\n";
	env +=    "Command line:       java -cp FinalCrypt.jar rdj/CLUI --help\r\n";
	env +=    "License:            " + Version.getLicense() + "\r\n";
	env += "\r\n";
	env +=    "OS Name:            " + System.getProperty("os.name") + "\r\n";
	env +=    "OS Architecture:    " + System.getProperty("os.arch") + "\r\n";
	env +=    "OS Version:         " + System.getProperty("os.version") + "\r\n";
	env +=    "OS Time:            " + configuration.getTime() + "\r\n";
	env += "\r\n";
	env +=    "Java Vendor:        " + System.getProperty("java.vendor") + "\r\n";
	env +=    "Java Version:       " + System.getProperty("java.version") + "\r\n";
	env +=    "Class Version:      " + System.getProperty("java.class.version") + "\r\n";
	env += "\r\n";
	env +=    "User Name:          " + System.getProperty("user.name") + "\r\n";
	env +=    "User Home:          " + System.getProperty("user.home") + "\r\n";
	env +=    "User Dir:           " + System.getProperty("user.dir") + "\r\n";
	env += "\r\n";
	env += symbols + "\r\n";
	env += "\r\n";
		
	return env;
    }
    
    @Override public void test(String message) { log(message, true, true, false, false, false); }
    
    @Override
    synchronized public void log(String message, boolean status, boolean log, boolean logfile, boolean errfile, boolean print)
    {
	if	((!status) && (!log))   {  }
	else if ((!status) && ( log))   { log(message,errfile); }
	else if (( status) && (!log))   {  }
	else if (( status) && ( log))	{ log(message,errfile); }
	if	(logfile)		{ logfile(message); }
	if	(errfile)		{ errfile(message); }
	if	(print)			{ print(message,errfile); }
    }

    public void status(String message)		    {  }
    public void log(String message, boolean err)    { if ( ! err ) { System.out.print(message); } else { System.err.print(message); } }
    public void logfile(String message)		    { try { Files.write(configuration.getLogFilePath(), message.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC); } catch (IOException ex) { log("Files.write(" + configuration.getLogFilePath() + ")..));", false, true, true, false, false); } }
    public void errfile(String message)		    { try { Files.write(configuration.getErrFilePath(), message.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC); } catch (IOException ex) { log("Files.write(" + configuration.getErrFilePath() + ")..));", false, true, true, false, false); } }
    public void print(String message, boolean err)  { if ( ! err ) { System.out.print(message); } else { System.err.print(message); } }
    
    public static void main(String[] args) { new CLUI(args); }
}

class ReaderThread extends Thread
{
    private CLUI clui;
    
    public ReaderThread(CLUI ui) { this.clui = ui; }
    
    @Override public void run()
    {
	clui.log("\r\nWould you like to see the User Manual (n/Y)? ", false, true, false, false, false); // Leave Error file to: true
        try(Scanner in = new Scanner(System.in))
	{
            String input = in.nextLine(); 
	    if ( input.trim().toLowerCase().equals("y") ) { clui.usage(true); } 
	    else if (( input.trim().toLowerCase().length() == 0 ) || ( input.toLowerCase().equals("\r\n") ))  { clui.usage(true); }
	    else { clui.log("\r\n", false, true, false, false, false); System.exit(1); }
        }
    }

}

class TimeoutThread extends Thread
{
    private CLUI clui;
    
    public TimeoutThread(CLUI ui) { this.clui = ui; }

    @Override public void run()
    {
        try {
            Thread.sleep(3000);
//            Robot robot = new Robot();
//            robot.keyPress(KeyEvent.VK_ENTER);
//            robot.keyRelease(KeyEvent.VK_ENTER);
        } catch(Exception e) { }
    }

}

class ConsoleEraser extends Thread
{
    private boolean running = true;
    public void run()		    { while (running) { System.err.print("\b "); try { Thread.currentThread().sleep(1); } catch(InterruptedException err) { break; } } }
    public synchronized void halt() { running = false; }
}