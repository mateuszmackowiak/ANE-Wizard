package pl.mateuszmackowiak.ane.wizard.android;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import pl.mateuszmackowiak.ane.wizard.android.WizardNewProjectWithAirSDKLocationPage.Platform;



public class NewANEMainProjectWizard extends Wizard implements INewWizard
{
	private WizardNewProjectWithAirSDKLocationPage wizardPage;
	private IConfigurationElement config;
	private IWorkbench workbench;
	private IStructuredSelection selection;
	private IProject project;

	/**
	 * Constructor
	 */
	public NewANEMainProjectWizard()
	{
		super();
	}
	
	
	
	
	/**
	 * Adding the page to the wizard.
	 */
	public void addPages() 
	{
		wizardPage = new WizardNewProjectWithAirSDKLocationPage("NewANEMainProject");
		wizardPage.setDescription("Create a new ANE Main Project");
		wizardPage.setTitle("New ANE Main Project");
		wizardPage.setInitialPackageName("com.yourcompany");
		addPage(wizardPage);
	}

	/**
	 * This method is called when 'Finish' button is pressed in
	 * the wizard. We will create an operation and run it
	 * using wizard as execution context.
	 */
	@Override
	public boolean performFinish()
	{
		if (project != null) return true;
		
		final IProject projectHandle = wizardPage.getProjectHandle();
		final String AirSDKLocation = wizardPage.getSDKLocationName();
		final String packageName = wizardPage.getPackageName();
		final URI projectURI = (!wizardPage.useDefaults()) ? wizardPage.getLocationURI() : null;
		final IProjectDescription desc = ResourcesPlugin.getWorkspace().newProjectDescription(projectHandle.getName());
		desc.setLocationURI(projectURI);
		final Set<Platform> platforms= wizardPage.getPlatformsEnabled();
		try 
        {
	        try{
	        	System.setProperty(WizardNewProjectWithAirSDKLocationPage.aneAIRSDKPathKey, AirSDKLocation);
	        }catch(Exception e){}
        
        
            getContainer().run(true, true, new WorkspaceModifyOperation() {
                protected void execute(IProgressMonitor monitor) throws CoreException 
                {
                    createProject(desc, projectHandle, monitor,AirSDKLocation, packageName, platforms);
                }
            });
            
            
            getContainer().run(true, true, new WorkspaceModifyOperation() {
                protected void execute(IProgressMonitor monitor) throws CoreException 
                {
                	try 
                    {
                		String projectName = projectHandle.getName();
    	            	final IProjectDescription descript = ResourcesPlugin.getWorkspace().newProjectDescription(projectName+"_AS");
    	            	if(projectURI!=null){
    	            		descript.setLocationURI( new URI(projectURI.toURL().toString()+"/AS"));
    	            	}else{
    	            		descript.setLocationURI( new URI((ResourcesPlugin.getWorkspace().getRoot().getLocation().toString()+"/"+projectHandle.getName()+"/AS").replace(" ", "%20")));
    	            	}
    	            	
    	            	IProject proj = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName+"_AS");
    	            	if(proj==null){
    	            		throw new Exception("proj null");
    	            	}
                        monitor.beginTask("", 2000);
                        proj.create(descript, new SubProgressMonitor(monitor, 1000));
                        if (monitor.isCanceled()) throw new OperationCanceledException();
                        proj.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 1000));
                        
                        createASProject(projectName, packageName, monitor, (IContainer) proj,AirSDKLocation, platforms);
                    }
                    catch (Exception ioe) 
                    {
            			IStatus status = new Status(IStatus.ERROR, "NewFileWizard", IStatus.OK,
            					ioe.toString(), null);
            			throw new CoreException(status);
            		}
                    finally
                    {
                        monitor.done();
                    }
                }
            });
        } 
        catch (InterruptedException e)
        {
            return false;
        } 
        catch (InvocationTargetException e) 
        {
            Throwable realException = e.getTargetException();
            MessageDialog.openError(getShell(), "Error", realException.getMessage());
            return false;
        } catch (Exception e1) {
        	MessageDialog.openError(getShell(), "Error", e1.toString());
        	return false;
		}
        
        project = projectHandle;

        if (project == null) return false;
        
        BasicNewProjectResourceWizard.updatePerspective(config);
        BasicNewProjectResourceWizard.selectAndReveal(project, workbench.getActiveWorkbenchWindow());

        return true;
	}
	
	
	void createASProject(String projectName,String packageName, IProgressMonitor monitor, IContainer container,String AirSDKLocation, Set<Platform> platforms) throws CoreException, IOException{


		createFolder("src", container, monitor);
		createFolder("bin", container, monitor);
		createFolder("libs", container, monitor);

		 String fullPackageString = "src";
         //Create folders if package not empty
         if(packageName!=null && !packageName.isEmpty()){
         	String[] packages = packageName.split("\\.");
         	
         	for (String packageElement : packages) {
         		fullPackageString+="/"+packageElement;
         		createFolder(fullPackageString, container, monitor);
				}   
         }
         copyFile(".project", ".project", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
         copyFile(".actionScriptProperties", ".actionScriptProperties", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
         copyFile(".flexLibProperties", ".flexLibProperties", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
        
         
         copyFile("Main.as", fullPackageString+"/"+ projectName + ".as", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
	}
	/**
     * This creates the project in the workspace.
     * 
     * @param description
     * @param projectHandle
     * @param monitor
     * @throws CoreException
     * @throws OperationCanceledException
     */
    void createProject(IProjectDescription description, IProject proj, IProgressMonitor monitor,String AirSDKLocation ,String packageName, Set<Platform> platforms) throws CoreException, OperationCanceledException 
	{
        try 
        {
            monitor.beginTask("", 2000);
            
            
            proj.create(description, new SubProgressMonitor(monitor, 1000));
            
            if (monitor.isCanceled()) throw new OperationCanceledException();
            
            proj.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(monitor, 1000));
            
            /*
             * Okay, now we have the project and we can do more things with it
             * before updating the perspective.
             */
            IContainer container = (IContainer) proj;
            
           
            // Create folders
            createFolder("AS", container, monitor);
            if(platforms!=null && platforms.size()>0){
            	for (Platform platform : platforms) {
					createFolder(platform.getFolderName(), container, monitor);
				}
            }
            
            // Copy files
            String projectName = description.getName();
            copyFile("build.properties.resources", "build.properties", projectName,packageName,AirSDKLocation, container, monitor, true,platforms);
            copyFile("build.xml.resources", "build.xml", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
            
            realCopyFile(container,"ant-contrib.jar",".ant-contrib.jar",monitor);
            //copyFile("ant-contrib.jar.resources", ".ant-contrib.jar", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
            //copyFile("extension.xml", "extension.xml", projectName,packageName,AirSDKLocation, container, monitor, false,platforms);
            
            
            InputStream is = createExtensionXMLInputStream(platforms,packageName,projectName);
            IFile file = container.getFile(new Path("extension.xml"));
            file.create(is, true, monitor);
    		
            try{
	            description.setName(projectName+"_AS");
	            
	            description.setLocation(new Path(container.getProjectRelativePath()+"/AS"));
	            proj.create(description, new SubProgressMonitor(monitor, 1000));
            }catch(Exception e){
            	System.out.println(e.toString());
            }
        }
        catch (Exception ioe) 
        {
			IStatus status = new Status(IStatus.ERROR, "NewFileWizard", IStatus.OK,
					ioe.toString(), null);
			throw new CoreException(status);
		}
        finally
        {
            monitor.done();
        }
    }
    
    

    
    private void createFolder(String path, IContainer container, IProgressMonitor monitor) throws CoreException
    {
    	final IFolder folder = container.getFolder(new Path(path));
    	if (!folder.exists())
    	{
    		folder.create(true, true, monitor);
    	}
    }
    
    
    
    private InputStream createExtensionXMLInputStream(Set<Platform> platformsList, String packageName, String projectName) throws ParserConfigurationException, TransformerException, UnsupportedEncodingException{
    	DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        ////////////////////////
        //Creating the XML tree

        //create the root element and add it to the document
        Element root = doc.createElement("extension");
        root.setAttribute("xmlns", "http://ns.adobe.com/air/extension/3.1");
        doc.appendChild(root);
        
        Element id = doc.createElement("id");
        id.appendChild(doc.createTextNode(packageName+"."+projectName));
        root.appendChild(id);
        
        Element versionNumber = doc.createElement("versionNumber");
        versionNumber.appendChild(doc.createTextNode("1"));
        root.appendChild(versionNumber);
        
        Element platforms = doc.createElement("platforms");
       
        
        platforms.appendChild(createPlatfomXMLNode(doc,platformsList, Platform.AndroidARM
    			, "lib"+projectName+".jar"
    			, packageName+"."+projectName+"Extension"
    			, packageName+"."+projectName+"Extension"));
        
        platforms.appendChild(createPlatfomXMLNode(doc,platformsList, Platform.IOS_ARM
    			, "lib"+projectName+".a"
    			, projectName+"ExtInitializer"
    			, projectName+"ExtFinalizer"));

        platforms.appendChild(createPlatfomXMLNode(doc,platformsList, Platform.IOS_x86
    			, "lib"+projectName+".a"
    			, projectName+"ExtInitializer"
    			, projectName+"ExtFinalizer"));

        platforms.appendChild(createPlatfomXMLNode(doc,platformsList, Platform.Windows_x86
    			, "lib"+projectName+".dll"
    			, projectName+"ExtInitializer"
    			, projectName+"ExtFinalizer"));

        platforms.appendChild(createPlatfomXMLNode(doc,platformsList, Platform.MacOS_x86
    			, "lib"+projectName+".framework"
    			, projectName+"ExtInitializer"
    			, projectName+"ExtFinalizer"));
        

    	
        //default
        Element platform = doc.createElement("platform");
        platform.setAttribute("name", "default");
        platform.appendChild(doc.createElement("applicationDeployment"));
        platforms.appendChild(platform);
        
        root.appendChild(platforms);
        
        DOMSource source = new DOMSource(doc);
    	StringWriter xmlAsWriter = new StringWriter();
    	StreamResult result = new StreamResult(xmlAsWriter);
    	Transformer trans = TransformerFactory.newInstance().newTransformer();
    	trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        trans.setOutputProperty(OutputKeys.INDENT, "yes");
    	trans.transform(source, result);
    	
    	return new ByteArrayInputStream(xmlAsWriter.toString().getBytes("UTF-8"));
        
    }
    
    private Node createPlatfomXMLNode(Document doc,Set<Platform> platforms, Platform platform, String nativeLib, String ini, String fin){
    	
    	
	    	Element platformElement,applicationDeployment, nativeLibrary, initializer, finalizer;
	        
	    	platformElement = doc.createElement("platform");
	    	
	    	platformElement.setAttribute("name", platform.getValue());
	     	applicationDeployment = doc.createElement("applicationDeployment");
	     	
	     	nativeLibrary = doc.createElement("nativeLibrary");
	     	nativeLibrary.appendChild(doc.createTextNode(nativeLib));
	     	applicationDeployment.appendChild(nativeLibrary);
	     	
	     	initializer = doc.createElement("initializer");
	     	initializer.appendChild(doc.createTextNode(ini));
	     	applicationDeployment.appendChild(initializer);
	     	
	     	finalizer = doc.createElement("finalizer");
	     	finalizer.appendChild(doc.createTextNode(fin));
	     	applicationDeployment.appendChild(finalizer);
	     	
	     	platformElement.appendChild(applicationDeployment);
	     	
	     if(isPlatformEnabled(platforms, platform)){
	     	return platformElement;
    	}else{
    		//TODO: element to comment
    		return doc.createComment(nodeToString(platformElement));
    	}
    }
    
    private static String nodeToString(Node node) {
        StringWriter sw = new StringWriter();
        try {
          Transformer t = TransformerFactory.newInstance().newTransformer();
          t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
          t.setOutputProperty(OutputKeys.INDENT, "yes");
          t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
          System.out.println("nodeToString Transformer Exception");
        }
        return sw.toString();
      }
    
    private void copyFile(String resourceName, String finalPath, String projectName,String packageName, String AirSDKLocation, IContainer container, IProgressMonitor monitor, boolean lookForPlatformsChange, Set<Platform> platforms) throws IOException, CoreException
    {
    	InputStream resourceStream = openFilteredResource(resourceName, projectName,packageName,AirSDKLocation, lookForPlatformsChange,platforms);
        addFileToProject(container, new Path(finalPath), resourceStream, monitor);
        resourceStream.close();
    }
    
    
    private void realCopyFile(IContainer container, String resourceName, String finalPath, IProgressMonitor monitor) throws IOException, CoreException{
    	InputStream resourceStream = this.getClass().getResourceAsStream("resources/" + resourceName);
    	addFileToProject(container, new Path(finalPath), resourceStream, monitor);
        resourceStream.close();
    	  
    }
    /**
     * Adds a new file to the project.
     * 
     * @param container
     * @param path
     * @param contentStream
     * @param monitor
     * @throws CoreException
     */
    private void addFileToProject(IContainer container, Path path, InputStream contentStream, IProgressMonitor monitor) throws CoreException 
	{
        final IFile file = container.getFile(path);

        if (file.exists()) 
        {
            file.setContents(contentStream, true, true, monitor);
        } 
        else 
        {
            file.create(contentStream, true, monitor);
        }

    }
    private boolean isPlatformEnabled(Set<Platform>platforms, Platform platform)
    {
    	if(platform==null || platforms==null || platforms.size()==0)
    		return false;
    	for (Platform plat : platforms) {
			if(plat ==platform){
				return true;
			}
		}
    	return false;
    }
    
    private InputStream openFilteredResource(String resourceName, String projectName, String packageName,String AirSDKLocation,boolean lookForPlatformsChange,Set<Platform>platforms) throws CoreException
    {
    	final String newline = "\n";
        String line;
        StringBuffer sb = new StringBuffer();
        
        String packName = "";
        if(packageName!=null && !packageName.isEmpty()){
        	packName = packageName;
        }
        
        try 
        {
            InputStream input = this.getClass().getResourceAsStream("resources/" + resourceName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            try {

                while ((line = reader.readLine()) != null) {
                	
                	line = line.replaceAll("\\$\\{MYPACKAGE_NAME\\}", packName);
                	
                	if(lookForPlatformsChange){
	                	//platforms
	                	line = line.replaceAll("\\$\\{MYIOS_ARM_FOLDER\\}", Platform.IOS_ARM.getFolderName());
	                	line = line.replaceAll("\\$\\{MYIOS_X86_FOLDER\\}", Platform.IOS_x86.getFolderName());
	                	line = line.replaceAll("\\$\\{MYANDROID_ARM_FOLDER\\}", Platform.AndroidARM.getFolderName());
	                	line = line.replaceAll("\\$\\{MYMACOS_X86_FOLDER\\}", Platform.MacOS_x86.getFolderName());
	                	line = line.replaceAll("\\$\\{MYQNX_ARM_FOLDER\\}", Platform.QNX_ARM.getFolderName());
	                	line = line.replaceAll("\\$\\{MYWINDOWS_X86_FOLDER\\}", Platform.Windows_x86.getFolderName());
	                	
	                	line = line.replaceAll("\\$\\{MYIOS_ARM_ENABLE\\}", String.valueOf(isPlatformEnabled(platforms,Platform.IOS_ARM)));
	                	line = line.replaceAll("\\$\\{MYIOS_X86_ENABLE\\}", String.valueOf(isPlatformEnabled(platforms,Platform.IOS_x86)));
	                	line = line.replaceAll("\\$\\{MYANDROID_ARM_ENABLE\\}", String.valueOf(isPlatformEnabled(platforms,Platform.AndroidARM)));
	                	line = line.replaceAll("\\$\\{MYMACOS_X86_ENABLE\\}", String.valueOf(isPlatformEnabled(platforms,Platform.MacOS_x86)));
	                	line = line.replaceAll("\\$\\{MYQNX_ARM_ENABLE\\}",  String.valueOf(isPlatformEnabled(platforms,Platform.QNX_ARM)));
	                	line = line.replaceAll("\\$\\{MYWINDOWS_X86_ENABLE\\}",  String.valueOf(isPlatformEnabled(platforms,Platform.Windows_x86)));
                	}
                	//line = line.replaceAll("\\$\\{VERSION\\}", "1");
                	line = line.replaceAll("\\$\\{MYAIR_SDK_PATH\\}", AirSDKLocation);
                    line = line.replaceAll("\\$\\{MYPROJECT_NAME\\}", projectName);
                    sb.append(line);
                    sb.append(newline);
                }

            } finally {
                reader.close();
            }

        } 
        catch (IOException ioe) 
        {
            IStatus status = new Status(IStatus.ERROR, "ExampleWizard", IStatus.OK, ioe.getLocalizedMessage(), null);
            throw new CoreException(status);
        }

        return new ByteArrayInputStream(sb.toString().getBytes());
    }
    
	/**
	 * We will accept the selection in the workbench to see if
	 * we can initialize from it.
	 * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) 
	{
		this.workbench = workbench;
		this.selection = selection;
	}
	
	/**
	 * Sets the initialization data for the wizard.
	 */
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException 
	{
		this.config = config;
	}
}