package com.globallink.api.sample;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.w3._2005._05.xmlmime.Base64Binary;

import com.globallink.api.GLExchange;
import com.globallink.api.config.ProjectDirectorConfig;
import com.globallink.api.model.Document;
import com.globallink.api.model.Project;
import com.globallink.api.model.ReferenceDocument;
import com.globallink.api.model.Submission;
import com.globallink.api.model.Target;

public class SampleUsage {

    private static String SEND = "-send";
    private static String RETRIEVE = "-retrieve";

    private static String SOURCE_ROOT_FOLDER = "resources/";
    private static String REFERENCE_ROOT_FOLDER = "references/";
    private static String TRANSLATED_FOLDER = "translated/";
    private static String CONFIG_FILE = "config.properties";

    private static String CONFIG_URL = "url";
    private static String CONFIG_USERNAME = "username";
    private static String CONFIG_PASSWORD = "password";
    private static String CONFIG_USERAGENT = "userAgent";
    private static String CONFIG_PROJECT = "project";
    private static String CONFIG_FILE_FORMAT = "fileFormatProfile";
    private static String CONFIG_PREFIX = "submissionPrefix";
    private static String CONFIG_SOURCE_LANGUAGE = "sourceLanguage";
    private static String CONFIG_TARGET_LANGUAGES = "targetLanguages";
    private static String CONFIG_SEPARATOR = ",";

    private static String DEFAULT_USER_AGENT = "glcapi.java";
    
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm");

    private static Properties properties = new Properties();

    public static void main(String[] args) throws Exception {
	System.setProperty("jsse.enableSNIExtension", "false");
	HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
		   @Override
		   public boolean verify(String hostName, SSLSession session) {
		      return true;
		   }
		});
	FileInputStream file = new FileInputStream(CONFIG_FILE);
	properties.load(file);
	file.close();
	try{
        	GLExchange pdClient = new GLExchange(getPDConfig());
        	for(Project project : pdClient.getProjects()){
        	    System.out.println("Project:"+project.getName()+":"+project.getShortcode());
        	}
	} catch(Exception e){
	    e.printStackTrace(System.out);
	    
	}
	
	if(args.length==0){
	    System.out.println("Use arg '"+SEND+"' for sending and/or '"+RETRIEVE+"' for retrieve");
	}

	for (String arg : args) {
	    if (SEND.equalsIgnoreCase(arg)) {
		doSend();
	    }
	    if (RETRIEVE.equalsIgnoreCase(arg)) {
		doRetrieve();
	    }
	}
    }
    
    private static void doSend() throws Exception {
	System.out.println("Starting send");
	GLExchange pdClient = new GLExchange(getPDConfig());

	String sourceLanguage = properties.getProperty(CONFIG_SOURCE_LANGUAGE);
	if (isEmpty(sourceLanguage)) {
	    throw new Exception("Configuration option '" + CONFIG_SOURCE_LANGUAGE + "' is not set");
	}
	String targetLanguagesStr = properties.getProperty(CONFIG_TARGET_LANGUAGES);
	if (isEmpty(targetLanguagesStr)) {
	    throw new Exception("Configuration option '" + CONFIG_TARGET_LANGUAGES + "' is not set");
	}
	String fileFormat = properties.getProperty(CONFIG_FILE_FORMAT);
	if (isEmpty(fileFormat)) {
	    throw new Exception("Configuration option '" + CONFIG_FILE_FORMAT + "' is not set");
	}
	Set<String> targetLanguages = new HashSet<String>();
	if (targetLanguagesStr.indexOf(CONFIG_SEPARATOR) > 0) {
	    String[] langsArray = targetLanguagesStr.split(CONFIG_SEPARATOR);
	    if (langsArray.length > 0) {
		for (String lang : langsArray) {
		    if (lang.trim().length() > 1) {
			targetLanguages.add(lang.trim());
		    }
		}
	    }
	} else {
	    if (targetLanguagesStr.trim().length() > 1) {
		targetLanguages.add(targetLanguagesStr.trim());
	    }
	}
	if (targetLanguages.size() <= 0) {
	    throw new Exception("Not able to find target languages");
	}

	String shortcode = properties.getProperty(CONFIG_PROJECT);
	if (isEmpty(shortcode)) {
	    throw new Exception("Configuration option '" + CONFIG_PROJECT + "' is not set");
	}
	Project project = pdClient.getProject(shortcode);
	Submission submission = new Submission();
	String prefix = properties.getProperty(CONFIG_PREFIX);
	submission.setName((isEmpty(prefix) ? "" : prefix) + "GLCAPI-" + dateFormat.format(new Date()));
	submission.setProject(project);
	submission.setPmNotes("Sample PM Note");
	submission.setInstructions("some subm instr");
	submission.setDueDate(new Date((new Date()).getTime() + (60 * 60 * 24 * 5L)));
	
	pdClient.initSubmission(submission);

	StringBuffer report = new StringBuffer();
	File folder = new File(SOURCE_ROOT_FOLDER + sourceLanguage);
	if (!folder.exists()) {
	    throw new Exception("Directory '" + folder + "' not found");
	}
	if (folder.listFiles().length <= 0) {
	    throw new Exception("Directory '" + folder + "' is empty");
	}
	for (File fileEntry : folder.listFiles()) {
	    Document document = new Document();
	    document.setData(new FileInputStream(fileEntry));
	    
	    Base64Binary data = new Base64Binary();
	    data.setValue(document.getData());
	    String filename = fileEntry.getName();
	    document.setFileformat(fileFormat);
	    document.setName(filename);
	    document.setSourceLanguage(sourceLanguage);
	    document.setTargetLanguages(targetLanguages.toArray(new String[targetLanguages.size()]));
	    String ticket = pdClient.uploadTranslatable(document);
	    if (ticket != null) {
		report.append(filename + " -> " + ticket + "\n");
	    }
	}
	
	folder = new File(REFERENCE_ROOT_FOLDER);
	if (folder.exists() && folder.listFiles().length > 0) {
        	for (File fileEntry : folder.listFiles()) {
        	    ReferenceDocument document = new ReferenceDocument();
        	    document.setData(new FileInputStream(fileEntry));
        	    document.setName(fileEntry.getName());
        	    pdClient.uploadReference(document);
        	}
	}
	System.out.println(report);

	String[] submissionTicket = pdClient.startSubmission();
	System.out.println("Started submission : " + submission.getName() + " [" + submissionTicket[0] + "]");
	int max = 100;
	
	while (max > 0) {
	    if (pdClient.isSubmissionComplete(submissionTicket[0])) {
		System.out.println("Comleted.");
		break;
	    }
	    max--;
	    Thread.sleep(2000);
	}
    }

    private static void doRetrieve() throws Exception {
	System.out.println("Starting retrieve");
	GLExchange pdClient = new GLExchange(getPDConfig());

	String shortcode = properties.getProperty(CONFIG_PROJECT);
	if (isEmpty(shortcode)) {
	    throw new Exception("Configuration option '" + CONFIG_PROJECT + "' is not set");
	}
	Target[] targets = pdClient.getCompletedTargets(1000);
	System.out.println("Found " + targets.length + " completed targets");

	StringBuffer report = new StringBuffer();
	for (Target target : targets) {
	    try {
		System.out.println(target.getDocumentName()+":"+target.getDocumentTicket());
		String docTicket = target.getTicket();
		InputStream translatedText = pdClient.downloadCompletedTarget(docTicket);
		saveFile(target, translatedText); // Do the processing that you need with the translated contents.
		report.append(target.getDocumentName() + " [" + target.getTicket() + "] downloaded. \n"); // On
													  // successful
													  // processing,
													  // send
													  // confirmation
		//Uncomment below to send download confirmation and mark target as Delivered
		//pdClient.sendDownloadConfirmation(target.getTicket());
	    } catch (Exception e) {
		System.out.println("Problem processing " + target.getDocumentName());
		System.out.println(e);
	    }

	}
	System.out.println(report);

    }

    private static void saveFile(Target target, InputStream inputStream) throws IOException {
	File folder = new File(SOURCE_ROOT_FOLDER + TRANSLATED_FOLDER);
	if (!folder.exists()) {
	    folder.mkdirs();
	}

	String fileName = folder + "/" + target.getTargetLocale() + "_" + target.getDocumentName();
	File targetFile = new File(fileName);
	FileOutputStream outputStream = new FileOutputStream(targetFile);
	int read = 0;
	byte[] bytes = new byte[1024];

	while ((read = inputStream.read(bytes)) != -1) {
	    outputStream.write(bytes, 0, read);
	}
	outputStream.close();
    }
    

    private static Boolean isEmpty(String string) {
	if (string == null || string.trim().length() <= 0) {
	    return true;
	} else {
	    return false;
	}
    }

    private static ProjectDirectorConfig getPDConfig() throws Exception {
	ProjectDirectorConfig config = new ProjectDirectorConfig();
	String url = properties.getProperty(CONFIG_URL);
	if (isEmpty(url)) {
	    throw new Exception("Configuration option '" + CONFIG_URL + "' is not set");
	} else {
	    config.setUrl(url);
	}
	
	System.out.println(url);

	String username = properties.getProperty(CONFIG_USERNAME);
	if (isEmpty(username)) {
	    throw new Exception("Configuration option '" + CONFIG_USERNAME + "' is not set");
	} else {
	    config.setUsername(username);
	}

	String password = properties.getProperty(CONFIG_PASSWORD);
	if (isEmpty(password)) {
	    throw new Exception("Configuration option '" + CONFIG_PASSWORD + "' is not set");
	} else {
	    config.setPassword(password);
	}

	String userAgent = properties.getProperty(CONFIG_USERAGENT);
	if (isEmpty(userAgent)) {
	    System.out.println(CONFIG_USERAGENT + " is not set. Using default '" + DEFAULT_USER_AGENT + "'.");
	    userAgent = DEFAULT_USER_AGENT;
	}
	config.setUserAgent(userAgent);

	return config;
    }

}
